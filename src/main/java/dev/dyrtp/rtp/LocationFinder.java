package dev.dyrtp.rtp;

import dev.dyrtp.DyRTP;
import dev.dyrtp.protection.ProtectionSnapshot;
import dev.dyrtp.protection.ProtectionService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

public class LocationFinder {

    private final DyRTP plugin;
    private final ProtectionService protection;
    private final ChunkLoader chunkLoader;
    private final SafeLocationChecker safeLocationChecker;

    public LocationFinder(DyRTP plugin, ProtectionService protection) {
        this.plugin = plugin;
        this.protection = protection;
        this.chunkLoader = new ChunkLoader(plugin);
        this.safeLocationChecker = new SafeLocationChecker();
    }

    public CompletableFuture<SearchResult> find(World world, Location origin) {
        CompletableFuture<SearchResult> future = new CompletableFuture<>();
        WorldProfile profile = WorldProfile.load(plugin, world);
        SearchState state = new SearchState(profile, SafetyRules.load(plugin, profile), protection.snapshot(), origin, future);
        runMain(() -> attemptBatch(state));
        return future;
    }

    private void attemptBatch(SearchState state) {
        if (state.future.isDone()) {
            return;
        }

        int maxAttempts = Math.max(1, plugin.getConfig().getInt("rtp.search.max-attempts", 160));
        int batchSize = Math.max(1, plugin.getConfig().getInt("rtp.search.batch-size", 24));
        boolean preferLoadedChunks = plugin.getConfig().getBoolean("rtp.search.prefer-loaded-chunks", true);
        int launched = 0;

        while (launched < batchSize && state.attempts < maxAttempts) {
            if (state.attempts >= maxAttempts) {
                state.future.complete(new SearchResult(null, state.attempts));
                return;
            }

            Candidate candidate = candidate(state.profile);
            state.attempts++;
            int chunkX = candidate.x >> 4;
            int chunkZ = candidate.z >> 4;

            if (chunkProtected(state, chunkX, chunkZ)) {
                continue;
            }

            if (preferLoadedChunks && state.profile.world().isChunkLoaded(chunkX, chunkZ)) {
                launched++;
                Location safe = checkCandidate(state, candidate);
                if (safe != null) {
                    completeFound(state, safe);
                    return;
                }
                continue;
            }

            launched++;
            state.inFlight++;
            chunkLoader.load(state.profile.world(), chunkX, chunkZ).whenComplete((chunk, throwable) ->
                    runMain(() -> {
                        state.inFlight--;
                        if (state.future.isDone()) {
                            return;
                        }
                        if (throwable != null) {
                            continueSearch(state, maxAttempts);
                            return;
                        }
                        Location safe = checkCandidate(state, candidate);
                        if (safe != null) {
                            completeFound(state, safe);
                        } else {
                            continueSearch(state, maxAttempts);
                        }
                    }));
        }

        if (state.inFlight == 0) {
            continueSearch(state, maxAttempts);
        }
    }

    private void continueSearch(SearchState state, int maxAttempts) {
        if (state.future.isDone() || state.inFlight > 0) {
            return;
        }
        if (state.attempts >= maxAttempts) {
            state.future.complete(new SearchResult(null, state.attempts));
            return;
        }
        runMain(() -> attemptBatch(state));
    }

    private Location checkCandidate(SearchState state, Candidate candidate) {
        Location safe = safeLocationChecker.findSafe(state.profile, state.rules, candidate.x, candidate.z);
        if (safe == null || tooCloseToOrigin(state, safe)) {
            return null;
        }
        return state.protection.isLocationProtected(safe) ? null : safe;
    }

    private void completeFound(SearchState state, Location safe) {
        boolean waitForPreload = plugin.getConfig().getBoolean("rtp.search.wait-for-preload", false);
        if (!waitForPreload) {
            state.future.complete(new SearchResult(safe, state.attempts));
            chunkLoader.preload(safe.getWorld(), safe.getBlockX() >> 4, safe.getBlockZ() >> 4);
            return;
        }
        chunkLoader.preload(safe.getWorld(), safe.getBlockX() >> 4, safe.getBlockZ() >> 4)
                .whenComplete((ignored, preloadError) -> runMain(() -> {
                    if (!state.future.isDone()) {
                        state.future.complete(new SearchResult(safe, state.attempts));
                    }
                }));
    }

    private boolean chunkProtected(SearchState state, int chunkX, int chunkZ) {
        long key = (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
        Boolean cached = state.chunkProtectionCache.get(key);
        if (cached != null) {
            return cached;
        }
        boolean protectedChunk = state.protection.isChunkProtected(state.profile.world(), chunkX, chunkZ);
        state.chunkProtectionCache.put(key, protectedChunk);
        return protectedChunk;
    }

    private void runMain(Runnable runnable) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
        } else {
            plugin.getServer().getScheduler().runTask(plugin, runnable);
        }
    }

    private Candidate candidate(WorldProfile profile) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double centerX = profile.centerX();
        double centerZ = profile.centerZ();
        int minRadius = profile.minRadius();
        int maxRadius = profile.maxRadius();

        if (profile.useWorldBorder()) {
            centerX = profile.world().getWorldBorder().getCenter().getX();
            centerZ = profile.world().getWorldBorder().getCenter().getZ();
            int borderRadius = Math.max(1, (int) Math.floor(profile.world().getWorldBorder().getSize() / 2.0D) - 2);
            maxRadius = Math.min(maxRadius, Math.max(minRadius + 1, borderRadius));
        }

        maxRadius = Math.max(minRadius + 1, maxRadius);
        int x;
        int z;

        if ("circle".equalsIgnoreCase(profile.shape())) {
            double minSquared = (double) minRadius * (double) minRadius;
            double maxSquared = (double) maxRadius * (double) maxRadius;
            double radius = Math.sqrt(random.nextDouble(minSquared, maxSquared + 1.0D));
            double angle = random.nextDouble(0, Math.PI * 2.0D);
            x = (int) Math.round(centerX + Math.cos(angle) * radius);
            z = (int) Math.round(centerZ + Math.sin(angle) * radius);
        } else {
            int dx;
            int dz;
            int tries = 0;
            do {
                dx = random.nextInt(-maxRadius, maxRadius + 1);
                dz = random.nextInt(-maxRadius, maxRadius + 1);
                tries++;
            } while (Math.max(Math.abs(dx), Math.abs(dz)) < minRadius && tries < 24);
            if (Math.max(Math.abs(dx), Math.abs(dz)) < minRadius) {
                int edge = random.nextInt(4);
                int side = random.nextBoolean() ? 1 : -1;
                int offset = random.nextInt(-maxRadius, maxRadius + 1);
                if (edge == 0) {
                    dx = side * minRadius;
                    dz = offset;
                } else if (edge == 1) {
                    dx = side * maxRadius;
                    dz = offset;
                } else if (edge == 2) {
                    dx = offset;
                    dz = side * minRadius;
                } else {
                    dx = offset;
                    dz = side * maxRadius;
                }
            }
            x = (int) Math.round(centerX + dx);
            z = (int) Math.round(centerZ + dz);
        }

        return new Candidate(x, z);
    }

    private boolean tooCloseToOrigin(SearchState state, Location location) {
        if (state.origin == null || state.origin.getWorld() == null || location.getWorld() == null) {
            return false;
        }
        if (!state.origin.getWorld().equals(location.getWorld())) {
            return false;
        }
        int minDistance = state.rules.minDistanceFromCurrent();
        if (minDistance <= 0) {
            return false;
        }
        double dx = state.origin.getX() - location.getX();
        double dz = state.origin.getZ() - location.getZ();
        return (dx * dx) + (dz * dz) < (double) minDistance * (double) minDistance;
    }

    private record Candidate(int x, int z) {
    }

    private static class SearchState {
        private final WorldProfile profile;
        private final SafetyRules rules;
        private final ProtectionSnapshot protection;
        private final Location origin;
        private final CompletableFuture<SearchResult> future;
        private final Map<Long, Boolean> chunkProtectionCache = new HashMap<>();
        private int attempts;
        private int inFlight;

        private SearchState(WorldProfile profile, SafetyRules rules, ProtectionSnapshot protection, Location origin, CompletableFuture<SearchResult> future) {
            this.profile = profile;
            this.rules = rules;
            this.protection = protection;
            this.origin = origin;
            this.future = future;
        }
    }

    public record SearchResult(Location location, int attempts) {
    }
}

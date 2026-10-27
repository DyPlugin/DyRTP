package dev.dyrtp.rtp;

import dev.dyrtp.DyRTP;
import dev.dyrtp.platform.Scheduler;
import dev.dyrtp.protection.ProtectionSnapshot;
import dev.dyrtp.protection.ProtectionService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class LocationFinder {

    private final DyRTP plugin;
    private final ProtectionService protection;
    private final ChunkLoader chunkLoader;
    private final SafeLocationChecker safeLocationChecker;
    private final Set<CompletableFuture<SearchResult>> searches = ConcurrentHashMap.newKeySet();

    public LocationFinder(DyRTP plugin, ProtectionService protection) {
        this.plugin = plugin;
        this.protection = protection;
        this.chunkLoader = new ChunkLoader(plugin);
        this.safeLocationChecker = new SafeLocationChecker();
    }

    public CompletableFuture<SearchResult> find(World world, Location origin) {
        return find(world, origin, null);
    }

    /** Searches for a safe spot; with a biome, only that biome qualifies and the biome attempt limit applies. */
    public CompletableFuture<SearchResult> find(World world, Location origin, String biome) {
        return find(world, origin, biome, 0);
    }

    /** maxRadius above 0 replaces the world's farthest distance for this search (dyrtp.radius.<blocks>). */
    public CompletableFuture<SearchResult> find(World world, Location origin, String biome, int maxRadius) {
        CompletableFuture<SearchResult> future = new CompletableFuture<>();
        runMain(() -> {
            try {
                int maximum = Math.max(1, Math.min(64, plugin.getConfig().getInt("rtp.search.max-concurrent-searches", 16)));
                if (searches.size() >= maximum || !plugin.isEnabled()) {
                    future.complete(new SearchResult(null, 0));
                    return;
                }
                searches.add(future);
                future.whenComplete((result, error) -> searches.remove(future));
                WorldProfile profile = WorldProfile.load(plugin, world).withMaxRadius(maxRadius);
                SearchState state = new SearchState(profile, SafetyRules.load(plugin, profile).withBiome(biome), protection.snapshot(), origin, future);
                state.maxAttempts = biome == null
                        ? Math.max(1, plugin.getConfig().getInt("rtp.search.max-attempts", 160))
                        : Math.max(1, Math.min(10000, plugin.getConfig().getInt("biome-selection.max-attempts", 1000)));
                long timeout = Math.max(1, Math.min(300, biome == null
                        ? plugin.getConfig().getLong("rtp.search.timeout-seconds", 30)
                        : plugin.getConfig().getLong("biome-selection.timeout-seconds", 60)));
                future.orTimeout(timeout, TimeUnit.SECONDS);
                attemptSafely(state);
            } catch (RuntimeException error) {
                future.completeExceptionally(error);
            }
        });
        return future;
    }

    /** A queued spot: its chunk is loaded first, then it is handed out like a search result with zero attempts. */
    public CompletableFuture<SearchResult> ready(Location spot) {
        return chunkLoader.load(spot.getWorld(), spot.getBlockX() >> 4, spot.getBlockZ() >> 4)
                .thenApply(chunk -> new SearchResult(spot, 0));
    }

    /** Fresh checks on the server thread; never trusts the search's claim cache. */
    public boolean isSafeNow(Location target, Location origin) {
        if (!Bukkit.isPrimaryThread() || target == null || target.getWorld() == null) {
            return false;
        }
        try {
            World world = target.getWorld();
            if (plugin.getConfig().getStringList("rtp.disabled-worlds").contains(world.getName())) return false;
            if (!world.isChunkLoaded(target.getBlockX() >> 4, target.getBlockZ() >> 4)) {
                return false;
            }
            WorldProfile profile = WorldProfile.load(plugin, world);
            SafetyRules rules = SafetyRules.load(plugin, profile);
            if (!safeLocationChecker.isSafeAt(profile, rules, target) || protection.isLocationProtected(target)) {
                return false;
            }
            if (origin != null && world.equals(origin.getWorld())) {
                double dx = target.getX() - origin.getX();
                double dz = target.getZ() - origin.getZ();
                if (dx * dx + dz * dz < (double) rules.minDistanceFromCurrent() * rules.minDistanceFromCurrent()) {
                    return false;
                }
            }
            return true;
        } catch (RuntimeException error) {
            return false;
        }
    }

    private void attemptSafely(SearchState state) {
        try {
            attemptBatch(state);
        } catch (RuntimeException error) {
            state.future.completeExceptionally(error);
        }
    }

    private void attemptBatch(SearchState state) {
        if (state.future.isDone() || state.completing || state.inFlight > 0 || !plugin.isEnabled()) {
            return;
        }

        int maxAttempts = state.maxAttempts;
        int batchSize = Math.max(1, Math.min(64, plugin.getConfig().getInt("rtp.search.batch-size", 24)));
        boolean preferLoadedChunks = plugin.getConfig().getBoolean("rtp.search.prefer-loaded-chunks", true);
        int launched = 0;

        while (launched < batchSize && state.attempts < maxAttempts && !state.future.isDone() && !state.completing) {
            if (state.attempts >= maxAttempts) {
                state.future.complete(new SearchResult(null, state.attempts));
                return;
            }

            Candidate candidate = candidate(state.profile);
            state.attempts++;
            launched++;
            int chunkX = candidate.x >> 4;
            int chunkZ = candidate.z >> 4;

            if (!state.profile.world().getWorldBorder().isInside(
                    new Location(state.profile.world(), candidate.x + 0.5D, 64, candidate.z + 0.5D))) {
                continue;
            }

            if (chunkProtected(state, chunkX, chunkZ)) {
                continue;
            }

            // Folia: a chunk of another region may not be inspected here, so every candidate takes the loader path.
            if (!Scheduler.FOLIA && preferLoadedChunks && state.profile.world().isChunkLoaded(chunkX, chunkZ)) {
                Location safe = checkCandidate(state, candidate);
                if (safe != null) {
                    completeFound(state, safe);
                    return;
                }
                continue;
            }

            state.inFlight++;
            Location owner = new Location(state.profile.world(), candidate.x + 0.5D, 64, candidate.z + 0.5D);
            chunkLoader.load(state.profile.world(), chunkX, chunkZ).whenComplete((chunk, throwable) ->
                    // The candidate is checked on the thread that owns it; the search continues on the main or global thread.
                    plugin.scheduler().runAt(owner, () -> {
                        state.inFlight--;
                        if (state.future.isDone() || state.completing || !plugin.isEnabled()) {
                            return;
                        }
                        if (throwable != null) {
                            continueSearch(state, maxAttempts);
                            return;
                        }
                        try {
                            Location safe = checkCandidate(state, candidate);
                            if (safe != null) {
                                completeFound(state, safe);
                            } else {
                                continueSearch(state, maxAttempts);
                            }
                        } catch (RuntimeException error) {
                            state.future.completeExceptionally(error);
                        }
                    }));
            // Check this candidate before generating another chunk. The batch budget
            // still bounds cheap checks of loaded or rejected candidates per tick.
            return;
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
        if (Scheduler.FOLIA) plugin.scheduler().runGlobal(() -> attemptSafely(state));
        else plugin.getServer().getScheduler().runTask(plugin, () -> attemptSafely(state));
    }

    private Location checkCandidate(SearchState state, Candidate candidate) {
        Location safe = safeLocationChecker.findSafe(state.profile, state.rules, candidate.x, candidate.z);
        if (safe == null || tooCloseToOrigin(state, safe)) {
            return null;
        }
        return state.protection.isLocationProtected(safe) ? null : safe;
    }

    private void completeFound(SearchState state, Location safe) {
        state.completing = true;
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
        plugin.scheduler().runMain(runnable);
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
        // On Folia the steps of one search run one after another on different region threads.
        private volatile int attempts;
        private volatile int maxAttempts = 160;
        private volatile int inFlight;
        private volatile boolean completing;

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

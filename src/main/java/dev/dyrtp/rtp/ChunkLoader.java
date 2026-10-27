package dev.dyrtp.rtp;

import dev.dyrtp.DyRTP;
import dev.dyrtp.platform.Scheduler;
import dev.dyrtp.protection.Reflect;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ChunkLoader {

    private final DyRTP plugin;

    public ChunkLoader(DyRTP plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings("unchecked")
    public CompletableFuture<Chunk> load(World world, int chunkX, int chunkZ) {
        if (Scheduler.FOLIA) return loadOnFolia(world, chunkX, chunkZ);
        if (world.isChunkLoaded(chunkX, chunkZ)) {
            return CompletableFuture.completedFuture(world.getChunkAt(chunkX, chunkZ));
        }

        if (!plugin.getConfig().getBoolean("rtp.search.load-chunks", true)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Candidate chunk is not loaded and chunk loading is disabled"));
        }

        if (plugin.getConfig().getBoolean("rtp.search.prefer-async-chunks", true)) {
            try {
                Object future = Reflect.call(world, "getChunkAtAsync", chunkX, chunkZ, true);
                if (future instanceof CompletableFuture<?>) {
                    return (CompletableFuture<Chunk>) future;
                }
            } catch (Exception ignored) {
            }
            try {
                Object future = Reflect.call(world, "getChunkAtAsync", chunkX, chunkZ);
                if (future instanceof CompletableFuture<?>) {
                    return (CompletableFuture<Chunk>) future;
                }
            } catch (Exception ignored) {
            }
        }

        CompletableFuture<Chunk> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                future.complete(world.getChunkAt(chunkX, chunkZ));
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future;
    }

    /**
     * Folia: chunk state may not be read from another region, so every chunk goes through the asynchronous loader,
     * which returns at once for a loaded chunk. With load-chunks off it only accepts chunks that already exist.
     */
    @SuppressWarnings("unchecked")
    private CompletableFuture<Chunk> loadOnFolia(World world, int chunkX, int chunkZ) {
        boolean generate = plugin.getConfig().getBoolean("rtp.search.load-chunks", true);
        try {
            Object future = Reflect.call(world, "getChunkAtAsync", chunkX, chunkZ, generate);
            if (future instanceof CompletableFuture<?>) {
                return ((CompletableFuture<Chunk>) future).thenCompose(chunk -> chunk == null
                        ? CompletableFuture.failedFuture(new IllegalStateException("Candidate chunk does not exist"))
                        : CompletableFuture.completedFuture(chunk));
            }
        } catch (Exception failure) {
            return CompletableFuture.failedFuture(failure);
        }
        return CompletableFuture.failedFuture(new IllegalStateException("Asynchronous chunk loading is unavailable"));
    }

    public CompletableFuture<Void> preload(World world, int centerChunkX, int centerChunkZ) {
        int radius = Math.max(0, Math.min(8, plugin.getConfig().getInt("rtp.search.preload-radius", 0)));
        if (radius <= 0) {
            return CompletableFuture.completedFuture(null);
        }
        List<CompletableFuture<Chunk>> futures = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int chunkX = centerChunkX + dx;
                int chunkZ = centerChunkZ + dz;
                if (Scheduler.FOLIA || !world.isChunkLoaded(chunkX, chunkZ)) {
                    futures.add(load(world, chunkX, chunkZ));
                }
            }
        }
        if (futures.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }
}

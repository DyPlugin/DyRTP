package dev.dyrtp.rtp;

import dev.dyrtp.DyRTP;
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
        if (world.isChunkLoaded(chunkX, chunkZ)) {
            return CompletableFuture.completedFuture(world.getChunkAt(chunkX, chunkZ));
        }

        if (!plugin.getConfig().getBoolean("rtp.search.load-chunks", true)) {
            return CompletableFuture.completedFuture(world.getChunkAt(chunkX, chunkZ));
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
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
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

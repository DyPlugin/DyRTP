package dev.dyrtp.rtp;

import dev.dyrtp.DyRTP;
import dev.dyrtp.protection.ProtectionService;
import dev.dyrtp.protection.ProtectionSnapshot;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.block.Block;
import org.bukkit.block.Biome;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocationFinderTest {
    @Test void pendingCandidateDoesNotLaunchSpeculativeChunkGeneration() {
        try (BoundedFixture f = new BoundedFixture()) {
            f.config.set("rtp.search.batch-size", 32);
            var result = f.finder.find(f.world, null);
            assertFalse(result.isDone());
            verify(f.loaders.constructed().get(0), times(1)).load(any(), anyInt(), anyInt());
            result.cancel(false);
        }
    }
    @Test void failedPendingCandidateSchedulesOnlyOneBoundedRetry() {
        try (BoundedFixture f = new BoundedFixture()) {
            f.config.set("rtp.search.batch-size", 32);
            f.config.set("rtp.search.max-attempts", 2);
            CompletableFuture<Chunk> second = new CompletableFuture<>();
            when(f.loaders.constructed().get(0).load(any(), anyInt(), anyInt())).thenReturn(f.chunk, second);
            var result = f.finder.find(f.world, null);
            f.chunk.completeExceptionally(new IllegalStateException("unloaded"));
            var retry = org.mockito.ArgumentCaptor.forClass(Runnable.class);
            verify(f.scheduler, times(1)).runTask(eq(f.plugin), retry.capture());
            retry.getValue().run();
            verify(f.loaders.constructed().get(0), times(2)).load(any(), anyInt(), anyInt());
            second.completeExceptionally(new IllegalStateException("unloaded"));
            assertNull(result.join().location());
            assertEquals(2, result.join().attempts());
            verify(f.scheduler, times(1)).runTask(eq(f.plugin), any(Runnable.class));
        }
    }
    @Test void concurrencyLimitRejectsExcessSearchAndTimeoutReleasesSlot() throws Exception {
        try (BoundedFixture f = new BoundedFixture()) {
            f.config.set("rtp.search.max-concurrent-searches", 1);
            f.config.set("rtp.search.timeout-seconds", 1);
            LocationFinder finder = new LocationFinder(f.plugin, f.protection);
            CompletableFuture<LocationFinder.SearchResult> first = finder.find(f.world, null);
            assertNull(finder.find(f.world, null).join().location());
            var error = assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> first.get(3, java.util.concurrent.TimeUnit.SECONDS));
            assertInstanceOf(java.util.concurrent.TimeoutException.class, error.getCause());
            CompletableFuture<LocationFinder.SearchResult> next = finder.find(f.world, null);
            assertFalse(next.isDone());
            next.cancel(false);
        }
    }
    @Test void failedCandidatesStopAtConfiguredAttemptLimit() {
        try (BoundedFixture f = new BoundedFixture()) {
            f.config.set("rtp.search.max-attempts", 3);
            f.config.set("rtp.search.batch-size", 1);
            when(f.loaders.constructed().get(0).load(any(), anyInt(), anyInt()))
                    .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("unloaded")));
            when(f.scheduler.runTask(eq(f.plugin), any(Runnable.class))).thenAnswer(call -> {
                ((Runnable) call.getArgument(1)).run(); return mock(org.bukkit.scheduler.BukkitTask.class);
            });
            var result = f.finder.find(f.world, null).join();
            assertNull(result.location()); assertEquals(3, result.attempts());
            verify(f.loaders.constructed().get(0), times(3)).load(any(), anyInt(), anyInt());
        }
    }
    @Test void cancelledSearchIgnoresLateChunkCompletion() {
        try (BoundedFixture f = new BoundedFixture()) {
            CompletableFuture<LocationFinder.SearchResult> result = f.finder.find(f.world, null);
            result.cancel(false);
            f.chunk.complete(mock(Chunk.class));
            verify(f.world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
            verify(f.world, never()).getHighestBlockYAt(anyInt(), anyInt(), any(HeightMap.class));
            assertTrue(result.isCancelled());
        }
    }
    private static final class BoundedFixture implements AutoCloseable {
        final DyRTP plugin = mock(DyRTP.class);
        final YamlConfiguration config = new YamlConfiguration();
        final World world = mock(World.class);
        final ProtectionService protection = mock(ProtectionService.class);
        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        final CompletableFuture<Chunk> chunk = new CompletableFuture<>();
        final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        final org.mockito.MockedConstruction<ChunkLoader> loaders;
        final LocationFinder finder;
        BoundedFixture() {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            when(plugin.getConfig()).thenReturn(config); when(plugin.isEnabled()).thenReturn(true);
            Server server = mock(Server.class); when(plugin.getServer()).thenReturn(server);
            when(server.getScheduler()).thenReturn(scheduler);
            when(world.getName()).thenReturn("world"); when(world.getMinHeight()).thenReturn(-64);
            when(world.getMaxHeight()).thenReturn(320);
            WorldBorder border = mock(WorldBorder.class); when(world.getWorldBorder()).thenReturn(border);
            when(border.isInside(any())).thenReturn(true);
            when(protection.snapshot()).thenReturn(mock(ProtectionSnapshot.class));
            loaders = mockConstruction(ChunkLoader.class, (mock, context) -> when(mock.load(any(), anyInt(), anyInt())).thenReturn(chunk));
            finder = new LocationFinder(plugin, protection);
        }
        public void close() { loaders.close(); bukkit.close(); }
    }
    @Test void finalValidationRejectsDisabledWorldBeforeAccessingTerrain() {
        DyRTP plugin = mock(DyRTP.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("rtp.disabled-worlds", java.util.List.of("blocked"));
        when(plugin.getConfig()).thenReturn(config);
        World world = mock(World.class); when(world.getName()).thenReturn("blocked");
        ProtectionService protection = mock(ProtectionService.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            assertFalse(new LocationFinder(plugin, protection).isSafeNow(new Location(world, 0.5, 64, 0.5), null));
            verify(world, never()).isChunkLoaded(anyInt(), anyInt());
        }
    }
    @Test void completedInlineChunkLoadStopsLaunchingMoreCandidates() {
        DyRTP plugin = mock(DyRTP.class);
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(mock(BukkitScheduler.class));
        when(plugin.isEnabled()).thenReturn(true);
        YamlConfiguration config = new YamlConfiguration();
        config.set("rtp.search.prefer-loaded-chunks", false);
        config.set("rtp.safety.min-distance-from-current", 0);
        when(plugin.getConfig()).thenReturn(config);
        World world = mock(World.class);
        WorldBorder border = mock(WorldBorder.class);
        when(world.getWorldBorder()).thenReturn(border); when(border.isInside(any())).thenReturn(true);
        when(world.getName()).thenReturn("world"); when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(world.getMinHeight()).thenReturn(-64); when(world.getMaxHeight()).thenReturn(320);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getHighestBlockYAt(anyInt(), anyInt(), any(HeightMap.class))).thenReturn(63);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
            Block block = mock(Block.class);
            int y = invocation.getArgument(1);
            when(block.getWorld()).thenReturn(world);
            when(block.getX()).thenReturn(invocation.getArgument(0)); when(block.getY()).thenReturn(y); when(block.getZ()).thenReturn(invocation.getArgument(2));
            when(block.getType()).thenReturn(y == 63 ? Material.STONE : Material.AIR);
            when(block.getBiome()).thenReturn(Biome.PLAINS);
            return block;
        });
        ProtectionService protection = mock(ProtectionService.class);
        when(protection.snapshot()).thenReturn(mock(ProtectionSnapshot.class));
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            assertNotNull(new LocationFinder(plugin, protection).find(world, null).join().location());
            verify(world, times(1)).getChunkAt(anyInt(), anyInt());
        }
    }

    @Test void protectionFailureCompletesSearchExceptionallyInsteadOfLeavingRequestPending() {
        DyRTP plugin = mock(DyRTP.class);
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(mock(BukkitScheduler.class));
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(world.getMinHeight()).thenReturn(-64); when(world.getMaxHeight()).thenReturn(320);
        ProtectionService protection = mock(ProtectionService.class);
        when(protection.snapshot()).thenThrow(new IllegalStateException("DyClaim failure"));
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            CompletableFuture<LocationFinder.SearchResult> result = assertDoesNotThrow(() -> new LocationFinder(plugin, protection).find(world, null));
            assertTrue(result.isCompletedExceptionally());
        }
    }
}

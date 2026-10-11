package dev.dyrtp.rtp;

import dev.dyrtp.DyRTP;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class ChunkLoaderTest {
    @Test void loadingDisabledDoesNotGenerateUnloadedCandidateChunk() {
        DyRTP plugin = mock(DyRTP.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("rtp.search.load-chunks", false);
        when(plugin.getConfig()).thenReturn(config);
        World world = mock(World.class);
        assertTrue(new ChunkLoader(plugin).load(world, 2, -3).isCompletedExceptionally());
        verify(world, never()).getChunkAt(anyInt(), anyInt());
    }
}

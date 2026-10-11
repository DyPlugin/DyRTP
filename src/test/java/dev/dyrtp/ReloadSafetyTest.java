package dev.dyrtp;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReloadSafetyTest {
    @TempDir Path folder;
    @Test void malformedProtectionListDoesNotReplaceRuntimeOrSavedSettings() throws Exception {
        DyRTP plugin = mock(DyRTP.class, CALLS_REAL_METHODS);
        YamlConfiguration working = new YamlConfiguration();
        working.set("rtp.disabled-worlds", java.util.List.of("restricted"));
        working.set("rtp.safety.blacklisted-blocks", java.util.List.of("MAGMA_BLOCK"));
        doReturn(folder.toFile()).when(plugin).getDataFolder();
        doReturn(working).when(plugin).getConfig();
        doReturn(Logger.getAnonymousLogger()).when(plugin).getLogger();
        doNothing().when(plugin).reloadConfig();
        String invalid = "rtp:\n  disabled-worlds: restricted\n";
        Files.writeString(folder.resolve("config.yml"), invalid);
        assertFalse(plugin.tryReloadPlugin());
        verify(plugin, never()).reloadConfig();
        assertEquals(java.util.List.of("restricted"), working.getStringList("rtp.disabled-worlds"));
        assertEquals(invalid, Files.readString(folder.resolve("config.yml")));
        working.save(folder.resolve("config.yml").toFile());
        String original = Files.readString(folder.resolve("config.yml"));
        assertFalse(plugin.applyConfigChanges(java.util.Map.of("rtp.safety.blacklisted-blocks", "MAGMA_BLOCK")));
        assertEquals(java.util.List.of("MAGMA_BLOCK"), working.getStringList("rtp.safety.blacklisted-blocks"));
        assertEquals(original, Files.readString(folder.resolve("config.yml")));
    }
    @Test void malformedConfigDoesNotReplaceWorkingSettings() throws Exception {
        DyRTP plugin = mock(DyRTP.class, CALLS_REAL_METHODS);
        YamlConfiguration working = new YamlConfiguration();
        working.set("economy.cost", 12.0);
        doReturn(folder.toFile()).when(plugin).getDataFolder();
        doReturn(working).when(plugin).getConfig();
        doReturn(Logger.getAnonymousLogger()).when(plugin).getLogger();
        doNothing().when(plugin).reloadConfig();
        Files.writeString(folder.resolve("config.yml"), "economy: [broken\n");
        assertDoesNotThrow(plugin::reloadPlugin);
        verify(plugin, never()).reloadConfig();
        assertEquals(12.0, working.getDouble("economy.cost"));
    }
    @Test void invalidInheritedWorldEditDoesNotChangeMemoryOrDisk() throws Exception {
        DyRTP plugin = mock(DyRTP.class, CALLS_REAL_METHODS);
        YamlConfiguration working = new YamlConfiguration();
        working.set("rtp.default-world.min-radius", 500);
        working.set("rtp.default-world.max-radius", 5000);
        working.set("rtp.worlds.survival.min-radius", 4500);
        doReturn(folder.toFile()).when(plugin).getDataFolder();
        doReturn(working).when(plugin).getConfig();
        doReturn(Logger.getAnonymousLogger()).when(plugin).getLogger();
        working.save(folder.resolve("config.yml").toFile());
        String original = Files.readString(folder.resolve("config.yml"));
        assertFalse(plugin.applyConfigChanges(java.util.Map.of("rtp.default-world.max-radius", 4000)));
        assertEquals(5000, working.getInt("rtp.default-world.max-radius"));
        assertEquals(original, Files.readString(folder.resolve("config.yml")));
    }
}

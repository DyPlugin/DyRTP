package dev.dyrtp.storage;
import dev.dyrtp.DyRTP;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.UUID;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerDataStoreTest {
    @TempDir Path folder;
    @Test void malformedReloadRetainsSavedPreference() throws Exception {
        DyRTP plugin = dev.dyrtp.TestPlugins.plugin();
        when(plugin.getDataFolder()).thenReturn(folder.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        UUID id = UUID.randomUUID();
        PlayerDataStore store = new PlayerDataStore(plugin);
        store.setLanguage(id, "tr");
        Files.writeString(folder.resolve("players.yml"), "players: [broken\n");
        try (var bukkit = mockStatic(org.bukkit.Bukkit.class)) {
            bukkit.when(org.bukkit.Bukkit::getLogger).thenReturn(Logger.getAnonymousLogger());
            assertDoesNotThrow(store::reload);
            assertEquals("tr", store.getLanguage(id));
        }
    }
}

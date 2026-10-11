package dev.dyrtp.lang;

import dev.dyrtp.DyRTP;
import dev.dyrtp.storage.PlayerDataStore;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MessageServiceTest {
    @TempDir Path temp;
    @Test void forcedGlobalLanguageOverridesSavedChoiceAndLocale() {
        DyRTP plugin = mock(DyRTP.class);
        when(plugin.getDataFolder()).thenReturn(temp.toFile());
        YamlConfiguration config = new YamlConfiguration();
        config.set("language.default", "en");
        when(plugin.getConfig()).thenReturn(config);
        PlayerDataStore store = mock(PlayerDataStore.class);
        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getLocale()).thenReturn("tr_TR");
        when(store.getLanguage(uuid)).thenReturn("tr");
        MessageService messages = new MessageService(plugin, store);
        assertEquals(Language.EN, messages.language(player));
        config.set("language.default", "auto");
        assertEquals(Language.TR, messages.language(player));
    }
    @Test void autoUsesSavedChoiceThenLocaleAndDisabledChoicesAreIgnored() {
        DyRTP plugin = mock(DyRTP.class);
        when(plugin.getDataFolder()).thenReturn(temp.toFile());
        YamlConfiguration config = new YamlConfiguration();
        config.set("language.default", "auto");
        when(plugin.getConfig()).thenReturn(config);
        PlayerDataStore store = mock(PlayerDataStore.class);
        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getLocale()).thenReturn("tr_TR");
        when(store.getLanguage(uuid)).thenReturn("en");
        MessageService messages = new MessageService(plugin, store);
        assertEquals(Language.EN, messages.language(player));
        config.set("language.save-player-choice", false);
        assertEquals(Language.TR, messages.language(player));
        config.set("language.use-player-locale", false);
        assertEquals(Language.EN, messages.language(player));
        config.set("language.default", "tr");
        assertEquals(Language.TR, messages.language(mock(org.bukkit.command.CommandSender.class)));
    }
    @Test void translationsMatchKeysAndPlaceholderContracts() throws Exception {
        var en = load("messages_en.yml");
        var tr = load("messages_tr.yml");
        assertEquals(en.getKeys(true), tr.getKeys(true));
        var pattern = java.util.regex.Pattern.compile("\\{[a-z-]+}");
        for (String key : en.getKeys(true)) {
            if (en.isConfigurationSection(key)) continue;
            var english = pattern.matcher(String.valueOf(en.get(key))).results().map(java.util.regex.MatchResult::group).collect(java.util.stream.Collectors.toSet());
            var turkish = pattern.matcher(String.valueOf(tr.get(key))).results().map(java.util.regex.MatchResult::group).collect(java.util.stream.Collectors.toSet());
            assertEquals(english, turkish, key);
        }
    }
    private YamlConfiguration load(String resource) throws Exception {
        try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            return YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}

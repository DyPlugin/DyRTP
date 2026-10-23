package dev.dyrtp;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MessageMigrationTest {
    @Test void unchangedAutomaticLabelsMigrateButCustomTranslationsRemain() throws Exception {
        DyRTP plugin = mock(DyRTP.class, CALLS_REAL_METHODS);
        YamlConfiguration defaults = new YamlConfiguration(), existing = new YamlConfiguration();
        defaults.set("language-auto", "Oto"); existing.set("language-auto", "Otomatik");
        defaults.set("help", java.util.List.of("/dyrtp dil <oto|en|tr>"));
        existing.set("help", java.util.List.of("/dyrtp dil <otomatik|en|tr>"));
        defaults.set("custom", "Oto"); existing.set("custom", "Benim otomatik tercihim");
        var method = DyRTP.class.getDeclaredMethod("copyMissing", ConfigurationSection.class, ConfigurationSection.class);
        method.setAccessible(true);
        assertTrue((boolean) method.invoke(plugin, defaults, existing));
        assertEquals("Oto", existing.getString("language-auto"));
        assertEquals(defaults.getStringList("help"), existing.getStringList("help"));
        assertEquals("Benim otomatik tercihim", existing.getString("custom"));
    }
    @Test void stockBlueMessageMigratesButCustomizedTranslationIsPreserved() throws Exception {
        DyRTP plugin = mock(DyRTP.class, CALLS_REAL_METHODS);
        var defaults = new YamlConfiguration(); var existing = new YamlConfiguration();
        defaults.set("rtp.title", "&aTeleporting!");
        defaults.set("rtp.custom", "&aTeleporting!");
        existing.set("rtp.title", "&9Teleporting!");
        existing.set("rtp.custom", "&9My custom text");
        var method = DyRTP.class.getDeclaredMethod("copyMissing", ConfigurationSection.class, ConfigurationSection.class);
        method.setAccessible(true);
        assertTrue((boolean) method.invoke(plugin, defaults, existing));
        assertEquals("&aTeleporting!", existing.getString("rtp.title"));
        assertEquals("&9My custom text", existing.getString("rtp.custom"));
    }
}

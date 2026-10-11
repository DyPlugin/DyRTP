package dev.dyrtp;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MessageMigrationTest {
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

package dev.dyrtp;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConfigValidatorTest {
    @Test void protectionListsMustRemainListsOfStrings() {
        for (String path : java.util.List.of("rtp.disabled-worlds", "rtp.safety.blacklisted-blocks",
                "rtp.safety.blacklisted-biomes", "rtp.default-world.allowed-biomes", "rtp.worlds.world.allowed-biomes")) {
            for (Object value : java.util.List.of("MAGMA_BLOCK", java.util.List.of("MAGMA_BLOCK", 7))) {
                var config = new YamlConfiguration(); config.set(path, value);
                assertThrows(IllegalArgumentException.class, () -> ConfigValidator.validate(config), path);
            }
        }
    }
    @Test void bundledConfigIsValid() throws Exception {
        var config = new YamlConfiguration();
        config.load("src/main/resources/config.yml");
        assertDoesNotThrow(() -> ConfigValidator.validate(config));
    }
    @Test void invalidCostsAndSearchBoundsAreRejected() {
        for (double cost : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY}) {
            var config = new YamlConfiguration(); config.set("economy.cost", cost);
            assertThrows(IllegalArgumentException.class, () -> ConfigValidator.validate(config));
        }
        var config = new YamlConfiguration(); config.set("rtp.search.batch-size", 100000);
        assertThrows(IllegalArgumentException.class, () -> ConfigValidator.validate(config));
        config.set("rtp.search.batch-size", "32");
        assertThrows(IllegalArgumentException.class, () -> ConfigValidator.validate(config));
    }
    @Test void invertedPerWorldRadiiAndHeightsAreRejected() {
        var config = new YamlConfiguration();
        config.set("rtp.default-world.min-radius", 500);
        config.set("rtp.worlds.world.max-radius", 400);
        assertThrows(IllegalArgumentException.class, () -> ConfigValidator.validate(config));
        config.set("rtp.worlds.world.max-radius", 600);
        config.set("rtp.worlds.world.min-y", 320);
        config.set("rtp.worlds.world.max-y", 100);
        assertThrows(IllegalArgumentException.class, () -> ConfigValidator.validate(config));
    }
    @Test void wrongPriceTypesAreRejectedInsteadOfDefaultingToFree() {
        for (Object price : new Object[]{"typo", true, java.util.List.of(100)}) {
            var config = new YamlConfiguration(); config.set("economy.cost", price);
            assertThrows(IllegalArgumentException.class, () -> ConfigValidator.validate(config));
        }
    }
}

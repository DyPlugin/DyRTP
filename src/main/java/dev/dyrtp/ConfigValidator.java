package dev.dyrtp;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.Set;

/** Validate a staged configuration before replacing the running settings. */
final class ConfigValidator {
    private ConfigValidator() { }

    static void validate(FileConfiguration config) {
        if (config.contains("economy.cost") && !(config.get("economy.cost") instanceof Number))
            invalid("economy.cost", "expected a number");
        double cost = config.getDouble("economy.cost", 100);
        if (!Double.isFinite(cost) || cost < 0) invalid("economy.cost", "must be finite and non-negative");
        String language = config.getString("language.default", "auto");
        if (!"auto".equals(language) && !dev.dyrtp.lang.Language.codes().contains(language)) invalid("language.default", "expected auto or one of " + dev.dyrtp.lang.Language.codes());
        bounded(config, "rtp.search.max-attempts", 1, 10000);
        bounded(config, "rtp.search.batch-size", 1, 64);
        bounded(config, "rtp.search.preload-radius", 0, 4);
        bounded(config, "rtp.search.timeout-seconds", 1, 300);
        bounded(config, "rtp.search.max-concurrent-searches", 1, 64);
        bounded(config, "protection.claim-buffer-chunks", 0, 16);
        bounded(config, "rtp.safety.nearby-surface-radius", 0, 4);
        bounded(config, "warmup.seconds", 0, 86400);
        bounded(config, "cooldown.seconds", 0, Integer.MAX_VALUE);
        bounded(config, "economy.confirmation.timeout-seconds", 1, 86400);
        bounded(config, "first-join-rtp.delay-seconds", 0, 86400);
        bounded(config, "death-rtp.delay-ticks", 0, 1728000);
        for (String path : Set.of("rtp.disabled-worlds", "rtp.safety.blacklisted-blocks",
                "rtp.safety.blacklisted-biomes", "rtp.default-world.allowed-biomes")) {
            stringList(config, path);
        }
        validateWorld(config.getConfigurationSection("rtp.default-world"), null, "rtp.default-world");
        ConfigurationSection worlds = config.getConfigurationSection("rtp.worlds");
        if (worlds != null) for (String name : worlds.getKeys(false)) {
            ConfigurationSection world = worlds.getConfigurationSection(name);
            if (world == null) invalid("rtp.worlds." + name, "expected a mapping");
            validateWorld(world, config.getConfigurationSection("rtp.default-world"), "rtp.worlds." + name);
        }
        for (String key : config.getKeys(true)) {
            Object value = config.get(key);
            if (key.endsWith(".enabled") || key.endsWith(".ignore-cooldown") || key.endsWith(".ignore-economy")) {
                if (!(value instanceof Boolean)) invalid(key, "expected true or false");
            }
        }
    }

    private static void validateWorld(ConfigurationSection world, ConfigurationSection defaults, String path) {
        if (world == null) return;
        stringList(world, "allowed-biomes");
        int min = integer(world, defaults, "min-radius", 0);
        int max = integer(world, defaults, "max-radius", 5000);
        if (min < 0 || max <= min || max > 29999984) invalid(path, "radii require 0 <= min < max <= 29999984");
        int low = integer(world, defaults, "min-y", -64);
        int high = integer(world, defaults, "max-y", 320);
        if (low >= high) invalid(path, "min-y must be below max-y");
        String shape = world.getString("shape", defaults == null ? "square" : defaults.getString("shape", "square"));
        if (!Set.of("square", "circle").contains(shape)) invalid(path + ".shape", "expected square or circle");
        for (String key : Set.of("center-x", "center-z")) {
            int center = integer(world, defaults, key, 0);
            if (Math.abs((long) center) + max > 30000000L) invalid(path + "." + key, "radius exceeds coordinate limits");
        }
    }

    private static int integer(ConfigurationSection world, ConfigurationSection defaults, String key, int fallback) {
        ConfigurationSection source = world.contains(key) ? world : defaults;
        if (source == null || !source.contains(key)) return fallback;
        Object value = source.get(key);
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() != number.intValue()) invalid(key, "expected an integer");
        return ((Number) value).intValue();
    }

    private static void bounded(FileConfiguration config, String path, int min, int max) {
        if (!config.contains(path)) return;
        Object value = config.get(path);
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() != number.intValue() || number.longValue() < min || number.longValue() > max)
            invalid(path, "expected an integer between " + min + " and " + max);
    }

    private static void invalid(String path, String reason) { throw new IllegalArgumentException(path + ": " + reason); }

    private static void stringList(ConfigurationSection config, String path) {
        if (!config.contains(path)) return;
        Object value = config.get(path);
        if (!(value instanceof java.util.List<?> values) || values.stream().anyMatch(item -> !(item instanceof String)))
            invalid(path, "expected a list of strings");
    }
}

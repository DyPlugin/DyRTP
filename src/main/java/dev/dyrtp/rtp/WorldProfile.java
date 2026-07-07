package dev.dyrtp.rtp;

import dev.dyrtp.DyRTP;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

public record WorldProfile(
        World world,
        boolean useWorldBorder,
        int centerX,
        int centerZ,
        int minRadius,
        int maxRadius,
        String shape,
        int minY,
        int maxY,
        List<String> allowedBiomes
) {

    public static WorldProfile load(DyRTP plugin, World world) {
        ConfigurationSection defaults = plugin.getConfig().getConfigurationSection("rtp.default-world");
        ConfigurationSection custom = plugin.getConfig().getConfigurationSection("rtp.worlds." + world.getName());

        boolean useBorder = getBoolean(custom, defaults, "use-world-border", false);
        int centerX = getInt(custom, defaults, "center-x", 0);
        int centerZ = getInt(custom, defaults, "center-z", 0);
        int minRadius = Math.max(0, getInt(custom, defaults, "min-radius", 0));
        int maxRadius = Math.max(minRadius + 1, getInt(custom, defaults, "max-radius", 1000));
        String shape = getString(custom, defaults, "shape", "square").toLowerCase();
        int minY = Math.max(world.getMinHeight(), getInt(custom, defaults, "min-y", world.getMinHeight()));
        int maxY = Math.min(world.getMaxHeight() - 1, getInt(custom, defaults, "max-y", world.getMaxHeight() - 1));
        List<String> biomes = custom != null && custom.contains("allowed-biomes")
                ? custom.getStringList("allowed-biomes")
                : defaults != null ? defaults.getStringList("allowed-biomes") : List.of();

        return new WorldProfile(world, useBorder, centerX, centerZ, minRadius, maxRadius, shape, minY, maxY, biomes);
    }

    private static boolean getBoolean(ConfigurationSection custom, ConfigurationSection defaults, String path, boolean fallback) {
        if (custom != null && custom.contains(path)) {
            return custom.getBoolean(path);
        }
        return defaults != null ? defaults.getBoolean(path, fallback) : fallback;
    }

    private static int getInt(ConfigurationSection custom, ConfigurationSection defaults, String path, int fallback) {
        if (custom != null && custom.contains(path)) {
            return custom.getInt(path);
        }
        return defaults != null ? defaults.getInt(path, fallback) : fallback;
    }

    private static String getString(ConfigurationSection custom, ConfigurationSection defaults, String path, String fallback) {
        if (custom != null && custom.contains(path)) {
            return custom.getString(path, fallback);
        }
        return defaults != null ? defaults.getString(path, fallback) : fallback;
    }
}

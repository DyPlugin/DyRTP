package dev.dyrtp.rtp;

import dev.dyrtp.DyRTP;
import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public record SafetyRules(
        boolean surfaceOnly,
        boolean requireNaturalSkyAccess,
        int minDistanceFromCurrent,
        int nearbySurfaceRadius,
        int maxNearbySurfaceDrop,
        boolean requireHeadRoom,
        Set<String> blacklistedBlocks,
        Set<String> blacklistedBiomes,
        Set<String> allowedBiomes
) {

    public static SafetyRules load(DyRTP plugin, WorldProfile profile) {
        return new SafetyRules(
                plugin.getConfig().getBoolean("rtp.safety.surface-only", true),
                plugin.getConfig().getBoolean("rtp.safety.require-natural-sky-access", true),
                Math.max(0, plugin.getConfig().getInt("rtp.safety.min-distance-from-current", 250)),
                Math.max(0, plugin.getConfig().getInt("rtp.safety.nearby-surface-radius", 0)),
                Math.max(0, plugin.getConfig().getInt("rtp.safety.max-nearby-surface-drop", 8)),
                plugin.getConfig().getBoolean("rtp.safety.require-head-room", true),
                upperSet(plugin.getConfig().getStringList("rtp.safety.blacklisted-blocks")),
                upperSet(plugin.getConfig().getStringList("rtp.safety.blacklisted-biomes")),
                upperSet(profile.allowedBiomes())
        );
    }

    public boolean blacklisted(Material material) {
        return blacklistedBlocks.contains(material.name());
    }

    public boolean biomeAllowed(Block block) {
        String biome = block.getBiome().name().toUpperCase(Locale.ROOT);
        return !blacklistedBiomes.contains(biome) && (allowedBiomes.isEmpty() || allowedBiomes.contains(biome));
    }

    private static Set<String> upperSet(Iterable<String> values) {
        return java.util.stream.StreamSupport.stream(values.spliterator(), false)
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }
}

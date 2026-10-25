package dev.dyrtp.rtp;

import dev.dyrtp.DyRTP;
import org.bukkit.Registry;
import org.bukkit.block.Biome;

import java.util.*;

/** Biomes players may pick with /rtp biome; names are lower case, as in Minecraft (desert, cherry_grove). */
public final class BiomeChoices {
    private BiomeChoices() {}

    public static List<String> available(DyRTP plugin) {
        List<String> configured = plugin.getConfig().getStringList("biome-selection.biomes");
        Set<String> blocked = new HashSet<>();
        for (String name : plugin.getConfig().getStringList("rtp.safety.blacklisted-biomes")) blocked.add(name.toLowerCase(Locale.ROOT));
        Collection<String> names = configured.isEmpty() ? serverBiomes() : configured;
        TreeSet<String> out = new TreeSet<>();
        for (String name : names) {
            String biome = normalize(name);
            if (!biome.isEmpty() && !biome.equals("custom") && !blocked.contains(biome)) out.add(biome);
        }
        return List.copyOf(out);
    }

    /** The player's input as a biome name from {@link #available}, or null when it is not one. */
    public static String match(DyRTP plugin, String input) {
        String wanted = normalize(input);
        return available(plugin).contains(wanted) ? wanted : null;
    }

    private static String normalize(String name) {
        String plain = name == null ? "" : name.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        return plain.startsWith("minecraft:") ? plain.substring("minecraft:".length()) : plain;
    }

    private static List<String> serverBiomes() {
        List<String> out = new ArrayList<>();
        for (Biome biome : Registry.BIOME) {
            try { out.add(biome.getKey().getKey()); } catch (RuntimeException ignored) { }
        }
        return out;
    }
}

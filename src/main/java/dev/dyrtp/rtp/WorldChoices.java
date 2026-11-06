package dev.dyrtp.rtp;

import dev.dyrtp.DyRTP;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import java.util.*;

/**
 * Worlds players may pick with /rtp world <world>. A world is offered when it is loaded, not in rtp.disabled-worlds,
 * listed in world-selection.worlds (empty lists every loaded world) and the sender has dyrtp.world.<world> or dyrtp.world.*.
 */
public final class WorldChoices {
    private WorldChoices() {}

    public static boolean enabled(DyRTP plugin) {
        return plugin.getConfig().getBoolean("world-selection.enabled", false);
    }

    public static List<String> available(DyRTP plugin, CommandSender sender) {
        if (!enabled(plugin)) return List.of();
        List<String> configured = plugin.getConfig().getStringList("world-selection.worlds");
        Set<String> disabled = dev.dyrtp.util.ConfigCache.stringSet(plugin.getConfig(), "rtp.disabled-worlds");
        TreeSet<String> out = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (World world : Bukkit.getWorlds()) {
            String name = world.getName();
            if (disabled.contains(name)) continue;
            if (!configured.isEmpty() && configured.stream().noneMatch(name::equalsIgnoreCase)) continue;
            if (!allowed(sender, name)) continue;
            out.add(name);
        }
        return List.copyOf(out);
    }

    /** The loaded world the sender typed, when it is one they may choose; otherwise null. */
    public static World match(DyRTP plugin, CommandSender sender, String input) {
        if (input == null) return null;
        for (String name : available(plugin, sender)) {
            if (name.equalsIgnoreCase(input.trim())) return Bukkit.getWorld(name);
        }
        return null;
    }

    private static boolean allowed(CommandSender sender, String world) {
        return sender.hasPermission("dyrtp.world.*") || sender.hasPermission("dyrtp.world." + world.toLowerCase(Locale.ROOT));
    }
}

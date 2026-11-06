package dev.dyrtp.rtp;

import dev.dyrtp.DyRTP;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * Commands run after a successful RTP, for example a starter kit. Each line is "console: &lt;command&gt;" or
 * "player: &lt;command&gt;"; a line without a prefix runs from the console. {player}, {world}, {x}, {y} and {z} are filled in.
 * Console commands run on the server (global) thread, player commands on the player's own thread, so Folia is safe.
 */
public final class AfterTeleportCommands {

    private AfterTeleportCommands() {
    }

    /** A parsed line: who runs it and the command without its leading slash. */
    record Line(boolean console, String command) {
    }

    public static void run(DyRTP plugin, Player player, Location target, boolean firstRtp) {
        if (!plugin.getConfig().getBoolean("after-teleport-commands.enabled", false)) return;
        if (plugin.getConfig().getBoolean("after-teleport-commands.first-rtp-only", false) && !firstRtp) return;
        for (String raw : plugin.getConfig().getStringList("after-teleport-commands.commands")) {
            Line line = parse(raw);
            if (line == null) continue;
            String command = fill(line.command(), player, target);
            try {
                if (line.console()) {
                    plugin.scheduler().runMain(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
                } else {
                    plugin.scheduler().runFor(player, () -> {
                        if (player.isOnline()) player.performCommand(command);
                    });
                }
            } catch (RuntimeException failure) {
                plugin.getLogger().warning("After-teleport command '" + command + "' failed: " + failure.getMessage());
            }
        }
    }

    static Line parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String text = raw.trim();
        boolean console = true;
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.startsWith("player:")) {
            console = false;
            text = text.substring("player:".length()).trim();
        } else if (lower.startsWith("console:")) {
            text = text.substring("console:".length()).trim();
        }
        while (text.startsWith("/")) text = text.substring(1);
        return text.isBlank() ? null : new Line(console, text);
    }

    static String fill(String command, Player player, Location target) {
        String world = target.getWorld() != null ? target.getWorld().getName() : "";
        return command.replace("{player}", player.getName())
                .replace("{world}", world)
                .replace("{x}", String.valueOf(target.getBlockX()))
                .replace("{y}", String.valueOf(target.getBlockY()))
                .replace("{z}", String.valueOf(target.getBlockZ()));
    }

    /** Lines as configured, for tests and validation. */
    static List<Line> parseAll(List<String> lines) {
        return lines.stream().map(AfterTeleportCommands::parse).filter(java.util.Objects::nonNull).toList();
    }
}

package dev.dyrtp.hook;

import dev.dyrtp.DyRTP;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Built-in %dyrtp_...% placeholders. Only loaded when PlaceholderAPI is installed, through register(),
 * so DyRTP never needs PlaceholderAPI classes otherwise.
 */
public final class PlaceholderHook extends PlaceholderExpansion {
    private final DyRTP plugin;

    public PlaceholderHook(DyRTP plugin) {
        this.plugin = plugin;
    }

    /** Returns the registered expansion, or null when registration failed. */
    public static Object register(DyRTP plugin) {
        PlaceholderHook hook = new PlaceholderHook(plugin);
        return hook.register() ? hook : null;
    }

    public static void unregister(Object hook) {
        if (hook instanceof PlaceholderHook expansion) expansion.unregister();
    }

    @Override public String getIdentifier() { return "dyrtp"; }
    @Override public String getAuthor() { return "Claww"; }
    @Override public String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer offline, String params) {
        Player player = offline == null ? null : offline.getPlayer();
        if (player == null || plugin.getRtpService() == null) return "";
        var rtp = plugin.getRtpService();
        return switch (params.toLowerCase(java.util.Locale.ROOT)) {
            case "cooldown" -> String.valueOf(rtp.cooldownRemaining(player));
            case "cooldown_formatted" -> {
                long seconds = rtp.cooldownRemaining(player);
                yield seconds > 0 ? rtp.formatCooldown(player, seconds) : "";
            }
            case "ready" -> String.valueOf(plugin.getConfig().getBoolean("rtp.enabled", true)
                    && rtp.destinationWorld(player) != null && rtp.cooldownRemaining(player) == 0 && !rtp.isTeleporting(player));
            case "price" -> {
                double price = rtp.price(player);
                yield price == Math.rint(price) ? String.valueOf((long) price) : String.valueOf(price);
            }
            case "price_formatted" -> {
                double price = rtp.price(player);
                yield price > 0 ? plugin.getEconomy().format(price) : "";
            }
            case "world" -> {
                World world = rtp.destinationWorld(player);
                yield world == null ? "" : world.getName();
            }
            default -> null;
        };
    }
}

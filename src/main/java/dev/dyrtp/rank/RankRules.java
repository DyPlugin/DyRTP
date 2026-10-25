package dev.dyrtp.rank;

import dev.dyrtp.DyRTP;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.OptionalLong;

/**
 * Per-rank cooldown, price and distance from permissions such as dyrtp.cooldown.60, dyrtp.price.250 or dyrtp.radius.10000.
 * For cooldown and price the lowest value wins, for distance the highest; a matching permission replaces the configured default.
 */
public final class RankRules {
    private final DyRTP plugin;

    public RankRules(DyRTP plugin) {
        this.plugin = plugin;
    }

    public int cooldownSeconds(Player player) {
        int base = Math.max(0, plugin.getConfig().getInt("cooldown.seconds", 0));
        if (!plugin.getConfig().getBoolean("cooldown.permission-overrides", true)) return base;
        OptionalLong rank = lowest(player, "dyrtp.cooldown.");
        return rank.isPresent() ? (int) Math.min(Integer.MAX_VALUE, rank.getAsLong()) : base;
    }

    /** Farthest RTP distance from dyrtp.radius.<blocks>; the highest one wins. 0 when the world's own setting applies. */
    public int maxRadius(Player player) {
        if (!plugin.getConfig().getBoolean("rtp.permission-radius", true)) return 0;
        OptionalLong highest = highest(player, "dyrtp.radius.");
        return highest.isPresent() ? (int) Math.min(30_000_000L, highest.getAsLong()) : 0;
    }

    public double price(Player player, double base) {
        if (!plugin.getConfig().getBoolean("economy.permission-prices", true)) return base;
        OptionalLong rank = lowest(player, "dyrtp.price.");
        return rank.isPresent() ? rank.getAsLong() : base;
    }

    static OptionalLong lowest(Player player, String prefix) {
        return pick(player, prefix, true);
    }

    static OptionalLong highest(Player player, String prefix) {
        return pick(player, prefix, false);
    }

    private static OptionalLong pick(Player player, String prefix, boolean lowest) {
        long best = lowest ? Long.MAX_VALUE : Long.MIN_VALUE;
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (!info.getValue()) continue;
            String node = info.getPermission();
            if (!node.regionMatches(true, 0, prefix, 0, prefix.length())) continue;
            String number = node.substring(prefix.length());
            if (number.isEmpty() || number.length() > 9 || !number.chars().allMatch(Character::isDigit)) continue;
            long value = Long.parseLong(number);
            best = lowest ? Math.min(best, value) : Math.max(best, value);
        }
        return best == (lowest ? Long.MAX_VALUE : Long.MIN_VALUE) ? OptionalLong.empty() : OptionalLong.of(best);
    }
}

package dev.dyrtp.api;

import dev.dyrtp.DyRTP;
import dev.dyrtp.rtp.RtpCause;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Supported entry point for other plugins. Events are in {@code dev.dyrtp.api.event}.
 * Every method except {@link #isAvailable()} throws IllegalStateException while DyRTP is not enabled.
 */
public final class DyRTPAPI {
    private DyRTPAPI() {}

    public static boolean isAvailable() {
        DyRTP plugin = DyRTP.getInstance();
        return plugin != null && plugin.isEnabled() && plugin.getRtpService() != null;
    }

    /** Seconds until the player may use /rtp again; 0 when ready. Includes dyrtp.cooldown.&lt;seconds&gt; permissions. */
    public static long getCooldownRemaining(Player player) { return plugin().getRtpService().cooldownRemaining(player); }

    /** What /rtp would cost the player now, including dyrtp.price.&lt;amount&gt; permissions; 0 when free. */
    public static double getPrice(Player player) { return plugin().getRtpService().price(player); }

    /** The world /rtp would search in from the player's current world, or null when RTP is not allowed there. */
    public static World getDestinationWorld(Player player) { return plugin().getRtpService().destinationWorld(player); }

    /** True while a search, warmup or teleport is running for the player. */
    public static boolean isTeleporting(Player player) { return plugin().getRtpService().isTeleporting(player); }

    /**
     * Starts a random teleport like /forcertp: cooldown and price do not apply, but warmup, safety checks,
     * protection checks and the DyRTP events do. The result arrives later through the events.
     */
    public static void randomTeleport(Player player) { plugin().getRtpService().request(player, RtpCause.FORCE); }

    private static DyRTP plugin() {
        if (!isAvailable()) throw new IllegalStateException("DyRTP is not enabled");
        return DyRTP.getInstance();
    }
}

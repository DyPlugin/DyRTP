package dev.dyrtp.listener;

import dev.dyrtp.DyRTP;
import dev.dyrtp.rtp.RtpCause;
import dev.dyrtp.rtp.RtpService;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class PlayerEventListener implements Listener {

    private final DyRTP plugin;
    private final RtpService rtpService;

    public PlayerEventListener(DyRTP plugin, RtpService rtpService) {
        this.plugin = plugin;
        this.rtpService = rtpService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("first-join-rtp.enabled", false)) {
            return;
        }
        if (event.getPlayer().hasPlayedBefore()) {
            return;
        }
        int seconds = Math.max(0, plugin.getConfig().getInt("first-join-rtp.delay-seconds", 2));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Player player = event.getPlayer();
            if (player.isOnline()) {
                rtpService.request(player, RtpCause.FIRST_JOIN);
            }
        }, seconds * 20L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (!plugin.getConfig().getBoolean("death-rtp.enabled", false)) {
            return;
        }
        if (plugin.getConfig().getBoolean("death-rtp.skip-if-bed-or-anchor", true)
                && (event.isBedSpawn() || event.isAnchorSpawn())) {
            return;
        }
        long delay = Math.max(0L, plugin.getConfig().getLong("death-rtp.delay-ticks", 5L));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Player player = event.getPlayer();
            if (player.isOnline()) {
                rtpService.request(player, RtpCause.DEATH);
            }
        }, delay);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.getConfig().getBoolean("warmup.cancel-on-move", true)) {
            return;
        }
        Player player = event.getPlayer();
        if (!rtpService.hasPending(player)) {
            return;
        }
        Location start = rtpService.pendingStart(player);
        Location to = event.getTo();
        if (start == null || to == null) {
            return;
        }
        if (start.getBlockX() != to.getBlockX()
                || start.getBlockY() != to.getBlockY()
                || start.getBlockZ() != to.getBlockZ()
                || !start.getWorld().equals(to.getWorld())) {
            rtpService.cancelForMove(player);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!plugin.getConfig().getBoolean("warmup.cancel-on-damage", true)) {
            return;
        }
        if (event.getEntity() instanceof Player player && rtpService.hasPending(player)) {
            rtpService.cancelForDamage(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        rtpService.cancelSilently(event.getPlayer());
    }
}

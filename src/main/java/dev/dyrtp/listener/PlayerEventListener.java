package dev.dyrtp.listener;

import dev.dyrtp.DyRTP;
import dev.dyrtp.rtp.RtpCause;
import dev.dyrtp.rtp.RtpService;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

public class PlayerEventListener implements Listener {

    private final DyRTP plugin;
    private final RtpService rtpService;
    private final Map<UUID, AutomaticRtp> automaticTasks = new HashMap<>();

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
        cancelAutomatic(event.getPlayer());
        long revision = rtpService.lifecycleRevision();
        UUID world = event.getPlayer().getWorld().getUID();
        AutomaticRtp operation = new AutomaticRtp();
        automaticTasks.put(event.getPlayer().getUniqueId(), operation);
        operation.task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Player player = event.getPlayer();
            if (!automaticTasks.remove(player.getUniqueId(), operation)) return;
            if (plugin.isEnabled() && rtpService.lifecycleRevision() == revision && player.isOnline() && !player.isDead()
                    && player.getWorld().getUID().equals(world) && plugin.getConfig().getBoolean("first-join-rtp.enabled", false)) {
                rtpService.request(player, RtpCause.FIRST_JOIN);
            }
        }, seconds * 20L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        cancelAutomatic(event.getPlayer());
        rtpService.cancelSilently(event.getPlayer());
        if (!plugin.getConfig().getBoolean("death-rtp.enabled", false)) {
            return;
        }
        if (plugin.getConfig().getBoolean("death-rtp.skip-if-bed-or-anchor", true)
                && (event.isBedSpawn() || event.isAnchorSpawn())) {
            return;
        }
        long delay = Math.max(0L, plugin.getConfig().getLong("death-rtp.delay-ticks", 5L));
        long revision = rtpService.lifecycleRevision();
        AutomaticRtp operation = new AutomaticRtp();
        operation.respawnWorld = event.getRespawnLocation().getWorld().getUID();
        operation.awaitingRespawnTransition = !event.getPlayer().getWorld().getUID().equals(operation.respawnWorld);
        automaticTasks.put(event.getPlayer().getUniqueId(), operation);
        operation.task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Player player = event.getPlayer();
            if (!automaticTasks.remove(player.getUniqueId(), operation)) return;
            if (plugin.isEnabled() && rtpService.lifecycleRevision() == revision && player.isOnline() && !player.isDead()
                    && player.getWorld().equals(event.getRespawnLocation().getWorld())
                    && plugin.getConfig().getBoolean("death-rtp.enabled", false)) {
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

    @EventHandler(ignoreCancelled = true)
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
        cancelAutomatic(event.getPlayer());
        rtpService.cancelSilently(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        cancelAutomatic(event.getEntity());
        rtpService.cancelSilently(event.getEntity());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (!rtpService.isTeleporting(event.getPlayer())) {
            AutomaticRtp operation = automaticTasks.get(event.getPlayer().getUniqueId());
            if (operation != null && operation.awaitingRespawnTransition
                    && event.getPlayer().getWorld().getUID().equals(operation.respawnWorld)) {
                operation.awaitingRespawnTransition = false;
            } else {
                cancelAutomatic(event.getPlayer());
            }
            rtpService.cancelSilently(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!rtpService.validateTeleport(event.getPlayer(), event.getTo())) event.setCancelled(true);
        else if (!rtpService.isTeleporting(event.getPlayer())) {
            cancelAutomatic(event.getPlayer());
            rtpService.cancelSilently(event.getPlayer());
        }
    }

    private void cancelAutomatic(Player player) {
        AutomaticRtp operation = automaticTasks.remove(player.getUniqueId());
        if (operation != null && operation.task != null) operation.task.cancel();
    }

    private static final class AutomaticRtp {
        private BukkitTask task;
        private UUID respawnWorld;
        private boolean awaitingRespawnTransition;
    }
}

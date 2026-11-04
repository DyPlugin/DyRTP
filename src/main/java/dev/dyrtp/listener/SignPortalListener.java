package dev.dyrtp.listener;

import dev.dyrtp.DyRTP;
import dev.dyrtp.lang.MessageService;
import dev.dyrtp.portal.PortalStore;
import dev.dyrtp.rtp.RtpCause;
import dev.dyrtp.rtp.RtpService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RTP signs and portal regions. Only administrators can create either; players use them with dyrtp.sign or dyrtp.portal.
 * A sign is marked in its own block data, so a player writing the same text on another sign gets nothing.
 */
public class SignPortalListener implements Listener {

    private final DyRTP plugin;
    private final RtpService rtpService;
    private final MessageService messages;
    private final PortalStore portals;
    private final NamespacedKey signKey;
    /** The portal each player is standing in, so a portal starts one RTP per entry, not one per step. */
    private final Map<UUID, String> inside = new ConcurrentHashMap<>();

    public SignPortalListener(DyRTP plugin, RtpService rtpService, MessageService messages, PortalStore portals) {
        this.plugin = plugin;
        this.rtpService = rtpService;
        this.messages = messages;
        this.portals = portals;
        this.signKey = new NamespacedKey(plugin, "rtp-sign");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignWrite(SignChangeEvent event) {
        if (!plugin.getConfig().getBoolean("signs.enabled", true)) return;
        String first = event.getLine(0) == null ? "" : ChatColor.stripColor(event.getLine(0)).trim();
        if (!first.equalsIgnoreCase(header())) return;
        Player player = event.getPlayer();
        if (!player.hasPermission("dyrtp.admin")) {
            event.setLine(0, first.replace("[", "").replace("]", ""));
            messages.send(player, "signs.no-permission");
            return;
        }
        String worldName = event.getLine(1) == null ? "" : ChatColor.stripColor(event.getLine(1)).trim();
        if (!worldName.isEmpty() && Bukkit.getWorld(worldName) == null) {
            messages.send(player, "signs.unknown-world", Map.of("{world}", worldName));
            return;
        }
        event.setLine(0, ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("signs.header-color", "&a&l")) + header());
        Block block = event.getBlock();
        String target = worldName;
        // The sign's text is written after this event; mark it on the next tick so the mark is not overwritten.
        // The player stands within reach, so their thread also owns the sign on Folia.
        plugin.scheduler().runLater(player, () -> {
            if (!(block.getState() instanceof Sign sign)) return;
            sign.getPersistentDataContainer().set(signKey, PersistentDataType.STRING, target);
            sign.update();
        }, 1L);
        messages.send(player, "signs.created", Map.of("{world}", target.isEmpty() ? messages.get(player, "signs.this-world") : target));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSignClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        if (!plugin.getConfig().getBoolean("signs.enabled", true)) return;
        if (!(event.getClickedBlock().getState() instanceof Sign sign)) return;
        String target = sign.getPersistentDataContainer().get(signKey, PersistentDataType.STRING);
        if (target == null) return;
        event.setCancelled(true); // Opens no sign editor on 1.20+.
        Player player = event.getPlayer();
        if (!player.hasPermission("dyrtp.use") || !player.hasPermission("dyrtp.sign")) {
            messages.send(player, "general.no-permission");
            return;
        }
        World world = target.isEmpty() ? null : Bukkit.getWorld(target);
        if (!target.isEmpty() && world == null) {
            messages.send(player, "rtp.world-disabled");
            return;
        }
        rtpService.request(player, RtpCause.COMMAND, null, world);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null || (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ() && from.getWorld() == to.getWorld())) return;
        Player player = event.getPlayer();
        if (!plugin.getConfig().getBoolean("portals.enabled", true) || portals.all().isEmpty()) {
            inside.remove(player.getUniqueId());
            return;
        }
        PortalStore.Portal portal = portals.at(to);
        if (portal == null) {
            inside.remove(player.getUniqueId());
            return;
        }
        if (portal.name().equals(inside.put(player.getUniqueId(), portal.name()))) return;
        if (!player.hasPermission("dyrtp.use") || !player.hasPermission("dyrtp.portal")) {
            messages.send(player, "general.no-permission");
            return;
        }
        World world = portal.target().isEmpty() ? null : Bukkit.getWorld(portal.target());
        if (!portal.target().isEmpty() && world == null) {
            messages.send(player, "rtp.world-disabled");
            return;
        }
        rtpService.request(player, RtpCause.PORTAL, null, world);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        inside.remove(event.getPlayer().getUniqueId());
    }

    private String header() {
        String text = plugin.getConfig().getString("signs.header", "[RTP]");
        return text == null || text.isBlank() ? "[RTP]" : text.trim();
    }
}

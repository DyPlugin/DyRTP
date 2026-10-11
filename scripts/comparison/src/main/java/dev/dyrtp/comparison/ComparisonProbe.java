package dev.dyrtp.comparison;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

/** Common black-box command-to-teleport measurement, for disposable servers only. */
public final class ComparisonProbe extends JavaPlugin implements Listener {
    private Run run;
    private final Set<String> hazards = Set.of("WATER", "LAVA", "FIRE", "CACTUS", "MAGMA_BLOCK", "CAMPFIRE",
            "SOUL_CAMPFIRE", "POWDER_SNOW", "SNOW", "COBWEB", "SWEET_BERRY_BUSH", "BEDROCK", "VOID_AIR");
    @Override public void onEnable() { getServer().getPluginManager().registerEvents(this, this); }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 4 || run != null) { sender.sendMessage("rtpcompare <dyrtp|betterrtp> <world> <count> <label>; one run at a time"); return true; }
        Player player = getServer().getPlayerExact("DyRtpReview");
        World world = getServer().getWorld(args[1]);
        if (player == null || world == null) { sender.sendMessage("Connect DyRtpReview and use an existing world"); return true; }
        String pluginName = args[0].equals("dyrtp") ? "DyRTP" : "BetterRTP";
        var plugin = getServer().getPluginManager().getPlugin(pluginName);
        if (plugin == null || !plugin.isEnabled()) { sender.sendMessage("Plugin unavailable: " + pluginName); return true; }
        int count = Math.max(1, Math.min(100, Integer.parseInt(args[2])));
        player.setGameMode(GameMode.CREATIVE);
        int y = world.getEnvironment() == World.Environment.NORMAL ? world.getHighestBlockYAt(0, 0) + 1 : 80;
        player.teleport(new Location(world, .5, y, .5));
        PermissionAttachment permissions = player.addAttachment(this);
        for (String permission : List.of("dyrtp.use", "betterrtp.use", "betterrtp.world." + world.getName())) permissions.setPermission(permission, true);
        run = new Run(player, world, args[0], args[3], count, permissions);
        getLogger().info("COMPARE START plugin=" + pluginName + " version=" + plugin.getDescription().getVersion() + " world=" + world.getName() + " label=" + args[3]);
        getServer().getScheduler().runTaskLater(this, this::next, 20L);
        return true;
    }
    private void next() {
        Run current = run;
        if (current == null) return;
        if (current.times.size() + current.failures >= current.count || current.failures >= 3) { finish(); return; }
        current.waiting = true;
        long request = ++current.sequence;
        current.started = System.nanoTime();
        try { getServer().dispatchCommand(current.player, current.plugin + ":" + current.plugin); }
        catch (RuntimeException error) { getLogger().warning("COMPARE command exception: " + error); }
        getServer().getScheduler().runTaskLater(this, () -> {
            if (run == current && current.waiting && current.sequence == request) {
                current.waiting = false; current.failures++;
                getLogger().warning("COMPARE request timeout label=" + current.label);
                getServer().getScheduler().runTaskLater(this, this::next, 5L);
            }
        }, 200L);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Run current = run;
        if (current == null || !current.waiting || event.getPlayer() != current.player || event.getTo() == null) return;
        double elapsed = (System.nanoTime() - current.started) / 1_000_000.0;
        Location target = event.getTo().clone();
        current.waiting = false;
        getServer().getScheduler().runTaskLater(this, () -> {
            if (run != current) return;
            Location actual = current.player.getLocation();
            if (actual.getWorld() != target.getWorld() || actual.distanceSquared(target) > .01) current.failures++;
            else {
                current.times.add(elapsed);
                if (!safe(target)) current.unsafe++;
            }
            getServer().getScheduler().runTaskLater(this, this::next, 5L);
        }, 1L);
    }
    @EventHandler public void onChunk(ChunkLoadEvent event) {
        if (run != null && event.getWorld() == run.world) {
            run.loaded++; if (event.isNewChunk()) run.generated++;
        }
    }
    private boolean safe(Location target) {
        World world = target.getWorld();
        Material ground = world.getBlockAt(target.getBlockX(), target.getBlockY() - 1, target.getBlockZ()).getType();
        if (!ground.isSolid() || hazards.contains(ground.name()) || ground.name().endsWith("_LEAVES") || !world.getWorldBorder().isInside(target)) return false;
        for (int y = target.getBlockY(); y <= target.getBlockY() + 1; y++) {
            var block = world.getBlockAt(target.getBlockX(), y, target.getBlockZ());
            if (hazards.contains(block.getType().name()) || !(block.getType().isAir() || block.isPassable())) return false;
        }
        return true;
    }
    private void finish() {
        Run current = run;
        current.player.removeAttachment(current.permissions);
        current.times.sort(Double::compare);
        int n = current.times.size();
        if (n > 0) getLogger().info(String.format(Locale.ROOT,
                "COMPARE RESULT plugin=%s world=%s label=%s successes=%d failures=%d unsafe=%d p50_ms=%.3f p95_ms=%.3f max_ms=%.3f chunk_loads=%d new_chunks=%d resident_start=%d resident_end=%d",
                current.plugin, current.world.getName(), current.label, n, current.failures, current.unsafe,
                current.times.get((n - 1) / 2), current.times.get((int) Math.ceil(n * .95) - 1), current.times.get(n - 1),
                current.loaded, current.generated, current.residentStart, current.world.getLoadedChunks().length));
        else getLogger().warning("COMPARE FAILED plugin=" + current.plugin + " label=" + current.label + " failures=" + current.failures);
        run = null;
    }
    private static final class Run {
        final Player player; final World world; final String plugin, label; final int count, residentStart;
        final PermissionAttachment permissions; final List<Double> times = new ArrayList<>();
        long started, sequence; boolean waiting; int failures, unsafe, loaded, generated;
        Run(Player player, World world, String plugin, String label, int count, PermissionAttachment permissions) {
            this.player = player; this.world = world; this.plugin = plugin; this.label = label; this.count = count; this.permissions = permissions;
            residentStart = world.getLoadedChunks().length;
        }
    }
}

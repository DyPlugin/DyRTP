package dev.dyrtp.probe;

import dev.dyrtp.DyRTP;
import dev.dyrtp.protection.Reflect;
import dev.dyrtp.rtp.LocationFinder;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Run only on an isolated server with a real protocol client named DyRtpReview. */
public class ReviewProbe extends JavaPlugin {
    @Override public void onEnable() { getCommand("dyrtpprobe").setExecutor(this); }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            if (args.length > 0 && args[0].equals("disable")) {
                getServer().getPluginManager().disablePlugin(getServer().getPluginManager().getPlugin("DyClaim"));
                DyRTP rtp = (DyRTP) getServer().getPluginManager().getPlugin("DyRTP");
                World world = getServer().getWorlds().get(0);
                check(!new LocationFinder(rtp, rtp.getProtection()).isSafeNow(new Location(world, -1.5, 101, -16.5), null), "disabled-DyClaim-final-validation");
                return true;
            }
            Player player = getServer().getPlayerExact("DyRtpReview");
            if (player == null) throw new IllegalStateException("Connect DyRtpReview first");
            DyRTP rtp = (DyRTP) getServer().getPluginManager().getPlugin("DyRTP");
            if (args.length > 0 && args[0].equals("metrics")) {
                measureSearches(rtp, player.getWorld(), new java.util.ArrayList<>(), new java.util.ArrayList<>(), player.getWorld().getLoadedChunks().length);
                return true;
            }
            if (args.length > 0 && args[0].equals("permissions")) {
                boolean wasOp = player.isOp();
                var attachment = player.addAttachment(this);
                try {
                    player.setOp(true);
                    attachment.setPermission("dyrtp.use", false);
                    attachment.setPermission("dyrtp.admin", false);
                    attachment.setPermission("dyrtp.bypass.warmup", false);
                    check(!player.hasPermission("dyrtp.use") && !player.hasPermission("dyrtp.admin")
                            && !player.hasPermission("dyrtp.bypass.warmup"), "op-explicit-deny");
                    var commandApi = getServer().getPluginCommand("dyrtp:dyrtp");
                    var tabs = commandApi.getTabCompleter().onTabComplete(player, commandApi, "dyrtp", new String[]{""});
                    check(!tabs.contains("admin") && !tabs.contains("rtp") && !tabs.contains("ışınlan"), "denied-help-tab-branch");
                    commandApi.getExecutor().onCommand(player, commandApi, "dyrtp", new String[0]);
                    player.setOp(false);
                    attachment.setPermission("dyrtp.use", true);
                    check(player.hasPermission("dyrtp.use") && !player.hasPermission("dyrtp.admin"), "single-player-leaf-grant");
                } finally { player.removeAttachment(attachment); player.setOp(wasOp); }
                return true;
            }
            if (args.length > 0 && args[0].equals("effects")) {
                verifyEffects(rtp, player);
                return true;
            }
            Object manager = Reflect.call(getServer().getPluginManager().getPlugin("DyClaim"), "getClaimManager");
            World world = player.getWorld();
            check(getServer().getPluginCommand("dyrtp:dyrtp").getPlugin() == rtp, "namespaced-command-owner");
            boolean loaded = world.isChunkLoaded(-50, -60);
            check(!rtp.getProtection().isChunkProtected(world, -50, -60), "unclaimed-chunk");
            check(loaded == world.isChunkLoaded(-50, -60), "query-does-not-load-chunk");
            Location target = new Location(world, -1.5, 101, -16.5);
            world.getChunkAt(-1, -2);
            world.getBlockAt(-2, 100, -17).setType(Material.STONE);
            world.getBlockAt(-2, 101, -17).setType(Material.AIR);
            world.getBlockAt(-2, 102, -17).setType(Material.AIR);
            Reflect.call(manager, "unclaimChunk", world.getName(), -1, -2);
            LocationFinder finder = new LocationFinder(rtp, rtp.getProtection());
            check(finder.isSafeNow(target, null), "negative-target-before-claim");
            Reflect.call(manager, "claimChunk", player, world.getChunkAt(-1, -2));
            check(rtp.getProtection().isLocationProtected(target), "negative-chunk-claimed");
            check(!finder.isSafeNow(target, null), "owner-claim-still-rejected");
            Reflect.call(manager, "unclaimChunk", world.getName(), -1, -2);
            finder.find(world, player.getLocation()).whenComplete((result, error) -> getServer().getScheduler().runTask(this, () -> {
                try {
                    if (error != null || result.location() == null) throw new IllegalStateException("Search failed", error);
                    Location found = result.location();
                    check(finder.isSafeNow(found, player.getLocation()), "real-location-search");
                    Reflect.call(manager, "claimChunk", player, found.getChunk());
                    check(!finder.isSafeNow(found, player.getLocation()), "claim-created-after-search");
                    Reflect.call(manager, "unclaimChunk", world.getName(), found.getBlockX() >> 4, found.getBlockZ() >> 4);
                    getLogger().info("PROBE COMPLETE");
                } catch (Exception failure) { getLogger().severe("PROBE FAILED: " + failure); }
            }));
        } catch (Exception failure) { getLogger().severe("PROBE FAILED: " + failure); }
        return true;
    }
    private void check(boolean condition, String scenario) {
        if (!condition) throw new IllegalStateException(scenario);
        getLogger().info("PROBE PASS " + scenario);
    }

    private void verifyEffects(DyRTP rtp, Player player) {
        var config = rtp.getConfig();
        Object warmup = config.get("warmup.enabled"), seconds = config.get("warmup.seconds"), blindness = config.get("feedback.effects.blindness.enabled");
        Runnable restore = () -> {
            rtp.getRtpService().cancelSilently(player);
            config.set("warmup.enabled", warmup); config.set("warmup.seconds", seconds);
            config.set("feedback.effects.blindness.enabled", blindness);
            player.removePotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS);
        };
        config.set("warmup.enabled", true); config.set("warmup.seconds", 5);
        config.set("feedback.effects.blindness.enabled", true);
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS, 1200, 2));
        rtp.getRtpService().request(player, dev.dyrtp.rtp.RtpCause.COMMAND);
        getServer().getScheduler().runTaskLater(this, () -> {
            try {
                check(rtp.getRtpService().hasPending(player), "effects-existing-warmup-started");
                rtp.getRtpService().cancelSilently(player);
                check(player.getPotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS).getAmplifier() == 2, "existing-blindness-preserved");
                player.removePotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS);
                rtp.getRtpService().request(player, dev.dyrtp.rtp.RtpCause.COMMAND);
                getServer().getScheduler().runTaskLater(this, () -> {
                    try {
                        check(rtp.getRtpService().hasPending(player) && player.hasPotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS), "effects-owned-warmup-started");
                        player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS, 1200, 3));
                        rtp.getRtpService().cancelSilently(player);
                        check(player.getPotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS).getAmplifier() == 3, "replacement-blindness-preserved");
                    } catch (Exception failure) { getLogger().severe("PROBE FAILED: " + failure); }
                    finally { restore.run(); }
                }, 30L);
            } catch (Exception failure) { getLogger().severe("PROBE FAILED: " + failure); restore.run(); }
        }, 30L);
    }

    private void measureSearches(DyRTP rtp, World world, java.util.List<Double> timings, java.util.List<Integer> attempts, int loadedBefore) {
        long started = System.nanoTime();
        new LocationFinder(rtp, rtp.getProtection()).find(world, null).whenComplete((result, error) -> {
            double elapsed = (System.nanoTime() - started) / 1_000_000.0;
            getServer().getScheduler().runTask(this, () -> {
                if (error != null || result == null || result.location() == null
                        || !new LocationFinder(rtp, rtp.getProtection()).isSafeNow(result.location(), null)) {
                    getLogger().severe("METRICS FAILED: " + error); return;
                }
                timings.add(elapsed); attempts.add(result.attempts());
                if (timings.size() < 20) {
                    measureSearches(rtp, world, timings, attempts, loadedBefore);
                } else {
                    timings.sort(Double::compare);
                    getLogger().info(String.format(java.util.Locale.ROOT,
                            "METRICS PASS world=%s samples=20 p50_ms=%.3f p95_ms=%.3f max_ms=%.3f mean_attempts=%.2f loaded_before=%d loaded_after=%d",
                            world.getName(), timings.get(9), timings.get(18), timings.get(19),
                            attempts.stream().mapToInt(Integer::intValue).average().orElse(0), loadedBefore, world.getLoadedChunks().length));
                }
            });
        });
    }
}

package dev.dyrtp.rtp;

import dev.dyrtp.DyRTP;
import dev.dyrtp.api.event.DyRtpFailEvent;
import dev.dyrtp.api.event.DyRtpPostTeleportEvent;
import dev.dyrtp.api.event.DyRtpPreTeleportEvent;
import dev.dyrtp.api.event.DyRtpRequestEvent;
import dev.dyrtp.cooldown.CooldownService;
import dev.dyrtp.economy.EconomyService;
import dev.dyrtp.lang.Language;
import dev.dyrtp.lang.MessageService;
import dev.dyrtp.protection.ProtectionService;
import dev.dyrtp.protection.Reflect;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class RtpService {

    private final DyRTP plugin;
    private final MessageService messages;
    private final EconomyService economy;
    private final CooldownService cooldownService;
    private final LocationFinder locationFinder;
    private final Map<UUID, PendingTeleport> pending = new ConcurrentHashMap<>();
    private final Map<UUID, PendingConfirmation> confirmations = new ConcurrentHashMap<>();
    private final Set<UUID> active = ConcurrentHashMap.newKeySet();

    public RtpService(DyRTP plugin, MessageService messages, EconomyService economy, ProtectionService protection) {
        this.plugin = plugin;
        this.messages = messages;
        this.economy = economy;
        this.cooldownService = new CooldownService(plugin);
        this.locationFinder = new LocationFinder(plugin, protection);
    }

    public void reload() {
        cancelAll();
    }

    public void request(Player player, RtpCause cause) {
        request(player, cause, false);
    }

    private void request(Player player, RtpCause cause, boolean confirmed) {
        if (!plugin.getConfig().getBoolean("rtp.enabled", true)) {
            sendRtp(player, "rtp.disabled");
            return;
        }
        UUID uuid = player.getUniqueId();
        if (pending.containsKey(uuid) || confirmations.containsKey(uuid) || active.contains(uuid)) {
            sendRtp(player, "rtp.already-running");
            return;
        }

        World world = resolveWorld(player.getWorld());
        if (world == null) {
            sendRtp(player, "rtp.world-disabled");
            return;
        }

        DyRtpRequestEvent requestEvent = new DyRtpRequestEvent(player, cause, world);
        Bukkit.getPluginManager().callEvent(requestEvent);
        if (requestEvent.isCancelled()) {
            sendRtp(player, "rtp.cancelled-by-plugin");
            return;
        }
        world = requestEvent.getWorld();
        if (world == null || plugin.getConfig().getStringList("rtp.disabled-worlds").contains(world.getName())) {
            sendRtp(player, "rtp.world-disabled");
            return;
        }

        if (!cause.ignoreCooldown(plugin)) {
            long remaining = cooldownService.remainingSeconds(player, world);
            if (remaining > 0L) {
                sendRtp(player, "rtp.cooldown", Map.of("{time}", formatTime(player, remaining)));
                return;
            }
        }

        if (costApplies(player, cause)) {
            if (!economy.available()) {
                sendRtp(player, "rtp.economy-missing");
                return;
            }
            if (!economy.has(player)) {
                sendRtp(player, "rtp.not-enough-money", Map.of("{cost}", economy.format(economy.cost())));
                return;
            }
            if (!confirmed && plugin.getConfig().getBoolean("economy.confirmation.enabled", true)) {
                askConfirmation(player, cause);
                return;
            }
        }

        searchAndTeleport(player, world, cause);
    }

    public void confirm(Player player) {
        PendingConfirmation confirmation = confirmations.remove(player.getUniqueId());
        if (confirmation == null || confirmation.expired()) {
            sendRtp(player, "rtp.no-confirmation");
            return;
        }
        sendRtp(player, "rtp.confirmed");
        request(player, confirmation.cause(), true);
    }

    public void reject(Player player) {
        PendingConfirmation confirmation = confirmations.remove(player.getUniqueId());
        if (confirmation == null) {
            sendRtp(player, "rtp.no-confirmation");
            return;
        }
        sendRtp(player, "rtp.rejected");
    }

    public boolean hasPending(Player player) {
        return pending.containsKey(player.getUniqueId());
    }

    public void cancelForMove(Player player) {
        cancel(player, "rtp.cancel-move");
    }

    public void cancelForDamage(Player player) {
        cancel(player, "rtp.cancel-damage");
    }

    public void cancel(Player player, String messagePath) {
        PendingTeleport pendingTeleport = pending.remove(player.getUniqueId());
        if (pendingTeleport == null) {
            return;
        }
        pendingTeleport.task.cancel();
        active.remove(player.getUniqueId());
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        playSound(player, "feedback.sounds.cancel");
        sendRtp(player, messagePath);
    }

    public void cancelSilently(Player player) {
        PendingTeleport pendingTeleport = pending.remove(player.getUniqueId());
        if (pendingTeleport != null) {
            pendingTeleport.task.cancel();
        }
        active.remove(player.getUniqueId());
        confirmations.remove(player.getUniqueId());
    }

    public void cancelAll() {
        for (PendingTeleport pendingTeleport : pending.values()) {
            pendingTeleport.task.cancel();
        }
        pending.clear();
        confirmations.clear();
        active.clear();
    }

    public Location pendingStart(Player player) {
        PendingTeleport pendingTeleport = pending.get(player.getUniqueId());
        return pendingTeleport != null ? pendingTeleport.start : null;
    }

    private boolean warmupEnabled(Player player, RtpCause cause) {
        return !cause.ignoreCooldown(plugin)
                && plugin.getConfig().getBoolean("warmup.enabled", false)
                && plugin.getConfig().getInt("warmup.seconds", 0) > 0
                && !player.hasPermission("dyrtp.bypass.warmup");
    }

    private void startWarmup(Player player, Location location, int attempts, World world, RtpCause cause) {
        int seconds = Math.max(1, plugin.getConfig().getInt("warmup.seconds", 3));
        sendRtp(player, "rtp.warmup", Map.of("{seconds}", String.valueOf(seconds)));
        showTitle(player, "rtp.warmup-title-v2", "rtp.warmup-subtitle-v2", Map.of());
        playSound(player, "feedback.sounds.warmup");

        if (plugin.getConfig().getBoolean("feedback.effects.blindness.enabled", false)) {
            int duration = seconds * 20 + 40;
            int amplifier = Math.max(0, plugin.getConfig().getInt("feedback.effects.blindness.amplifier", 0));
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, duration, amplifier, false, false, false));
        }

        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            pending.remove(player.getUniqueId());
            player.removePotionEffect(PotionEffectType.BLINDNESS);
            finishTeleport(player, location, attempts, world, cause);
        }, seconds * 20L);

        pending.put(player.getUniqueId(), new PendingTeleport(task, player.getLocation()));
    }

    private void searchAndTeleport(Player player, World world, RtpCause cause) {
        if (!player.isOnline()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        if (!active.add(uuid)) {
            sendRtp(player, "rtp.already-running");
            return;
        }
        sendRtp(player, "rtp.searching-safe");
        sendActionbar(player, messages.get(player, "rtp.searching-safe"));

        locationFinder.find(world, player.getLocation()).thenAccept(result -> runMain(() -> {
            if (!player.isOnline()) {
                active.remove(uuid);
                return;
            }
            if (result.location() == null) {
                active.remove(uuid);
                Bukkit.getPluginManager().callEvent(new DyRtpFailEvent(player, cause, result.attempts()));
                sendRtp(player, "rtp.failed-safe", Map.of("{attempts}", String.valueOf(result.attempts())));
                playSound(player, "feedback.sounds.failed");
                return;
            }
            if (warmupEnabled(player, cause)) {
                startWarmup(player, result.location(), result.attempts(), world, cause);
            } else {
                finishTeleport(player, result.location(), result.attempts(), world, cause);
            }
        }));
    }

    private void finishTeleport(Player player, Location location, int attempts, World world, RtpCause cause) {
        DyRtpPreTeleportEvent preTeleportEvent = new DyRtpPreTeleportEvent(player, cause, location);
        Bukkit.getPluginManager().callEvent(preTeleportEvent);
        if (preTeleportEvent.isCancelled() || preTeleportEvent.getLocation() == null) {
            active.remove(player.getUniqueId());
            sendRtp(player, "rtp.cancelled-by-plugin");
            return;
        }
        Location target = preTeleportEvent.getLocation();

        if (costApplies(player, cause)) {
            if (!economy.withdraw(player)) {
                active.remove(player.getUniqueId());
                sendRtp(player, "rtp.not-enough-money", Map.of("{cost}", economy.format(economy.cost())));
                return;
            }
        }

        target.setYaw(player.getLocation().getYaw());
        target.setPitch(player.getLocation().getPitch());
        teleport(player, target).thenAccept(success -> runMain(() -> {
            try {
                if (!Boolean.TRUE.equals(success) || !player.isOnline()) {
                    Bukkit.getPluginManager().callEvent(new DyRtpFailEvent(player, cause, attempts));
                    sendRtp(player, "rtp.failed-safe", Map.of("{attempts}", String.valueOf(attempts)));
                    playSound(player, "feedback.sounds.failed");
                    return;
                }
                if (!cause.ignoreCooldown(plugin)) {
                    cooldownService.start(player, target.getWorld() != null ? target.getWorld() : world);
                }
                if (cause == RtpCause.FIRST_JOIN && plugin.getConfig().getBoolean("first-join-rtp.set-as-respawn", false)) {
                    player.setBedSpawnLocation(target, true);
                }

                Map<String, String> placeholders = coordinatePlaceholders(target, attempts);
                if (costApplies(player, cause)) {
                    sendRtp(player, "rtp.fee-charged", Map.of("{cost}", economy.format(economy.cost())));
                }
                sendRtp(player, "rtp.success-location-v2", placeholders);
                showTitle(player, "rtp.success-title", "rtp.success-subtitle-v2", placeholders);
                sendActionbar(player, messages.get(player, "rtp.success-location-v2", placeholders));
                playSound(player, "feedback.sounds.success");
                Bukkit.getPluginManager().callEvent(new DyRtpPostTeleportEvent(player, cause, target));
            } finally {
                active.remove(player.getUniqueId());
            }
        }));
    }

    private World resolveWorld(World current) {
        String override = plugin.getConfig().getString("rtp.world-overrides." + current.getName());
        if (override == null || override.isBlank()) {
            return current;
        }
        return Bukkit.getWorld(override);
    }

    private void sendRtp(Player player, String path) {
        sendRtp(player, path, Map.of());
    }

    private void sendRtp(Player player, String path, Map<String, String> placeholders) {
        if (plugin.getConfig().getBoolean("feedback.chat.enabled", true)) {
            messages.send(player, path, placeholders);
        }
    }

    private void sendActionbar(Player player, String message) {
        if (!plugin.getConfig().getBoolean("feedback.actionbar.enabled", false)) {
            return;
        }
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
    }

    private boolean costApplies(Player player, RtpCause cause) {
        return !cause.ignoreEconomy(plugin)
                && economy.enabled()
                && economy.cost() > 0.0D
                && !player.hasPermission("dyrtp.bypass.economy");
    }

    private void askConfirmation(Player player, RtpCause cause) {
        int timeout = Math.max(5, plugin.getConfig().getInt("economy.confirmation.timeout-seconds", 30));
        confirmations.put(player.getUniqueId(), new PendingConfirmation(cause, System.currentTimeMillis() + timeout * 1000L));
        Map<String, String> placeholders = Map.of("{cost}", economy.format(economy.cost()));
        sendRtp(player, "rtp.confirm-price", placeholders);

        boolean turkish = messages.language(player) == Language.TR;
        TextComponent approve = new TextComponent(messages.get(player, "rtp.confirm-button"));
        approve.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, turkish ? "/dyrtp:onayla" : "/dyrtp:confirm"));
        TextComponent space = new TextComponent(" ");
        TextComponent reject = new TextComponent(messages.get(player, "rtp.cancel-button"));
        reject.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, turkish ? "/dyrtp:reddet" : "/dyrtp:cancel"));
        player.spigot().sendMessage(approve, space, reject);
    }

    private void showTitle(Player player, String titlePath, String subtitlePath, Map<String, String> placeholders) {
        if (!plugin.getConfig().getBoolean("feedback.titles.enabled", false)) {
            return;
        }
        int fadeIn = plugin.getConfig().getInt("feedback.titles.fade-in", 10);
        int stay = plugin.getConfig().getInt("feedback.titles.stay", 40);
        int fadeOut = plugin.getConfig().getInt("feedback.titles.fade-out", 10);
        player.sendTitle(
                messages.get(player, titlePath, placeholders),
                messages.get(player, subtitlePath, placeholders),
                fadeIn,
                stay,
                fadeOut
        );
    }

    private void playSound(Player player, String path) {
        if (!plugin.getConfig().getBoolean("feedback.sounds.enabled", false)) {
            return;
        }
        String soundName = plugin.getConfig().getString(path, "");
        try {
            Sound sound = Sound.valueOf(soundName);
            float volume = (float) plugin.getConfig().getDouble("feedback.sounds.volume", 1.0D);
            float pitch = (float) plugin.getConfig().getDouble("feedback.sounds.pitch", 1.0D);
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void runMain(Runnable runnable) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<Boolean> teleport(Player player, Location location) {
        try {
            Object result = Reflect.call(player, "teleportAsync", location);
            if (result instanceof CompletableFuture<?>) {
                return (CompletableFuture<Boolean>) result;
            }
        } catch (Exception ignored) {
        }
        return CompletableFuture.completedFuture(player.teleport(location));
    }

    private Map<String, String> coordinatePlaceholders(Location location, int attempts) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("{x}", String.valueOf(location.getBlockX()));
        placeholders.put("{y}", String.valueOf(location.getBlockY()));
        placeholders.put("{z}", String.valueOf(location.getBlockZ()));
        placeholders.put("{world}", location.getWorld() != null ? location.getWorld().getName() : "world");
        placeholders.put("{attempts}", String.valueOf(attempts));
        return placeholders;
    }

    private String formatTime(Player player, long seconds) {
        if (messages.language(player) == Language.TR) {
            if (seconds < 60L) {
                return seconds + " sn";
            }
            if (seconds < 3600L) {
                long minutes = seconds / 60L;
                long rest = seconds % 60L;
                return rest > 0L ? minutes + " dk " + rest + " sn" : minutes + " dk";
            }
            long hours = seconds / 3600L;
            long minutes = (seconds % 3600L) / 60L;
            return minutes > 0L ? hours + " sa " + minutes + " dk" : hours + " sa";
        }
        if (seconds < 60L) {
            return seconds + "s";
        }
        if (seconds >= 3600L) {
            long hours = seconds / 3600L;
            long minutes = (seconds % 3600L) / 60L;
            return minutes > 0L ? hours + "h " + minutes + "m" : hours + "h";
        }
        long minutes = seconds / 60L;
        long rest = seconds % 60L;
        return rest > 0L ? minutes + "m " + rest + "s" : minutes + "m";
    }

    private record PendingTeleport(BukkitTask task, Location start) {
    }

    private record PendingConfirmation(RtpCause cause, long expiresAt) {

        private boolean expired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}

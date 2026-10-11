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
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
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
    private final Map<UUID, Request> active = new ConcurrentHashMap<>();
    private final Map<UUID, Request> teleporting = new ConcurrentHashMap<>();
    private long lifecycleRevision;

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
        request(player, cause, null);
    }

    private void request(Player player, RtpCause cause, PendingConfirmation confirmed) {
        if (!player.isOnline() || player.isDead() || !plugin.isEnabled()) return;
        if (cause == RtpCause.COMMAND && !player.hasPermission("dyrtp.use")) {
            messages.send(player, "general.no-permission");
            return;
        }
        if (!plugin.getConfig().getBoolean("rtp.enabled", true)) {
            sendRtp(player, "rtp.disabled");
            return;
        }
        UUID uuid = player.getUniqueId();
        confirmations.computeIfPresent(uuid, (id, value) -> value.expired() ? null : value);
        if (pending.containsKey(uuid) || confirmations.containsKey(uuid) || active.containsKey(uuid) || teleporting.containsKey(uuid)) {
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

        double quotedCost = costApplies(player, cause) ? economy.cost() : 0;
        if (confirmed != null && (!confirmed.world().equals(world.getUID())
                || !confirmed.sourceWorld().equals(player.getWorld().getUID())
                || Double.compare(confirmed.cost(), quotedCost) != 0)) {
            sendRtp(player, "rtp.confirmation-changed");
            return;
        }
        if (quotedCost > 0) {
            if (!economy.available()) {
                sendRtp(player, "rtp.economy-missing");
                return;
            }
            try {
                if (!economy.has(player, quotedCost)) {
                    sendRtp(player, "rtp.not-enough-money", Map.of("{cost}", economy.format(quotedCost)));
                    return;
                }
            } catch (IllegalStateException providerFailure) {
                sendRtp(player, "rtp.economy-failed");
                return;
            }
            if (confirmed == null && plugin.getConfig().getBoolean("economy.confirmation.enabled", true)) {
                askConfirmation(player, cause, world, quotedCost);
                return;
            }
        }

        searchAndTeleport(player, world, cause, quotedCost);
    }

    public void confirm(Player player) {
        confirm(player, null);
    }

    public void confirm(Player player, String token) {
        PendingConfirmation confirmation = confirmations.get(player.getUniqueId());
        if (confirmation != null && token != null && !confirmation.token().equals(token)) {
            sendRtp(player, "rtp.no-confirmation");
            return;
        }
        if (confirmation != null) confirmations.remove(player.getUniqueId(), confirmation);
        if (confirmation == null || confirmation.expired()) {
            sendRtp(player, "rtp.no-confirmation");
            return;
        }
        sendRtp(player, "rtp.confirmed");
        request(player, confirmation.cause(), confirmation);
    }

    public void reject(Player player) {
        reject(player, null);
    }

    public void reject(Player player, String token) {
        PendingConfirmation confirmation = confirmations.get(player.getUniqueId());
        if (confirmation != null && token != null && !confirmation.token().equals(token)) {
            sendRtp(player, "rtp.no-confirmation");
            return;
        }
        if (confirmation != null) confirmations.remove(player.getUniqueId(), confirmation);
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
        if (!active.containsKey(player.getUniqueId())) return;
        cancelSilently(player);
        playSound(player, "feedback.sounds.cancel");
        sendRtp(player, messagePath);
    }

    public void cancelSilently(Player player) {
        PendingTeleport pendingTeleport = pending.remove(player.getUniqueId());
        if (pendingTeleport != null) {
            pendingTeleport.task.cancel();
        }
        Request request = active.remove(player.getUniqueId());
        if (request != null) {
            if (request.search != null) request.search.cancel(false);
            clearBlindness(request);
            refund(request);
        }
        confirmations.remove(player.getUniqueId());
    }

    public void cancelAll() {
        lifecycleRevision++;
        for (Request request : java.util.List.copyOf(active.values())) cancelSilently(request.player);
        pending.clear();
        confirmations.clear();
        active.clear();
    }

    public long lifecycleRevision() { return lifecycleRevision; }

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

    private void startWarmup(Request request, Location location, int attempts, World world, RtpCause cause) {
        Player player = request.player;
        int seconds = Math.max(1, plugin.getConfig().getInt("warmup.seconds", 3));
        sendRtp(player, "rtp.warmup", Map.of("{seconds}", String.valueOf(seconds)));
        showTitle(player, "rtp.warmup-title-v2", "rtp.warmup-subtitle-v2", Map.of());
        playSound(player, "feedback.sounds.warmup");

        if (plugin.getConfig().getBoolean("feedback.effects.blindness.enabled", false)
                && !player.hasPotionEffect(PotionEffectType.BLINDNESS)) {
            int duration = seconds * 20 + 40;
            int amplifier = Math.max(0, plugin.getConfig().getInt("feedback.effects.blindness.amplifier", 0));
            PotionEffect effect = new PotionEffect(PotionEffectType.BLINDNESS, duration, amplifier, false, false, false);
            if (player.addPotionEffect(effect)) {
                request.blindness = effect;
                request.blindnessAppliedAt = player.getTicksLived();
            }
        }

        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!current(request)) return;
            pending.remove(player.getUniqueId());
            clearBlindness(request);
            finishTeleport(request, location, attempts, world, cause);
        }, seconds * 20L);

        pending.put(player.getUniqueId(), new PendingTeleport(task, player.getLocation()));
    }

    private void searchAndTeleport(Player player, World world, RtpCause cause, double cost) {
        if (!player.isOnline()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        Request request = new Request(player, cause, cost, plugin.getConfig().getBoolean("economy.charge-on-success-only", true));
        if (active.putIfAbsent(uuid, request) != null) {
            sendRtp(player, "rtp.already-running");
            return;
        }
        sendRtp(player, "rtp.searching-safe");
        sendActionbar(player, messages.get(player, "rtp.searching-safe"));

        request.search = locationFinder.find(world, request.origin);
        request.search.whenComplete((result, error) -> runMain(() -> {
            if (!current(request)) return;
            if (!player.isOnline() || player.isDead() || !player.getWorld().equals(request.origin.getWorld())) {
                cancelSilently(player);
                return;
            }
            if (error != null || result == null || result.location() == null) {
                fail(request, result != null ? result.attempts() : 0);
                return;
            }
            if (warmupEnabled(player, cause)) {
                startWarmup(request, result.location(), result.attempts(), world, cause);
            } else {
                finishTeleport(request, result.location(), result.attempts(), world, cause);
            }
        }));
    }

    private void finishTeleport(Request request, Location location, int attempts, World world, RtpCause cause) {
        Player player = request.player;
        if (!current(request)) return;
        if (!player.isOnline() || player.isDead() || !player.getWorld().equals(request.origin.getWorld())
                || (cause == RtpCause.COMMAND && !player.hasPermission("dyrtp.use"))) {
            cancelSilently(player);
            return;
        }
        DyRtpPreTeleportEvent preTeleportEvent = new DyRtpPreTeleportEvent(player, cause, location);
        Bukkit.getPluginManager().callEvent(preTeleportEvent);
        if (preTeleportEvent.isCancelled() || preTeleportEvent.getLocation() == null) {
            cancelSilently(player);
            sendRtp(player, "rtp.cancelled-by-plugin");
            return;
        }
        Location target = preTeleportEvent.getLocation().clone();
        if (!current(request)) return;
        // Public event handlers may change permission, session or world state.
        if (!eligible(request)) {
            cancelSilently(player);
            return;
        }
        if (!locationFinder.isSafeNow(target, request.origin)) {
            fail(request, attempts);
            return;
        }

        double currentCost = costApplies(player, cause) ? economy.cost() : 0;
        if (Double.compare(currentCost, request.cost) != 0) {
            cancelSilently(player);
            sendRtp(player, "rtp.confirmation-changed");
            return;
        }
        if (request.cost > 0) {
            request.withdrawal = economy.withdraw(player, request.cost);
            if (request.withdrawal == null) {
                cancelSilently(player);
                sendRtp(player, "rtp.economy-failed");
                return;
            }
        }
        if (!current(request)) {
            refund(request);
            return;
        }

        target.setYaw(player.getLocation().getYaw());
        target.setPitch(player.getLocation().getPitch());
        teleporting.put(player.getUniqueId(), request);
        boolean success;
        Listener finalGuard = new Listener() { };
        try {
            // Register last among HIGHEST handlers for this synchronous operation.
            // This sees destinations redirected by already registered plugins.
            Bukkit.getPluginManager().registerEvent(PlayerTeleportEvent.class, finalGuard, EventPriority.HIGHEST,
                    (listener, event) -> {
                        PlayerTeleportEvent teleportEvent = (PlayerTeleportEvent) event;
                        if (teleportEvent.getPlayer().getUniqueId().equals(player.getUniqueId())
                                && !validateTeleport(player, teleportEvent.getTo())) teleportEvent.setCancelled(true);
                    }, plugin, true);
            // The search loaded the target chunk. Keep the teleport and event dispatch
            // on the main thread so a cancelled request cannot later teleport asynchronously.
            success = player.teleport(target);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("RTP teleport failed: " + exception.getMessage());
            success = false;
        } finally {
            HandlerList.unregisterAll(finalGuard);
            teleporting.remove(player.getUniqueId(), request);
        }
        if (!current(request)) return;
            try {
                if (!success || !player.isOnline()) {
                    fail(request, attempts);
                    return;
                }
                request.withdrawal = null; // Completed payment must never be refunded by later cleanup.
                target = player.getLocation().clone();
                if (!cause.ignoreCooldown(plugin)) {
                    cooldownService.start(player, target.getWorld() != null ? target.getWorld() : world);
                }
                if (cause == RtpCause.FIRST_JOIN && plugin.getConfig().getBoolean("first-join-rtp.set-as-respawn", false)) {
                    player.setBedSpawnLocation(target, true);
                }

                Map<String, String> placeholders = coordinatePlaceholders(target, attempts);
                if (request.cost > 0) {
                    sendRtp(player, "rtp.fee-charged", Map.of("{cost}", economy.format(request.cost)));
                }
                sendRtp(player, "rtp.success-location-v2", placeholders);
                showTitle(player, "rtp.success-title", "rtp.success-subtitle-v2", placeholders);
                sendActionbar(player, messages.get(player, "rtp.success-location-v2", placeholders));
                playSound(player, "feedback.sounds.success");
                Bukkit.getPluginManager().callEvent(new DyRtpPostTeleportEvent(player, cause, target));
            } finally {
                active.remove(player.getUniqueId(), request);
            }
    }

    /** Checks the final destination supplied by other teleport listeners. */
    public boolean validateTeleport(Player player, Location target) {
        Request request = teleporting.get(player.getUniqueId());
        return request == null || (current(request) && eligible(request) && target != null
                && locationFinder.isSafeNow(target, request.origin));
    }

    public boolean isTeleporting(Player player) {
        return teleporting.containsKey(player.getUniqueId());
    }

    private boolean current(Request request) {
        return plugin.isEnabled() && active.get(request.player.getUniqueId()) == request;
    }

    private boolean eligible(Request request) {
        Player player = request.player;
        return player.isOnline() && !player.isDead() && player.getWorld().equals(request.origin.getWorld())
                && (request.cause != RtpCause.COMMAND || player.hasPermission("dyrtp.use"));
    }

    private void fail(Request request, int attempts) {
        if (!current(request)) return;
        cancelSilently(request.player);
        Bukkit.getPluginManager().callEvent(new DyRtpFailEvent(request.player, request.cause, attempts));
        sendRtp(request.player, "rtp.failed-safe", Map.of("{attempts}", String.valueOf(attempts)));
        playSound(request.player, "feedback.sounds.failed");
    }

    private void refund(Request request) {
        EconomyService.Withdrawal withdrawal = request.withdrawal;
        request.withdrawal = null;
        if (withdrawal != null && request.refundOnFailure && !withdrawal.refund() && request.player.isOnline())
            sendRtp(request.player, "rtp.refund-failed");
    }

    private void clearBlindness(Request request) {
        if (request.blindness == null) return;
        PotionEffect current = request.player.getPotionEffect(PotionEffectType.BLINDNESS);
        PotionEffect own = request.blindness;
        int expected = own.getDuration() - (request.player.getTicksLived() - request.blindnessAppliedAt);
        if (current != null && current.getAmplifier() == own.getAmplifier() && current.isAmbient() == own.isAmbient()
                && current.hasParticles() == own.hasParticles() && current.hasIcon() == own.hasIcon()
                && Math.abs(current.getDuration() - expected) <= 1)
            request.player.removePotionEffect(PotionEffectType.BLINDNESS);
        request.blindness = null;
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

    private void askConfirmation(Player player, RtpCause cause, World world, double cost) {
        int timeout = Math.max(5, plugin.getConfig().getInt("economy.confirmation.timeout-seconds", 30));
        String token = UUID.randomUUID().toString();
        confirmations.put(player.getUniqueId(), new PendingConfirmation(cause, System.currentTimeMillis() + timeout * 1000L,
                world.getUID(), player.getWorld().getUID(), cost, token));
        Map<String, String> placeholders = Map.of("{cost}", economy.format(cost));
        sendRtp(player, "rtp.confirm-price", placeholders);

        boolean turkish = messages.language(player) == Language.TR;
        TextComponent approve = new TextComponent(messages.get(player, "rtp.confirm-button"));
        approve.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, (turkish ? "/dyrtp:dyrtp onayla " : "/dyrtp:dyrtp confirm ") + token));
        TextComponent space = new TextComponent(" ");
        TextComponent reject = new TextComponent(messages.get(player, "rtp.cancel-button"));
        reject.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, (turkish ? "/dyrtp:dyrtp reddet " : "/dyrtp:dyrtp cancel ") + token));
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
        if (!plugin.isEnabled()) return;
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
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

    private static final class Request {
        private final Player player;
        private final RtpCause cause;
        private final double cost;
        private final boolean refundOnFailure;
        private final Location origin;
        private CompletableFuture<LocationFinder.SearchResult> search;
        private EconomyService.Withdrawal withdrawal;
        private PotionEffect blindness;
        private int blindnessAppliedAt;

        private Request(Player player, RtpCause cause, double cost, boolean refundOnFailure) {
            this.player = player;
            this.cause = cause;
            this.cost = cost;
            this.refundOnFailure = refundOnFailure;
            this.origin = player.getLocation().clone();
        }
    }

    private record PendingConfirmation(RtpCause cause, long expiresAt, UUID world, UUID sourceWorld, double cost, String token) {

        private boolean expired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}

package dev.dyrtp.update;

import dev.dyrtp.DyRTP;
import dev.dyrtp.lang.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.concurrent.atomic.AtomicBoolean;

public class ModrinthUpdateChecker {

    private static final String PROJECT_SLUG = "dyrtp";
    private static final String PROJECT_URL = "https://modrinth.com/plugin/dyrtp";
    private static final String NOTIFY_PERMISSION = "dyrtp.admin";
    private static final long CHECK_INTERVAL_MINUTES = 360L;

    private final DyRTP plugin;
    private final MessageService messages;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    private BukkitTask repeatingTask;
    private BukkitTask initialTask;
    private volatile long generation;
    private final AtomicBoolean checking = new AtomicBoolean();
    private String lastNotifiedVersion = "";

    public ModrinthUpdateChecker(DyRTP plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    public void start() {
        cancel();
        initialTask = Bukkit.getScheduler().runTaskLater(plugin, this::check, 60L);
        repeatingTask = Bukkit.getScheduler().runTaskTimer(
                plugin,
                this::check,
                CHECK_INTERVAL_MINUTES * 60L * 20L,
                CHECK_INTERVAL_MINUTES * 60L * 20L
        );
    }

    public void reload() {
        start();
    }

    public void cancel() {
        generation++;
        if (initialTask != null) { initialTask.cancel(); initialTask = null; }
        if (repeatingTask != null) {
            repeatingTask.cancel();
            repeatingTask = null;
        }
    }

    private void check() {
        if (!checking.compareAndSet(false, true)) return;
        String current = plugin.getDescription().getVersion();
        String game = plugin.getServer().getBukkitVersion().split("-", 2)[0];
        String name = plugin.getServer().getName().toLowerCase(java.util.Locale.ROOT);
        String loader = name.contains("purpur") ? "purpur" : name.contains("paper") ? "paper"
                : name.contains("spigot") ? "spigot" : "bukkit";
        long requestedGeneration = generation;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Optional<Release> latest = fetchLatestVersion(game, loader);
                if (latest.isEmpty() || compareVersions(latest.get().version(), current) <= 0
                        || !plugin.isEnabled() || requestedGeneration != generation) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (requestedGeneration != generation || !plugin.isEnabled()) return;
                    Release release = latest.get();
                    if (release.version().equalsIgnoreCase(lastNotifiedVersion)) return;
                    lastNotifiedVersion = release.version();
                    notifyUpdate(current, release);
                });
            } catch (Exception exception) {
                plugin.getLogger().warning("Modrinth update check failed: " + exception.getMessage());
            } finally { checking.set(false); }
        });
    }

    private Optional<Release> fetchLatestVersion(String game, String loader) throws Exception {
        String encodedProject = URLEncoder.encode(PROJECT_SLUG, StandardCharsets.UTF_8);
        URI uri = URI.create("https://api.modrinth.com/v2/project/" + encodedProject
                + "/version?include_changelog=false&game_versions="
                + URLEncoder.encode("[\"" + game + "\"]", StandardCharsets.UTF_8)
                + "&loaders=" + URLEncoder.encode("[\"" + loader + "\"]", StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .header("User-Agent", "DyRTP/" + plugin.getDescription().getVersion()
                        + " (Minecraft server update checker)")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new java.io.IOException("HTTP " + response.statusCode());
        }
        return selectRelease(response.body(), game, loader);
    }

    private void notifyUpdate(String current, Release latest) {
        CommandSender console = Bukkit.getConsoleSender();
        messages.send(console, "updates.available", placeholders(current, latest));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(NOTIFY_PERMISSION)) {
                messages.send(player, "updates.available", placeholders(current, latest));
            }
        }
    }

    private static java.util.Map<String, String> placeholders(String current, Release latest) {
        return java.util.Map.of(
                "{current}", current,
                "{latest}", latest.version(),
                "{url}", latest.url()
        );
    }

    static Optional<Release> selectRelease(String json, String game, String loader) {
        JsonArray versions = JsonParser.parseString(json).getAsJsonArray();
        Release best = null;
        for (var element : versions) {
            JsonObject entry = element.getAsJsonObject();
            if (!"release".equals(entry.get("version_type").getAsString())
                    || !contains(entry.getAsJsonArray("game_versions"), game)
                    || !contains(entry.getAsJsonArray("loaders"), loader)) continue;
            String version = entry.get("version_number").getAsString();
            String id = entry.get("id").getAsString();
            if (!id.matches("[A-Za-z0-9]+")) continue;
            if (best == null || compareVersions(version, best.version()) > 0)
                best = new Release(version, PROJECT_URL + "/version/" + id);
        }
        return Optional.ofNullable(best);
    }

    private static boolean contains(JsonArray values, String expected) {
        if (values == null) return false;
        for (var value : values) if (expected.equals(value.getAsString())) return true;
        return false;
    }

    record Release(String version, String url) { }

    private static int compareVersions(String left, String right) {
        int[] leftCore = versionCore(left);
        int[] rightCore = versionCore(right);
        for (int i = 0; i < Math.max(leftCore.length, rightCore.length); i++) {
            int leftPart = i < leftCore.length ? leftCore[i] : 0;
            int rightPart = i < rightCore.length ? rightCore[i] : 0;
            if (leftPart != rightPart) {
                return Integer.compare(leftPart, rightPart);
            }
        }
        return Integer.compare(stabilityRank(left), stabilityRank(right));
    }

    private static int[] versionCore(String version) {
        Matcher matcher = Pattern.compile("\\d+").matcher(version.replaceFirst("(?i)^v", "").split("[-+]", 2)[0]);
        int[] parts = new int[4];
        int index = 0;
        while (matcher.find() && index < parts.length) {
            try {
                parts[index] = Integer.parseInt(matcher.group());
            } catch (NumberFormatException ignored) {
                parts[index] = Integer.MAX_VALUE;
            }
            index++;
        }
        if (index == parts.length) {
            return parts;
        }
        int[] trimmed = new int[index];
        System.arraycopy(parts, 0, trimmed, 0, index);
        return trimmed;
    }

    private static int stabilityRank(String version) {
        String lower = version.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("snapshot") || lower.contains("dev")) {
            return 0;
        }
        if (lower.contains("alpha")) {
            return 1;
        }
        if (lower.contains("beta")) {
            return 2;
        }
        return 3;
    }
}

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

public class ModrinthUpdateChecker {

    private static final Pattern VERSION_PATTERN = Pattern.compile("\"version_number\"\\s*:\\s*\"([^\"]+)\"");
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
    private String lastNotifiedVersion = "";

    public ModrinthUpdateChecker(DyRTP plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    public void start() {
        cancel();
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, this::check, 60L);
        repeatingTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
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
        if (repeatingTask != null) {
            repeatingTask.cancel();
            repeatingTask = null;
        }
    }

    private void check() {
        try {
            String current = plugin.getDescription().getVersion();
            Optional<String> latest = fetchLatestVersion();
            if (latest.isEmpty() || compareVersions(latest.get(), current) <= 0) {
                return;
            }
            if (latest.get().equalsIgnoreCase(lastNotifiedVersion)) {
                return;
            }
            lastNotifiedVersion = latest.get();
            Bukkit.getScheduler().runTask(plugin, () -> notifyUpdate(current, latest.get()));
        } catch (Exception exception) {
            plugin.getLogger().fine("Modrinth update check failed: " + exception.getMessage());
        }
    }

    private Optional<String> fetchLatestVersion() throws Exception {
        String encodedProject = URLEncoder.encode(PROJECT_SLUG, StandardCharsets.UTF_8);
        URI uri = URI.create("https://api.modrinth.com/v2/project/" + encodedProject
                + "/version?include_changelog=false");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .header("User-Agent", "DyRTP/" + plugin.getDescription().getVersion()
                        + " (Minecraft server update checker)")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return Optional.empty();
        }
        Matcher matcher = VERSION_PATTERN.matcher(response.body());
        String best = null;
        while (matcher.find()) {
            String version = unescapeJson(matcher.group(1));
            if (best == null || compareVersions(version, best) > 0) {
                best = version;
            }
        }
        return Optional.ofNullable(best);
    }

    private void notifyUpdate(String current, String latest) {
        CommandSender console = Bukkit.getConsoleSender();
        messages.send(console, "updates.available", placeholders(current, latest));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(NOTIFY_PERMISSION)) {
                messages.send(player, "updates.available", placeholders(current, latest));
            }
        }
    }

    private static java.util.Map<String, String> placeholders(String current, String latest) {
        return java.util.Map.of(
                "{current}", current,
                "{latest}", latest,
                "{url}", PROJECT_URL
        );
    }

    private static String unescapeJson(String value) {
        return value.replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

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
        Matcher matcher = Pattern.compile("\\d+").matcher(version);
        int[] parts = new int[4];
        int index = 0;
        while (matcher.find() && index < parts.length) {
            try {
                parts[index++] = Integer.parseInt(matcher.group());
            } catch (NumberFormatException ignored) {
                parts[index++] = 0;
            }
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

package dev.dyrtp.command;

import dev.dyrtp.DyRTP;
import dev.dyrtp.lang.Language;
import dev.dyrtp.lang.MessageService;
import dev.dyrtp.protection.HookStatus;
import dev.dyrtp.protection.ProtectionService;
import dev.dyrtp.rtp.RtpCause;
import dev.dyrtp.rtp.RtpService;
import dev.dyrtp.storage.PlayerDataStore;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DyRtpCommand implements CommandExecutor, TabCompleter {

    private final DyRTP plugin;
    private final MessageService messages;
    private final ProtectionService protection;
    private final RtpService rtpService;
    private final PlayerDataStore playerDataStore;

    public DyRtpCommand(
            DyRTP plugin,
            MessageService messages,
            ProtectionService protection,
            RtpService rtpService,
            PlayerDataStore playerDataStore
    ) {
        this.plugin = plugin;
        this.messages = messages;
        this.protection = protection;
        this.rtpService = rtpService;
        this.playerDataStore = playerDataStore;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (matches(command.getName(), "onayla") || matches(label, "onayla", "confirm")) {
            return confirm(sender);
        }
        if (matches(command.getName(), "reddet") || matches(label, "reddet", "cancel")) {
            return reject(sender);
        }
        if (matches(command.getName(), "forcertp") || matches(label, "forcertp", "rtpforce")) {
            return forceRtp(sender, args, 0);
        }
        if (args.length == 0 || matches(args[0], "rtp", "ışınlan", "isinlan", "rastgele")) {
            return rtp(sender);
        }

        if (matches(args[0], "help", "yardım", "yardim")) {
            help(sender);
            return true;
        }
        if (matches(args[0], "reload", "yenile")) {
            return reload(sender);
        }
        if (matches(args[0], "language", "lang", "dil")) {
            return playerLanguage(sender, args);
        }
        if (matches(args[0], "admin", "yönetim", "yonetim")) {
            return admin(sender, args);
        }

        messages.send(sender, "general.unknown-command");
        return true;
    }

    private boolean rtp(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        if (!sender.hasPermission("dyrtp.use")) {
            messages.send(sender, "general.no-permission");
            return true;
        }
        rtpService.request(player, RtpCause.COMMAND);
        return true;
    }

    private boolean confirm(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        rtpService.confirm(player);
        return true;
    }

    private boolean reject(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        rtpService.reject(player);
        return true;
    }

    private void help(CommandSender sender) {
        for (String line : messages.getList(sender, "general.help-v4")) {
            sender.sendMessage(line);
        }
    }

    private boolean admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dyrtp.admin")) {
            messages.send(sender, "general.no-permission");
            return true;
        }
        if (args.length < 2 || matches(args[1], "help", "yardım", "yardim")) {
            for (String line : messages.getList(sender, "admin.help-v4")) {
                sender.sendMessage(line);
            }
            return true;
        }

        String sub = normalize(args[1]);
        if (matches(sub, "reload", "yenile")) {
            return reload(sender);
        }
        if (matches(sub, "radius", "limit", "sınır", "sinir")) {
            return radius(sender, args);
        }
        if (matches(sub, "minradius", "enyakın", "enyakin")) {
            return radiusValue(sender, "rtp.default-world.min-radius", args, 2);
        }
        if (matches(sub, "maxradius", "enuzak")) {
            return radiusValue(sender, "rtp.default-world.max-radius", args, 2);
        }
        if (matches(sub, "language", "lang", "dil")) {
            return globalLanguage(sender, args);
        }
        if (matches(sub, "economy", "ekonomi")) {
            return economy(sender, args);
        }
        if (matches(sub, "price", "fiyat", "ücret", "ucret")) {
            return price(sender, args);
        }
        if (matches(sub, "warmup", "delay", "gecikme", "bekletme", "ışınlanmabeklemesi", "isinlanmabeklemesi")) {
            return secondsFeature(sender, args, "warmup.enabled", "warmup.seconds");
        }
        if (matches(sub, "cooldown", "bekleme")) {
            return secondsFeature(sender, args, "cooldown.enabled", "cooldown.seconds");
        }

        messages.send(sender, "general.unknown-command");
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission("dyrtp.admin")) {
            messages.send(sender, "general.no-permission");
            return true;
        }
        plugin.reloadPlugin();
        messages.send(sender, "general.reload");
        return true;
    }

    private boolean forceRtp(CommandSender sender, String[] args, int targetIndex) {
        if (!sender.hasPermission("dyrtp.admin")) {
            messages.send(sender, "general.no-permission");
            return true;
        }
        if (args.length <= targetIndex) {
            messages.send(sender, "general.invalid-value");
            return true;
        }
        String targetName = join(args, targetIndex);
        Player target = findOnlinePlayer(targetName);
        if (target == null) {
            messages.send(sender, "general.player-not-found", Map.of("{player}", targetName));
            return true;
        }
        rtpService.request(target, RtpCause.FORCE);
        messages.send(sender, "admin.force-started", Map.of("{player}", target.getName()));
        return true;
    }

    private boolean playerLanguage(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        if (args.length < 2) {
            messages.send(sender, "general.invalid-language");
            return true;
        }
        String language = normalize(args[1]);
        language = normalizeLanguage(language);
        if (!language.equals("en") && !language.equals("tr") && !language.equals("auto")) {
            messages.send(sender, "general.invalid-language");
            return true;
        }
        playerDataStore.setLanguage(player.getUniqueId(), language);
        messages.send(sender, "general.language-changed", Map.of("{language}", displayLanguage(sender, language)));
        return true;
    }

    private boolean globalLanguage(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messages.send(sender, "general.invalid-language");
            return true;
        }
        String language = normalize(args[2]);
        language = normalizeLanguage(language);
        if (!language.equals("en") && !language.equals("tr") && !language.equals("auto")) {
            messages.send(sender, "general.invalid-language");
            return true;
        }
        plugin.getConfig().set("language.default", language);
        plugin.getConfig().set("language.use-player-locale", language.equals("auto"));
        plugin.saveConfig();
        plugin.reloadPlugin();
        messages.send(sender, "admin.global-language-changed", Map.of("{language}", displayLanguage(sender, language)));
        return true;
    }

    private boolean radius(CommandSender sender, String[] args) {
        if (args.length < 4) {
            messages.send(sender, "general.invalid-value");
            return true;
        }
        String target = normalize(args[2]);
        if (matches(target, "min", "minimum", "closest", "near", "enyakın", "enyakin")) {
            return radiusValue(sender, "rtp.default-world.min-radius", args, 3);
        }
        if (matches(target, "max", "maximum", "farthest", "far", "enuzak")) {
            return radiusValue(sender, "rtp.default-world.max-radius", args, 3);
        }
        messages.send(sender, "general.invalid-value");
        return true;
    }

    private boolean radiusValue(CommandSender sender, String path, String[] args, int valueIndex) {
        if (args.length <= valueIndex) {
            messages.send(sender, "general.invalid-value");
            return true;
        }
        try {
            int value = Math.max(0, Integer.parseInt(args[valueIndex]));
            int min = plugin.getConfig().getInt("rtp.default-world.min-radius", 500);
            int max = plugin.getConfig().getInt("rtp.default-world.max-radius", 5000);
            if (path.endsWith("min-radius")) {
                if (value >= max) {
                    messages.send(sender, "general.invalid-value");
                    return true;
                }
            } else if (value <= min) {
                messages.send(sender, "general.invalid-value");
                return true;
            }
            plugin.getConfig().set(path, value);
            plugin.saveConfig();
            plugin.reloadPlugin();
            updated(sender, path, value);
        } catch (NumberFormatException e) {
            messages.send(sender, "general.invalid-value");
        }
        return true;
    }

    private boolean economy(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messages.send(sender, "general.invalid-value");
            return true;
        }
        Boolean bool = parseBoolean(args[2]);
        if (bool != null) {
            plugin.getConfig().set("economy.enabled", bool);
            boolean defaultPriceApplied = bool && plugin.getConfig().getDouble("economy.cost", 0.0D) <= 0.0D;
            if (defaultPriceApplied) {
                plugin.getConfig().set("economy.cost", 100.0D);
            }
            plugin.saveConfig();
            plugin.reloadPlugin();
            updated(sender, "economy.enabled", bool);
            if (defaultPriceApplied) {
                updated(sender, "economy.cost", 100.0D);
            }
            return true;
        }
        try {
            double cost = Math.max(0.0D, Double.parseDouble(args[2].replace(',', '.')));
            plugin.getConfig().set("economy.cost", cost);
            plugin.getConfig().set("economy.enabled", cost > 0.0D);
            plugin.saveConfig();
            plugin.reloadPlugin();
            updated(sender, "economy.cost", cost);
        } catch (NumberFormatException e) {
            messages.send(sender, "general.invalid-value");
        }
        return true;
    }

    private boolean price(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messages.send(sender, "general.invalid-value");
            return true;
        }
        try {
            double cost = Math.max(0.0D, Double.parseDouble(args[2].replace(',', '.')));
            plugin.getConfig().set("economy.cost", cost);
            plugin.getConfig().set("economy.enabled", cost > 0.0D);
            plugin.saveConfig();
            plugin.reloadPlugin();
            updated(sender, "economy.cost", cost);
        } catch (NumberFormatException e) {
            messages.send(sender, "general.invalid-value");
        }
        return true;
    }

    private boolean secondsFeature(CommandSender sender, String[] args, String enabledPath, String secondsPath) {
        if (args.length < 3) {
            messages.send(sender, "general.invalid-value");
            return true;
        }
        Boolean bool = parseBoolean(args[2]);
        Object shown;
        String path;
        if (bool != null) {
            plugin.getConfig().set(enabledPath, bool);
            if (bool && plugin.getConfig().getInt(secondsPath, 0) <= 0) {
                plugin.getConfig().set(secondsPath, 3);
            }
            shown = bool;
            path = enabledPath;
        } else {
            try {
                int seconds = Math.max(0, Integer.parseInt(args[2]));
                plugin.getConfig().set(secondsPath, seconds);
                plugin.getConfig().set(enabledPath, seconds > 0);
                if (seconds > 0) {
                    shown = seconds + "s";
                    path = secondsPath;
                } else {
                    shown = false;
                    path = enabledPath;
                }
            } catch (NumberFormatException e) {
                messages.send(sender, "general.invalid-value");
                return true;
            }
        }
        plugin.saveConfig();
        plugin.reloadPlugin();
        updated(sender, path, shown);
        return true;
    }

    private void updated(CommandSender sender, String path, Object value) {
        String message = value instanceof Boolean bool
                ? (bool ? "general.setting-enabled" : "general.setting-disabled")
                : "general.setting-changed";
        messages.send(sender, message, Map.of(
                "{path}", path,
                "{setting}", settingName(sender, path),
                "{value}", displayValue(sender, value)
        ));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean tr = messages.language(sender) == Language.TR;
        if (args.length == 1) {
            if (matches(command.getName(), "forcertp")) {
                return startsWith(args[0], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
            }
            return startsWith(args[0], tr
                    ? List.of("yardım", "dil", "yenile", "yönetim")
                    : List.of("help", "language", "reload", "admin"));
        }
        if (args.length == 2 && matches(args[0], "language", "lang", "dil")) {
            return startsWith(args[1], List.of("auto", "en", "tr"));
        }
        if (!matches(args[0], "admin", "yönetim", "yonetim")) {
            return List.of();
        }
        if (args.length == 2) {
            return startsWith(args[1], tr
                    ? List.of("yenile", "dil", "ekonomi", "fiyat", "bekleme", "gecikme", "sınır")
                    : List.of("reload", "language", "economy", "price", "cooldown", "delay", "radius"));
        }
        if (args.length == 3 && matches(args[1], "language", "lang", "dil")) {
            return startsWith(args[2], tr ? List.of("otomatik", "en", "tr") : List.of("auto", "en", "tr"));
        }
        if (args.length == 3 && matches(args[1], "economy", "ekonomi")) {
            return startsWith(args[2], tr ? List.of("aç", "kapat") : List.of("on", "off"));
        }
        if (args.length == 3 && matches(args[1], "price", "fiyat", "ücret", "ucret")) {
            return startsWith(args[2], List.of("0", "100", "500", "1000"));
        }
        if (args.length == 3 && matches(args[1], "warmup", "delay", "gecikme", "bekletme", "ışınlanmabeklemesi", "isinlanmabeklemesi")) {
            return startsWith(args[2], tr ? List.of("3", "5", "10", "kapalı") : List.of("3", "5", "10", "off"));
        }
        if (args.length == 3 && matches(args[1], "cooldown", "bekleme")) {
            return startsWith(args[2], tr ? List.of("60", "300", "600", "kapalı") : List.of("60", "300", "600", "off"));
        }
        if (args.length == 3 && matches(args[1], "radius", "limit", "sınır", "sinir")) {
            return startsWith(args[2], tr ? List.of("enyakın", "enuzak") : List.of("min", "max"));
        }
        if (args.length == 4 && matches(args[1], "radius", "limit", "sınır", "sinir")) {
            return startsWith(args[3], matches(args[2], "min", "minimum", "closest", "near", "enyakın", "enyakin")
                    ? List.of("500", "750", "1000")
                    : List.of("5000", "7500", "10000"));
        }
        return List.of();
    }

    private String displayValue(CommandSender sender, Object value) {
        if (value instanceof Boolean bool) {
            return messages.get(sender, bool ? "value.enabled" : "value.disabled");
        }
        return String.valueOf(value);
    }

    private String settingName(CommandSender sender, String path) {
        String key = "settings." + path;
        String translated = messages.get(sender, key);
        if (!translated.equals(key)) {
            return translated;
        }
        if (path.startsWith("protection.hooks.")) {
            return path.substring("protection.hooks.".length()) + " " + messages.get(sender, "settings.protection-hook");
        }
        return path;
    }

    private String displayLanguage(CommandSender sender, String language) {
        return switch (language) {
            case "tr" -> messages.get(sender, "value.language-tr");
            case "en" -> messages.get(sender, "value.language-en");
            default -> messages.get(sender, "value.language-auto");
        };
    }

    private Player findOnlinePlayer(String input) {
        Player exact = Bukkit.getPlayerExact(input);
        if (exact != null) {
            return exact;
        }
        Player partial = Bukkit.getPlayer(input);
        if (partial != null) {
            return partial;
        }
        String normalizedInput = normalizePlayerName(input);
        for (Player player : Bukkit.getOnlinePlayers()) {
            String normalizedPlayer = normalizePlayerName(player.getName());
            if (normalizedPlayer.equals(normalizedInput)
                    || normalizedPlayer.endsWith(normalizedInput)
                    || normalizedInput.endsWith(normalizedPlayer)) {
                return player;
            }
        }
        return null;
    }

    private static String normalizePlayerName(String value) {
        String lower = value == null ? "" : value.toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lower.length(); i++) {
            char character = lower.charAt(i);
            if (Character.isLetterOrDigit(character)) {
                builder.append(character);
            }
        }
        return builder.toString();
    }

    private static List<String> startsWith(String input, List<String> values) {
        String lower = normalize(input);
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (normalize(value).startsWith(lower)) {
                result.add(value);
            }
        }
        return result;
    }

    private static Object parse(String raw) {
        Boolean bool = parseBoolean(raw);
        if (bool != null) {
            return bool;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ignored) {
        }
        return raw;
    }

    private static String normalizeLanguage(String language) {
        if (matches(language, "otomatik", "automatic")) {
            return "auto";
        }
        return language;
    }

    private static Boolean parseBoolean(String raw) {
        String value = normalize(raw);
        if (matches(value, "true", "on", "açık", "acik", "aç", "ac", "enable", "enabled")) {
            return true;
        }
        if (matches(value, "false", "off", "kapalı", "kapali", "kapat", "disable", "disabled")) {
            return false;
        }
        return null;
    }

    private static String join(String[] args, int start) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString();
    }

    private static String normalizePath(String path) {
        return path.replace('_', '-');
    }

    private static boolean matches(String value, String... options) {
        String normalized = normalize(value);
        for (String option : options) {
            if (normalized.equals(normalize(option))) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        int namespace = value.indexOf(':');
        String normalized = namespace >= 0 ? value.substring(namespace + 1) : value;
        return normalized.toLowerCase(Locale.ROOT);
    }
}

package dev.dyrtp.command;

import dev.dyrtp.DyRTP;
import dev.dyrtp.lang.Language;
import dev.dyrtp.lang.MessageService;
import dev.dyrtp.protection.HookStatus;
import dev.dyrtp.protection.ProtectionService;
import dev.dyrtp.rtp.BiomeChoices;
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
        if (matches(command.getName(), "dyra") || matches(label, "dyra")) {
            args = adminArguments(args);
        }
        if (matches(command.getName(), "onayla") || matches(label, "onayla", "confirm")) {
            return confirm(sender, args.length > 0 ? args[0] : null);
        }
        if (matches(command.getName(), "reddet") || matches(label, "reddet", "cancel")) {
            return reject(sender, args.length > 0 ? args[0] : null);
        }
        if (matches(command.getName(), "forcertp") || matches(label, "forcertp", "rtpforce")) {
            return forceRtp(sender, args, 0);
        }
        if (args.length == 0 || matches(args[0], "rtp", "ışınlan", "isinlan", "rastgele")) {
            return rtp(sender);
        }

        if (matches(args[0], "confirm", "onayla")) {
            return confirm(sender, args.length > 1 ? args[1] : null);
        }
        if (matches(args[0], "cancel", "reddet", "iptal")) {
            return reject(sender, args.length > 1 ? args[1] : null);
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
        if (matches(args[0], "biome", "biyom")) {
            return biome(sender, args);
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

    private boolean biome(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        if (!sender.hasPermission("dyrtp.use") || !sender.hasPermission("dyrtp.biome")) {
            messages.send(sender, "general.no-permission");
            return true;
        }
        if (!plugin.getConfig().getBoolean("biome-selection.enabled", true)) {
            messages.send(sender, "rtp.biome-disabled");
            return true;
        }
        if (args.length < 2) {
            messages.send(sender, "rtp.biome-usage");
            return true;
        }
        String biome = BiomeChoices.match(plugin, String.join("_", java.util.Arrays.copyOfRange(args, 1, args.length)));
        if (biome == null) {
            messages.send(sender, "rtp.biome-unknown", Map.of("{biome}", args[1]));
            return true;
        }
        rtpService.request(player, RtpCause.COMMAND, biome);
        return true;
    }

    private boolean confirm(CommandSender sender, String token) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        if (!sender.hasPermission("dyrtp.use")) {
            messages.send(sender, "general.no-permission");
            return true;
        }
        if (token == null) rtpService.confirm(player);
        else rtpService.confirm(player, token);
        return true;
    }

    private boolean reject(CommandSender sender, String token) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        if (token == null) rtpService.reject(player);
        else rtpService.reject(player, token);
        return true;
    }

    private void help(CommandSender sender) {
        messages.send(sender, "general.help-header");
        if (sender instanceof Player) {
            if (sender.hasPermission("dyrtp.use")) {
                messages.send(sender, "general.help-rtp");
                messages.send(sender, "general.help-confirm");
            }
            messages.send(sender, "general.help-cancel");
            if (sender.hasPermission("dyrtp.biome") && plugin.getConfig().getBoolean("biome-selection.enabled", true)) {
                messages.send(sender, "general.help-biome");
            }
            messages.send(sender, "general.help-language");
        }
        if (sender.hasPermission("dyrtp.admin")) {
            messages.send(sender, "general.help-admin");
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
        if (matches(sub, "force", "zorla", "forcertp", "rtpforce")) {
            return forceRtp(sender, args, 2);
        }
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
        if (plugin.tryReloadPlugin()) {
            messages.send(sender, "general.reload");
        } else {
            messages.send(sender, "general.reload-failed");
        }
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
        if (!language.equals("auto") && !Language.codes().contains(language)) {
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
        if (!language.equals("auto") && !Language.codes().contains(language)) {
            messages.send(sender, "general.invalid-language");
            return true;
        }
        if (!applyChanges(sender, Map.of("language.default", language, "language.use-player-locale", language.equals("auto")))) {
            return true;
        }
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
            int value = Integer.parseInt(args[valueIndex]);
            if (value < 0 || value > 29_999_984) {
                messages.send(sender, "general.invalid-value");
                return true;
            }
            int min = plugin.getConfig().getInt("rtp.default-world.min-radius", 500);
            int max = plugin.getConfig().getInt("rtp.default-world.max-radius", 5000);
            if (path.endsWith("min-radius")) {
                if (value >= max) {
                    messages.send(sender, "general.invalid-value");
                    return true;
                }
            } else if (value <= min
                    || Math.abs(plugin.getConfig().getDouble("rtp.default-world.center-x", 0)) + value > 30_000_000
                    || Math.abs(plugin.getConfig().getDouble("rtp.default-world.center-z", 0)) + value > 30_000_000) {
                messages.send(sender, "general.invalid-value");
                return true;
            }
            if (!applyChanges(sender, Map.of(path, value))) return true;
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
            if (bool && !requireEconomyProvider(sender)) return true;
            boolean defaultPriceApplied = bool && plugin.getConfig().getDouble("economy.cost", 0.0D) <= 0.0D;
            Map<String, Object> changes = new java.util.HashMap<>();
            changes.put("economy.enabled", bool);
            if (defaultPriceApplied) changes.put("economy.cost", 100.0D);
            if (!applyChanges(sender, changes)) return true;
            updated(sender, "economy.enabled", bool);
            if (defaultPriceApplied) {
                updated(sender, "economy.cost", 100.0D);
            }
            return true;
        }
        try {
            double cost = Double.parseDouble(args[2].replace(',', '.'));
            if (!Double.isFinite(cost) || cost < 0) {
                messages.send(sender, "general.invalid-value");
                return true;
            }
            if (cost > 0 && !requireEconomyProvider(sender)) return true;
            if (!applyChanges(sender, Map.of("economy.cost", cost, "economy.enabled", cost > 0.0D))) return true;
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
            double cost = Double.parseDouble(args[2].replace(',', '.'));
            if (!Double.isFinite(cost) || cost < 0) {
                messages.send(sender, "general.invalid-value");
                return true;
            }
            if (cost > 0 && !requireEconomyProvider(sender)) return true;
            if (!applyChanges(sender, Map.of("economy.cost", cost, "economy.enabled", cost > 0.0D))) return true;
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
        Map<String, Object> changes = new java.util.HashMap<>();
        Object shown;
        String path;
        if (bool != null) {
            changes.put(enabledPath, bool);
            if (bool && plugin.getConfig().getInt(secondsPath, 0) <= 0) {
                changes.put(secondsPath, 3);
            }
            shown = bool;
            path = enabledPath;
        } else {
            try {
                int seconds = Integer.parseInt(args[2]);
                if (seconds < 0 || (secondsPath.equals("warmup.seconds") && seconds > 86_400)) {
                    messages.send(sender, "general.invalid-value");
                    return true;
                }
                changes.put(secondsPath, seconds);
                changes.put(enabledPath, seconds > 0);
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
        if (!applyChanges(sender, changes)) return true;
        updated(sender, path, shown);
        return true;
    }

    private boolean requireEconomyProvider(CommandSender sender) {
        if (plugin.getEconomy() != null && plugin.getEconomy().available()) return true;
        messages.send(sender, "rtp.economy-missing");
        return false;
    }

    private boolean applyChanges(CommandSender sender, Map<String, Object> changes) {
        if (plugin.applyConfigChanges(changes)) return true;
        messages.send(sender, "general.reload-failed");
        return false;
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
        if (matches(command.getName(), "dyra") || matches(alias, "dyra")) {
            args = adminArguments(args);
        }
        boolean tr = messages.language(sender) == Language.TR;
        if (args.length == 1) {
            if (matches(command.getName(), "forcertp")) {
                if (!sender.hasPermission("dyrtp.admin")) return List.of();
                return startsWith(args[0], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
            }
            if (matches(command.getName(), "onayla", "reddet")) return List.of();
            List<String> words = new ArrayList<>();
            words.add(tr ? "yardım" : "help");
            if (sender instanceof Player) {
                words.add(tr ? "dil" : "language");
                words.add(tr ? "reddet" : "cancel");
                if (sender.hasPermission("dyrtp.use")) {
                    words.add(tr ? "ışınlan" : "rtp");
                    words.add(tr ? "onayla" : "confirm");
                    if (biomeChoice(sender)) words.add(tr ? "biyom" : "biome");
                }
            }
            if (sender.hasPermission("dyrtp.admin")) words.add("admin");
            return startsWith(args[0], words);
        }
        if (args.length == 2 && matches(args[0], "biome", "biyom")) {
            return biomeChoice(sender) ? startsWith(args[1], BiomeChoices.available(plugin)) : List.of();
        }
        if (args.length == 2 && matches(args[0], "language", "lang", "dil")) {
            return sender instanceof Player
                    ? startsWith(args[1], languageChoices(tr))
                    : List.of();
        }
        if (args.length == 0 || !sender.hasPermission("dyrtp.admin")
                || !matches(args[0], "admin", "yönetim", "yonetim")) {
            return List.of();
        }
        if (args.length == 2) {
            return startsWith(args[1], tr
                    ? List.of("yardım", "yenile", "dil", "ekonomi", "fiyat", "bekleme", "gecikme", "sınır", "zorla")
                    : List.of("help", "reload", "language", "economy", "price", "cooldown", "delay", "radius", "force"));
        }
        if (args.length == 3 && matches(args[1], "force", "zorla", "forcertp", "rtpforce")) {
            return startsWith(args[2], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        }
        if (args.length == 3 && matches(args[1], "language", "lang", "dil")) {
            return startsWith(args[2], languageChoices(tr));
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
        return Language.codes().contains(language) ? messages.get(sender, "value.language-" + language) : messages.get(sender, "value.language-auto");
    }

    private boolean biomeChoice(CommandSender sender) {
        return sender instanceof Player && sender.hasPermission("dyrtp.use") && sender.hasPermission("dyrtp.biome")
                && plugin.getConfig().getBoolean("biome-selection.enabled", true);
    }

    private static List<String> languageChoices(boolean turkish) {
        List<String> choices = new java.util.ArrayList<>();
        choices.add(turkish ? "oto" : "auto");
        choices.addAll(Language.codes());
        return choices;
    }

    private Player findOnlinePlayer(String input) {
        Player exact = Bukkit.getPlayerExact(input);
        if (exact != null) {
            return exact;
        }
        return null;
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

    private static String[] adminArguments(String[] args) {
        String[] admin = new String[args.length + 1];
        admin[0] = "admin";
        System.arraycopy(args, 0, admin, 1, args.length);
        return admin;
    }

    private static String normalizeLanguage(String language) {
        if (matches(language, "oto", "otomatik", "automatic")) {
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

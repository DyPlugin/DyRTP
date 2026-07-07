package dev.dyrtp.lang;

import dev.dyrtp.DyRTP;
import dev.dyrtp.storage.PlayerDataStore;
import dev.dyrtp.util.Colors;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MessageService {

    private final DyRTP plugin;
    private final PlayerDataStore playerDataStore;
    private final Map<Language, FileConfiguration> messages = new HashMap<>();
    private final Map<Language, FileConfiguration> bundledMessages = new HashMap<>();

    public MessageService(DyRTP plugin, PlayerDataStore playerDataStore) {
        this.plugin = plugin;
        this.playerDataStore = playerDataStore;
        reload();
    }

    public void reload() {
        messages.clear();
        bundledMessages.clear();
        messages.put(Language.EN, YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "messages_en.yml")));
        messages.put(Language.TR, YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "messages_tr.yml")));
        bundledMessages.put(Language.EN, bundled("messages_en.yml"));
        bundledMessages.put(Language.TR, bundled("messages_tr.yml"));
    }

    public Language language(CommandSender sender) {
        String defaultSetting = plugin.getConfig().getString("language.default", "auto");
        Language fallback = Language.fromCode(defaultSetting, Language.EN);
        if (sender instanceof Player player) {
            String saved = playerDataStore.getLanguage(player.getUniqueId());
            if (saved != null) {
                return Language.fromCode(saved, fallback);
            }
            if ("auto".equalsIgnoreCase(defaultSetting)
                    || plugin.getConfig().getBoolean("language.use-player-locale", true)) {
                String locale = player.getLocale();
                if (locale != null && locale.toLowerCase(Locale.ROOT).startsWith("tr")) {
                    return Language.TR;
                }
                return Language.EN;
            }
        }
        return fallback;
    }

    public String get(CommandSender sender, String path) {
        return get(sender, path, Map.of());
    }

    public String get(CommandSender sender, String path, Map<String, String> placeholders) {
        FileConfiguration file = messages.getOrDefault(language(sender), messages.get(Language.EN));
        String text = file.getString(path);
        if (text == null) {
            FileConfiguration bundled = bundledMessages.getOrDefault(language(sender), bundledMessages.get(Language.EN));
            text = bundled.getString(path);
        }
        if (text == null) {
            text = messages.get(Language.EN).getString(path, path);
        }
        if (text == null || text.equals(path)) {
            text = bundledMessages.get(Language.EN).getString(path, path);
        }
        return Colors.color(apply(text, placeholders));
    }

    public List<String> getList(CommandSender sender, String path) {
        FileConfiguration file = messages.getOrDefault(language(sender), messages.get(Language.EN));
        List<String> list = file.getStringList(path);
        if (list.isEmpty()) {
            FileConfiguration bundled = bundledMessages.getOrDefault(language(sender), bundledMessages.get(Language.EN));
            list = bundled.getStringList(path);
        }
        if (list.isEmpty()) {
            list = messages.get(Language.EN).getStringList(path);
        }
        if (list.isEmpty()) {
            list = bundledMessages.get(Language.EN).getStringList(path);
        }
        return list.stream()
                .map(line -> Colors.color(apply(line, Map.of())))
                .toList();
    }

    public void send(CommandSender sender, String path) {
        send(sender, path, Map.of());
    }

    public void send(CommandSender sender, String path, Map<String, String> placeholders) {
        sender.sendMessage(get(sender, path, placeholders));
    }

    public Map<String, String> basePlaceholders() {
        return Map.of("{prefix}", plugin.getConfig().getString("prefix", "&7[&dDyRTP&7]"));
    }

    public String apply(String text, Map<String, String> placeholders) {
        Map<String, String> all = new HashMap<>(basePlaceholders());
        all.putAll(placeholders);
        String result = text;
        for (Map.Entry<String, String> entry : all.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private FileConfiguration bundled(String fileName) {
        InputStream inputStream = plugin.getResource(fileName);
        if (inputStream == null) {
            return new YamlConfiguration();
        }
        return YamlConfiguration.loadConfiguration(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
    }
}

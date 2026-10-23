package dev.dyrtp;

import dev.dyrtp.command.DyRtpCommand;
import dev.dyrtp.economy.EconomyService;
import dev.dyrtp.lang.MessageService;
import dev.dyrtp.listener.PlayerEventListener;
import dev.dyrtp.protection.ProtectionService;
import dev.dyrtp.rtp.RtpService;
import dev.dyrtp.storage.PlayerDataStore;
import dev.dyrtp.update.ModrinthUpdateChecker;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class DyRTP extends JavaPlugin {

    private static DyRTP instance;

    private PlayerDataStore playerDataStore;
    private MessageService messages;
    private EconomyService economy;
    private ProtectionService protection;
    private RtpService rtpService;
    private PlayerEventListener playerEventListener;
    private ModrinthUpdateChecker updateChecker;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        if (!validateFiles()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getConfig().options().copyDefaults(true);
        applyConfigUpgrades();
        saveConfig();
        if (!new File(getDataFolder(), "messages_en.yml").exists()) saveResource("messages_en.yml", false);
        if (!new File(getDataFolder(), "messages_tr.yml").exists()) saveResource("messages_tr.yml", false);
        ensureMessageDefaults("messages_en.yml");
        ensureMessageDefaults("messages_tr.yml");

        this.playerDataStore = new PlayerDataStore(this);
        this.messages = new MessageService(this, playerDataStore);
        this.economy = new EconomyService(this);
        this.protection = new ProtectionService(this);
        this.rtpService = new RtpService(this, messages, economy, protection);
        this.playerEventListener = new PlayerEventListener(this, rtpService);

        DyRtpCommand command = new DyRtpCommand(this, messages, protection, rtpService, playerDataStore);
        PluginCommand dyRtpCommand = getCommand("dyrtp");
        if (dyRtpCommand != null) {
            dyRtpCommand.setExecutor(command);
            dyRtpCommand.setTabCompleter(command);
        }
        PluginCommand adminCommand = getCommand("dyra");
        if (adminCommand != null) {
            adminCommand.setExecutor(command);
            adminCommand.setTabCompleter(command);
        }
        PluginCommand confirmCommand = getCommand("onayla");
        if (confirmCommand != null) {
            confirmCommand.setExecutor(command);
        }
        PluginCommand cancelCommand = getCommand("reddet");
        if (cancelCommand != null) {
            cancelCommand.setExecutor(command);
        }
        PluginCommand forceCommand = getCommand("forcertp");
        if (forceCommand != null) {
            forceCommand.setExecutor(command);
            forceCommand.setTabCompleter(command);
        }

        getServer().getPluginManager().registerEvents(playerEventListener, this);
        this.updateChecker = new ModrinthUpdateChecker(this, messages);
        this.updateChecker.start();
        getLogger().info("DyRTP v" + getDescription().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        if (rtpService != null) {
            rtpService.cancelAll();
        }
        if (playerDataStore != null) {
            playerDataStore.save();
        }
        if (updateChecker != null) {
            updateChecker.cancel();
        }
        getLogger().info("DyRTP disabled.");
    }

    public void reloadPlugin() { tryReloadPlugin(); }

    public boolean tryReloadPlugin() {
        if (!validateFiles()) return false;
        // Invalidate requests before services or hook references change.
        rtpService.cancelAll();
        reloadConfig();
        getConfig().options().copyDefaults(true);
        applyConfigUpgrades();
        saveConfig();
        ensureMessageDefaults("messages_en.yml");
        ensureMessageDefaults("messages_tr.yml");
        playerDataStore.reload();
        messages.reload();
        economy.reload();
        protection.reload();
        rtpService.reload();
        if (updateChecker != null) {
            updateChecker.reload();
        }
        return true;
    }

    public boolean applyConfigChanges(java.util.Map<String, Object> changes) {
        try {
            // Stage changes separately; a rejected edit must not alter getConfig().
            YamlConfiguration staged = new YamlConfiguration();
            staged.loadFromString(getConfig().saveToString());
            changes.forEach(staged::set);
            ConfigValidator.validate(staged);
            if (!validateFiles()) return false;
            java.nio.file.Path destination = new File(getDataFolder(), "config.yml").toPath();
            java.nio.file.Path temporary = destination.resolveSibling("config.yml.tmp");
            staged.save(temporary.toFile());
            try {
                java.nio.file.Files.move(temporary, destination, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                java.nio.file.Files.move(temporary, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return tryReloadPlugin();
        } catch (Exception exception) {
            getLogger().warning("Setting change rejected; running settings retained: " + exception.getMessage());
            return false;
        }
    }

    private boolean validateFiles() {
        try {
            YamlConfiguration staged = new YamlConfiguration();
            staged.load(new File(getDataFolder(), "config.yml"));
            ConfigValidator.validate(staged);
            for (String name : new String[]{"messages_en.yml", "messages_tr.yml", "players.yml"}) {
                File file = new File(getDataFolder(), name);
                if (file.exists()) new YamlConfiguration().load(file);
            }
            return true;
        } catch (Exception exception) {
            getLogger().warning("Configuration rejected; running settings retained: " + exception.getMessage());
            return false;
        }
    }

    public static DyRTP getInstance() {
        return instance;
    }

    public PlayerDataStore getPlayerDataStore() {
        return playerDataStore;
    }

    public MessageService getMessages() {
        return messages;
    }

    public EconomyService getEconomy() {
        return economy;
    }

    public ProtectionService getProtection() {
        return protection;
    }

    public RtpService getRtpService() {
        return rtpService;
    }

    private void applyConfigUpgrades() {
        if (getConfig().getInt("config-version", 0) < 9) {
            String prefix = getConfig().getString("prefix");
            if ("&7[&9DyRTP&7]".equals(prefix) || "&7[&dDyRTP&7]".equals(prefix)) {
                getConfig().set("prefix", "&7[&aDyRTP&7]");
            }
            getConfig().set("config-version", 9);
        }
    }

    private void ensureMessageDefaults(String fileName) {
        File file = new File(getDataFolder(), fileName);
        if (!file.exists()) {
            saveResource(fileName, false);
            return;
        }
        try (InputStream inputStream = getResource(fileName)) {
            if (inputStream == null) {
                return;
            }
            YamlConfiguration existing = new YamlConfiguration();
            existing.load(file);
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8)
            );
            if (copyMissing(defaults, existing)) {
                existing.save(file);
            }
        } catch (Exception exception) {
            getLogger().warning("Could not update missing message keys in " + fileName + ": " + exception.getMessage());
        }
    }

    private boolean copyMissing(ConfigurationSection defaults, ConfigurationSection target) {
        boolean changed = false;
        for (String key : defaults.getKeys(false)) {
            Object value = defaults.get(key);
            ConfigurationSection defaultSection = defaults.getConfigurationSection(key);
            if (defaultSection != null) {
                ConfigurationSection targetSection = target.getConfigurationSection(key);
                if (targetSection == null) {
                    if (target.contains(key)) continue;
                    targetSection = target.createSection(key);
                    changed = true;
                }
                if (copyMissing(defaultSection, targetSection)) {
                    changed = true;
                }
            } else if (!target.contains(key)) {
                target.set(key, value);
                changed = true;
            } else if (target.get(key) instanceof String existing && value instanceof String text
                    && existing.contains("&9") && existing.replace("&9", "&a").equals(text)) {
                // Only migrate unchanged stock text; custom translations keep their formatting.
                target.set(key, text);
                changed = true;
            } else if (target.get(key) instanceof String existing && value instanceof String text
                    && !existing.equals(text) && automaticLabel(existing).equals(text)) {
                target.set(key, text);
                changed = true;
            } else if (target.get(key) instanceof java.util.List<?> existing && value instanceof java.util.List<?> lines
                    && !existing.equals(lines)
                    && existing.stream().map(line -> line instanceof String text ? automaticLabel(text) : line).toList().equals(lines)) {
                target.set(key, lines);
                changed = true;
            }
        }
        return changed;
    }

    private static String automaticLabel(String text) {
        return text.replace("Otomatik", "Oto").replace("otomatik", "oto");
    }
}

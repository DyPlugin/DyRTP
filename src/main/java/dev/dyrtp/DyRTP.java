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
        getConfig().options().copyDefaults(true);
        applyConfigUpgrades();
        saveConfig();
        saveResource("messages_en.yml", false);
        saveResource("messages_tr.yml", false);
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

    public void reloadPlugin() {
        reloadConfig();
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
        int version = getConfig().getInt("config-version", 0);
        if (version < 6) {
            getConfig().set("config-version", 6);
        }
        if (version < 7) {
            if ("&7[&dDyRTP&7]".equals(getConfig().getString("prefix"))) {
                getConfig().set("prefix", "&7[&9DyRTP&7]");
            }
            getConfig().set("config-version", 7);
        }
        if (version < 8) {
            upgradeDefaultInt("rtp.search.max-attempts", 120, 240);
            upgradeDefaultInt("rtp.search.batch-size", 16, 32);
            upgradeDefaultInt("rtp.search.preload-radius", 2, 1);
            getConfig().set("config-version", 8);
        }
        getConfig().set("updates", null);
    }

    private void upgradeDefaultInt(String path, int oldDefault, int newDefault) {
        if (getConfig().getInt(path, oldDefault) == oldDefault) {
            getConfig().set(path, newDefault);
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
            YamlConfiguration existing = YamlConfiguration.loadConfiguration(file);
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
                    targetSection = target.createSection(key);
                    changed = true;
                }
                if (copyMissing(defaultSection, targetSection)) {
                    changed = true;
                }
            } else if (!target.contains(key)) {
                target.set(key, value);
                changed = true;
            }
        }
        return changed;
    }
}

package dev.dyrtp.storage;

import dev.dyrtp.DyRTP;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class PlayerDataStore {

    private final DyRTP plugin;
    private final File file;
    private FileConfiguration data;
    /** One background save at a time for RTP counts; a later count is written by the save already queued. */
    private final java.util.concurrent.atomic.AtomicBoolean saveQueued = new java.util.concurrent.atomic.AtomicBoolean();

    public PlayerDataStore(DyRTP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
        reload();
    }

    public synchronized void reload() {
        if (!file.exists()) {
            try {
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Could not create players.yml: " + e.getMessage());
            }
        }
        try {
            YamlConfiguration staged = new YamlConfiguration();
            staged.load(file);
            this.data = staged;
        } catch (Exception exception) {
            if (data == null) throw new IllegalStateException("Could not load players.yml; refusing to overwrite data", exception);
            plugin.getLogger().warning("Could not reload players.yml; existing preferences retained: " + exception.getMessage());
        }
    }

    public synchronized String getLanguage(UUID uuid) {
        return data.getString("players." + uuid + ".language");
    }

    public synchronized void setLanguage(UUID uuid, String language) {
        if (language == null || language.equalsIgnoreCase("auto")) {
            data.set("players." + uuid + ".language", null);
        } else {
            data.set("players." + uuid + ".language", language.toLowerCase(java.util.Locale.ROOT));
        }
        save();
    }

    /** Successful random teleports of a player, for /dyrtp stats and %dyrtp_count%. */
    public synchronized int rtpCount(UUID uuid) {
        return Math.max(0, data.getInt("players." + uuid + ".rtp-count", 0));
    }

    /** Counts one successful random teleport and returns the new total; written to players.yml in the background. */
    public synchronized int addRtp(UUID uuid) {
        int count = rtpCount(uuid) + 1;
        data.set("players." + uuid + ".rtp-count", count);
        saveSoon();
        return count;
    }

    private void saveSoon() {
        if (!plugin.isEnabled() || !saveQueued.compareAndSet(false, true)) return;
        try {
            plugin.scheduler().runAsync(() -> {
                saveQueued.set(false);
                save();
            });
        } catch (RuntimeException schedulerUnavailable) {
            saveQueued.set(false);
        }
    }

    public synchronized void save() {
        try {
            java.nio.file.Path temporary = file.toPath().resolveSibling("players.yml.tmp");
            data.save(temporary.toFile());
            try {
                java.nio.file.Files.move(temporary, file.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                java.nio.file.Files.move(temporary, file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save players.yml: " + e.getMessage());
        }
    }
}

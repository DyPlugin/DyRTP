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

    public PlayerDataStore(DyRTP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
        reload();
    }

    public void reload() {
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

    public String getLanguage(UUID uuid) {
        return data.getString("players." + uuid + ".language");
    }

    public void setLanguage(UUID uuid, String language) {
        if (language == null || language.equalsIgnoreCase("auto")) {
            data.set("players." + uuid + ".language", null);
        } else {
            data.set("players." + uuid + ".language", language.toLowerCase(java.util.Locale.ROOT));
        }
        save();
    }

    public void save() {
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

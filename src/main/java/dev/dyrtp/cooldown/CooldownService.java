package dev.dyrtp.cooldown;

import dev.dyrtp.DyRTP;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CooldownService {

    private final DyRTP plugin;
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

    public CooldownService(DyRTP plugin) {
        this.plugin = plugin;
    }

    public long remainingSeconds(Player player, World world) {
        if (!plugin.getConfig().getBoolean("cooldown.enabled", false)) {
            return 0L;
        }
        if (player.hasPermission("dyrtp.bypass.cooldown")) {
            return 0L;
        }
        long expiresAt = cooldowns.getOrDefault(key(player.getUniqueId(), world), 0L);
        long remainingMillis = expiresAt - System.currentTimeMillis();
        return Math.max(0L, (remainingMillis + 999L) / 1000L);
    }

    public void start(Player player, World world) {
        if (!plugin.getConfig().getBoolean("cooldown.enabled", false)) {
            return;
        }
        int seconds = Math.max(0, plugin.getConfig().getInt("cooldown.seconds", 0));
        cooldowns.put(key(player.getUniqueId(), world), System.currentTimeMillis() + seconds * 1000L);
    }

    public void clear() {
        cooldowns.clear();
    }

    private String key(UUID uuid, World world) {
        if (plugin.getConfig().getBoolean("cooldown.per-world", false)) {
            return uuid + ":" + world.getName();
        }
        return uuid.toString();
    }
}

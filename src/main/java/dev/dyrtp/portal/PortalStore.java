package dev.dyrtp.portal;

import dev.dyrtp.DyRTP;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

/**
 * RTP portal regions from portals.yml. Walking into one starts an RTP, optionally into another world.
 * The list is replaced as a whole, so movement checks on any thread read a consistent copy.
 */
public class PortalStore {

    public record Portal(String name, String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, String target) {
        public boolean contains(Location location) {
            if (location.getWorld() == null || !location.getWorld().getName().equals(world)) return false;
            return containsBlock(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }

        public boolean containsBlock(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }

        public static Portal of(String name, Location a, Location b, String target) {
            return new Portal(name, a.getWorld().getName(),
                    Math.min(a.getBlockX(), b.getBlockX()), Math.min(a.getBlockY(), b.getBlockY()), Math.min(a.getBlockZ(), b.getBlockZ()),
                    Math.max(a.getBlockX(), b.getBlockX()), Math.max(a.getBlockY(), b.getBlockY()), Math.max(a.getBlockZ(), b.getBlockZ()),
                    target == null ? "" : target);
        }
    }

    /** Largest side of a portal region in blocks, so a typo cannot cover half a world. */
    public static final int MAX_SIDE = 64;

    private final DyRTP plugin;
    private final File file;
    private volatile List<Portal> portals = List.of();
    /** Portals by world name, so a movement check only looks at the portals of the player's world. */
    private volatile Map<String, List<Portal>> byWorld = Map.of();

    public PortalStore(DyRTP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "portals.yml");
        reload();
    }

    public void reload() {
        if (!file.exists()) {
            use(List.of());
            return;
        }
        try {
            YamlConfiguration data = new YamlConfiguration();
            data.load(file);
            List<Portal> loaded = new ArrayList<>();
            ConfigurationSection section = data.getConfigurationSection("portals");
            if (section != null) for (String name : section.getKeys(false)) {
                ConfigurationSection p = section.getConfigurationSection(name);
                if (p == null || p.getString("world") == null) continue;
                loaded.add(new Portal(name, p.getString("world"), p.getInt("min.x"), p.getInt("min.y"), p.getInt("min.z"),
                        p.getInt("max.x"), p.getInt("max.y"), p.getInt("max.z"), p.getString("target", "")));
            }
            use(List.copyOf(loaded));
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not load portals.yml; portals kept as they were: " + exception.getMessage());
        }
    }

    public List<Portal> all() {
        return portals;
    }

    public boolean isEmpty() {
        return portals.isEmpty();
    }

    public Portal at(Location location) {
        org.bukkit.World world = location.getWorld();
        if (world == null) return null;
        List<Portal> inWorld = byWorld.get(world.getName());
        if (inWorld == null) return null;
        int x = location.getBlockX(), y = location.getBlockY(), z = location.getBlockZ();
        for (Portal portal : inWorld) if (portal.containsBlock(x, y, z)) return portal;
        return null;
    }

    private void use(List<Portal> next) {
        Map<String, List<Portal>> index = new HashMap<>();
        for (Portal portal : next) index.computeIfAbsent(portal.world(), name -> new ArrayList<>()).add(portal);
        index.replaceAll((name, list) -> List.copyOf(list));
        // The index is published before the list, so a reader that sees a portal in all() also finds it in at().
        byWorld = Map.copyOf(index);
        portals = next;
    }

    public Portal get(String name) {
        for (Portal portal : portals) if (portal.name().equalsIgnoreCase(name)) return portal;
        return null;
    }

    public synchronized boolean put(Portal portal) {
        List<Portal> next = new ArrayList<>(portals);
        next.removeIf(existing -> existing.name().equalsIgnoreCase(portal.name()));
        next.add(portal);
        return save(next);
    }

    public synchronized boolean remove(String name) {
        List<Portal> next = new ArrayList<>(portals);
        if (!next.removeIf(existing -> existing.name().equalsIgnoreCase(name))) return false;
        return save(next);
    }

    private boolean save(List<Portal> next) {
        YamlConfiguration data = new YamlConfiguration();
        for (Portal portal : next) {
            String path = "portals." + portal.name();
            data.set(path + ".world", portal.world());
            data.set(path + ".min.x", portal.minX());
            data.set(path + ".min.y", portal.minY());
            data.set(path + ".min.z", portal.minZ());
            data.set(path + ".max.x", portal.maxX());
            data.set(path + ".max.y", portal.maxY());
            data.set(path + ".max.z", portal.maxZ());
            data.set(path + ".target", portal.target());
        }
        try {
            file.getParentFile().mkdirs();
            java.nio.file.Path temporary = file.toPath().resolveSibling("portals.yml.tmp");
            data.save(temporary.toFile());
            try {
                java.nio.file.Files.move(temporary, file.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                java.nio.file.Files.move(temporary, file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            use(List.copyOf(next));
            return true;
        } catch (java.io.IOException exception) {
            plugin.getLogger().warning("Could not save portals.yml: " + exception.getMessage());
            return false;
        }
    }
}

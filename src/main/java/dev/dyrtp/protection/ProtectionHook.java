package dev.dyrtp.protection;

import org.bukkit.Location;
import org.bukkit.World;

public interface ProtectionHook {

    String id();

    String pluginName();

    boolean available();

    boolean isLocationProtected(Location location) throws Exception;

    default boolean isChunkProtected(World world, int chunkX, int chunkZ) throws Exception {
        return isLocationProtected(new Location(world, (chunkX << 4) + 8.0D, 64.0D, (chunkZ << 4) + 8.0D));
    }
}

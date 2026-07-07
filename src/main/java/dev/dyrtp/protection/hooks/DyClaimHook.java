package dev.dyrtp.protection.hooks;

import dev.dyrtp.DyRTP;
import dev.dyrtp.protection.Reflect;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

public class DyClaimHook extends AbstractProtectionHook {

    public DyClaimHook(DyRTP plugin) {
        super(plugin, "DyClaim", "DyClaim");
    }

    @Override
    public boolean isLocationProtected(Location location) throws Exception {
        return isChunkProtected(location.getWorld(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    @Override
    public boolean isChunkProtected(World world, int chunkX, int chunkZ) throws Exception {
        Plugin dyClaim = plugin.getServer().getPluginManager().getPlugin("DyClaim");
        if (dyClaim == null || !dyClaim.isEnabled()) {
            return false;
        }
        Object claimManager = Reflect.call(dyClaim, "getClaimManager");
        int buffer = Math.max(0, plugin.getConfig().getInt("protection.claim-buffer-chunks", 0));
        for (int dx = -buffer; dx <= buffer; dx++) {
            for (int dz = -buffer; dz <= buffer; dz++) {
                Object result = Reflect.call(claimManager, "isChunkClaimed", world.getName(), chunkX + dx, chunkZ + dz);
                if (Boolean.TRUE.equals(result)) {
                    return true;
                }
            }
        }
        return false;
    }
}

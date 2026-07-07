package dev.dyrtp.protection.hooks;

import dev.dyrtp.DyRTP;
import dev.dyrtp.protection.Reflect;
import org.bukkit.Location;

public class WorldGuardHook extends AbstractProtectionHook {

    public WorldGuardHook(DyRTP plugin) {
        super(plugin, "WorldGuard", "WorldGuard");
    }

    @Override
    public boolean isLocationProtected(Location location) throws Exception {
        Object worldGuard = Reflect.staticCall("com.sk89q.worldguard.WorldGuard", "getInstance");
        Object platform = Reflect.call(worldGuard, "getPlatform");
        Object container = Reflect.call(platform, "getRegionContainer");
        Object query = Reflect.call(container, "createQuery");
        Object adapted = Reflect.staticCall("com.sk89q.worldedit.bukkit.BukkitAdapter", "adapt", location);
        Object regions = Reflect.call(query, "getApplicableRegions", adapted);
        Object size = Reflect.call(regions, "size");
        return size instanceof Number number && number.intValue() > 0;
    }
}

package dev.dyrtp.protection.hooks;

import dev.dyrtp.DyRTP;
import dev.dyrtp.protection.Reflect;
import org.bukkit.Location;

public class GriefDefenderHook extends AbstractProtectionHook {

    public GriefDefenderHook(DyRTP plugin) {
        super(plugin, "GriefDefender", "GriefDefender");
    }

    @Override
    public boolean isLocationProtected(Location location) throws Exception {
        Object core = Reflect.staticCall("com.griefdefender.api.GriefDefender", "getCore");
        try {
            Object claim = Reflect.call(core, "getClaimAt", null, location);
            return claim != null && !Boolean.TRUE.equals(Reflect.call(claim, "isWilderness"));
        } catch (NoSuchMethodException ignored) {
            Object claims = Reflect.call(core, "getAllClaims");
            if (claims instanceof Iterable<?> iterable) {
                for (Object claim : iterable) {
                    Object contains = Reflect.call(claim, "contains", location.getBlockX(), location.getBlockY(), location.getBlockZ());
                    if (Boolean.TRUE.equals(contains)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}

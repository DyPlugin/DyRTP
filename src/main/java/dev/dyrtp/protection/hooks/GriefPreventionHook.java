package dev.dyrtp.protection.hooks;

import dev.dyrtp.DyRTP;
import dev.dyrtp.protection.Reflect;
import org.bukkit.Location;

public class GriefPreventionHook extends AbstractProtectionHook {

    public GriefPreventionHook(DyRTP plugin) {
        super(plugin, "GriefPrevention", "GriefPrevention");
    }

    @Override
    public boolean isLocationProtected(Location location) throws Exception {
        Object instance = Reflect.staticField("me.ryanhamshire.GriefPrevention.GriefPrevention", "instance");
        Object dataStore = instance.getClass().getField("dataStore").get(instance);
        Object claim = Reflect.call(dataStore, "getClaimAt", location, true, null);
        return claim != null;
    }
}

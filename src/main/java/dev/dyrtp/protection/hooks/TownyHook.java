package dev.dyrtp.protection.hooks;

import dev.dyrtp.DyRTP;
import dev.dyrtp.protection.Reflect;
import org.bukkit.Location;

public class TownyHook extends AbstractProtectionHook {

    public TownyHook(DyRTP plugin) {
        super(plugin, "Towny", "Towny");
    }

    @Override
    public boolean isLocationProtected(Location location) throws Exception {
        Object api = Reflect.staticCall("com.palmergames.bukkit.towny.TownyAPI", "getInstance");
        try {
            Object wilderness = Reflect.call(api, "isWilderness", location);
            return Boolean.FALSE.equals(wilderness);
        } catch (NoSuchMethodException ignored) {
            return Reflect.call(api, "getTownBlock", location) != null;
        }
    }
}

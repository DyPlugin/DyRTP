package dev.dyrtp.protection.hooks;

import dev.dyrtp.DyRTP;
import dev.dyrtp.protection.Reflect;
import org.bukkit.Location;

public class KingdomsHook extends AbstractProtectionHook {

    public KingdomsHook(DyRTP plugin) {
        super(plugin, "Kingdoms", "Kingdoms");
    }

    @Override
    public boolean isLocationProtected(Location location) throws Exception {
        Object land = Reflect.staticCall("org.kingdoms.constants.land.Land", "getLand", location);
        return land != null && Boolean.TRUE.equals(Reflect.call(land, "isClaimed"));
    }
}

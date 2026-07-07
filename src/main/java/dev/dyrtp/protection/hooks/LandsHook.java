package dev.dyrtp.protection.hooks;

import dev.dyrtp.DyRTP;
import dev.dyrtp.protection.Reflect;
import org.bukkit.Location;

public class LandsHook extends AbstractProtectionHook {

    public LandsHook(DyRTP plugin) {
        super(plugin, "Lands", "Lands");
    }

    @Override
    public boolean isLocationProtected(Location location) throws Exception {
        Object integration = Reflect.staticCall("me.angeschossen.lands.api.LandsIntegration", "of", plugin);
        return Reflect.call(integration, "getArea", location) != null;
    }
}

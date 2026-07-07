package dev.dyrtp.protection.hooks;

import dev.dyrtp.DyRTP;
import dev.dyrtp.protection.Reflect;
import org.bukkit.Location;

public class RedProtectHook extends AbstractProtectionHook {

    public RedProtectHook(DyRTP plugin) {
        super(plugin, "RedProtect", "RedProtect");
    }

    @Override
    public boolean isLocationProtected(Location location) throws Exception {
        Object redProtect = Reflect.staticCall("br.net.fabiozumbi12.RedProtect.Bukkit.RedProtect", "get");
        Object api = Reflect.call(redProtect, "getAPI");
        return Reflect.call(api, "getRegion", location) != null;
    }
}

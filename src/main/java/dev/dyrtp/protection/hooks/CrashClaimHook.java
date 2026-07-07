package dev.dyrtp.protection.hooks;

import dev.dyrtp.DyRTP;
import dev.dyrtp.protection.Reflect;
import org.bukkit.Location;

public class CrashClaimHook extends AbstractProtectionHook {

    public CrashClaimHook(DyRTP plugin) {
        super(plugin, "CrashClaim", "CrashClaim");
    }

    @Override
    public boolean isLocationProtected(Location location) throws Exception {
        Object crashClaim = Reflect.staticCall("net.crashcraft.crashclaim.CrashClaim", "getPlugin");
        Object api = Reflect.call(crashClaim, "getApi");
        return Reflect.call(api, "getClaim", location) != null;
    }
}

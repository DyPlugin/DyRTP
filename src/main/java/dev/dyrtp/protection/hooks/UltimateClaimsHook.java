package dev.dyrtp.protection.hooks;

import dev.dyrtp.DyRTP;
import dev.dyrtp.protection.Reflect;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

public class UltimateClaimsHook extends AbstractProtectionHook {

    public UltimateClaimsHook(DyRTP plugin) {
        super(plugin, "UltimateClaims", "UltimateClaims");
    }

    @Override
    public boolean isLocationProtected(Location location) throws Exception {
        Plugin ultimateClaims = plugin.getServer().getPluginManager().getPlugin("UltimateClaims");
        if (ultimateClaims == null) {
            return false;
        }
        Object manager = Reflect.call(ultimateClaims, "getClaimManager");
        Object hasClaim = Reflect.call(manager, "hasClaim", location.getChunk());
        return Boolean.TRUE.equals(hasClaim);
    }
}

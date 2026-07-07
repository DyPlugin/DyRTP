package dev.dyrtp.protection.hooks;

import dev.dyrtp.DyRTP;
import dev.dyrtp.protection.Reflect;
import org.bukkit.Location;

public class ResidenceHook extends AbstractProtectionHook {

    public ResidenceHook(DyRTP plugin) {
        super(plugin, "Residence", "Residence");
    }

    @Override
    public boolean isLocationProtected(Location location) throws Exception {
        Object instance = Reflect.staticCall("com.bekvon.bukkit.residence.Residence", "getInstance");
        Object manager;
        try {
            manager = Reflect.call(instance, "getResidenceManagerAPI");
        } catch (NoSuchMethodException ignored) {
            manager = Reflect.call(instance, "getResidenceManager");
        }
        return Reflect.call(manager, "getByLoc", location) != null;
    }
}

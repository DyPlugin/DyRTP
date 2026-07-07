package dev.dyrtp.protection.hooks;

import dev.dyrtp.DyRTP;
import dev.dyrtp.protection.Reflect;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

public class CommonReflectionHook extends AbstractProtectionHook {

    private static final String[] BOOLEAN_METHODS = {
            "isClaimed", "isProtected", "hasClaim", "hasClaims", "isInClaim", "isPlotArea"
    };

    private static final String[] OBJECT_METHODS = {
            "getClaimAt", "getClaim", "getRegion", "getArea", "getPlot", "getIslandAt", "getIslandAtLocation", "getByLoc"
    };

    private final List<String> accessorNames = List.of("getApi", "getAPI", "getClaimManager", "getRegionManager", "getPlotManager");

    public CommonReflectionHook(DyRTP plugin, String id, String pluginName) {
        super(plugin, id, pluginName);
    }

    @Override
    public boolean isLocationProtected(Location location) {
        Plugin target = plugin.getServer().getPluginManager().getPlugin(pluginName());
        if (target == null || !target.isEnabled()) {
            return false;
        }

        for (Object root : roots(target)) {
            Boolean protectedLocation = tryCommonMethods(root, location);
            if (protectedLocation != null) {
                return protectedLocation;
            }
        }
        return false;
    }

    private List<Object> roots(Plugin target) {
        List<Object> roots = new ArrayList<>();
        roots.add(target);
        for (String accessor : accessorNames) {
            try {
                Object child = Reflect.call(target, accessor);
                if (child != null) {
                    roots.add(child);
                }
            } catch (Exception ignored) {
            }
        }
        return roots;
    }

    private Boolean tryCommonMethods(Object root, Location location) {
        for (String method : BOOLEAN_METHODS) {
            try {
                Object result = Reflect.call(root, method, location);
                if (result instanceof Boolean value) {
                    return value;
                }
            } catch (Exception ignored) {
            }
            try {
                Object result = Reflect.call(root, method, location.getChunk());
                if (result instanceof Boolean value) {
                    return value;
                }
            } catch (Exception ignored) {
            }
        }
        for (String method : OBJECT_METHODS) {
            try {
                Object result = Reflect.call(root, method, location);
                if (result != null) {
                    return true;
                }
            } catch (Exception ignored) {
            }
            try {
                Object result = Reflect.call(root, method, location.getChunk());
                if (result != null) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}

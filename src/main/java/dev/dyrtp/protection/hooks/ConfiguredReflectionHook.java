package dev.dyrtp.protection.hooks;

import dev.dyrtp.DyRTP;
import dev.dyrtp.protection.Reflect;
import org.bukkit.Location;

public class ConfiguredReflectionHook extends AbstractProtectionHook {

    private final String className;
    private final String staticAccessor;
    private final String methodName;
    private final boolean claimedWhenResultIs;

    public ConfiguredReflectionHook(
            DyRTP plugin,
            String id,
            String pluginName,
            String className,
            String staticAccessor,
            String methodName,
            boolean claimedWhenResultIs
    ) {
        super(plugin, id, pluginName);
        this.className = className;
        this.staticAccessor = staticAccessor;
        this.methodName = methodName;
        this.claimedWhenResultIs = claimedWhenResultIs;
    }

    @Override
    public boolean isLocationProtected(Location location) throws Exception {
        Object result;
        if (staticAccessor == null || staticAccessor.isBlank()) {
            result = Reflect.staticCall(className, methodName, location);
        } else {
            Object target = Reflect.staticCall(className, staticAccessor);
            result = Reflect.call(target, methodName, location);
        }
        return interpret(result);
    }

    private boolean interpret(Object result) {
        if (result instanceof Boolean value) {
            return value == claimedWhenResultIs;
        }
        return result != null && claimedWhenResultIs;
    }
}

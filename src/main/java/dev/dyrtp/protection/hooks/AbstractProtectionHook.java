package dev.dyrtp.protection.hooks;

import dev.dyrtp.DyRTP;
import dev.dyrtp.protection.ProtectionHook;

public abstract class AbstractProtectionHook implements ProtectionHook {

    protected final DyRTP plugin;
    private final String id;
    private final String pluginName;

    protected AbstractProtectionHook(DyRTP plugin, String id, String pluginName) {
        this.plugin = plugin;
        this.id = id;
        this.pluginName = pluginName;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String pluginName() {
        return pluginName;
    }

    @Override
    public boolean available() {
        return plugin.getServer().getPluginManager().isPluginEnabled(pluginName);
    }
}

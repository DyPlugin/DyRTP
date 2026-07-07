package dev.dyrtp.protection;

import dev.dyrtp.DyRTP;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;

public final class ProtectionSnapshot {

    private final DyRTP plugin;
    private final List<ProtectionHook> hooks;
    private final boolean failClosed;

    ProtectionSnapshot(DyRTP plugin, List<ProtectionHook> hooks, boolean failClosed) {
        this.plugin = plugin;
        this.hooks = List.copyOf(hooks);
        this.failClosed = failClosed;
    }

    public boolean isLocationProtected(Location location) {
        for (ProtectionHook hook : hooks) {
            try {
                if (hook.isLocationProtected(location)) {
                    return true;
                }
            } catch (Exception e) {
                if (failClosed) {
                    return true;
                }
                plugin.getLogger().fine("Protection hook " + hook.id() + " skipped: " + e.getMessage());
            }
        }
        return false;
    }

    public boolean isChunkProtected(World world, int chunkX, int chunkZ) {
        for (ProtectionHook hook : hooks) {
            try {
                if (hook.isChunkProtected(world, chunkX, chunkZ)) {
                    return true;
                }
            } catch (Exception e) {
                if (failClosed) {
                    return true;
                }
                plugin.getLogger().fine("Protection hook " + hook.id() + " skipped: " + e.getMessage());
            }
        }
        return false;
    }
}

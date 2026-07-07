package dev.dyrtp.protection;

import dev.dyrtp.DyRTP;
import dev.dyrtp.protection.hooks.CommonReflectionHook;
import dev.dyrtp.protection.hooks.ConfiguredReflectionHook;
import dev.dyrtp.protection.hooks.CrashClaimHook;
import dev.dyrtp.protection.hooks.DyClaimHook;
import dev.dyrtp.protection.hooks.GriefDefenderHook;
import dev.dyrtp.protection.hooks.GriefPreventionHook;
import dev.dyrtp.protection.hooks.KingdomsHook;
import dev.dyrtp.protection.hooks.LandsHook;
import dev.dyrtp.protection.hooks.RedProtectHook;
import dev.dyrtp.protection.hooks.ResidenceHook;
import dev.dyrtp.protection.hooks.TownyHook;
import dev.dyrtp.protection.hooks.UltimateClaimsHook;
import dev.dyrtp.protection.hooks.WorldGuardHook;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ProtectionService {

    private final DyRTP plugin;
    private final List<ProtectionHook> hooks = new ArrayList<>();

    public ProtectionService(DyRTP plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        hooks.clear();
        hooks.add(new DyClaimHook(plugin));
        hooks.add(new WorldGuardHook(plugin));
        hooks.add(new GriefPreventionHook(plugin));
        hooks.add(new TownyHook(plugin));
        hooks.add(new LandsHook(plugin));
        hooks.add(new ResidenceHook(plugin));
        hooks.add(new GriefDefenderHook(plugin));
        hooks.add(new RedProtectHook(plugin));
        hooks.add(new KingdomsHook(plugin));
        hooks.add(new UltimateClaimsHook(plugin));
        hooks.add(new CrashClaimHook(plugin));
        hooks.add(new CommonReflectionHook(plugin, "Factions", "Factions"));
        hooks.add(new CommonReflectionHook(plugin, "FactionsUUID", "Factions"));
        hooks.add(new CommonReflectionHook(plugin, "SaberFactions", "Factions"));
        hooks.add(new CommonReflectionHook(plugin, "FactionsBridge", "FactionsBridge"));
        hooks.add(new CommonReflectionHook(plugin, "hClaims", "hClaims"));
        hooks.add(new CommonReflectionHook(plugin, "HuskClaims", "HuskClaims"));
        hooks.add(new CommonReflectionHook(plugin, "HuskTowns", "HuskTowns"));
        hooks.add(new CommonReflectionHook(plugin, "Pueblos", "Pueblos"));
        hooks.add(new CommonReflectionHook(plugin, "ProtectionStones", "ProtectionStones"));
        hooks.add(new CommonReflectionHook(plugin, "PlotSquared", "PlotSquared"));
        hooks.add(new CommonReflectionHook(plugin, "MinePlots", "MinePlots"));
        hooks.add(new CommonReflectionHook(plugin, "BentoBox", "BentoBox"));
        hooks.add(new CommonReflectionHook(plugin, "SuperiorSkyblock2", "SuperiorSkyblock2"));
        hooks.add(new CommonReflectionHook(plugin, "ClaimChunk", "ClaimChunk"));
        hooks.add(new CommonReflectionHook(plugin, "GriefPreventionFlags", "GriefPreventionFlags"));
        loadConfiguredHooks();
    }

    public boolean isLocationProtected(Location location) {
        return snapshot().isLocationProtected(location);
    }

    public boolean isChunkProtected(World world, int chunkX, int chunkZ) {
        return snapshot().isChunkProtected(world, chunkX, chunkZ);
    }

    public ProtectionSnapshot snapshot() {
        if (!plugin.getConfig().getBoolean("protection.enabled", true)) {
            return new ProtectionSnapshot(plugin, List.of(), false);
        }
        List<ProtectionHook> activeHooks = new ArrayList<>();
        for (ProtectionHook hook : hooks) {
            if (!enabled(hook) || !hook.available()) {
                continue;
            }
            activeHooks.add(hook);
        }
        return new ProtectionSnapshot(plugin, activeHooks, plugin.getConfig().getBoolean("protection.fail-closed", false));
    }

    public List<HookStatus> statuses() {
        List<HookStatus> result = new ArrayList<>();
        for (ProtectionHook hook : hooks) {
            result.add(new HookStatus(hook.id(), enabled(hook), hook.available()));
        }
        return Collections.unmodifiableList(result);
    }

    private boolean enabled(ProtectionHook hook) {
        return plugin.getConfig().getBoolean("protection.hooks." + hook.id(), true);
    }

    private void loadConfiguredHooks() {
        if (!plugin.getConfig().getBoolean("protection.custom-reflection-hooks.enabled", false)) {
            return;
        }
        for (Map<?, ?> section : plugin.getConfig().getMapList("protection.custom-reflection-hooks.hooks")) {
            String id = string(section.get("id"));
            String pluginName = string(section.get("plugin-name"));
            String className = string(section.get("class"));
            String method = string(section.get("method"));
            if (id == null || pluginName == null || className == null || method == null) {
                continue;
            }
            hooks.add(new ConfiguredReflectionHook(
                    plugin,
                    id,
                    pluginName,
                    className,
                    section.containsKey("static-accessor") ? string(section.get("static-accessor")) : "",
                    method,
                    booleanValue(section.get("claimed-when-result-is"), true)
            ));
        }
    }

    private String string(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private boolean booleanValue(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return fallback;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}

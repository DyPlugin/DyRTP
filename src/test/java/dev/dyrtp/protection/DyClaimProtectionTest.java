package dev.dyrtp.protection;

import dev.dyrtp.DyRTP;
import dev.dyrtp.protection.hooks.DyClaimHook;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DyClaimProtectionTest {
    public interface ClaimProvider { ClaimManager getClaimManager(); }
    public static class ClaimManager {
        final Set<String> claims = new HashSet<>();
        public boolean isChunkClaimed(String world, int x, int z) { return claims.contains(world + ":" + x + ":" + z); }
    }
    private DyRTP plugin;
    private PluginManager plugins;
    private World world;
    private ClaimManager claims;
    private Plugin dyClaim;
    private DyClaimHook hook;

    @BeforeEach void setup() {
        plugin = dev.dyrtp.TestPlugins.plugin();
        Server server = mock(Server.class);
        plugins = mock(PluginManager.class);
        world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(plugins);
        dyClaim = mock(Plugin.class, withSettings().extraInterfaces(ClaimProvider.class));
        when(dyClaim.isEnabled()).thenReturn(true);
        claims = new ClaimManager();
        when(((ClaimProvider) dyClaim).getClaimManager()).thenReturn(claims);
        hook = new DyClaimHook(plugin);
    }

    @Test void absentPluginDoesNotBlockRtp() throws Exception {
        assertFalse(hook.isChunkProtected(world, 0, 0));
    }

    @Test void realCoordinateApiFindsClaimWithoutLoadingChunk() throws Exception {
        when(plugins.getPlugin("DyClaim")).thenReturn(dyClaim);
        assertFalse(hook.isChunkProtected(world, -1, -2));
        claims.claims.add("world:-1:-2");
        assertTrue(hook.isLocationProtected(new Location(world, -0.1, 70, -16.1)));
        verify(world, never()).getChunkAt(anyInt(), anyInt());
    }

    @Test void freshQueryDetectsClaimCreatedAfterSearch() throws Exception {
        when(plugins.getPlugin("DyClaim")).thenReturn(dyClaim);
        ProtectionSnapshot snapshot = new ProtectionSnapshot(plugin, List.of(hook), false);
        Location target = new Location(world, 0.5, 70, 0.5);
        assertFalse(snapshot.isLocationProtected(target));
        claims.claims.add("world:0:0");
        assertTrue(snapshot.isLocationProtected(target));
    }

    @Test void hookFailureStopsOperationEvenWhenGenericFailClosedDisabled() {
        ProtectionHook broken = mock(ProtectionHook.class);
        when(broken.id()).thenReturn("DyClaim");
        try { when(broken.isLocationProtected(any())).thenThrow(new IllegalStateException("storage unavailable")); }
        catch (Exception impossible) { throw new AssertionError(impossible); }
        ProtectionSnapshot snapshot = new ProtectionSnapshot(plugin, List.of(broken), false);
        assertThrows(IllegalStateException.class, () -> snapshot.isLocationProtected(new Location(world, 0, 70, 0)));
    }

    @Test void disabledPluginDuringActiveSnapshotDoesNotBecomeUnclaimed() {
        when(plugins.getPlugin("DyClaim")).thenReturn(dyClaim);
        when(dyClaim.isEnabled()).thenReturn(false);
        ProtectionSnapshot snapshot = new ProtectionSnapshot(plugin, List.of(hook), false);
        assertThrows(IllegalStateException.class, () -> snapshot.isChunkProtected(world, 0, 0));
    }
    @Test void freshSnapshotMustNotDropInstalledButDisabledDyClaim() {
        when(plugins.getPlugin("DyClaim")).thenReturn(dyClaim);
        when(dyClaim.isEnabled()).thenReturn(false);
        ProtectionService service = new ProtectionService(plugin);
        assertThrows(IllegalStateException.class, () -> service.isChunkProtected(world, 0, 0));
    }
}

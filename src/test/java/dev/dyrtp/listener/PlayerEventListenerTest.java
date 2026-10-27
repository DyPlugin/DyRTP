package dev.dyrtp.listener;
import dev.dyrtp.DyRTP;
import dev.dyrtp.rtp.RtpCause;
import dev.dyrtp.rtp.RtpService;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;

class PlayerEventListenerTest {
    @Test void respawnTransitionAllowanceCannotPreserveLaterWorldChanges() {
        Fixture f = new Fixture();
        World deathWorld = mock(World.class); when(deathWorld.getUID()).thenReturn(UUID.randomUUID());
        when(f.player.getWorld()).thenReturn(deathWorld);
        f.listener.onRespawn(f.respawn(false, false));
        when(f.player.getWorld()).thenReturn(f.world);
        f.listener.onWorldChange(new PlayerChangedWorldEvent(f.player, deathWorld));
        f.listener.onWorldChange(new PlayerChangedWorldEvent(f.player, f.world));
        f.callbacks.get(0).run();
        verify(f.rtp, never()).request(any(), any());
    }
    @Test void nativeCrossWorldRespawnDoesNotCancelItsOwnDeathRtp() {
        Fixture f = new Fixture();
        World deathWorld = mock(World.class); when(deathWorld.getUID()).thenReturn(UUID.randomUUID());
        when(f.player.getWorld()).thenReturn(deathWorld);
        f.listener.onRespawn(f.respawn(false, false));
        when(f.player.getWorld()).thenReturn(f.world);
        f.listener.onWorldChange(new PlayerChangedWorldEvent(f.player, deathWorld));
        f.callbacks.get(0).run();
        verify(f.rtp).request(f.player, RtpCause.DEATH);
    }
    @Test void delayedDeathRtpDoesNotFollowPlayerIntoAnotherWorld() {
        Fixture f = new Fixture();
        f.listener.onRespawn(f.respawn(false, false));
        when(f.player.getWorld()).thenReturn(mock(World.class));
        f.callbacks.get(0).run();
        verify(f.rtp, never()).request(any(), any());
    }
    @Test void externalTeleportCancelsDelayedAutomaticRtpEvenIfCallbackArrives() {
        Fixture f = new Fixture();
        f.listener.onRespawn(f.respawn(false, false));
        f.listener.onTeleport(new PlayerTeleportEvent(f.player, new Location(f.world, 0, 64, 0), new Location(f.world, 30, 64, 30)));
        f.callbacks.get(0).run();
        verify(f.rtp, never()).request(any(), any());
    }
    @Test void worldChangeCancelsDelayedRtpEvenWhenPlayerReturnsBeforeCallback() {
        Fixture f = new Fixture();
        f.listener.onRespawn(f.respawn(false, false));
        f.listener.onWorldChange(new PlayerChangedWorldEvent(f.player, f.world));
        f.callbacks.get(0).run();
        verify(f.rtp, never()).request(any(), any());
    }
    @Test void cancelledOldCallbackCannotConsumeOrRunNewRespawnRequest() {
        Fixture f = new Fixture();
        f.listener.onRespawn(f.respawn(false, false));
        f.listener.onRespawn(f.respawn(false, false));
        f.callbacks.get(0).run();
        verify(f.rtp, never()).request(any(), any());
        f.callbacks.get(1).run();
        verify(f.rtp).request(f.player, RtpCause.DEATH);
    }
    @Test void normalRespawnRequestsDeathRtpOnce() {
        Fixture f = new Fixture();
        f.listener.onRespawn(f.respawn(false, false));
        f.callbacks.get(0).run();
        f.callbacks.get(0).run();
        verify(f.rtp).request(f.player, RtpCause.DEATH);
    }
    @Test void deadPlayerDoesNotReceiveDelayedRtp() {
        Fixture f = new Fixture();
        f.listener.onRespawn(f.respawn(false, false));
        when(f.player.isDead()).thenReturn(true);
        f.callbacks.get(0).run();
        verify(f.rtp, never()).request(any(), any());
    }
    @Test void bedAndAnchorRespawnsDoNotScheduleAutomaticRtp() {
        Fixture f = new Fixture();
        f.listener.onRespawn(f.respawn(true, false));
        f.listener.onRespawn(f.respawn(false, true));
        verify(f.scheduler, never()).runTaskLater(any(), any(Runnable.class), anyLong());
    }
    @Test void normalFirstJoinRequestsFirstJoinRtpOnce() {
        Fixture f = new Fixture();
        f.listener.onJoin(new PlayerJoinEvent(f.player, "joined"));
        f.callbacks.get(0).run();
        f.callbacks.get(0).run();
        verify(f.rtp).request(f.player, RtpCause.FIRST_JOIN);
    }
    private static class Fixture {
        final DyRTP plugin = dev.dyrtp.TestPlugins.plugin();
        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        final RtpService rtp = mock(RtpService.class);
        final Player player = mock(Player.class);
        final World world = mock(World.class);
        final List<Runnable> callbacks = new ArrayList<>();
        final PlayerEventListener listener;
        Fixture() {
            Server server = mock(Server.class);
            when(plugin.getServer()).thenReturn(server);
            when(server.getScheduler()).thenReturn(scheduler);
            when(plugin.isEnabled()).thenReturn(true);
            var config = new YamlConfiguration();
            config.set("death-rtp.enabled", true);
            config.set("first-join-rtp.enabled", true);
            when(plugin.getConfig()).thenReturn(config);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(player.isOnline()).thenReturn(true);
            when(player.getWorld()).thenReturn(world);
            when(world.getUID()).thenReturn(UUID.randomUUID());
            when(rtp.validateTeleport(any(), any())).thenReturn(true);
            when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), anyLong())).thenAnswer(call -> {
                callbacks.add(call.getArgument(1)); return mock(BukkitTask.class);
            });
            listener = new PlayerEventListener(plugin, rtp);
        }
        PlayerRespawnEvent respawn(boolean bed, boolean anchor) {
            PlayerRespawnEvent event = mock(PlayerRespawnEvent.class);
            when(event.getPlayer()).thenReturn(player);
            when(event.getRespawnLocation()).thenReturn(new Location(world, 0, 64, 0));
            when(event.isBedSpawn()).thenReturn(bed);
            when(event.isAnchorSpawn()).thenReturn(anchor);
            return event;
        }
    }
}

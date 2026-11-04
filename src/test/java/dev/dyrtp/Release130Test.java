package dev.dyrtp;

import dev.dyrtp.cooldown.CooldownService;
import dev.dyrtp.economy.EconomyService;
import dev.dyrtp.lang.MessageService;
import dev.dyrtp.listener.SignPortalListener;
import dev.dyrtp.portal.PortalStore;
import dev.dyrtp.rank.RankRules;
import dev.dyrtp.rtp.RtpCause;
import dev.dyrtp.rtp.RtpService;
import dev.dyrtp.rtp.WorldChoices;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class Release130Test {
    @TempDir Path temp;

    private static DyRTP plugin(YamlConfiguration config) {
        DyRTP plugin = TestPlugins.plugin();
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getName()).thenReturn("DyRTP");
        return plugin;
    }

    private static World world(String name) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(name);
        when(world.getUID()).thenReturn(UUID.nameUUIDFromBytes(name.getBytes()));
        return world;
    }

    private static Player player(String... permissions) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getEffectivePermissions()).thenReturn(Set.of());
        Set<String> granted = Set.of(permissions);
        when(player.hasPermission(anyString())).thenAnswer(call -> granted.contains(call.<String>getArgument(0)));
        return player;
    }

    // World price and cooldown
    @Test void aWorldsOwnPriceAndCooldownReplaceTheDefaults() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("economy.cost", 100D);
        config.set("cooldown.enabled", true);
        config.set("cooldown.seconds", 300);
        config.set("rtp.worlds.world_nether.price", 250D);
        config.set("rtp.worlds.world_nether.cooldown", 30);
        DyRTP plugin = plugin(config);
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        World overworld = world("world"), nether = world("world_nether");
        EconomyService economy = new EconomyService(plugin);
        Player player = player();
        assertEquals(100D, economy.cost(player, overworld));
        assertEquals(250D, economy.cost(player, nether));
        RankRules ranks = new RankRules(plugin);
        assertEquals(300, ranks.cooldownSeconds(player, overworld));
        assertEquals(30, ranks.cooldownSeconds(player, nether));
        CooldownService cooldowns = new CooldownService(plugin);
        cooldowns.start(player, nether);
        long remaining = cooldowns.remainingSeconds(player, nether);
        assertTrue(remaining > 25 && remaining <= 30, "remaining " + remaining);
    }

    @Test void invalidWorldPriceOrCooldownIsRejectedBeforeItIsUsed() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("rtp.worlds.world_nether.price", -5);
        assertThrows(IllegalArgumentException.class, () -> ConfigValidator.validate(config));
        config.set("rtp.worlds.world_nether.price", 5);
        config.set("rtp.worlds.world_nether.cooldown", 1.5);
        assertThrows(IllegalArgumentException.class, () -> ConfigValidator.validate(config));
        config.set("rtp.worlds.world_nether.cooldown", 60);
        assertDoesNotThrow(() -> ConfigValidator.validate(config));
    }

    // World choice
    @Test void worldChoiceIsOffByDefaultAndFollowsTheListAndPermissions() {
        YamlConfiguration config = new YamlConfiguration();
        DyRTP plugin = plugin(config);
        World overworld = world("world"), nether = world("world_nether"), end = world("world_the_end"), locked = world("event");
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(overworld, nether, end, locked));
            bukkit.when(() -> Bukkit.getWorld("world_nether")).thenReturn(nether);
            Player everything = player("dyrtp.world.*");
            assertEquals(List.of(), WorldChoices.available(plugin, everything), "off until world-selection.enabled");

            config.set("world-selection.enabled", true);
            config.set("rtp.disabled-worlds", List.of("event"));
            assertEquals(List.of("world", "world_nether", "world_the_end"), WorldChoices.available(plugin, everything));
            config.set("world-selection.worlds", List.of("world", "World_Nether"));
            assertEquals(List.of("world", "world_nether"), WorldChoices.available(plugin, everything));

            Player netherOnly = player("dyrtp.world.world_nether");
            assertEquals(List.of("world_nether"), WorldChoices.available(plugin, netherOnly));
            assertSame(nether, WorldChoices.match(plugin, netherOnly, "WORLD_NETHER"));
            assertNull(WorldChoices.match(plugin, netherOnly, "world"));
            assertEquals(List.of(), WorldChoices.available(plugin, player()), "no permission, no worlds");
        }
    }

    // Portals
    @Test void portalsAreSavedReloadedAndMatchTheirBlocks() {
        YamlConfiguration config = new YamlConfiguration();
        DyRTP plugin = plugin(config);
        when(plugin.getDataFolder()).thenReturn(temp.toFile());
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("test"));
        World world = world("world");
        PortalStore store = new PortalStore(plugin);
        assertTrue(store.all().isEmpty());
        PortalStore.Portal portal = PortalStore.Portal.of("spawn", new Location(world, 5, 64, -3), new Location(world, 2, 66, -1), "world_nether");
        assertEquals(2, portal.minX());
        assertEquals(5, portal.maxX());
        assertEquals(-3, portal.minZ());
        assertTrue(store.put(portal));

        PortalStore reloaded = new PortalStore(plugin);
        assertEquals(1, reloaded.all().size());
        assertEquals("world_nether", reloaded.get("SPAWN").target());
        assertNotNull(reloaded.at(new Location(world, 3.5, 65, -2.5)));
        assertNull(reloaded.at(new Location(world, 6, 65, -2)));
        assertNull(reloaded.at(new Location(world("other"), 3, 65, -2)));
        assertTrue(reloaded.remove("spawn"));
        assertFalse(reloaded.remove("spawn"));
        assertTrue(new PortalStore(plugin).all().isEmpty());
    }

    @Test void walkingIntoAPortalStartsOneRtpPerEntry() {
        YamlConfiguration config = new YamlConfiguration();
        DyRTP plugin = plugin(config);
        when(plugin.getDataFolder()).thenReturn(temp.toFile());
        World world = world("world");
        PortalStore store = new PortalStore(plugin);
        store.put(PortalStore.Portal.of("spawn", new Location(world, 0, 64, 0), new Location(world, 2, 66, 2), ""));
        RtpService rtp = mock(RtpService.class);
        SignPortalListener listener = new SignPortalListener(plugin, rtp, mock(MessageService.class), store);
        Player player = player("dyrtp.use", "dyrtp.portal");

        listener.onMove(move(player, new Location(world, -1, 64, 1), new Location(world, 0, 64, 1)));
        listener.onMove(move(player, new Location(world, 0, 64, 1), new Location(world, 1, 64, 1)));
        verify(rtp, times(1)).request(player, RtpCause.PORTAL, null, null);

        listener.onMove(move(player, new Location(world, 1, 64, 1), new Location(world, 5, 64, 1)));
        listener.onMove(move(player, new Location(world, 5, 64, 1), new Location(world, 2, 64, 1)));
        verify(rtp, times(2)).request(player, RtpCause.PORTAL, null, null);

        config.set("portals.enabled", false);
        listener.onMove(move(player, new Location(world, 2, 64, 1), new Location(world, 5, 64, 1)));
        listener.onMove(move(player, new Location(world, 5, 64, 1), new Location(world, 1, 64, 1)));
        verify(rtp, times(2)).request(player, RtpCause.PORTAL, null, null);
    }

    @Test void portalNeedsItsPermission() {
        YamlConfiguration config = new YamlConfiguration();
        DyRTP plugin = plugin(config);
        when(plugin.getDataFolder()).thenReturn(temp.toFile());
        World world = world("world");
        PortalStore store = new PortalStore(plugin);
        store.put(PortalStore.Portal.of("spawn", new Location(world, 0, 64, 0), new Location(world, 2, 66, 2), ""));
        RtpService rtp = mock(RtpService.class);
        MessageService messages = mock(MessageService.class);
        SignPortalListener listener = new SignPortalListener(plugin, rtp, messages, store);
        Player player = player("dyrtp.use");
        listener.onMove(move(player, new Location(world, -1, 64, 1), new Location(world, 0, 64, 1)));
        verify(rtp, never()).request(any(), any(), any(), any());
        verify(messages).send(player, "general.no-permission");
    }

    @Test void portalsSkipTheWarmupOnlyWhenConfigured() {
        YamlConfiguration config = new YamlConfiguration();
        DyRTP plugin = plugin(config);
        assertFalse(RtpCause.PORTAL.ignoreCooldown(plugin), "portals keep the cooldown");
        assertFalse(RtpCause.PORTAL.ignoreEconomy(plugin), "portals keep the price");
    }

    private static PlayerMoveEvent move(Player player, Location from, Location to) {
        return new PlayerMoveEvent(player, from, to);
    }
}

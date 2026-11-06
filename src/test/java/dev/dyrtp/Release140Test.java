package dev.dyrtp;

import dev.dyrtp.command.DyRtpCommand;
import dev.dyrtp.economy.EconomyService;
import dev.dyrtp.lang.Language;
import dev.dyrtp.lang.MessageService;
import dev.dyrtp.listener.PlayerEventListener;
import dev.dyrtp.listener.SignPortalListener;
import dev.dyrtp.portal.PortalStore;
import dev.dyrtp.protection.ProtectionService;
import dev.dyrtp.protection.ProtectionSnapshot;
import dev.dyrtp.rtp.ChunkLoader;
import dev.dyrtp.rtp.DistancePreset;
import dev.dyrtp.rtp.LocationFinder;
import dev.dyrtp.rtp.RtpCause;
import dev.dyrtp.rtp.RtpService;
import dev.dyrtp.storage.PlayerDataStore;
import dev.dyrtp.util.ConfigCache;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class Release140Test {
    @TempDir Path temp;

    // Cached configuration lists
    @Test void cachedListsAreReusedUntilTheListOrTheConfigurationChanges() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("rtp.disabled-worlds", List.of("event"));
        Set<String> first = ConfigCache.stringSet(config, "rtp.disabled-worlds");
        assertEquals(Set.of("event"), first);
        assertSame(first, ConfigCache.stringSet(config, "rtp.disabled-worlds"), "no copy while nothing changed");
        config.set("rtp.disabled-worlds", List.of("event", "lobby"));
        assertEquals(Set.of("event", "lobby"), ConfigCache.stringSet(config, "rtp.disabled-worlds"), "set(...) is seen at once");
        YamlConfiguration reloaded = new YamlConfiguration();
        assertEquals(Set.of(), ConfigCache.stringSet(reloaded, "rtp.disabled-worlds"), "a reloaded configuration is seen at once");
        reloaded.set("rtp.safety.blacklisted-blocks", List.of("magma_block", " ", "Lava"));
        assertEquals(Set.of("MAGMA_BLOCK", "LAVA"), ConfigCache.upperSet(reloaded, "rtp.safety.blacklisted-blocks"));
    }

    // Keep away from players
    @Test void playerPositionsLeaveOutSpectatorsAndThePlayerAsking() {
        World world = mock(World.class);
        Player near = online(world, 0, 0, GameMode.SURVIVAL);
        Player asking = online(world, 1000.5, 1000.5, GameMode.SURVIVAL);
        Player spectator = online(world, 50, 50, GameMode.SPECTATOR);
        when(world.getPlayers()).thenReturn(List.of(near, asking, spectator));
        double[] positions = positions(world, new Location(world, 1000, 64, 1000));
        assertArrayEquals(new double[]{0, 0}, positions);
        assertTrue(nearPlayer(positions, 30, 40, 64), "50 blocks away is inside a 64 block radius");
        assertFalse(nearPlayer(positions, 30, 40, 50), "exactly at the radius is allowed");
        when(world.getPlayers()).thenThrow(new IllegalStateException("wrong thread"));
        assertEquals(0, positions(world, null).length, "never a reason to stop RTP");
    }

    @Test void spotsNearOtherPlayersAreRejectedBeforeAnyChunkIsLoaded() {
        for (int radius : new int[]{512, 0}) try (SearchFixture f = new SearchFixture()) {
            f.config.set("rtp.safety.avoid-players-radius", radius);
            Player other = online(f.world, 0, 0, GameMode.SURVIVAL);
            when(f.world.getPlayers()).thenReturn(List.of(other));
            var result = f.finder.find(f.world, null);
            ChunkLoader loader = f.loaders.constructed().get(0);
            if (radius > 0) {
                assertNull(result.join().location());
                assertEquals(10, result.join().attempts());
                verify(loader, never()).load(any(), anyInt(), anyInt());
            } else {
                verify(loader).load(any(), anyInt(), anyInt());
                result.cancel(false);
            }
        }
    }

    // After-teleport commands
    @Test void afterTeleportCommandsRunAsConsoleOrPlayerWithPlaceholders() {
        YamlConfiguration config = new YamlConfiguration();
        DyRTP plugin = TestPlugins.plugin();
        when(plugin.getConfig()).thenReturn(config);
        World world = mock(World.class);
        when(world.getName()).thenReturn("survival");
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Alex");
        when(player.isOnline()).thenReturn(true);
        Location target = new Location(world, 120.5, 70, -40.5);
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getConsoleSender).thenReturn(console);
            config.set("after-teleport-commands.commands", List.of("console: give {player} bread 16", "player: /home {world}",
                    "say {player} landed at {x} {y} {z}", "  "));
            runCommands(plugin, player, target, true);
            bukkit.verify(() -> Bukkit.dispatchCommand(any(), anyString()), never());

            config.set("after-teleport-commands.enabled", true);
            runCommands(plugin, player, target, false);
            bukkit.verify(() -> Bukkit.dispatchCommand(console, "give Alex bread 16"));
            bukkit.verify(() -> Bukkit.dispatchCommand(console, "say Alex landed at 120 70 -41"));
            verify(player).performCommand("home survival");

            config.set("after-teleport-commands.first-rtp-only", true);
            runCommands(plugin, player, target, false);
            bukkit.verify(() -> Bukkit.dispatchCommand(console, "give Alex bread 16"), times(1));
            runCommands(plugin, player, target, true);
            bukkit.verify(() -> Bukkit.dispatchCommand(console, "give Alex bread 16"), times(2));
        }
    }

    @Test void aSuccessfulRtpCountsRunsCommandsAndOpensTheWayBack() {
        try (ServiceFixture f = new ServiceFixture()) {
            f.config.set("stats.enabled", true);
            f.config.set("back.enabled", true);
            f.config.set("after-teleport-commands.enabled", true);
            f.config.set("after-teleport-commands.first-rtp-only", true);
            f.config.set("after-teleport-commands.commands", List.of("kit starter {player}"));
            when(f.store.addRtp(f.player.getUniqueId())).thenReturn(1, 2);
            f.teleportSucceeds();
            f.service.request(f.player, RtpCause.COMMAND);
            f.service.request(f.player, RtpCause.COMMAND);
            verify(f.store, times(2)).addRtp(f.player.getUniqueId());
            f.bukkit.verify(() -> Bukkit.dispatchCommand(any(), eq("kit starter Alex")), times(1));
            assertTrue(f.service.hasReturnPoint(f.player));
        }
    }

    @Test void defaultsCountNothingAndKeepNoWayBack() {
        try (ServiceFixture f = new ServiceFixture()) {
            f.teleportSucceeds();
            f.service.request(f.player, RtpCause.COMMAND);
            verify(f.player).teleport(any(Location.class));
            verify(f.store, never()).addRtp(any());
            assertFalse(f.service.hasReturnPoint(f.player));
            assertEquals(RtpService.BackResult.DISABLED, f.service.back(f.player));
            f.bukkit.verify(() -> Bukkit.dispatchCommand(any(), anyString()), never());
        }
    }

    // Statistics
    @Test void rtpCountsAreSavedInPlayersFileAndShownByThePlaceholder() throws Exception {
        DyRTP plugin = TestPlugins.plugin();
        when(plugin.getDataFolder()).thenReturn(temp.toFile());
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        UUID id = UUID.randomUUID();
        PlayerDataStore store = new PlayerDataStore(plugin);
        assertEquals(0, store.rtpCount(id));
        assertEquals(1, store.addRtp(id));
        assertEquals(2, store.addRtp(id));
        store.setLanguage(id, "de");
        store.save();
        PlayerDataStore reloaded = new PlayerDataStore(plugin);
        assertEquals(2, reloaded.rtpCount(id));
        assertEquals("de", reloaded.getLanguage(id), "counts live next to the language preference");

        when(plugin.getPlayerDataStore()).thenReturn(reloaded);
        org.bukkit.OfflinePlayer offline = mock(org.bukkit.OfflinePlayer.class);
        when(offline.getUniqueId()).thenReturn(id);
        assertEquals("2", new dev.dyrtp.hook.PlaceholderHook(plugin).onRequest(offline, "count"));
    }

    @Test void statsCommandFollowsItsSwitchAndPermissions() {
        DyRTP plugin = TestPlugins.plugin();
        YamlConfiguration config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
        PlayerDataStore store = mock(PlayerDataStore.class);
        MessageService messages = mock(MessageService.class);
        Player player = player("dyrtp.stats");
        when(player.getName()).thenReturn("Alex");
        when(store.rtpCount(player.getUniqueId())).thenReturn(7);
        DyRtpCommand command = new DyRtpCommand(plugin, messages, null, mock(RtpService.class), store);
        Command dyrtp = command("dyrtp");
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayerExact("Alex")).thenReturn(player);
            command.onCommand(player, dyrtp, "rtp", new String[]{"stats"});
            verify(messages).send(player, "stats.disabled");

            config.set("stats.enabled", true);
            command.onCommand(player, dyrtp, "rtp", new String[]{"istatistik"});
            verify(messages).send(player, "stats.self", Map.of("{count}", "7"));
            command.onCommand(player, dyrtp, "rtp", new String[]{"stats", "Alex"});
            verify(messages).send(player, "general.no-permission");

            CommandSender console = mock(CommandSender.class);
            when(console.hasPermission("dyrtp.stats.others")).thenReturn(true);
            command.onCommand(console, dyrtp, "rtp", new String[]{"stats", "Alex"});
            verify(messages).send(console, "stats.other", Map.of("{player}", "Alex", "{count}", "7"));
        }
    }

    // Near and far
    @Test void nearAndFarAreOffByDefaultAndReadTheirDistances() {
        YamlConfiguration config = new YamlConfiguration();
        DyRTP plugin = TestPlugins.plugin();
        when(plugin.getConfig()).thenReturn(config);
        assertNull(DistancePreset.load(plugin, "near"));
        config.set("distance-presets.enabled", true);
        assertEquals(new DistancePreset("near", 100, 1000), DistancePreset.load(plugin, "near"));
        assertEquals(new DistancePreset("far", 5000, 20000), DistancePreset.load(plugin, "far"));
        config.set("distance-presets.far.min-radius", 8000);
        config.set("distance-presets.far.max-radius", 9000);
        assertEquals(new DistancePreset("far", 8000, 9000), DistancePreset.load(plugin, "far"));
        assertNull(DistancePreset.load(plugin, "middle"));
        assertEquals("dyrtp.far", DistancePreset.load(plugin, "far").permission());
    }

    @Test void nearCommandNeedsItsPermissionAndSearchesBetweenItsDistances() {
        DyRTP plugin = TestPlugins.plugin();
        YamlConfiguration config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
        MessageService messages = mock(MessageService.class);
        RtpService rtp = mock(RtpService.class);
        DyRtpCommand command = new DyRtpCommand(plugin, messages, null, rtp, null);
        Player denied = player("dyrtp.use");
        command.onCommand(denied, command("dyrtp"), "rtp", new String[]{"near"});
        verify(messages).send(denied, "rtp.preset-disabled");
        config.set("distance-presets.enabled", true);
        command.onCommand(denied, command("dyrtp"), "rtp", new String[]{"yakın"});
        verify(messages).send(denied, "general.no-permission");
        Player allowed = player("dyrtp.use", "dyrtp.far");
        command.onCommand(allowed, command("dyrtp"), "rtp", new String[]{"uzak"});
        verify(rtp).request(allowed, RtpCause.COMMAND, new DistancePreset("far", 5000, 20000));
        verify(rtp, never()).request(eq(denied), any(), any(DistancePreset.class));
    }

    @Test void aPresetSearchSkipsReadySpotsAndKeepsItsDistances() {
        try (ServiceFixture f = new ServiceFixture()) {
            f.config.set("rtp.queue.size", 3);
            f.service.request(f.player, RtpCause.COMMAND, new DistancePreset("near", 100, 1000));
            LocationFinder finder = f.finder.constructed().get(0);
            verify(finder).findBetween(eq(f.world), any(), eq(100), eq(1000));
            verify(finder, never()).find(any(), any(), any(), anyInt());
            assertEquals(0, f.service.queue().ready(f.world));
        }
    }

    // Back
    @Test void backReturnsOnceThenRespectsItsCooldown() {
        try (ServiceFixture f = new ServiceFixture()) {
            f.config.set("back.enabled", true);
            f.config.set("back.cooldown-seconds", 60);
            f.teleportSucceeds();
            when(f.player.teleportAsync(any(Location.class), any(PlayerTeleportEvent.TeleportCause.class)))
                    .thenReturn(CompletableFuture.completedFuture(true));
            assertEquals(RtpService.BackResult.NONE, f.service.back(f.player), "nothing to return to yet");
            f.service.request(f.player, RtpCause.COMMAND);
            assertEquals(RtpService.BackResult.STARTED, f.service.back(f.player));
            verify(f.player).teleportAsync(eq(new Location(f.world, 0, 64, 0)), any(PlayerTeleportEvent.TeleportCause.class));
            verify(f.messages).send(eq(f.player), eq("back.returned"), anyMap());
            assertEquals(RtpService.BackResult.NONE, f.service.back(f.player), "only once per RTP");
            f.service.request(f.player, RtpCause.COMMAND);
            assertEquals(RtpService.BackResult.COOLDOWN, f.service.back(f.player));
            assertTrue(f.service.backCooldown(f.player) > 55);
        }
    }

    @Test void forcedAndAutomaticRtpOpenNoWayBackAndQuitForgetsIt() {
        try (ServiceFixture f = new ServiceFixture()) {
            f.config.set("back.enabled", true);
            f.teleportSucceeds();
            f.service.request(f.player, RtpCause.FORCE);
            assertFalse(f.service.hasReturnPoint(f.player));
            f.service.request(f.player, RtpCause.PORTAL);
            assertTrue(f.service.hasReturnPoint(f.player));
            f.service.forgetPlayer(f.player);
            assertFalse(f.service.hasReturnPoint(f.player));
        }
    }

    @Test void failedBackKeepsThePointForAnotherTry() {
        try (ServiceFixture f = new ServiceFixture()) {
            f.config.set("back.enabled", true);
            f.teleportSucceeds();
            when(f.player.teleportAsync(any(Location.class), any(PlayerTeleportEvent.TeleportCause.class)))
                    .thenReturn(CompletableFuture.completedFuture(false));
            f.service.request(f.player, RtpCause.COMMAND);
            assertEquals(RtpService.BackResult.STARTED, f.service.back(f.player));
            verify(f.messages).send(eq(f.player), eq("back.failed"), anyMap());
            assertTrue(f.service.hasReturnPoint(f.player));
        }
    }

    @Test void backCommandReportsEachOutcome() {
        DyRTP plugin = TestPlugins.plugin();
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        MessageService messages = mock(MessageService.class);
        RtpService rtp = mock(RtpService.class);
        DyRtpCommand command = new DyRtpCommand(plugin, messages, null, rtp, null);
        Player player = player("dyrtp.use", "dyrtp.back");
        when(rtp.back(player)).thenReturn(RtpService.BackResult.DISABLED, RtpService.BackResult.NONE, RtpService.BackResult.BUSY);
        for (String word : new String[]{"back", "geri", "dön"}) command.onCommand(player, command("dyrtp"), "rtp", new String[]{word});
        verify(messages).send(player, "back.disabled");
        verify(messages).send(player, "back.none");
        verify(messages).send(player, "rtp.already-running");
        Player denied = player("dyrtp.use");
        command.onCommand(denied, command("dyrtp"), "rtp", new String[]{"back"});
        verify(rtp, never()).back(denied);
    }

    // Admin test
    @Test void adminTestReportsTimeAndAttemptsWithoutTeleporting() {
        DyRTP plugin = TestPlugins.plugin();
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        MessageService messages = mock(MessageService.class);
        RtpService rtp = mock(RtpService.class);
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        CommandSender admin = mock(CommandSender.class);
        when(admin.hasPermission("dyrtp.admin")).thenReturn(true);
        when(rtp.testSearch(world)).thenReturn(CompletableFuture.completedFuture(new LocationFinder.SearchResult(new Location(world, 10.5, 70, -3.5), 4)),
                CompletableFuture.completedFuture(new LocationFinder.SearchResult(null, 240)));
        DyRtpCommand command = new DyRtpCommand(plugin, messages, null, rtp, null);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            command.onCommand(admin, command("dyrtp"), "dyrtp", new String[]{"admin", "test", "world"});
            command.onCommand(admin, command("dyra"), "dyra", new String[]{"dene", "world"});
            command.onCommand(admin, command("dyrtp"), "dyrtp", new String[]{"admin", "test", "missing"});
        }
        verify(messages, times(2)).send(admin, "admin.test-started", Map.of("{world}", "world"));
        verify(messages).send(eq(admin), eq("admin.test-found"), argThat(map -> "4".equals(map.get("{attempts}"))
                && "10".equals(map.get("{x}")) && map.containsKey("{ms}")));
        verify(messages).send(eq(admin), eq("admin.test-failed"), argThat(map -> "240".equals(map.get("{attempts}"))));
        verify(messages).send(admin, "signs.unknown-world", Map.of("{world}", "missing"));
        verify(rtp, never()).request(any(), any());
    }

    @Test void adminHelpUsesTheNewListWithTheTestLineInEveryLanguage() throws Exception {
        for (Language language : Language.values()) {
            YamlConfiguration messages = new YamlConfiguration();
            try (var input = getClass().getClassLoader().getResourceAsStream(language.fileName())) {
                messages.load(new java.io.InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8));
            }
            List<String> v5 = messages.getStringList("admin.help-v5");
            List<String> v6 = messages.getStringList("admin.help-v6");
            assertEquals(v5.size() + 1, v6.size(), language.code());
            assertTrue(v6.get(v6.size() - 2).contains("/dyrtp admin test"), language.code());
        }
    }

    @Test void newPlayerCommandsAppearInTabOnlyWhenTurnedOn() {
        DyRTP plugin = TestPlugins.plugin();
        YamlConfiguration config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
        MessageService messages = mock(MessageService.class);
        when(messages.language(any())).thenReturn(Language.EN);
        DyRtpCommand command = new DyRtpCommand(plugin, messages, null, mock(RtpService.class), null);
        Player player = player("dyrtp.use", "dyrtp.near", "dyrtp.far", "dyrtp.back", "dyrtp.stats");
        assertFalse(command.onTabComplete(player, command("dyrtp"), "rtp", new String[]{""}).contains("near"));
        config.set("distance-presets.enabled", true);
        config.set("back.enabled", true);
        config.set("stats.enabled", true);
        assertTrue(command.onTabComplete(player, command("dyrtp"), "rtp", new String[]{""}).containsAll(List.of("near", "far", "back", "stats")));
        when(messages.language(any())).thenReturn(Language.TR);
        assertTrue(command.onTabComplete(player, command("dyrtp"), "rtp", new String[]{""}).containsAll(List.of("yakın", "uzak", "geri", "istatistik")));
    }

    // Validation
    @Test void newSettingsAreValidated() {
        for (Map.Entry<String, ?> bad : List.<Map.Entry<String, ?>>of(
                Map.entry("rtp.safety.avoid-players-radius", 600),
                Map.entry("back.expire-seconds", -1),
                Map.entry("back.cooldown-seconds", "soon"),
                Map.entry("after-teleport-commands.commands", "give {player} bread"),
                Map.entry("after-teleport-commands.first-rtp-only", "yes"),
                Map.entry("stats.enabled", "on"))) {
            YamlConfiguration config = new YamlConfiguration();
            config.set(bad.getKey(), bad.getValue());
            assertThrows(IllegalArgumentException.class, () -> ConfigValidator.validate(config), bad.getKey());
        }
        YamlConfiguration presets = new YamlConfiguration();
        presets.set("distance-presets.near.min-radius", 1000);
        presets.set("distance-presets.near.max-radius", 1000);
        assertThrows(IllegalArgumentException.class, () -> ConfigValidator.validate(presets));
        presets.set("distance-presets.near.max-radius", 1500);
        presets.set("rtp.safety.avoid-players-radius", 64);
        presets.set("after-teleport-commands.commands", List.of("console: give {player} bread 16"));
        assertDoesNotThrow(() -> ConfigValidator.validate(presets));
    }

    @Test void bundledDefaultsKeepEveryNewFeatureOff() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.load("src/main/resources/config.yml");
        assertEquals(0, config.getInt("rtp.safety.avoid-players-radius", -1));
        for (String path : List.of("distance-presets.enabled", "back.enabled", "stats.enabled", "after-teleport-commands.enabled",
                "after-teleport-commands.first-rtp-only")) {
            assertTrue(config.contains(path), path);
            assertFalse(config.getBoolean(path), path);
        }
        assertEquals(List.of(), config.getStringList("after-teleport-commands.commands"));
        YamlConfiguration descriptor = new YamlConfiguration();
        descriptor.load("src/main/resources/plugin.yml");
        assertEquals("op", descriptor.getString("permissions.dyrtp.stats.others.default"));
        for (String node : List.of("near", "far", "back", "stats")) assertTrue(descriptor.getBoolean("permissions.dyrtp." + node + ".default"), node);
    }

    // Optimizations keep the hot paths cheap
    @Test void movementWithoutPortalsOrWarmupReadsNoSettings() {
        DyRTP plugin = TestPlugins.plugin();
        when(plugin.getName()).thenReturn("DyRTP");
        when(plugin.getDataFolder()).thenReturn(temp.toFile());
        RtpService rtp = mock(RtpService.class);
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Player player = player();
        PlayerMoveEvent move = new PlayerMoveEvent(player, new Location(world, 0, 64, 0), new Location(world, 1, 64, 0));
        new SignPortalListener(plugin, rtp, mock(MessageService.class), new PortalStore(plugin)).onMove(move);
        new PlayerEventListener(plugin, rtp).onMove(move);
        verify(plugin, never()).getConfig();
        verify(rtp, never()).cancelForMove(any());
    }

    @Test void portalLookupOnlyLooksAtThePlayersWorld() {
        DyRTP plugin = TestPlugins.plugin();
        when(plugin.getDataFolder()).thenReturn(temp.toFile());
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        World nether = mock(World.class);
        when(nether.getName()).thenReturn("world_nether");
        PortalStore store = new PortalStore(plugin);
        store.put(PortalStore.Portal.of("a", new Location(world, 0, 64, 0), new Location(world, 2, 66, 2), ""));
        store.put(PortalStore.Portal.of("b", new Location(nether, 0, 64, 0), new Location(nether, 2, 66, 2), ""));
        assertEquals("a", store.at(new Location(world, 1, 65, 1)).name());
        assertEquals("b", store.at(new Location(nether, 1, 65, 1)).name());
        assertTrue(store.remove("a"));
        assertNull(store.at(new Location(world, 1, 65, 1)));
        assertFalse(store.isEmpty());
    }

    // Helpers
    private static void runCommands(DyRTP plugin, Player player, Location target, boolean first) {
        try {
            var method = Class.forName("dev.dyrtp.rtp.AfterTeleportCommands")
                    .getMethod("run", DyRTP.class, Player.class, Location.class, boolean.class);
            method.invoke(null, plugin, player, target, first);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static double[] positions(World world, Location origin) {
        try {
            var method = LocationFinder.class.getDeclaredMethod("playerPositions", World.class, Location.class);
            method.setAccessible(true);
            return (double[]) method.invoke(null, world, origin);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static boolean nearPlayer(double[] positions, double x, double z, int radius) {
        try {
            var method = LocationFinder.class.getDeclaredMethod("nearPlayer", double[].class, double.class, double.class, int.class);
            method.setAccessible(true);
            return (boolean) method.invoke(null, positions, x, z, radius);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static Player online(World world, double x, double z, GameMode mode) {
        Player player = mock(Player.class);
        when(player.getGameMode()).thenReturn(mode);
        when(player.getLocation()).thenReturn(new Location(world, x, 64, z));
        return player;
    }

    private static Player player(String... permissions) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        Set<String> granted = Set.of(permissions);
        when(player.hasPermission(anyString())).thenAnswer(call -> granted.contains(call.<String>getArgument(0)));
        return player;
    }

    private static Command command(String name) {
        Command command = mock(Command.class);
        when(command.getName()).thenReturn(name);
        return command;
    }

    /** A search over a small mocked world whose chunk loads never finish. */
    private static final class SearchFixture implements AutoCloseable {
        final DyRTP plugin = TestPlugins.plugin();
        final YamlConfiguration config = new YamlConfiguration();
        final World world = mock(World.class);
        final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        final MockedConstruction<ChunkLoader> loaders;
        final LocationFinder finder;

        SearchFixture() {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            when(plugin.getConfig()).thenReturn(config);
            when(plugin.isEnabled()).thenReturn(true);
            Server server = mock(Server.class);
            when(plugin.getServer()).thenReturn(server);
            when(server.getScheduler()).thenReturn(mock(BukkitScheduler.class));
            config.set("rtp.default-world.min-radius", 0);
            config.set("rtp.default-world.max-radius", 50);
            config.set("rtp.search.max-attempts", 10);
            config.set("rtp.search.batch-size", 64);
            config.set("rtp.search.prefer-loaded-chunks", false);
            when(world.getName()).thenReturn("world");
            when(world.getMinHeight()).thenReturn(-64);
            when(world.getMaxHeight()).thenReturn(320);
            WorldBorder border = mock(WorldBorder.class);
            when(world.getWorldBorder()).thenReturn(border);
            when(border.isInside(any())).thenReturn(true);
            ProtectionService protection = mock(ProtectionService.class);
            when(protection.snapshot()).thenReturn(mock(ProtectionSnapshot.class));
            loaders = mockConstruction(ChunkLoader.class, (mock, context) ->
                    when(mock.load(any(), anyInt(), anyInt())).thenReturn(new CompletableFuture<Chunk>()));
            finder = new LocationFinder(plugin, protection);
        }

        @Override
        public void close() {
            loaders.close();
            bukkit.close();
        }
    }

    /** RtpService with a mocked search that always finds a spot that stays safe. */
    private static final class ServiceFixture implements AutoCloseable {
        final DyRTP plugin = TestPlugins.plugin();
        final YamlConfiguration config = new YamlConfiguration();
        final Player player = mock(Player.class);
        final World world = mock(World.class);
        final MessageService messages = mock(MessageService.class);
        final PlayerDataStore store = mock(PlayerDataStore.class);
        final MockedStatic<Bukkit> bukkit;
        final MockedConstruction<LocationFinder> finder;
        final RtpService service;

        ServiceFixture() {
            EconomyService economy = mock(EconomyService.class);
            when(plugin.getConfig()).thenReturn(config);
            when(plugin.isEnabled()).thenReturn(true);
            when(plugin.getPlayerDataStore()).thenReturn(store);
            config.set("rtp.queue.size", 0);
            Server server = mock(Server.class);
            when(plugin.getServer()).thenReturn(server);
            when(server.getScheduler()).thenReturn(mock(BukkitScheduler.class));
            when(world.getName()).thenReturn("world");
            when(world.getUID()).thenReturn(UUID.randomUUID());
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(player.getName()).thenReturn("Alex");
            when(player.getWorld()).thenReturn(world);
            when(player.getLocation()).thenAnswer(call -> new Location(world, 0, 64, 0));
            when(player.isOnline()).thenReturn(true);
            when(player.hasPermission("dyrtp.use")).thenReturn(true);
            when(player.spigot()).thenReturn(mock(Player.Spigot.class));
            when(messages.get(any(), anyString())).thenReturn("text");
            when(messages.get(any(), anyString(), anyMap())).thenReturn("text");
            bukkit = mockStatic(Bukkit.class);
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
            bukkit.when(Bukkit::getConsoleSender).thenReturn(mock(ConsoleCommandSender.class));
            Location spot = new Location(world, 900.5, 64, 900.5);
            finder = mockConstruction(LocationFinder.class, (mock, context) -> {
                when(mock.find(any(), any())).thenAnswer(call -> CompletableFuture.completedFuture(new LocationFinder.SearchResult(spot, 1)));
                when(mock.find(any(), any(), any())).thenAnswer(call -> mock.find(call.getArgument(0), call.getArgument(1)));
                when(mock.find(any(), any(), any(), anyInt())).thenAnswer(call -> mock.find(call.getArgument(0), call.getArgument(1)));
                when(mock.findBetween(any(), any(), anyInt(), anyInt())).thenReturn(new CompletableFuture<>());
                when(mock.isSafeNow(any(), any())).thenReturn(true);
            });
            service = new RtpService(plugin, messages, economy, mock(ProtectionService.class));
        }

        void teleportSucceeds() {
            when(player.teleport(any(Location.class))).thenReturn(true);
        }

        @Override
        public void close() {
            finder.close();
            bukkit.close();
        }
    }
}

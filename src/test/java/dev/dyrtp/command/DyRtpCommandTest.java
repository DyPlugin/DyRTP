package dev.dyrtp.command;

import dev.dyrtp.DyRTP;
import dev.dyrtp.lang.*;
import dev.dyrtp.rtp.RtpService;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DyRtpCommandTest {
    @Test void missingBedrockNameDoesNotFallbackToJavaPlayer() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("dyrtp.admin")).thenReturn(true);
        Command command = mock(Command.class);
        when(command.getName()).thenReturn("forcertp");
        Player javaPlayer = mock(Player.class);
        when(javaPlayer.getName()).thenReturn("Alpha_Player");
        RtpService rtp = mock(RtpService.class);
        try (var bukkit = mockStatic(org.bukkit.Bukkit.class)) {
            bukkit.when(() -> org.bukkit.Bukkit.getPlayer(".Alpha_Player")).thenReturn(javaPlayer);
            bukkit.when(org.bukkit.Bukkit::getOnlinePlayers).thenReturn(java.util.List.of(javaPlayer));
            new DyRtpCommand(null, mock(MessageService.class), null, rtp, null)
                    .onCommand(sender, command, "forcertp", new String[]{".Alpha_Player"});
            verifyNoInteractions(rtp);
        }
    }
    @Test void bedrockNamesKeepTheirDotAndDoNotSelectJavaNames() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("dyrtp.admin")).thenReturn(true);
        Command command = mock(Command.class);
        when(command.getName()).thenReturn("dyrtp");
        Player bedrock = mock(Player.class);
        when(bedrock.getName()).thenReturn(".Alpha_Player");
        Player javaPlayer = mock(Player.class);
        when(javaPlayer.getName()).thenReturn("Alpha_Player");
        RtpService rtp = mock(RtpService.class);
        MessageService messages = mock(MessageService.class);
        when(messages.language(sender)).thenReturn(Language.EN);
        try (var bukkit = mockStatic(org.bukkit.Bukkit.class)) {
            bukkit.when(() -> org.bukkit.Bukkit.getPlayerExact(".Alpha_Player")).thenReturn(bedrock);
            bukkit.when(() -> org.bukkit.Bukkit.getPlayerExact("Alpha_Player")).thenReturn(javaPlayer);
            bukkit.when(org.bukkit.Bukkit::getOnlinePlayers).thenReturn(java.util.List.of(javaPlayer, bedrock));
            DyRtpCommand executor = new DyRtpCommand(null, messages, null, rtp, null);
            executor.onCommand(sender, command, "dyrtp", new String[]{"admin", "force", ".Alpha_Player"});
            verify(rtp).request(bedrock, dev.dyrtp.rtp.RtpCause.FORCE);
            verify(rtp, never()).request(javaPlayer, dev.dyrtp.rtp.RtpCause.FORCE);
            assertEquals(java.util.List.of(".Alpha_Player"),
                    executor.onTabComplete(sender, command, "dyrtp", new String[]{"admin", "force", ".Al"}));
        }
    }
    @Test void deniedAdministratorDoesNotSeeAdminCompletions() {
        MessageService messages = mock(MessageService.class);
        when(messages.language(any())).thenReturn(Language.TR);
        DyRtpCommand executor = new DyRtpCommand(null, messages, null, null, null);
        CommandSender sender = mock(CommandSender.class);
        Command command = mock(Command.class);
        when(command.getName()).thenReturn("dyrtp");
        assertFalse(executor.onTabComplete(sender, command, "dyrtp", new String[]{""}).contains("yönetim"));
        assertTrue(executor.onTabComplete(sender, command, "dyrtp", new String[]{"admin", ""}).isEmpty());
    }
    @Test void namespacedSubcommandConfirmsOnlyRtp() {
        MessageService messages = mock(MessageService.class);
        RtpService rtp = mock(RtpService.class);
        Player player = mock(Player.class);
        when(player.hasPermission("dyrtp.use")).thenReturn(true);
        Command command = mock(Command.class);
        when(command.getName()).thenReturn("dyrtp");
        new DyRtpCommand(null, messages, null, rtp, null).onCommand(player, command, "dyrtp:dyrtp", new String[]{"onayla"});
        verify(rtp).confirm(player);
    }
    @Test void invalidPricesDoNotMutateConfiguration() {
        for (String value : new String[]{"-1", "NaN", "Infinity"}) {
            DyRTP plugin = mock(DyRTP.class);
            YamlConfiguration config = new YamlConfiguration();
            config.set("economy.cost", 100.0);
            when(plugin.getConfig()).thenReturn(config);
            CommandSender admin = mock(CommandSender.class);
            when(admin.hasPermission("dyrtp.admin")).thenReturn(true);
            Command command = mock(Command.class);
            when(command.getName()).thenReturn("dyrtp");
            MessageService messages = mock(MessageService.class);
            when(messages.get(any(), anyString())).thenAnswer(invocation -> invocation.getArgument(1));
            new DyRtpCommand(plugin, messages, null, null, null)
                    .onCommand(admin, command, "dyrtp", new String[]{"admin", "price", value});
            assertEquals(100.0, config.getDouble("economy.cost"), value);
            verify(plugin, never()).saveConfig();
        }
    }
    @Test void forceDoesNotGuessAnotherPlayersName() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("dyrtp.admin")).thenReturn(true);
        Command command = mock(Command.class);
        when(command.getName()).thenReturn("forcertp");
        RtpService rtp = mock(RtpService.class);
        Player wrong = mock(Player.class);
        when(wrong.getName()).thenReturn("Alpha2");
        try (var bukkit = mockStatic(org.bukkit.Bukkit.class)) {
            bukkit.when(() -> org.bukkit.Bukkit.getPlayer("Alpha")).thenReturn(wrong);
            bukkit.when(org.bukkit.Bukkit::getOnlinePlayers).thenReturn(java.util.List.of(wrong));
            new DyRtpCommand(null, mock(MessageService.class), null, rtp, null)
                    .onCommand(sender, command, "forcertp", new String[]{"Alpha"});
            verifyNoInteractions(rtp);
        }
    }
    @Test void helpDoesNotAdvertiseDeniedPlayerOrAdminCommands() {
        Player player = mock(Player.class);
        Command command = mock(Command.class);
        when(command.getName()).thenReturn("dyrtp");
        MessageService messages = mock(MessageService.class);
        new DyRtpCommand(null, messages, null, null, null).onCommand(player, command, "dyrtp", new String[]{"help"});
        verify(messages, never()).send(player, "general.help-admin");
        verify(messages, never()).send(player, "general.help-rtp");
        verify(messages).send(player, "general.help-language");
    }
    @Test void turkishTabUsesAdminWithOnlyTurkishSubactions() {
        Player player = mock(Player.class);
        when(player.hasPermission("dyrtp.admin")).thenReturn(true);
        when(player.hasPermission("dyrtp.use")).thenReturn(true);
        MessageService messages = mock(MessageService.class);
        when(messages.language(player)).thenReturn(Language.TR);
        Command command = mock(Command.class);
        when(command.getName()).thenReturn("dyrtp");
        DyRtpCommand executor = new DyRtpCommand(null, messages, null, null, null);
        assertEquals(java.util.List.of("yardım", "dil", "reddet", "ışınlan", "onayla", "admin"),
                executor.onTabComplete(player, command, "dyrtp", new String[]{""}));
        assertEquals(java.util.List.of("oto", "en", "tr"),
                executor.onTabComplete(player, command, "dyrtp", new String[]{"dil", ""}));
        assertFalse(executor.onTabComplete(player, command, "dyrtp", new String[]{"admin", ""}).contains("reload"));
    }
    @Test void negativeRadiusAndOversizedWarmupDoNotMutateSettings() {
        for (String[] args : new String[][]{{"admin", "minradius", "-1"}, {"admin", "delay", "86401"}, {"admin", "delay", "-1"}}) {
            DyRTP plugin = mock(DyRTP.class);
            YamlConfiguration config = new YamlConfiguration();
            config.set("rtp.default-world.min-radius", 500);
            config.set("rtp.default-world.max-radius", 5000);
            config.set("warmup.seconds", 3);
            when(plugin.getConfig()).thenReturn(config);
            CommandSender sender = mock(CommandSender.class);
            when(sender.hasPermission("dyrtp.admin")).thenReturn(true);
            Command command = mock(Command.class);
            when(command.getName()).thenReturn("dyrtp");
            MessageService messages = mock(MessageService.class);
            when(messages.get(any(), anyString())).thenAnswer(invocation -> invocation.getArgument(1));
            new DyRtpCommand(plugin, messages, null, null, null).onCommand(sender, command, "dyrtp", args);
            assertEquals(500, config.getInt("rtp.default-world.min-radius"));
            assertEquals(3, config.getInt("warmup.seconds"));
            verify(plugin, never()).saveConfig();
        }
    }
    @Test void inheritedInvalidRadiusEditPreservesMemoryAndDiskAndDoesNotReportSuccess(@org.junit.jupiter.api.io.TempDir java.nio.file.Path folder) throws Exception {
        DyRTP plugin = mock(DyRTP.class, CALLS_REAL_METHODS);
        YamlConfiguration config = new YamlConfiguration();
        config.set("rtp.default-world.min-radius", 500);
        config.set("rtp.default-world.max-radius", 5000);
        config.set("rtp.worlds.survival.min-radius", 4500);
        doReturn(config).when(plugin).getConfig();
        doReturn(folder.toFile()).when(plugin).getDataFolder();
        doReturn(java.util.logging.Logger.getAnonymousLogger()).when(plugin).getLogger();
        doNothing().when(plugin).saveConfig();
        doReturn(true).when(plugin).tryReloadPlugin();
        config.save(folder.resolve("config.yml").toFile());
        String original = java.nio.file.Files.readString(folder.resolve("config.yml"));
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("dyrtp.admin")).thenReturn(true);
        Command command = mock(Command.class);
        when(command.getName()).thenReturn("dyrtp");
        MessageService messages = mock(MessageService.class);
        when(messages.get(any(), anyString())).thenAnswer(invocation -> invocation.getArgument(1));
        new DyRtpCommand(plugin, messages, null, null, null)
                .onCommand(sender, command, "dyrtp", new String[]{"admin", "radius", "max", "4000"});
        assertEquals(5000, config.getInt("rtp.default-world.max-radius"));
        assertEquals(original, java.nio.file.Files.readString(folder.resolve("config.yml")));
        verify(messages).send(sender, "general.reload-failed");
        verify(messages, never()).send(eq(sender), eq("general.setting-changed"), anyMap());
    }
    @Test void clickedConfirmationAndCancellationKeepTheirRequestToken() {
        Player player = mock(Player.class);
        when(player.hasPermission("dyrtp.use")).thenReturn(true);
        Command command = mock(Command.class);
        when(command.getName()).thenReturn("dyrtp");
        RtpService rtp = mock(RtpService.class);
        DyRtpCommand executor = new DyRtpCommand(null, mock(MessageService.class), null, rtp, null);
        executor.onCommand(player, command, "dyrtp:dyrtp", new String[]{"confirm", "quote-token"});
        executor.onCommand(player, command, "dyrtp:dyrtp", new String[]{"reddet", "cancel-token"});
        when(command.getName()).thenReturn("onayla");
        executor.onCommand(player, command, "onayla", new String[]{"legacy-token"});
        verify(rtp).confirm(player, "quote-token");
        verify(rtp).reject(player, "cancel-token");
        verify(rtp).confirm(player, "legacy-token");
    }
    @Test void dyraUsesAdminBranchPermissionAndTab() {
        Command command = mock(Command.class);
        when(command.getName()).thenReturn("dyra");
        CommandSender sender = mock(CommandSender.class);
        MessageService messages = mock(MessageService.class);
        when(messages.language(sender)).thenReturn(Language.EN);
        DyRtpCommand executor = new DyRtpCommand(null, messages, null, null, null);
        executor.onCommand(sender, command, "dyra", new String[]{});
        verify(messages).send(sender, "general.no-permission");
        assertTrue(executor.onTabComplete(sender, command, "dyra", new String[]{""}).isEmpty());
        when(sender.hasPermission("dyrtp.admin")).thenReturn(true);
        assertTrue(executor.onTabComplete(sender, command, "dyra", new String[]{""}).contains("reload"));
        executor.onCommand(sender, command, "dyra", new String[]{});
        verify(messages).getList(sender, "admin.help-v4");
    }
    @Test void helpOmitsItsOwnCommandLine() {
        Command command = mock(Command.class);
        when(command.getName()).thenReturn("dyrtp");
        MessageService messages = mock(MessageService.class);
        CommandSender sender = mock(CommandSender.class);
        new DyRtpCommand(null, messages, null, null, null).onCommand(sender, command, "dyr", new String[]{"help"});
        verify(messages, never()).send(sender, "general.help-help");
    }
    @Test void otoAndLegacyOtomatikBothStoreAutoLanguage() {
        Player player = mock(Player.class);
        var store = mock(dev.dyrtp.storage.PlayerDataStore.class);
        java.util.UUID id = java.util.UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        Command command = mock(Command.class);
        when(command.getName()).thenReturn("dyrtp");
        MessageService messages = mock(MessageService.class);
        when(messages.get(any(), anyString())).thenAnswer(invocation -> invocation.getArgument(1));
        DyRtpCommand executor = new DyRtpCommand(null, messages, null, null, store);
        executor.onCommand(player, command, "dyr", new String[]{"dil", "Oto"});
        executor.onCommand(player, command, "dyr", new String[]{"dil", "otomatik"});
        verify(store, times(2)).setLanguage(id, "auto");
    }
    @Test void paidConfigurationRequiresProviderButFreeOrOffRemainsAvailable() {
        for (String[] args : new String[][]{{"admin", "price", "20"}, {"admin", "economy", "on"}, {"admin", "economy", "20"}, {"admin", "price", "0"}, {"admin", "economy", "off"}, {"admin", "economy", "0"}}) {
            DyRTP plugin = mock(DyRTP.class);
            var economy = mock(dev.dyrtp.economy.EconomyService.class);
            when(plugin.getEconomy()).thenReturn(economy);
            YamlConfiguration config = new YamlConfiguration();
            config.set("economy.cost", 100.0);
            when(plugin.getConfig()).thenReturn(config);
            when(plugin.applyConfigChanges(anyMap())).thenReturn(true);
            Command command = mock(Command.class);
            when(command.getName()).thenReturn("dyrtp");
            CommandSender sender = mock(CommandSender.class);
            when(sender.hasPermission("dyrtp.admin")).thenReturn(true);
            MessageService messages = mock(MessageService.class);
            when(messages.get(any(), anyString())).thenAnswer(invocation -> invocation.getArgument(1));
            new DyRtpCommand(plugin, messages, null, null, null).onCommand(sender, command, "dyrtp", args);
            if (args[2].equals("0") || args[2].equals("off")) verify(plugin).applyConfigChanges(anyMap());
            else {
                verify(plugin, never()).applyConfigChanges(anyMap());
                verify(messages).send(sender, "rtp.economy-missing");
            }
        }
    }
}

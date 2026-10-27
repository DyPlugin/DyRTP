package dev.dyrtp.rtp;

import dev.dyrtp.DyRTP;
import dev.dyrtp.economy.EconomyService;
import dev.dyrtp.lang.MessageService;
import dev.dyrtp.protection.ProtectionService;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class RtpServiceTest {
    @Test void firstJoinAndDeathRtpUseTheConfiguredWorldAndFallBackWhenItIsMissing() {
        try (Fixture f = new Fixture()) {
            World survival = mock(World.class);
            when(survival.getName()).thenReturn("survival");
            when(survival.getUID()).thenReturn(UUID.randomUUID());
            f.bukkit.when(() -> Bukkit.getWorld("survival")).thenReturn(survival);
            var events = org.mockito.ArgumentCaptor.forClass(org.bukkit.event.Event.class);
            when(f.plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("test"));
            f.config.set("first-join-rtp.world", "survival");
            f.service.request(f.player, RtpCause.FIRST_JOIN);
            f.service.cancelSilently(f.player);
            f.config.set("death-rtp.world", "missing");
            f.service.request(f.player, RtpCause.DEATH);
            f.service.cancelSilently(f.player);
            f.service.request(f.player, RtpCause.COMMAND);
            verify(f.pluginManager, atLeast(3)).callEvent(events.capture());
            var worlds = events.getAllValues().stream().filter(e -> e instanceof dev.dyrtp.api.event.DyRtpRequestEvent)
                    .map(e -> ((dev.dyrtp.api.event.DyRtpRequestEvent) e).getWorld()).toList();
            assertEquals(java.util.List.of(survival, f.world, f.world), worlds, "first join goes to survival; a missing death world and /rtp use the current world");
        }
    }
    @Test void twoPlayersSharingDestinationKeepTicketUntilBothWarmupsEnd() {
        try (Fixture f = new Fixture()) {
            f.config.set("warmup.enabled", true); f.config.set("warmup.seconds", 3);
            Player second = mock(Player.class);
            when(second.getUniqueId()).thenReturn(UUID.randomUUID());
            when(second.getWorld()).thenReturn(f.world);
            when(second.getLocation()).thenReturn(new Location(f.world, 0, 64, 0));
            when(second.isOnline()).thenReturn(true); when(second.hasPermission("dyrtp.use")).thenReturn(true);
            CompletableFuture<LocationFinder.SearchResult> otherResult = new CompletableFuture<>();
            when(f.finder.constructed().get(0).find(any(), any())).thenReturn(f.result, otherResult);
            f.service.request(f.player, RtpCause.COMMAND); f.service.request(second, RtpCause.COMMAND);
            Location target = new Location(f.world, 40, 64, 40);
            f.result.complete(new LocationFinder.SearchResult(target, 1));
            otherResult.complete(new LocationFinder.SearchResult(target, 1));
            f.service.cancelSilently(f.player);
            verify(f.world, never()).removePluginChunkTicket(anyInt(), anyInt(), any());
            f.service.cancelSilently(second);
            verify(f.world).addPluginChunkTicket(2, 2, f.plugin);
            verify(f.world).removePluginChunkTicket(2, 2, f.plugin);
        }
    }
    @Test void ordinaryPlayerKeepsDestinationLoadedThroughoutWarmupAndReleasesIt() {
        try (Fixture f = new Fixture()) {
            f.config.set("warmup.enabled", true);
            f.config.set("warmup.seconds", 3);
            var scheduler = f.plugin.getServer().getScheduler();
            var callback = org.mockito.ArgumentCaptor.forClass(Runnable.class);
            Location target = new Location(f.world, -40.5, 64, 40.5);
            java.util.concurrent.atomic.AtomicBoolean retained = new java.util.concurrent.atomic.AtomicBoolean();
            when(f.world.addPluginChunkTicket(-3, 2, f.plugin)).thenAnswer(call -> { retained.set(true); return true; });
            when(f.world.removePluginChunkTicket(-3, 2, f.plugin)).thenAnswer(call -> { retained.set(false); return true; });
            when(f.finder.constructed().get(0).isSafeNow(any(), any())).thenAnswer(call -> retained.get());
            when(f.player.teleport(any(Location.class))).thenReturn(true);
            f.service.request(f.player, RtpCause.COMMAND);
            f.result.complete(new LocationFinder.SearchResult(target, 1));
            verify(scheduler).runTaskLater(eq(f.plugin), callback.capture(), eq(60L));
            callback.getValue().run();
            verify(f.player).teleport(target);
            assertFalse(retained.get());
            verify(f.world).removePluginChunkTicket(-3, 2, f.plugin);
        }
    }
    @Test void cancelledWarmupReleasesDestinationTicketExactlyOnce() {
        try (Fixture f = new Fixture()) {
            f.config.set("warmup.enabled", true);
            f.config.set("warmup.seconds", 3);
            f.service.request(f.player, RtpCause.COMMAND);
            f.result.complete(new LocationFinder.SearchResult(new Location(f.world, 40, 64, 40), 1));
            f.service.cancelAll();
            f.service.cancelAll();
            verify(f.world).addPluginChunkTicket(2, 2, f.plugin);
            verify(f.world).removePluginChunkTicket(2, 2, f.plugin);
            verify(f.player, never()).teleport(any(Location.class));
        }
    }
    @Test void missingProviderKeepsNonOpRtpFreeEvenWithStoredPaidSettings() {
        try (Fixture f = new Fixture()) {
            when(f.economy.enabled()).thenReturn(true);
            when(f.economy.cost()).thenReturn(100D);
            when(f.economy.available()).thenReturn(false);
            f.service.request(f.player, RtpCause.COMMAND);
            verify(f.finder.constructed().get(0)).find(any(), any());
            verify(f.economy, never()).has(any(), anyDouble());
        }
    }
    @Test void expiredQuoteCannotStartSearchOrDebit() throws Exception {
        try (Fixture f = new Fixture()) {
            java.lang.reflect.Field field = RtpService.class.getDeclaredField("confirmations");
            field.setAccessible(true);
            Class<?> quoteType = Class.forName("dev.dyrtp.rtp.RtpService$PendingConfirmation");
            var constructor = quoteType.getDeclaredConstructors()[0]; constructor.setAccessible(true);
            Object quote = constructor.newInstance(RtpCause.COMMAND, 0L, f.world.getUID(), f.world.getUID(), 10D, "expired", null);
            @SuppressWarnings("unchecked") var quotes = (java.util.Map<UUID, Object>) field.get(f.service);
            quotes.put(f.player.getUniqueId(), quote);
            f.service.confirm(f.player, "expired");
            verify(f.finder.constructed().get(0), never()).find(any(), any());
            verify(f.economy, never()).withdraw(any(), anyDouble());
            assertFalse(quotes.containsKey(f.player.getUniqueId()));
        }
    }
    @Test void preTeleportPermissionRevocationPreventsDebitAndTeleport() {
        try (Fixture f = new Fixture()) {
            paid(f);
            when(f.finder.constructed().get(0).isSafeNow(any(), any())).thenReturn(true);
            doAnswer(call -> {
                if (call.getArgument(0) instanceof dev.dyrtp.api.event.DyRtpPreTeleportEvent)
                    when(f.player.hasPermission("dyrtp.use")).thenReturn(false);
                return null;
            }).when(f.pluginManager).callEvent(any());
            f.service.request(f.player, RtpCause.COMMAND);
            f.result.complete(new LocationFinder.SearchResult(new Location(f.world, 40, 64, 40), 1));
            verify(f.economy, never()).withdraw(any(), anyDouble());
            verify(f.player, never()).teleport(any(Location.class));
        }
    }
    @Test void oldWarmupCannotConsumeNewRequestAfterCancellationOrReload() {
        for (boolean reload : new boolean[]{false, true}) try (Fixture f = new Fixture()) {
            paid(f);
            f.config.set("warmup.enabled", true);
            f.config.set("warmup.seconds", 3);
            java.util.List<Runnable> callbacks = new java.util.ArrayList<>();
            Server server = mock(Server.class);
            org.bukkit.scheduler.BukkitScheduler scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
            when(f.plugin.getServer()).thenReturn(server);
            when(server.getScheduler()).thenReturn(scheduler);
            when(scheduler.runTaskLater(eq(f.plugin), any(Runnable.class), anyLong())).thenAnswer(call -> {
                callbacks.add(call.getArgument(1)); return mock(org.bukkit.scheduler.BukkitTask.class);
            });
            CompletableFuture<LocationFinder.SearchResult> next = new CompletableFuture<>();
            when(f.finder.constructed().get(0).find(any(), any())).thenReturn(f.result, next);
            when(f.finder.constructed().get(0).isSafeNow(any(), any())).thenReturn(true);
            when(f.player.teleport(any(Location.class))).thenReturn(true);
            when(f.economy.withdraw(f.player, 10D)).thenReturn(mock(EconomyService.Withdrawal.class));
            f.service.request(f.player, RtpCause.COMMAND);
            f.result.complete(new LocationFinder.SearchResult(new Location(f.world, 40, 64, 40), 1));
            if (reload) f.service.reload(); else f.service.cancelSilently(f.player);
            f.service.request(f.player, RtpCause.COMMAND);
            next.complete(new LocationFinder.SearchResult(new Location(f.world, 50, 64, 50), 1));
            callbacks.get(0).run();
            assertTrue(f.service.hasPending(f.player));
            verify(f.economy, never()).withdraw(any(), anyDouble());
            verify(f.player, never()).teleport(any(Location.class));
            callbacks.get(1).run();
            callbacks.get(1).run();
            verify(f.economy).withdraw(f.player, 10D);
            verify(f.player).teleport(any(Location.class));
        }
    }
    @Test void configuredRefundOptOutRetainsDebitAfterFailedTeleport() {
        try (Fixture f = new Fixture()) {
            paid(f);
            f.config.set("economy.charge-on-success-only", false);
            EconomyService.Withdrawal receipt = mock(EconomyService.Withdrawal.class);
            when(f.economy.withdraw(f.player, 10D)).thenReturn(receipt);
            when(f.finder.constructed().get(0).isSafeNow(any(), any())).thenReturn(true);
            f.service.request(f.player, RtpCause.COMMAND);
            f.result.complete(new LocationFinder.SearchResult(new Location(f.world, 40, 64, 40), 1));
            verify(f.economy).withdraw(f.player, 10D);
            verify(receipt, never()).refund();
        }
    }
    @Test void anotherPlayerCannotConfirmOwnersQuote() {
        try (Fixture f = new Fixture()) {
            paid(f);
            f.config.set("economy.confirmation.enabled", true);
            f.service.request(f.player, RtpCause.COMMAND);
            Player other = mock(Player.class);
            when(other.getUniqueId()).thenReturn(UUID.randomUUID());
            f.service.confirm(other);
            verify(f.finder.constructed().get(0), never()).find(any(), any());
            f.service.confirm(f.player);
            verify(f.finder.constructed().get(0)).find(any(), any());
        }
    }
    @Test void chosenBiomeReachesTheSearchAndSurvivesThePaymentConfirmation() {
        try (Fixture f = new Fixture()) {
            paid(f);
            f.config.set("economy.confirmation.enabled", true);
            f.service.request(f.player, RtpCause.COMMAND, "desert");
            verify(f.finder.constructed().get(0), never()).find(any(), any(), any(), anyInt());
            f.service.confirm(f.player);
            verify(f.finder.constructed().get(0)).find(eq(f.world), any(), eq("desert"), anyInt());
            f.service.cancelSilently(f.player);
            f.service.request(f.player, RtpCause.FORCE);
            verify(f.finder.constructed().get(0)).find(eq(f.world), any(), isNull(), anyInt());
        }
    }
    private static void paid(Fixture f) {
        f.config.set("economy.confirmation.enabled", false);
        when(f.economy.enabled()).thenReturn(true);
        when(f.economy.cost()).thenReturn(10D);
        when(f.economy.available()).thenReturn(true);
        when(f.economy.has(f.player, 10D)).thenReturn(true);
    }
    @Test void balanceProviderErrorShowsServiceFailureAndDoesNotStartSearch() {
        try (Fixture f = new Fixture()) {
            when(f.economy.enabled()).thenReturn(true);
            when(f.economy.cost()).thenReturn(10D);
            when(f.economy.available()).thenReturn(true);
            when(f.economy.has(f.player, 10D)).thenThrow(new IllegalStateException("offline"));
            f.service.request(f.player, RtpCause.COMMAND);
            verify(f.messages).send(eq(f.player), eq("rtp.economy-failed"), anyMap());
            verify(f.finder.constructed().get(0), never()).find(any(), any());
            verify(f.messages, never()).send(eq(f.player), eq("rtp.not-enough-money"), anyMap());
        }
    }
    @Test void failedDebitShowsProviderFailureAndReleasesRequest() {
        try (Fixture f = new Fixture()) {
            f.config.set("economy.confirmation.enabled", false);
            when(f.economy.enabled()).thenReturn(true);
            when(f.economy.cost()).thenReturn(10D);
            when(f.economy.available()).thenReturn(true);
            when(f.economy.has(f.player, 10D)).thenReturn(true);
            when(f.finder.constructed().get(0).isSafeNow(any(), any())).thenReturn(true);
            f.service.request(f.player, RtpCause.COMMAND);
            f.result.complete(new LocationFinder.SearchResult(new Location(f.world, 40, 64, 40), 1));
            verify(f.messages).send(eq(f.player), eq("rtp.economy-failed"), anyMap());
            verify(f.messages, never()).send(eq(f.player), eq("rtp.not-enough-money"), anyMap());
            verify(f.player, never()).teleport(any(Location.class));
            f.service.request(f.player, RtpCause.COMMAND);
            verify(f.finder.constructed().get(0), times(2)).find(any(), any());
        }
    }
    @Test void cancelledSearchCannotTeleportOrConsumeMoney() {
        try (Fixture f = new Fixture()) {
            f.service.request(f.player, RtpCause.COMMAND);
            f.service.cancelSilently(f.player);
            f.result.complete(new LocationFinder.SearchResult(new Location(f.world, 40, 64, 40), 1));
            verify(f.player, never()).teleport(any(Location.class));
            verify(f.economy, never()).withdraw(any(), anyDouble());
            assertTrue(f.result.isCancelled());
        }
    }
    @Test void reloadInvalidatesSearchAndCannotApplyCooldownOrTeleport() {
        try (Fixture f = new Fixture()) {
            f.service.request(f.player, RtpCause.COMMAND);
            f.service.reload();
            f.result.complete(new LocationFinder.SearchResult(new Location(f.world, 40, 64, 40), 1));
            verify(f.player, never()).teleport(any(Location.class));
            assertTrue(f.result.isCancelled());
        }
    }
    @Test void changedQuoteDoesNotStartSearchOrWithdraw() {
        try (Fixture f = new Fixture()) {
            when(f.economy.enabled()).thenReturn(true);
            when(f.economy.cost()).thenReturn(10D);
            when(f.economy.available()).thenReturn(true);
            when(f.economy.has(f.player, 10D)).thenReturn(true);
            f.service.request(f.player, RtpCause.COMMAND);
            when(f.economy.cost()).thenReturn(20D);
            f.service.confirm(f.player);
            verify(f.finder.constructed().get(0), never()).find(any(), any());
            verify(f.economy, never()).withdraw(any(), anyDouble());
        }
    }
    @Test void revokedPermissionInvalidatesConfirmation() {
        try (Fixture f = new Fixture()) {
            when(f.economy.enabled()).thenReturn(true);
            when(f.economy.cost()).thenReturn(10D);
            when(f.economy.available()).thenReturn(true);
            when(f.economy.has(f.player, 10D)).thenReturn(true);
            f.service.request(f.player, RtpCause.COMMAND);
            when(f.player.hasPermission("dyrtp.use")).thenReturn(false);
            f.service.confirm(f.player);
            verify(f.finder.constructed().get(0), never()).find(any(), any());
        }
    }
    @Test void staleButtonTokenCannotApproveOrDiscardTheCurrentQuote() {
        try (Fixture f = new Fixture()) {
            when(f.economy.enabled()).thenReturn(true);
            when(f.economy.cost()).thenReturn(10D);
            when(f.economy.available()).thenReturn(true);
            when(f.economy.has(f.player, 10D)).thenReturn(true);
            f.service.request(f.player, RtpCause.COMMAND);
            f.service.confirm(f.player, UUID.randomUUID().toString());
            f.service.reject(f.player, UUID.randomUUID().toString());
            verify(f.finder.constructed().get(0), never()).find(any(), any());
            f.service.confirm(f.player);
            verify(f.finder.constructed().get(0)).find(any(), any());
        }
    }
    @Test void failedTeleportRefundsTheQuotedWithdrawalOnce() {
        try (Fixture f = new Fixture()) {
            f.config.set("economy.confirmation.enabled", false);
            when(f.economy.enabled()).thenReturn(true);
            when(f.economy.cost()).thenReturn(10D);
            when(f.economy.available()).thenReturn(true);
            when(f.economy.has(f.player, 10D)).thenReturn(true);
            EconomyService.Withdrawal receipt = mock(EconomyService.Withdrawal.class);
            when(receipt.refund()).thenReturn(true);
            when(f.economy.withdraw(f.player, 10D)).thenReturn(receipt);
            when(f.finder.constructed().get(0).isSafeNow(any(), any())).thenReturn(true);
            f.service.request(f.player, RtpCause.COMMAND);
            f.result.complete(new LocationFinder.SearchResult(new Location(f.world, 40, 64, 40), 1));
            f.service.cancelSilently(f.player);
            verify(f.economy).withdraw(f.player, 10D);
            verify(receipt).refund();
            verify(f.player).teleport(any(Location.class));
        }
    }
    @Test void newlyUnsafeTargetDoesNotTeleportOrCharge() {
        try (Fixture f = new Fixture()) {
            f.service.request(f.player, RtpCause.COMMAND);
            f.result.complete(new LocationFinder.SearchResult(new Location(f.world, 40, 64, 40), 1));
            verify(f.player, never()).teleport(any(Location.class));
            verify(f.economy, never()).withdraw(any(), anyDouble());
        }
    }
    @Test void finalEventGuardRejectsOtherPluginDestinationRedirect() {
        try (Fixture f = new Fixture()) {
            paid(f);
            EconomyService.Withdrawal receipt = mock(EconomyService.Withdrawal.class);
            when(receipt.refund()).thenReturn(true);
            when(f.economy.withdraw(f.player, 10D)).thenReturn(receipt);
            Location safe = new Location(f.world, 40, 64, 40);
            Location unsafe = new Location(f.world, -20, 64, -20);
            when(f.finder.constructed().get(0).isSafeNow(eq(safe), any())).thenReturn(true);
            java.util.concurrent.atomic.AtomicReference<EventExecutor> executor = new java.util.concurrent.atomic.AtomicReference<>();
            java.util.concurrent.atomic.AtomicReference<Listener> listener = new java.util.concurrent.atomic.AtomicReference<>();
            doAnswer(call -> { listener.set(call.getArgument(1)); executor.set(call.getArgument(3)); return null; })
                    .when(f.pluginManager).registerEvent(eq(PlayerTeleportEvent.class), any(), any(), any(), eq(f.plugin), eq(true));
            when(f.player.teleport(any(Location.class))).thenAnswer(call -> {
                PlayerTeleportEvent event = new PlayerTeleportEvent(f.player, f.player.getLocation(), call.getArgument(0), PlayerTeleportEvent.TeleportCause.PLUGIN);
                event.setTo(unsafe); // An earlier plugin redirected the originally safe destination.
                executor.get().execute(listener.get(), event);
                assertTrue(event.isCancelled());
                return !event.isCancelled();
            });
            f.service.request(f.player, RtpCause.COMMAND);
            f.result.complete(new LocationFinder.SearchResult(safe, 1));
            verify(f.player).teleport(any(Location.class));
            verify(f.economy).withdraw(f.player, 10D);
            verify(receipt).refund();
        }
    }
    @Test void successfulTeleportDoesNotRefundOrRepeatCompletedRequest() {
        try (Fixture f = new Fixture()) {
            f.config.set("economy.confirmation.enabled", false);
            when(f.economy.enabled()).thenReturn(true);
            when(f.economy.cost()).thenReturn(10D);
            when(f.economy.available()).thenReturn(true);
            when(f.economy.has(f.player, 10D)).thenReturn(true);
            EconomyService.Withdrawal receipt = mock(EconomyService.Withdrawal.class);
            when(f.economy.withdraw(f.player, 10D)).thenReturn(receipt);
            when(f.finder.constructed().get(0).isSafeNow(any(), any())).thenReturn(true);
            when(f.player.teleport(any(Location.class))).thenReturn(true);
            f.service.request(f.player, RtpCause.COMMAND);
            f.result.complete(new LocationFinder.SearchResult(new Location(f.world, 40, 64, 40), 1));
            f.service.cancelAll();
            verify(f.player).teleport(any(Location.class));
            verify(receipt, never()).refund();
        }
    }
    @Test void aReadySpotIsUsedAndAnUnsafeOneFallsBackToALiveSearch() {
        try (Fixture f = new Fixture()) {
            f.config.set("rtp.queue.size", 1);
            Location spot = new Location(f.world, 3000.5, 64, 3000.5);
            LocationFinder finder = f.finder.constructed().get(0);
            when(finder.ready(any())).thenAnswer(call -> CompletableFuture.completedFuture(new LocationFinder.SearchResult(call.getArgument(0), 0)));
            f.result.complete(new LocationFinder.SearchResult(spot, 4));
            f.service.queue().refill(f.world);
            assertEquals(1, f.service.queue().ready(f.world), "the background search filled the queue");
            when(finder.isSafeNow(any(), any())).thenReturn(true);
            when(f.player.teleport(any(Location.class))).thenReturn(true);
            f.service.request(f.player, RtpCause.COMMAND);
            verify(finder).ready(spot);
            verify(f.player).teleport(any(Location.class));
            verify(finder, never()).find(eq(f.world), notNull());
            f.service.cancelAll();
            f.service.queue().refill(f.world);
            when(finder.isSafeNow(any(), any())).thenReturn(false);
            f.service.request(f.player, RtpCause.COMMAND);
            verify(finder, atLeastOnce()).find(eq(f.world), notNull());
            verify(f.messages).send(eq(f.player), eq("rtp.failed-safe"), anyMap());
        }
    }
    @Test void spotsTooCloseToThePlayerAreNotHandedOut() {
        try (Fixture f = new Fixture()) {
            f.config.set("rtp.queue.size", 1);
            f.result.complete(new LocationFinder.SearchResult(new Location(f.world, 20.5, 64, 20.5), 1));
            f.service.queue().refill(f.world);
            assertNull(f.service.queue().take(f.world, new Location(f.world, 0, 64, 0)));
            assertNotNull(f.service.queue().take(f.world, new Location(f.world, 900, 64, 900)), "a farther player may take it");
        }
    }
    private static class Fixture implements AutoCloseable {
        final DyRTP plugin = dev.dyrtp.TestPlugins.plugin();
        final YamlConfiguration config = new YamlConfiguration();
        final Player player = mock(Player.class);
        final World world = mock(World.class);
        final EconomyService economy = mock(EconomyService.class);
        final MessageService messages = mock(MessageService.class);
        final PluginManager pluginManager = mock(PluginManager.class);
        final CompletableFuture<LocationFinder.SearchResult> result = new CompletableFuture<>();
        final MockedStatic<Bukkit> bukkit;
        final MockedConstruction<LocationFinder> finder;
        final RtpService service;
        Fixture() {
            // The per-player price follows the configured price unless a test sets dyrtp.price permissions.
            when(economy.cost(any(Player.class))).thenAnswer(call -> economy.cost());
            when(plugin.getConfig()).thenReturn(config);
            when(plugin.isEnabled()).thenReturn(true);
            config.set("rtp.queue.size", 0); // The ready-spot queue has its own tests.
            Server server = mock(Server.class);
            org.bukkit.scheduler.BukkitScheduler scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
            when(plugin.getServer()).thenReturn(server);
            when(server.getScheduler()).thenReturn(scheduler);
            when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), anyLong()))
                    .thenReturn(mock(org.bukkit.scheduler.BukkitTask.class));
            when(world.getName()).thenReturn("world");
            when(world.getUID()).thenReturn(UUID.randomUUID());
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(player.getWorld()).thenReturn(world);
            when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
            when(player.isOnline()).thenReturn(true);
            when(player.hasPermission("dyrtp.use")).thenReturn(true);
            when(player.spigot()).thenReturn(mock(Player.Spigot.class));
            when(economy.format(anyDouble())).thenReturn("10.00");
            when(messages.get(any(), anyString())).thenReturn("confirmation");
            bukkit = mockStatic(Bukkit.class);
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            finder = mockConstruction(LocationFinder.class, (mock, context) -> {
                when(mock.find(any(), any())).thenReturn(result);
                when(mock.find(any(), any(), any())).thenAnswer(call -> mock.find(call.getArgument(0), call.getArgument(1)));
                when(mock.find(any(), any(), any(), anyInt())).thenAnswer(call -> mock.find(call.getArgument(0), call.getArgument(1)));
            });
            service = new RtpService(plugin, messages, economy, mock(ProtectionService.class));
        }
        public void close() { finder.close(); bukkit.close(); }
    }
}

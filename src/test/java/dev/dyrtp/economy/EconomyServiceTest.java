package dev.dyrtp.economy;
import dev.dyrtp.DyRTP;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class EconomyServiceTest {
    @Test void providerIsDetectedWhilePaymentsAreDisabledAndAfterRegistrationChanges() {
        Fixture f = new Fixture();
        var manager = f.plugin.getServer().getPluginManager();
        var services = f.plugin.getServer().getServicesManager();
        when(manager.getPlugin("Vault")).thenReturn(mock(org.bukkit.plugin.Plugin.class));
        @SuppressWarnings("unchecked") var registration = (org.bukkit.plugin.RegisteredServiceProvider<Economy>) mock(org.bukkit.plugin.RegisteredServiceProvider.class);
        Economy provider = mock(Economy.class);
        when(registration.getProvider()).thenReturn(provider);
        when(services.getRegistration(Economy.class)).thenReturn(registration);
        assertTrue(f.service.available());
        when(services.getRegistration(Economy.class)).thenReturn(null);
        assertFalse(f.service.available());
    }
    @Test void balanceProviderFailureDoesNotMasqueradeAsInsufficientFunds() throws Exception {
        Fixture f = new Fixture();
        Economy economy = mock(Economy.class);
        Player player = mock(Player.class);
        java.lang.reflect.Field provider = EconomyService.class.getDeclaredField("economy");
        provider.setAccessible(true);
        provider.set(f.service, economy);
        when(economy.has(player, 10D)).thenThrow(new IllegalStateException("provider unavailable"));
        assertThrows(IllegalStateException.class, () -> f.service.has(player, 10D));
        assertNull(f.service.withdraw(player, 10D));
        verify(economy, never()).withdrawPlayer(any(Player.class), anyDouble());
    }
    @Test void invalidPricesAreRejectedInsteadOfBecomingFree() {
        Fixture f = new Fixture();
        for (double amount : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY}) {
            f.config.set("economy.cost", amount);
            assertThrows(IllegalArgumentException.class, f.service::cost);
        }
    }
    @Test void failedRefundIsNotRetriedAndUsesOriginalProvider() throws Exception {
        Fixture f = new Fixture();
        Economy original = mock(Economy.class);
        Economy replacement = mock(Economy.class);
        Player player = mock(Player.class);
        java.lang.reflect.Field provider = EconomyService.class.getDeclaredField("economy");
        provider.setAccessible(true);
        provider.set(f.service, original);
        when(original.has(player, 12D)).thenReturn(true);
        when(original.withdrawPlayer(player, 12D)).thenReturn(new EconomyResponse(12D, 88D, EconomyResponse.ResponseType.SUCCESS, null));
        when(original.depositPlayer(player, 12D)).thenReturn(new EconomyResponse(0D, 88D, EconomyResponse.ResponseType.FAILURE, "unavailable"));
        EconomyService.Withdrawal withdrawal = f.service.withdraw(player, 12D);
        assertNotNull(withdrawal);
        provider.set(f.service, replacement);
        assertFalse(withdrawal.refund());
        assertFalse(withdrawal.refund());
        verify(original).depositPlayer(player, 12D);
        verifyNoInteractions(replacement);
    }
    @Test void providerWithdrawalExceptionIsNotBlindlyRetriedOrRefunded() throws Exception {
        Fixture f = new Fixture();
        Economy economy = mock(Economy.class);
        Player player = mock(Player.class);
        java.lang.reflect.Field provider = EconomyService.class.getDeclaredField("economy");
        provider.setAccessible(true);
        provider.set(f.service, economy);
        when(economy.has(player, 10D)).thenReturn(true);
        when(economy.withdrawPlayer(player, 10D)).thenThrow(new IllegalStateException("unknown result"));
        assertNull(f.service.withdraw(player, 10D));
        verify(economy).withdrawPlayer(player, 10D);
        verify(economy, never()).depositPlayer(any(Player.class), anyDouble());
    }
    private static class Fixture {
        final DyRTP plugin = dev.dyrtp.TestPlugins.plugin();
        final YamlConfiguration config = new YamlConfiguration();
        final EconomyService service;
        Fixture() {
            when(plugin.getConfig()).thenReturn(config);
            when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
            var server = mock(org.bukkit.Server.class);
            when(plugin.getServer()).thenReturn(server);
            when(server.getPluginManager()).thenReturn(mock(org.bukkit.plugin.PluginManager.class));
            when(server.getServicesManager()).thenReturn(mock(org.bukkit.plugin.ServicesManager.class));
            service = new EconomyService(plugin);
        }
    }
}

package dev.dyrtp.economy;

import dev.dyrtp.DyRTP;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public class EconomyService {

    private final DyRTP plugin;
    private Economy economy;

    public EconomyService(DyRTP plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        economy = null;
        if (!plugin.getConfig().getBoolean("economy.enabled", false)) {
            return;
        }
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return;
        }
        RegisteredServiceProvider<Economy> registration = plugin.getServer()
                .getServicesManager()
                .getRegistration(Economy.class);
        if (registration != null) {
            economy = registration.getProvider();
        }
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("economy.enabled", false);
    }

    public boolean available() {
        return economy != null;
    }

    public double cost() {
        return Math.max(0.0D, plugin.getConfig().getDouble("economy.cost", 0.0D));
    }

    public boolean has(Player player) {
        return !enabled() || cost() <= 0.0D || (economy != null && economy.has(player, cost()));
    }

    public boolean withdraw(Player player) {
        if (!enabled() || cost() <= 0.0D) {
            return true;
        }
        if (economy == null || !economy.has(player, cost())) {
            return false;
        }
        return economy.withdrawPlayer(player, cost()).transactionSuccess();
    }

    public String format(double amount) {
        if (economy != null) {
            return economy.format(amount);
        }
        return String.format("%.2f", amount);
    }
}

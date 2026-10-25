package dev.dyrtp.economy;

import dev.dyrtp.DyRTP;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public class EconomyService {
    private final DyRTP plugin;
    private Economy economy;
    private final dev.dyrtp.rank.RankRules ranks;
    public EconomyService(DyRTP plugin) { this.plugin = plugin; this.ranks = new dev.dyrtp.rank.RankRules(plugin); reload(); }
    public void reload() {
        economy = null;
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            if (enabled()) plugin.getLogger().warning("Vault is not installed; RTP remains free.");
            return;
        }
        RegisteredServiceProvider<Economy> registration = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (registration != null) economy = registration.getProvider();
        if (economy == null && enabled()) plugin.getLogger().warning("No economy provider is registered; RTP remains free.");
    }
    public boolean enabled() { return plugin.getConfig().getBoolean("economy.enabled", false); }
    public boolean available() {
        // Vault alone is not a payment provider. Requery so a provider enabled or
        // disabled after DyRTP is observed without forcing paid RTP on first.
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            economy = null;
        } else {
            RegisteredServiceProvider<Economy> registration = plugin.getServer().getServicesManager().getRegistration(Economy.class);
            economy = registration == null ? null : registration.getProvider();
        }
        return economy != null;
    }
    public double cost() {
        double amount = plugin.getConfig().getDouble("economy.cost", 0);
        if (!Double.isFinite(amount) || amount < 0) throw new IllegalArgumentException("economy.cost must be finite and nonnegative");
        return amount;
    }
    /** The configured price, or a lower one from a dyrtp.price.<amount> permission. */
    public double cost(Player player) { return ranks.price(player, cost()); }
    public boolean has(Player player) { return !enabled() || has(player, cost(player)); }
    public boolean has(Player player, double amount) {
        validate(amount);
        try { return amount == 0 || (economy != null && economy.has(player, amount)); }
        catch (RuntimeException exception) {
            plugin.getLogger().warning("Economy balance query failed: " + exception.getMessage());
            throw new IllegalStateException("Economy balance query failed", exception);
        }
    }
    public boolean withdraw(Player player) { return !enabled() || withdraw(player, cost(player)) != null; }
    public Withdrawal withdraw(Player player, double amount) {
        validate(amount);
        if (amount == 0) return new Withdrawal(player, null, 0);
        Economy provider = economy;
        if (provider == null) return null;
        try {
            if (!has(player, amount)) return null;
        } catch (IllegalStateException balanceFailure) {
            return null; // No debit was attempted; its result is not uncertain.
        }
        try {
            EconomyResponse response = provider.withdrawPlayer(player, amount);
            return response != null && response.transactionSuccess() ? new Withdrawal(player, provider, amount) : null;
        } catch (RuntimeException exception) {
            plugin.getLogger().severe("Economy withdrawal result is uncertain for " + player.getUniqueId() + "; manual reconciliation required: " + exception.getMessage());
            return null;
        }
    }
    private static void validate(double amount) {
        if (!Double.isFinite(amount) || amount < 0) throw new IllegalArgumentException("Invalid economy amount");
    }
    public String format(double amount) {
        if (economy != null) {
            try { return economy.format(amount); } catch (RuntimeException ignored) { }
        }
        return String.format(java.util.Locale.ROOT, "%.2f", amount);
    }
    public final class Withdrawal {
        private final Player player;
        private final Economy provider;
        private final double amount;
        private boolean refunded;
        private Withdrawal(Player player, Economy provider, double amount) { this.player = player; this.provider = provider; this.amount = amount; }
        public boolean refund() {
            if (refunded) return false;
            refunded = true; // An uncertain provider response must never trigger another deposit.
            if (provider == null || amount == 0) return true;
            try {
                EconomyResponse response = provider.depositPlayer(player, amount);
                if (response != null && response.transactionSuccess()) return true;
            } catch (RuntimeException ignored) { }
            plugin.getLogger().severe("RTP refund failed or is uncertain for " + player.getUniqueId() + " (" + amount + "); manual reconciliation required.");
            return false;
        }
    }
}

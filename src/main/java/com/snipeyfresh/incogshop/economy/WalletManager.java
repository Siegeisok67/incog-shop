package com.snipeyfresh.incogshop.economy;

import com.snipeyfresh.incogshop.IncogShopPlugin;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class WalletManager {
    private final IncogShopPlugin plugin;
    private final File file;
    private final Map<UUID, Double> balances = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private Economy vault;
    private boolean usingVault;

    public WalletManager(IncogShopPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "wallets.yml");
    }

    public void load() {
        setupProvider();
        balances.clear(); names.clear();
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        var section = yml.getConfigurationSection("wallets");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    balances.put(id, Math.max(0, section.getDouble(key + ".balance", 0)));
                    names.put(id, section.getString(key + ".name", "Unknown"));
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    private void setupProvider() {
        String mode = plugin.getConfig().getString("economy.mode", "VAULT").toUpperCase();
        usingVault = false;
        vault = null;

        if (!mode.equals("INTERNAL")) {
            RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
            if (rsp != null) vault = rsp.getProvider();
            usingVault = vault != null;
        }

        if (mode.equals("VAULT") && !usingVault) {
            plugin.getLogger().severe("economy.mode is VAULT, but Vault has no registered economy provider. Available providers: " + availableProviders());
        } else if (usingVault) {
            plugin.getLogger().info("Using Vault economy provider: " + vault.getName());
        } else {
            plugin.getLogger().warning("Using Incog-Shop internal wallet fallback.");
        }
    }

    private String availableProviders() {
        StringBuilder names = new StringBuilder();
        for (RegisteredServiceProvider<Economy> registration : Bukkit.getServicesManager().getRegistrations(Economy.class)) {
            Economy provider = registration.getProvider();
            if (provider == null) continue;
            if (!names.isEmpty()) names.append(", ");
            names.append(provider.getName());
        }
        return names.isEmpty() ? "none" : names.toString();
    }

    public boolean ready() {
        String mode = plugin.getConfig().getString("economy.mode", "VAULT").toUpperCase();
        return !mode.equals("VAULT") || usingVault;
    }
    public boolean usingVault() { return usingVault; }
    public String providerName() { return usingVault ? vault.getName() : "Incog-Shop Internal"; }

    public void save() {
        // Keep names and legacy balances available for INTERNAL fallback and lookup. Vault remains the source of truth when active.
        YamlConfiguration yml = new YamlConfiguration();
        for (var e : balances.entrySet()) {
            String root = "wallets." + e.getKey();
            yml.set(root + ".balance", round(e.getValue()));
            yml.set(root + ".name", names.getOrDefault(e.getKey(), "Unknown"));
        }
        try { yml.save(file); }
        catch (IOException ex) { plugin.getLogger().severe("Could not save wallets.yml: " + ex.getMessage()); }
    }

    private OfflinePlayer offline(UUID id) { return Bukkit.getOfflinePlayer(id); }

    public double get(UUID id) {
        if (usingVault) return Math.max(0, vault.getBalance(offline(id)));
        return balances.computeIfAbsent(id, x -> plugin.getConfig().getDouble("economy.starting-balance", 500.0));
    }

    public void touch(UUID id, String name) {
        names.put(id, name);
        if (!usingVault) get(id);
    }

    public boolean set(UUID id, double amount) {
        amount = Math.max(0, round(amount));
        if (!usingVault) { balances.put(id, amount); return true; }
        double current = get(id);
        if (amount > current) return transaction(vault.depositPlayer(offline(id), amount - current));
        if (amount < current) return transaction(vault.withdrawPlayer(offline(id), current - amount));
        return true;
    }

    public boolean deposit(UUID id, double amount) {
        amount = Math.max(0, round(amount));
        if (amount <= 0) return true;
        if (!usingVault) { balances.put(id, round(get(id) + amount)); return true; }
        return transaction(vault.depositPlayer(offline(id), amount));
    }

    public boolean withdraw(UUID id, double amount) {
        amount = Math.max(0, round(amount));
        if (get(id) + 1e-9 < amount) return false;
        if (!usingVault) { balances.put(id, round(get(id) - amount)); return true; }
        return transaction(vault.withdrawPlayer(offline(id), amount));
    }

    private static boolean transaction(EconomyResponse response) { return response != null && response.transactionSuccess(); }

    public UUID findByName(String name) {
        var online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        for (var e : names.entrySet()) if (e.getValue().equalsIgnoreCase(name)) return e.getKey();
        @SuppressWarnings("deprecation") OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        return offline.hasPlayedBefore() ? offline.getUniqueId() : null;
    }

    public static double round(double v) { return Math.round(v * 100.0) / 100.0; }
}

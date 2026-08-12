package com.snipeyfresh.incogshop.command;

import com.snipeyfresh.incogshop.IncogShopPlugin;
import com.snipeyfresh.incogshop.util.Money;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class PayCommand implements CommandExecutor {
    private final IncogShopPlugin plugin;
    public PayCommand(IncogShopPlugin plugin) { this.plugin = plugin; }
    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
        if (args.length != 2) return false;
        Player target = plugin.getServer().getPlayerExact(args[0]);
        if (target == null) { player.sendMessage(plugin.prefix() + "§cThat player must be online."); return true; }
        if (target.getUniqueId().equals(player.getUniqueId())) { player.sendMessage(plugin.prefix() + "§cYou cannot pay yourself."); return true; }
        double amount = Money.parsePositive(args[1]);
        if (amount <= 0) { player.sendMessage(plugin.prefix() + "§cInvalid amount. Examples: 10000, 10k, 2.5m."); return true; }
        double min = Math.max(0.01, plugin.getConfig().getDouble("economy.pay-minimum", 0.01));
        if (!Double.isFinite(amount) || amount < min) { player.sendMessage(plugin.prefix() + "§cMinimum payment is " + plugin.money(min) + "."); return true; }
        if (!plugin.wallets().withdraw(player.getUniqueId(), amount)) { player.sendMessage(plugin.prefix() + "§cInsufficient funds."); return true; }
        plugin.wallets().deposit(target.getUniqueId(), amount);
        plugin.market().audit("PAY", player.getUniqueId(), player.getName(), target.getUniqueId().toString(), 0, amount, "target=" + target.getName());
        player.sendMessage(plugin.prefix() + "§aPaid §f" + target.getName() + " §a" + plugin.money(amount) + ".");
        target.sendMessage(plugin.prefix() + "§aReceived §f" + plugin.money(amount) + " §afrom " + player.getName() + "."); return true;
    }
}

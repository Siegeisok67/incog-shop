package com.snipeyfresh.incogshop.command;

import com.snipeyfresh.incogshop.IncogShopPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class BalanceCommand implements CommandExecutor {
    private final IncogShopPlugin plugin;
    public BalanceCommand(IncogShopPlugin plugin) { this.plugin = plugin; }
    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Usage: /balance <player>"); return true; }
            sender.sendMessage(plugin.prefix() + "§6Balance: §f" + plugin.money(plugin.wallets().get(p.getUniqueId()))); return true;
        }
        if (!sender.hasPermission("incogshop.balance.others")) { sender.sendMessage(plugin.prefix() + "§cNo permission."); return true; }
        Player online = plugin.getServer().getPlayerExact(args[0]);
        UUID id = online != null ? online.getUniqueId() : plugin.wallets().findByName(args[0]);
        if (id == null) { sender.sendMessage(plugin.prefix() + "§cNo stored wallet found for that player."); return true; }
        sender.sendMessage(plugin.prefix() + "§6" + args[0] + "'s balance: §f" + plugin.money(plugin.wallets().get(id))); return true;
    }
}

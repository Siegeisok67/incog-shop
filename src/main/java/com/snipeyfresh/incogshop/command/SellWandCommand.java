package com.snipeyfresh.incogshop.command;

import com.snipeyfresh.incogshop.IncogShopPlugin;
import com.snipeyfresh.incogshop.util.SellWand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class SellWandCommand implements CommandExecutor {
    private final IncogShopPlugin plugin;

    public SellWandCommand(IncogShopPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("incogshop.sellwand")) {
            player.sendMessage(plugin.prefix() + "§cYou do not have permission: §fincogshop.sellwand");
            return true;
        }
        if (!plugin.getConfig().getBoolean("sell-wand.enabled", true)) {
            player.sendMessage(plugin.prefix() + "§cThe Sell Wand is disabled.");
            return true;
        }
        var leftover = player.getInventory().addItem(SellWand.create(plugin));
        if (!leftover.isEmpty()) player.getWorld().dropItemNaturally(player.getLocation(), leftover.values().iterator().next());
        player.sendMessage(plugin.prefix() + "§aReceived a Sell Wand. Right-click a chest, barrel, or shulker box to bulk-sell its eligible contents to the market.");
        return true;
    }
}

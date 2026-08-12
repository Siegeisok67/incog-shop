package com.snipeyfresh.incogshop.command;

import com.snipeyfresh.incogshop.IncogShopPlugin;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public final class SellCommand implements CommandExecutor {
    private final IncogShopPlugin plugin;
    public SellCommand(IncogShopPlugin plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }

        if (args.length == 0) {
            if (!plugin.getConfig().getBoolean("sell-gui.enabled", true)) { player.sendMessage(plugin.prefix() + "§cThe sell GUI is disabled."); return true; }
            plugin.sellGui().open(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("hand")) {
            ItemStack held = player.getInventory().getItemInMainHand();
            if (!plugin.market().isEligibleStack(held)) {
                player.sendMessage(plugin.prefix() + "§cHold an eligible, plain item in your hand first.");
                return true;
            }
            plugin.customSell().beginQuote(player, held.getType(), held.getAmount(), true);
            return true;
        }

        Material material = Material.matchMaterial(args[0]);
        if (material == null || !plugin.market().isSellAllowed(material)) {
            player.sendMessage(plugin.prefix() + "§cUnknown or unsellable item: §f" + args[0]);
            return true;
        }
        int available = plugin.market().sellableCount(player, material);
        if (available <= 0) {
            player.sendMessage(plugin.prefix() + "§cYou have no eligible §f" + material.name() + " §cto sell.");
            return true;
        }
        plugin.customSell().beginQuote(player, material, available, false);
        return true;
    }
}

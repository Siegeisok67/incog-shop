package com.snipeyfresh.incogshop.command;

import com.snipeyfresh.incogshop.IncogShopPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class SellCommand implements CommandExecutor {
    private final IncogShopPlugin plugin;
    public SellCommand(IncogShopPlugin plugin) { this.plugin = plugin; }
    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
        if (!plugin.getConfig().getBoolean("sell-gui.enabled", true)) { player.sendMessage(plugin.prefix() + "§cThe sell GUI is disabled."); return true; }
        plugin.sellGui().open(player);
        return true;
    }
}

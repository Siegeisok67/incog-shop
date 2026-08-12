package com.snipeyfresh.incogshop.listener;

import com.snipeyfresh.incogshop.IncogShopPlugin;
import com.snipeyfresh.incogshop.gui.XpVaultGui;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;

public final class XpVaultGuiListener implements Listener {
    private final IncogShopPlugin plugin;
    public XpVaultGuiListener(IncogShopPlugin plugin){this.plugin=plugin;}

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof XpVaultGui.Holder)) return;
        event.setCancelled(true);
        int slot=event.getRawSlot();
        if (slot < 0 || slot >= 27) return;
        if (slot == 22) { player.closeInventory(); return; }

        long moved=0;
        long current=plugin.xpVault().current(player);
        long stored=plugin.xpVault().stored(player.getUniqueId());

        if (slot == XpVaultGui.DEPOSIT_25) moved=plugin.xpVault().deposit(player, Math.max(1,current/4));
        else if (slot == XpVaultGui.DEPOSIT_50) moved=plugin.xpVault().deposit(player, Math.max(1,current/2));
        else if (slot == XpVaultGui.DEPOSIT_ALL) moved=plugin.xpVault().deposit(player,current);
        else if (slot == XpVaultGui.WITHDRAW_25) moved=-plugin.xpVault().withdraw(player, Math.max(1,stored/4));
        else if (slot == XpVaultGui.WITHDRAW_50) moved=-plugin.xpVault().withdraw(player, Math.max(1,stored/2));
        else if (slot == XpVaultGui.WITHDRAW_ALL) moved=-plugin.xpVault().withdraw(player,stored);
        else return;

        if (moved > 0) player.sendMessage(plugin.prefix()+"§aDeposited §f"+moved+" XP §ainto your XP Vault.");
        else if (moved < 0) player.sendMessage(plugin.prefix()+"§aWithdrew §f"+(-moved)+" XP §afrom your XP Vault.");
        else player.sendMessage(plugin.prefix()+"§eNo XP was moved.");

        plugin.xpVaultGui().open(player);
    }
}

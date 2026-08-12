package com.snipeyfresh.incogshop.gui;

import com.snipeyfresh.incogshop.IncogShopPlugin;
import com.snipeyfresh.incogshop.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class SellGui {
    public static final int SELL_SLOTS = 45;
    public static final int CONFIRM_SLOT = 49;
    public static final int CANCEL_SLOT = 45;
    private final IncogShopPlugin plugin;

    public SellGui(IncogShopPlugin plugin) { this.plugin = plugin; }

    public static final class Holder implements InventoryHolder {
        private boolean processed;
        private boolean cancelled;
        @Override public Inventory getInventory() { return null; }
        public boolean processed() { return processed; }
        public boolean cancelled() { return cancelled; }
        public void setProcessed(boolean value) { processed = value; }
        public void setCancelled(boolean value) { cancelled = value; }
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(new Holder(), 54, Text.color("&8Incog-Shop &7• &aSell Items"));
        ItemStack filler = ShopGui.named(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);
        inv.setItem(CANCEL_SLOT, ShopGui.named(Material.BARRIER, "&cCancel", List.of("&7Return every item without selling.")));
        inv.setItem(47, ShopGui.named(Material.BOOK, "&eHow This Works", List.of("&7Place eligible market items in the", "&7top 5 rows.", "", "&aClosing the menu sells everything.", "&aYou can also click Sell Now.", "", "&7Ineligible items are returned/stashed.")));
        inv.setItem(CONFIRM_SLOT, ShopGui.named(Material.EMERALD_BLOCK, "&aSell Eligible Items", List.of("&7The server adds sold items to stock", "&7and pays your Vault balance.", "", "&eClick to sell now", "&7Closing the menu does the same thing.")));
        inv.setItem(51, ShopGui.named(Material.GOLD_INGOT, "&6Balance: &f" + plugin.money(plugin.wallets().get(player.getUniqueId())), List.of("&7Economy: &f" + plugin.wallets().providerName())));
        player.openInventory(inv);
    }
}

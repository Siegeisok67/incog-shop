package com.snipeyfresh.incogshop.listener;

import com.snipeyfresh.incogshop.IncogShopPlugin;
import com.snipeyfresh.incogshop.gui.SellGui;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public final class SellGuiListener implements Listener {
    private final IncogShopPlugin plugin;
    public SellGuiListener(IncogShopPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof SellGui.Holder) || !(event.getWhoClicked() instanceof Player player)) return;
        int raw = event.getRawSlot();
        if (raw >= SellGui.SELL_SLOTS && raw < top.getSize()) {
            event.setCancelled(true);
            if (raw == SellGui.CANCEL_SLOT) { player.closeInventory(); return; }
            if (raw == SellGui.CONFIRM_SLOT) confirm(player, top);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SellGui.Holder)) return;
        for (int slot : event.getRawSlots()) if (slot >= SellGui.SELL_SLOTS && slot < 54) { event.setCancelled(true); return; }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof SellGui.Holder) || !(event.getPlayer() instanceof Player player)) return;
        returnItems(player, event.getInventory());
    }

    private void confirm(Player player, Inventory inv) {
        double total = 0; int sold = 0;
        for (int i = 0; i < SellGui.SELL_SLOTS; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || stack.getType().isAir()) continue;
            inv.setItem(i, null);
            var result = plugin.market().sellEscrowStack(player, stack);
            if (result.success()) {
                sold += result.amount(); total += result.total();
                int remainder = stack.getAmount() - result.amount();
                if (remainder > 0) { ItemStack left = stack.clone(); left.setAmount(remainder); giveOrDrop(player, left); }
            } else giveOrDrop(player, stack);
        }
        player.sendMessage(plugin.prefix() + (sold > 0 ? "§aSold §f" + sold + " §aitem(s) for §f" + plugin.money(total) + "§a." : "§eNo eligible items were sold."));
        player.closeInventory();
    }

    private void returnItems(Player player, Inventory inv) {
        for (int i = 0; i < SellGui.SELL_SLOTS; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || stack.getType().isAir()) continue;
            inv.setItem(i, null); giveOrDrop(player, stack);
        }
    }

    private static void giveOrDrop(Player player, ItemStack stack) {
        Map<Integer, ItemStack> extra = player.getInventory().addItem(stack);
        for (ItemStack item : extra.values()) player.getWorld().dropItemNaturally(player.getLocation(), item);
    }
}

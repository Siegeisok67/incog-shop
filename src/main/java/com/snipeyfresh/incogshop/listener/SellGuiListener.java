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

public final class SellGuiListener implements Listener {
    private final IncogShopPlugin plugin;
    public SellGuiListener(IncogShopPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof SellGui.Holder holder) || !(event.getWhoClicked() instanceof Player player)) return;
        int raw = event.getRawSlot();
        if (raw >= SellGui.SELL_SLOTS && raw < top.getSize()) {
            event.setCancelled(true);
            if (raw == SellGui.CANCEL_SLOT) {
                holder.setCancelled(true);
                returnItems(player, top);
                holder.setProcessed(true);
                player.closeInventory();
                return;
            }
            if (raw == SellGui.CONFIRM_SLOT) {
                processSale(player, top, holder);
                player.closeInventory();
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SellGui.Holder)) return;
        for (int slot : event.getRawSlots()) if (slot >= SellGui.SELL_SLOTS && slot < 54) { event.setCancelled(true); return; }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof SellGui.Holder holder) || !(event.getPlayer() instanceof Player player)) return;
        if (holder.processed()) return;
        if (holder.cancelled()) returnItems(player, event.getInventory());
        else processSale(player, event.getInventory(), holder);
    }

    private void processSale(Player player, Inventory inv, SellGui.Holder holder) {
        if (holder.processed()) return;
        holder.setProcessed(true);
        double total = 0; int sold = 0; int stashed = 0;
        for (int i = 0; i < SellGui.SELL_SLOTS; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || stack.getType().isAir()) continue;
            inv.setItem(i, null);
            var result = plugin.market().sellEscrowStack(player, stack.clone());
            if (result.success()) {
                sold += result.amount(); total += result.total();
                int remainder = stack.getAmount() - result.amount();
                if (remainder > 0) {
                    ItemStack left = stack.clone(); left.setAmount(remainder);
                    stashed += giveOrStash(player, left);
                }
            } else stashed += giveOrStash(player, stack);
        }
        player.sendMessage(plugin.prefix() + (sold > 0
                ? "§aSold §f" + sold + " §aitem(s) for §f" + plugin.money(total) + "§a."
                : "§eNo eligible items were sold.")
                + (stashed > 0 ? " §d" + stashed + " overflow item(s) went to /stash." : ""));
    }

    private void returnItems(Player player, Inventory inv) {
        int stashed=0;
        for (int i = 0; i < SellGui.SELL_SLOTS; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || stack.getType().isAir()) continue;
            inv.setItem(i, null);
            stashed += giveOrStash(player, stack);
        }
        if(stashed>0) player.sendMessage(plugin.prefix()+"§d"+stashed+" returned item(s) did not fit and were moved to /stash.");
    }

    private int giveOrStash(Player player, ItemStack stack) {
        return plugin.stash().deliverOrStash(player, stack);
    }
}

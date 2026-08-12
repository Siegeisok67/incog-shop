package com.snipeyfresh.incogshop.listener;

import com.snipeyfresh.incogshop.IncogShopPlugin;
import com.snipeyfresh.incogshop.gui.LayoutEditorGui;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class LayoutEditorListener implements Listener {
    private final IncogShopPlugin plugin;
    public LayoutEditorListener(IncogShopPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof LayoutEditorGui.Holder holder)) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;

        if (holder.screen().equals("menu")) {
            if (slot == 20) plugin.layoutEditor().open(player, "categories", null);
            else if (slot == 22) plugin.layoutEditor().open(player, "subcategories", null);
            else if (slot == 24) plugin.layoutEditor().open(player, "items", null);
            else if (slot == 40) plugin.adminSetupGui().open(player);
            return;
        }

        if (slot == 43) {
            plugin.layouts().reset(holder.screen());
            player.sendMessage(plugin.prefix() + "§aReset the §f" + holder.screen() + " §alayout to the centered defaults.");
            plugin.layoutEditor().open(player, holder.screen(), null);
            return;
        }
        if (slot == 44) { plugin.layoutEditor().open(player, "menu", null); return; }

        if (holder.selectedKey() != null) {
            plugin.layouts().set(holder.screen(), holder.selectedKey(), slot);
            player.sendMessage(plugin.prefix() + "§aMoved §f" + holder.selectedKey() + " §ato slot §f" + slot + "§a.");
            plugin.layoutEditor().open(player, holder.screen(), null);
            return;
        }

        ItemStack item = event.getView().getTopInventory().getItem(slot);
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta.getLore() == null) return;
        for (String line : meta.getLore()) {
            String clean = org.bukkit.ChatColor.stripColor(line);
            if (clean != null && clean.startsWith("Control: ")) {
                plugin.layoutEditor().open(player, holder.screen(), clean.substring("Control: ".length()).trim());
                return;
            }
        }
    }
}

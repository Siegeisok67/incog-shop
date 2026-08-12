package com.snipeyfresh.incogshop.listener;

import com.snipeyfresh.incogshop.IncogShopPlugin;
import com.snipeyfresh.incogshop.gui.LayoutEditorGui;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class LayoutEditorListener implements Listener {
    private final IncogShopPlugin plugin;
    public LayoutEditorListener(IncogShopPlugin plugin){this.plugin=plugin;}

    @EventHandler public void click(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p))return;
        if(!(e.getView().getTopInventory().getHolder() instanceof LayoutEditorGui.Holder h))return;
        e.setCancelled(true);
        int slot=e.getRawSlot();
        if(slot<0||slot>=e.getView().getTopInventory().getSize())return;

        if(h.screen().equals("menu")){
            if(slot==11) plugin.layoutEditor().open(p,"categories",null);
            else if(slot==13) plugin.layoutEditor().open(p,"subcategories",null);
            else if(slot==15) plugin.layoutEditor().open(p,"items",null);
            else if(slot==22) p.closeInventory();
            return;
        }

        if(slot==44){plugin.layoutEditor().open(p,"menu",null);return;}
        if(slot==53){p.closeInventory();p.sendMessage(plugin.prefix()+"§aGUI layout saved.");return;}

        if(h.selectedKey()!=null){
            plugin.layouts().set(h.screen(),h.selectedKey(),slot);
            p.sendMessage(plugin.prefix()+"§aMoved §f"+h.selectedKey()+" §ato slot §f"+slot+" §7("+h.screen()+").");
            plugin.layoutEditor().open(p,h.screen(),null);
            return;
        }

        ItemStack item=e.getView().getTopInventory().getItem(slot);
        if(item==null||!item.hasItemMeta())return;
        ItemMeta meta=item.getItemMeta();
        if(meta.getLore()==null)return;
        for(String line:meta.getLore()){
            String clean=org.bukkit.ChatColor.stripColor(line);
            if(clean!=null&&clean.startsWith("Logical key: ")){
                plugin.layoutEditor().open(p,h.screen(),clean.substring("Logical key: ".length()).trim());
                return;
            }
        }
    }
}

package com.snipeyfresh.incogshop.gui;

import com.snipeyfresh.incogshop.IncogShopPlugin;
import com.snipeyfresh.incogshop.util.Text;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.*;

public final class StashGui {
    public record Holder(int page) implements InventoryHolder { public Inventory getInventory(){return null;} }
    private final IncogShopPlugin plugin;
    public StashGui(IncogShopPlugin plugin){this.plugin=plugin;}

    public void open(Player player,int requestedPage){
        List<ItemStack> items=plugin.stash().get(player.getUniqueId());
        int pages=Math.max(1,(items.size()+44)/45);
        int page=Math.max(0,Math.min(pages-1,requestedPage));
        Inventory inv=Bukkit.createInventory(new Holder(page),54, Text.color("&8Incog-Shop &7• &dStash"));
        int start=page*45;
        for(int i=0;i<45 && start+i<items.size();i++){
            ItemStack icon=items.get(start+i).clone();
            ItemMeta meta=icon.getItemMeta();
            List<String> lore=meta.hasLore()&&meta.getLore()!=null?new ArrayList<>(meta.getLore()):new ArrayList<>();
            lore.add(""); lore.add(Text.color("&eClick to claim this stack"));
            meta.setLore(lore); icon.setItemMeta(meta); inv.setItem(i,icon);
        }
        ItemStack filler=ShopGui.named(Material.BLACK_STAINED_GLASS_PANE," ",List.of());
        for(int i=45;i<54;i++) inv.setItem(i,filler);
        if(page>0) inv.setItem(45,ShopGui.named(Material.ARROW,"&ePrevious Page",List.of()));
        inv.setItem(47,ShopGui.named(Material.CHEST,"&aClaim All",List.of("&7Move as much as possible", "&7into your inventory.","","&eClick to claim")));
        inv.setItem(49,ShopGui.named(Material.ENDER_CHEST,"&dStashed Stacks: &f"+items.size(),List.of("&7Overflow from Incog-Shop deliveries", "&7is kept safely here.")));
        inv.setItem(51,ShopGui.named(Material.EMERALD_BLOCK,"&aSell All Eligible",List.of("&7Sell every stash item accepted", "&7by the server market.", "", "&7Custom/ineligible items stay stashed.", "&eClick to sell")));
        if(page<pages-1) inv.setItem(53,ShopGui.named(Material.ARROW,"&eNext Page",List.of()));
        player.openInventory(inv);
    }
}

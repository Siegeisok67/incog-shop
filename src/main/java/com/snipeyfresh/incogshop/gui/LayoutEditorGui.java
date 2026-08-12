package com.snipeyfresh.incogshop.gui;

import com.snipeyfresh.incogshop.IncogShopPlugin;
import com.snipeyfresh.incogshop.custom.CustomCategoryManager.CustomCategory;
import com.snipeyfresh.incogshop.util.Text;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import java.util.*;

public final class LayoutEditorGui {
    public record Holder(String screen, String selectedKey) implements InventoryHolder {
        public Inventory getInventory(){ return null; }
    }

    private final IncogShopPlugin plugin;
    public LayoutEditorGui(IncogShopPlugin plugin){ this.plugin=plugin; }

    public void open(Player p, String screen, String selected) {
        String normalized = screen == null ? "menu" : screen.toLowerCase(Locale.ROOT);
        if (normalized.equals("categories")) openCategories(p, selected);
        else if (normalized.equals("subcategories")) openSubcategories(p, selected);
        else if (normalized.equals("items")) openItems(p, selected);
        else openMenu(p);
    }

    public void open(Player p, String selected) { open(p, "menu", selected); }

    private void openMenu(Player p) {
        Inventory inv=Bukkit.createInventory(new Holder("menu",null),27,Text.color("&8Incog-Shop &7• &dLayout Editor"));
        fill(inv);
        inv.setItem(11,ShopGui.named(Material.CHEST,"&6Category Screen",List.of("&7Move category and main-control buttons.","","&eClick to edit")));
        inv.setItem(13,ShopGui.named(Material.BOOKSHELF,"&eSubcategory Screen",List.of("&7Move subcategory buttons and controls.","","&eClick to edit")));
        inv.setItem(15,ShopGui.named(Material.ITEM_FRAME,"&bItem Browser",List.of("&7Move item-display slots and controls.","","&eClick to edit")));
        inv.setItem(22,ShopGui.named(Material.BARRIER,"&cClose",List.of()));
        p.openInventory(inv);
    }

    private void openCategories(Player p,String selected){
        Inventory inv=base("categories",selected,"&8Incog-Shop &7• &dCategories Layout");
        put(inv,"search",Material.COMPASS,"&bSearch",40,selected);
        put(inv,"orders",Material.WRITABLE_BOOK,"&bAll Market Orders",41,selected);
        put(inv,"help",Material.BOOK,"&eHow Market Works",48,selected);
        put(inv,"balance",Material.GOLD_INGOT,"&6Balance/Admin",49,selected);
        int[] built=ShopGui.centeredSlots(MarketCategory.values().length);
        MarketCategory[] cats=MarketCategory.values();
        for(int i=0;i<cats.length;i++) put(inv,"builtin:"+cats[i].name(),cats[i].icon(),"&6"+cats[i].display(),i<built.length?built[i]:10+i,selected);
        int n=0;
        for(CustomCategory c:plugin.customCategories().all()) put(inv,"custom:"+c.id(),c.icon(),"&d"+c.display(),10+(n++%27),selected);
        finish(inv);
        p.openInventory(inv);
    }

    private void openSubcategories(Player p,String selected){
        Inventory inv=base("subcategories",selected,"&8Incog-Shop &7• &dSubcategory Layout");
        // Logical positions are shared across all built-in category subcategory screens.
        for(int i=0;i<21;i++) put(inv,"sub:"+i,Material.BOOKSHELF,"&eSubcategory Slot "+(i+1),10+i,selected);
        put(inv,"all",Material.CHEST,"&6All Category Items",39,selected);
        put(inv,"search",Material.COMPASS,"&bSearch Category",40,selected);
        put(inv,"back",Material.ARROW,"&eBack",45,selected);
        put(inv,"status",Material.GOLD_INGOT,"&6Balance/Admin",49,selected);
        finish(inv);
        p.openInventory(inv);
    }

    private void openItems(Player p,String selected){
        Inventory inv=base("items",selected,"&8Incog-Shop &7• &dItem Browser Layout");
        for(int i=0;i<36;i++) put(inv,"item:"+i,Material.ITEM_FRAME,"&bItem Position "+(i+1),i,selected);
        put(inv,"previous",Material.ARROW,"&ePrevious Page",45,selected);
        put(inv,"back",Material.CHEST,"&6Back/Categories",46,selected);
        put(inv,"search",Material.COMPASS,"&bSearch",47,selected);
        put(inv,"clear",Material.BARRIER,"&cClear Search",48,selected);
        put(inv,"status",Material.GOLD_INGOT,"&6Balance/Admin",49,selected);
        put(inv,"orders",Material.WRITABLE_BOOK,"&bAll Market Orders",50,selected);
        put(inv,"section",Material.BOOK,"&6Current Section",51,selected);
        put(inv,"page",Material.PAPER,"&fPage Indicator",52,selected);
        put(inv,"next",Material.ARROW,"&eNext Page",53,selected);
        finish(inv);
        p.openInventory(inv);
    }

    private Inventory base(String screen,String selected,String title){
        Inventory inv=Bukkit.createInventory(new Holder(screen,selected),54,Text.color(title));
        for(int i=0;i<54;i++) inv.setItem(i,ShopGui.named(Material.GRAY_STAINED_GLASS_PANE,"&8Empty Slot &7#"+i,List.of("&7Select a control, then click this slot.")));
        return inv;
    }

    private void finish(Inventory inv){
        inv.setItem(44,ShopGui.named(Material.NETHER_STAR,"&dChange Screen",List.of("&7Return to layout screen selection.")));
        inv.setItem(53,ShopGui.named(Material.BARRIER,"&cDone",List.of("&7Save automatically and exit.")));
    }

    private void put(Inventory inv,String key,Material mat,String name,int fallback,String selected){
        String screen=((Holder)inv.getHolder()).screen();
        int slot=plugin.layouts().slot(screen,key,fallback);
        if(slot==44||slot==53) return; // reserved editor controls
        List<String> lore=new ArrayList<>();
        lore.add("&7Logical key: &f"+key);
        lore.add("&7Current slot: &f"+slot);
        lore.add("");
        lore.add(key.equals(selected)?"&aSELECTED - click destination":"&eClick to select, then click destination");
        inv.setItem(slot,ShopGui.named(mat,name,lore));
    }

    private void fill(Inventory inv){
        ItemStack filler=ShopGui.named(Material.GRAY_STAINED_GLASS_PANE," ",List.of());
        for(int i=0;i<inv.getSize();i++) inv.setItem(i,filler);
    }
}

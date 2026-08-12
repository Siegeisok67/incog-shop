package com.snipeyfresh.incogshop.gui;

import com.snipeyfresh.incogshop.IncogShopPlugin;
import com.snipeyfresh.incogshop.market.MarketEntry;
import com.snipeyfresh.incogshop.market.MarketMode;
import com.snipeyfresh.incogshop.custom.CustomCategoryManager.CustomCategory;
import com.snipeyfresh.incogshop.shop.PlayerShop;
import com.snipeyfresh.incogshop.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class ShopGui {
    public static final int ITEMS_PER_PAGE = 36;
    public static int[] centeredSlots(int count) {
        if (count <= 0) return new int[0];
        int[] slots = new int[count];
        int written = 0, row = 1;
        while (written < count) {
            int inRow = Math.min(7, count - written);
            int startColumn = 1 + (7 - inRow) / 2;
            for (int i = 0; i < inRow; i++) slots[written++] = row * 9 + startColumn + i;
            row++;
        }
        return slots;
    }
    public int[] subcategoryLayoutSlots(int count) {
        int[] out = new int[count];
        for (int i=0;i<count;i++) out[i]=plugin.layouts().slot("subcategories","sub:"+i,10+i);
        return out;
    }

    public int[] itemLayoutSlots() {
        int[] out = new int[ITEMS_PER_PAGE];
        for (int i=0;i<ITEMS_PER_PAGE;i++) out[i]=plugin.layouts().slot("items","item:"+i,i);
        return out;
    }
    private final IncogShopPlugin plugin;

    public ShopGui(IncogShopPlugin plugin) { this.plugin = plugin; }

    public record CategoryHolder(boolean admin) implements InventoryHolder { @Override public Inventory getInventory() { return null; } }
    public record SubcategoryHolder(boolean admin, MarketCategory category) implements InventoryHolder { @Override public Inventory getInventory() { return null; } }
    public record MarketHolder(int page, boolean admin, MarketCategory category, MarketSubcategory subcategory, String query) implements InventoryHolder { @Override public Inventory getInventory() { return null; } }
    public record CustomMarketHolder(String categoryId, int page, boolean admin) implements InventoryHolder { @Override public Inventory getInventory() { return null; } }
    public record PlacementCategoryHolder(Material material) implements InventoryHolder { @Override public Inventory getInventory() { return null; } }
    public record PlacementSubcategoryHolder(Material material, MarketCategory category) implements InventoryHolder { @Override public Inventory getInventory() { return null; } }
    public record PlayerShopHolder(UUID shopId) implements InventoryHolder { @Override public Inventory getInventory() { return null; } }

    public void openCategories(Player player, boolean admin) {
        Inventory inv = Bukkit.createInventory(new CategoryHolder(admin), 54, Text.color(admin ? "&8Incog-Shop Admin &7• &fBrowse" : "&8Incog-Shop &7• &fBrowse"));
        decorate(inv, Material.GRAY_STAINED_GLASS_PANE);

        MarketCategory[] cats = MarketCategory.values();
        int[] defaults = centeredSlots(cats.length);
        for (int i = 0; i < cats.length; i++) {
            MarketCategory c = cats[i];
            long count = filtered(c, null, "", admin).size();
            List<String> lore = new ArrayList<>();
            lore.add("&7" + count + " market items");
            if (c == MarketCategory.ALL) lore.add("&8Browse everything in one list.");
            else lore.add("&8Open subcategories.");
            lore.add(""); lore.add("&eClick to browse");
            int fallback = i < defaults.length ? defaults[i] : 10 + i;
            int slot = plugin.layouts().slot("categories", "builtin:" + c.name(), fallback);
            inv.setItem(slot, named(c.icon(), "&6" + c.display(), lore));
        }

        int customIndex = 0;
        for (CustomCategory c : plugin.customCategories().all()) {
            long count = plugin.customCategories().materials(c.id()).stream()
                    .filter(m -> admin || plugin.market().isMarketEnabled(m)).count();
            int slot = plugin.layouts().slot("categories", "custom:" + c.id(), 10 + (customIndex++ % 27));
            inv.setItem(slot, named(c.icon(), "&d" + c.display(), List.of("&7" + count + " market items", "&8Custom category", "", "&eClick to browse")));
        }

        int searchSlot = plugin.layouts().slot("categories", "search", 40);
        int ordersSlot = plugin.layouts().slot("categories", "orders", 41);
        int helpSlot = plugin.layouts().slot("categories", "help", 48);
        int balanceSlot = plugin.layouts().slot("categories", "balance", 49);
        inv.setItem(searchSlot, named(Material.COMPASS, "&bSearch Market", List.of("&7Search every enabled item by name.", "&7Example: &fdiamond&7, &foak log", "", "&eClick, then type in chat")));
        if (!admin) inv.setItem(ordersSlot, named(Material.WRITABLE_BOOK, "&bAll Market Orders", List.of("&7Browse every active Buy and Sell Order", "&7without finding an item first.", "", "&eClick to browse")));
        inv.setItem(helpSlot, named(Material.BOOK, "&eHow the Market Works", List.of("&7Players create server stock by selling.", "&7Buying removes from that stock.", "&7Prices react to supply and demand.")));
        inv.setItem(balanceSlot, named(admin ? Material.COMMAND_BLOCK : Material.GOLD_INGOT, admin ? "&cAdmin Market" : "&6Balance: &f" + plugin.money(plugin.wallets().get(player.getUniqueId())), admin ? List.of("&7Use /marketadmin layout to move buttons.", "&7Use F on an item to cycle market mode.", "&7Use Q to reorganize built-in categories.") : List.of("&7Buy and sell directly from the menus.")));
        if (admin) {
            inv.setItem(50, infiniteStockButton());
            inv.setItem(51, named(Material.ARMOR_STAND, "&dEdit GUI Layout", List.of("&7Move category and control buttons", "&7to custom slots.", "", "&eClick to edit")));
            inv.setItem(52, named(Material.NAME_TAG, "&dCustom Categories", List.of("&7Create: &f/marketadmin createcategory", "&7Assign: &f/marketadmin setcategory", "&7Delete: &f/marketadmin deletecategory")));
            inv.setItem(53, named(Material.ITEM_FRAME, "&dAdd / Configure Item", List.of("&7Use &f/marketadmin additem", "&7or press &fF &7on an item to cycle:", "&aBuy & Sell &7→ &eSell Only &7→ &cDisabled")));
        }
        player.openInventory(inv);
    }

    public void openSubcategories(Player player, boolean admin, MarketCategory category) {
        if (category == MarketCategory.ALL) { openMarket(player, 0, admin, MarketCategory.ALL, null, ""); return; }
        Inventory inv = Bukkit.createInventory(new SubcategoryHolder(admin, category), 54, Text.color("&8" + (admin ? "Incog-Shop Admin" : "Incog-Shop") + " &7• &f" + category.display()));
        decorate(inv, Material.GRAY_STAINED_GLASS_PANE);
        inv.setItem(4, named(category.icon(), "&6" + category.display(), List.of("&7Choose a section below.")));
        List<MarketSubcategory> subs = MarketSubcategory.forCategory(category);
        int[] subcategorySlots = subcategoryLayoutSlots(subs.size());
        for (int i = 0; i < subs.size() && i < subcategorySlots.length; i++) {
            MarketSubcategory sub = subs.get(i);
            long count = filtered(category, sub, "", admin).size();
            inv.setItem(subcategorySlots[i], named(sub.icon(), "&e" + sub.display(), List.of("&7" + count + " items", "", "&eClick to browse")));
        }
        inv.setItem(plugin.layouts().slot("subcategories","all",39), named(Material.CHEST, "&6All " + category.display(), List.of("&7Show every item in this category.", "", "&eClick to browse")));
        inv.setItem(plugin.layouts().slot("subcategories","search",40), named(Material.COMPASS, "&bSearch " + category.display(), List.of("&7Search within this category.", "", "&eClick, then type in chat")));
        inv.setItem(plugin.layouts().slot("subcategories","back",45), named(Material.ARROW, "&eBack", List.of("&7Return to categories.")));
        inv.setItem(plugin.layouts().slot("subcategories","status",49), named(admin ? Material.COMMAND_BLOCK : Material.GOLD_INGOT, admin ? "&cAdmin Mode" : "&6Balance: &f" + plugin.money(plugin.wallets().get(player.getUniqueId())), admin ? List.of("&7Category management is available", "&7from the item list using Q.") : List.of("&7Select a subcategory to continue.")));
        if (admin) inv.setItem(50, infiniteStockButton());
        player.openInventory(inv);
    }

    public void openMarket(Player player, int page, boolean admin) { openMarket(player, page, admin, MarketCategory.ALL, null, ""); }
    public void openMarket(Player player, int page, boolean admin, MarketCategory category, String query) { openMarket(player, page, admin, category, null, query); }

    public void openMarket(Player player, int requestedPage, boolean admin, MarketCategory category, MarketSubcategory subcategory, String query) {
        List<Material> materials = filtered(category, subcategory, query, admin);
        int pages = Math.max(1, (int)Math.ceil(materials.size() / (double)ITEMS_PER_PAGE));
        int page = Math.max(0, Math.min(pages - 1, requestedPage));
        String section = subcategory != null ? subcategory.display() : category.display();
        String suffix = query == null || query.isBlank() ? section : "Search: " + query;
        Inventory inv = Bukkit.createInventory(new MarketHolder(page, admin, category, subcategory, query == null ? "" : query), 54, Text.color("&8" + (admin ? "Incog-Shop Admin" : "Incog-Shop") + " &7• &f" + suffix));
        ItemStack filler = named(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 36; i < 54; i++) inv.setItem(i, filler);
        int start = page * ITEMS_PER_PAGE;
        int[] itemSlots = itemLayoutSlots();
        for (int logical = 0; logical < ITEMS_PER_PAGE && start + logical < materials.size(); logical++) {
            inv.setItem(itemSlots[logical], marketIcon(materials.get(start + logical), admin));
        }

        inv.setItem(plugin.layouts().slot("items","previous",45), named(Material.ARROW, "&ePrevious Page", page > 0 ? List.of("&7Go to page &f" + page) : List.of("&8Already on the first page.")));
        inv.setItem(plugin.layouts().slot("items","back",46), named(Material.CHEST, subcategory == null ? "&6Categories" : "&6Subcategories", List.of(subcategory == null ? "&7Return to category selection." : "&7Return to " + category.display() + ".")));
        inv.setItem(plugin.layouts().slot("items","search",47), named(Material.COMPASS, "&bSearch", List.of("&7Current: &f" + ((query == null || query.isBlank()) ? "None" : query), "", "&eClick, then type in chat")));
        if (query != null && !query.isBlank()) inv.setItem(plugin.layouts().slot("items","clear",48), named(Material.BARRIER, "&cClear Search", List.of("&7Return to the current section.")));
        inv.setItem(plugin.layouts().slot("items","status",49), named(admin ? Material.COMMAND_BLOCK : Material.GOLD_INGOT, admin ? "&cAdmin Controls" : "&6Balance: &f" + plugin.money(plugin.wallets().get(player.getUniqueId())), admin ? List.of("&aLeft &7+64 stock", "&cRight &7-64 stock", "&aShift-left &7+10% base price", "&cShift-right &7-10% base price", "&eMiddle &7reset price/pressure", "&bF &7cycle Buy+Sell / Sell Only / Disabled", "&dQ &7change category") : List.of("&aLeft-click &7Buy 1", "&aShift-left &7Buy a stack", "&cRight-click &7Sell 1", "&cShift-right &7Sell a stack", "&bF / Middle &7Order Book")));
        if (admin) inv.setItem(plugin.layouts().slot("items","orders",50), infiniteStockButton());
        else inv.setItem(plugin.layouts().slot("items","orders",50), named(Material.WRITABLE_BOOK, "&bAll Market Orders",
                List.of("&7Browse all active Buy/Sell Orders.", "", "&eClick to browse")));
        inv.setItem(plugin.layouts().slot("items","section",51), named(category.icon(), "&6" + section, List.of("&7Current market section.")));
        inv.setItem(plugin.layouts().slot("items","page",52), named(Material.PAPER, "&fPage &e" + (page + 1) + "&7/&e" + pages, List.of("&7Items shown: &f" + materials.size())));
        inv.setItem(plugin.layouts().slot("items","next",53), named(Material.ARROW, "&eNext Page", page + 1 < pages ? List.of("&7Go to page &f" + (page + 2)) : List.of("&8Already on the last page.")));
        player.openInventory(inv);
    }


    public void openCustomCategory(Player player, String categoryId, int requestedPage, boolean admin) {
        CustomCategory category = plugin.customCategories().get(categoryId);
        if (category == null) { openCategories(player, admin); return; }
        List<Material> materials = plugin.customCategories().materials(category.id()).stream()
                .filter(m -> admin || plugin.market().isMarketEnabled(m))
                .toList();
        int pages = Math.max(1, (int)Math.ceil(materials.size() / (double)ITEMS_PER_PAGE));
        int page = Math.max(0, Math.min(pages - 1, requestedPage));
        Inventory inv = Bukkit.createInventory(new CustomMarketHolder(category.id(), page, admin), 54,
                Text.color("&8" + (admin ? "Incog-Shop Admin" : "Incog-Shop") + " &7• &d" + category.display()));
        ItemStack filler = named(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i=36;i<54;i++) inv.setItem(i,filler);
        int start = page * ITEMS_PER_PAGE;
        for (int slot=0; slot<ITEMS_PER_PAGE && start+slot<materials.size(); slot++) inv.setItem(slot, marketIcon(materials.get(start+slot),admin));
        inv.setItem(45,named(Material.ARROW,"&ePrevious Page",List.of()));
        inv.setItem(46,named(Material.CHEST,"&6Categories",List.of("&7Return to category selection.")));
        inv.setItem(49,named(category.icon(),"&d"+category.display(),List.of("&7Custom category","&7Items: &f"+materials.size())));
        inv.setItem(52,named(Material.PAPER,"&fPage &e"+(page+1)+"&7/&e"+pages,List.of()));
        inv.setItem(53,named(Material.ARROW,"&eNext Page",List.of()));
        player.openInventory(inv);
    }

    private ItemStack infiniteStockButton() {
        boolean enabled = plugin.market().isInfiniteStockEnabled();
        return named(enabled ? Material.LIME_DYE : Material.GRAY_DYE,
                enabled ? "&aInfinite Stock: ENABLED" : "&cInfinite Stock: DISABLED",
                enabled
                        ? List.of("&7Players can buy items even when", "&7stored market stock is 0.", "&7Purchases do not reduce stock.", "&7Selling still adds to stock.", "", "&eClick to disable infinite stock.")
                        : List.of("&7Purchases require actual stock", "&7and reduce stored supply normally.", "", "&eClick to enable infinite stock."));
    }

    public List<Material> filtered(MarketCategory category, String query) { return filtered(category, null, query, false); }
    public List<Material> filtered(MarketCategory category, String query, boolean includeDisabled) { return filtered(category, null, query, includeDisabled); }

    public List<Material> filtered(MarketCategory category, MarketSubcategory subcategory, String query, boolean includeDisabled) {
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT).trim().replace(' ', '_');
        return plugin.market().tradableMaterials().stream()
                .filter(m -> includeDisabled || plugin.market().isMarketEnabled(m))
                .filter(m -> category == MarketCategory.ALL || plugin.customCategories().assigned(m) == null)
                .filter(m -> category == MarketCategory.ALL || plugin.market().categoryOf(m) == category)
                .filter(m -> subcategory == null || plugin.market().subcategoryOf(m) == subcategory)
                .filter(m -> q.isBlank() || m.name().toLowerCase(Locale.ROOT).contains(q))
                .toList();
    }

    public void openPlacementCategories(Player player, Material material) {
        Inventory inv = Bukkit.createInventory(new PlacementCategoryHolder(material), 54, Text.color("&8Organize Item &7• &fCategory"));
        decorate(inv, Material.PURPLE_STAINED_GLASS_PANE);
        inv.setItem(4, marketIcon(material, true));
        List<MarketCategory> categories = java.util.Arrays.stream(MarketCategory.values()).filter(c -> c != MarketCategory.ALL).toList();
        int[] categorySlots = centeredSlots(categories.size());
        for (int i = 0; i < categories.size() && i < categorySlots.length; i++) {
            MarketCategory c = categories.get(i);
            boolean current = plugin.market().categoryOf(material) == c;
            inv.setItem(categorySlots[i], named(c.icon(), (current ? "&a" : "&6") + c.display(), List.of(current ? "&aCurrent category" : "&7Move item here", "", "&eClick to continue")));
        }
        inv.setItem(40, named(Material.RECOVERY_COMPASS, "&bReset Automatic Placement", List.of("&7Remove your custom category choice", "&7and use Incog-Shop's automatic grouping.", "", "&eClick to reset")));
        inv.setItem(45, named(Material.ARROW, "&eCancel", List.of("&7Return to the admin market.")));
        player.openInventory(inv);
    }

    public void openPlacementSubcategories(Player player, Material material, MarketCategory category) {
        Inventory inv = Bukkit.createInventory(new PlacementSubcategoryHolder(material, category), 54, Text.color("&8Organize Item &7• &f" + category.display()));
        decorate(inv, Material.PURPLE_STAINED_GLASS_PANE);
        inv.setItem(4, marketIcon(material, true));
        List<MarketSubcategory> subs = MarketSubcategory.forCategory(category);
        int[] subcategorySlots = subcategoryLayoutSlots(subs.size());
        for (int i = 0; i < subs.size() && i < subcategorySlots.length; i++) {
            MarketSubcategory sub = subs.get(i);
            boolean current = plugin.market().categoryOf(material) == category && plugin.market().subcategoryOf(material) == sub;
            inv.setItem(subcategorySlots[i], named(sub.icon(), (current ? "&a" : "&e") + sub.display(), List.of(current ? "&aCurrent subcategory" : "&7Place item here", "", "&eClick to save")));
        }
        inv.setItem(45, named(Material.ARROW, "&eBack", List.of("&7Choose a different category.")));
        player.openInventory(inv);
    }

    private ItemStack marketIcon(Material material, boolean admin) {
        ItemStack icon = new ItemStack(material);
        ItemMeta meta = icon.getItemMeta();
        MarketEntry e = plugin.market().entry(material);
        meta.setDisplayName(Text.color("&f" + Text.prettyEnum(material.name())));
        List<String> lore = new ArrayList<>();
        lore.add(Text.color("&8────────────────"));
        lore.add(Text.color("&7Category: &f" + plugin.market().categoryOf(material).display()));
        lore.add(Text.color("&7Section: &f" + plugin.market().subcategoryOf(material).display()));
        lore.add("");
        lore.add(Text.color("&7Server Stock: &f" + e.stock()));
        MarketMode mode = plugin.market().marketMode(material);
        lore.add(Text.color("&7Market Mode: " + (mode == MarketMode.BUY_SELL ? "&aBuy & Sell" : mode == MarketMode.SELL_ONLY ? "&eSell Only" : "&cDisabled")));
        lore.add(Text.color((plugin.market().isBuyAllowed(material) ? "&aBuy: &f" + plugin.money(plugin.market().buyUnitPrice(material)) : "&cBuy: &8Unavailable")));
        lore.add(Text.color("&cSell: &f" + plugin.money(plugin.market().sellUnitPrice(material))));
        if (admin) {
            lore.add(Text.color("&7Base: &f" + plugin.money(e.basePrice())));
            lore.add(Text.color("&7Status: " + (plugin.market().isMarketEnabled(material) ? "&aEnabled" : "&cRemoved")));
            lore.add("");
            lore.add(Text.color("&aLeft &7+64  &cRight &7-64 stock"));
            lore.add(Text.color("&aShift-left &7+10%  &cShift-right &7-10%"));
            lore.add(Text.color("&eMiddle &7Reset price  &bF &7Toggle market"));
            lore.add(Text.color("&dQ / Drop &7Change category"));
        } else {
            lore.add("");
            lore.add(Text.color("&aLeft-click &7Buy 1   &aShift-left &7Buy stack"));
            lore.add(Text.color("&cRight-click &7Sell 1  &cShift-right &7Sell stack"));
            lore.add(Text.color("&bF / Middle-click &7Open Buy/Sell Order Book"));
            double bestBuyOrder = plugin.orders().bestBuy(material);
            double bestSellOrder = plugin.orders().bestSell(material);
            if (bestBuyOrder > 0 || bestSellOrder > 0) {
                lore.add("");
                lore.add(Text.color("&7Best order bid: " + (bestBuyOrder > 0 ? "&a" + plugin.money(bestBuyOrder) : "&8None")));
                lore.add(Text.color("&7Best order ask: " + (bestSellOrder > 0 ? "&c" + plugin.money(bestSellOrder) : "&8None")));
            }
            lore.add("");
            lore.add(Text.color("&8Selling instantly adds items to server stock."));
        }
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.values());
        icon.setItemMeta(meta);
        return icon;
    }

    public void openPlayerShop(Player player, PlayerShop shop) {
        Inventory inv = Bukkit.createInventory(new PlayerShopHolder(shop.id()), 45, Text.color("&8Player Shop &7• &f" + shop.ownerName()));
        decorate(inv, Material.BLACK_STAINED_GLASS_PANE);
        ItemStack display = shop.item();
        ItemMeta meta = display.getItemMeta();
        List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add(""); lore.add(Text.color("&7Price each: &f" + plugin.money(shop.price())));
        lore.add(Text.color("&7Stock: &f" + plugin.shops().stock(shop)));
        lore.add(Text.color("&7Seller: &f" + shop.ownerName()));
        meta.setLore(lore); display.setItemMeta(meta);
        inv.setItem(13, display);
        inv.setItem(29, named(Material.LIME_CONCRETE, "&aBuy 1", List.of("&7Cost: &f" + plugin.money(shop.price()))));
        int stack = Math.max(1, shop.item().getMaxStackSize());
        inv.setItem(31, named(Material.LIME_CONCRETE, "&aBuy up to " + stack, List.of("&7Limited by stock, balance,", "&7and inventory space.")));
        inv.setItem(33, named(Material.BOOK, "&eShop Information", List.of("&7Owner: &f" + shop.ownerName(), "&7Stock: &f" + plugin.shops().stock(shop), "&7Price: &f" + plugin.money(shop.price()), "", "&7Owner restock:", "&fSneak-right-click the container", "&fand place matching items inside.")));
        player.openInventory(inv);
    }

    private static void decorate(Inventory inv, Material material) {
        ItemStack filler = named(material, " ", List.of());
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
    }

    public static ItemStack named(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(Text.color(name));
        meta.setLore(lore.stream().map(Text::color).toList());
        meta.addItemFlags(ItemFlag.values());
        stack.setItemMeta(meta);
        return stack;
    }
}

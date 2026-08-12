package com.snipeyfresh.incogshop.gui;

import com.snipeyfresh.incogshop.IncogShopPlugin;
import com.snipeyfresh.incogshop.market.MarketEntry;
import com.snipeyfresh.incogshop.market.MarketMode;
import com.snipeyfresh.incogshop.custom.CustomCategoryManager.CustomCategory;
import com.snipeyfresh.incogshop.custom.CustomCategoryManager.CustomSubcategory;
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

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ShopGui {
    public static final int ITEMS_PER_PAGE = 36;
    public static int[] centeredSlots(int count) {
        if (count <= 0) return new int[0];

        int[] slots = new int[count];
        int rows = Math.max(1, (int) Math.ceil(count / 7.0));
        int basePerRow = count / rows;
        int extra = count % rows;
        int written = 0;

        for (int rowIndex = 0; rowIndex < rows; rowIndex++) {
            int inRow = basePerRow + (rowIndex < extra ? 1 : 0);
            int inventoryRow = rowIndex + 1;
            int[] columns = centeredColumns(inRow);
            for (int column : columns) {
                if (written >= count) break;
                slots[written++] = inventoryRow * 9 + column;
            }
        }
        return slots;
    }

    private static int[] centeredColumns(int count) {
        return switch (count) {
            case 1 -> new int[]{4};
            case 2 -> new int[]{3, 5};
            case 3 -> new int[]{3, 4, 5};
            case 4 -> new int[]{2, 3, 5, 6};
            case 5 -> new int[]{2, 3, 4, 5, 6};
            case 6 -> new int[]{1, 2, 3, 5, 6, 7};
            default -> new int[]{1, 2, 3, 4, 5, 6, 7};
        };
    }
    public int[] subcategoryLayoutSlots(int count) {
        int[] out = new int[count];
        int[] defaults = centeredSlots(count);
        for (int i = 0; i < count; i++) {
            int fallback = i < defaults.length ? defaults[i] : 10 + i;
            out[i] = plugin.layouts().slot("subcategories", "sub:" + i, fallback);
        }
        return out;
    }

    public java.util.Map<String,Integer> itemControlSlots() {
        java.util.LinkedHashMap<String,Integer> out = new java.util.LinkedHashMap<>();
        String[] keys = {"previous","back","search","clear","status","orders","section","page","next"};
        int[] defaults = {45,46,47,48,49,50,51,52,53};
        java.util.Set<Integer> used = new java.util.HashSet<>();

        for (int i=0;i<keys.length;i++) {
            int desired = plugin.layouts().slot("items", keys[i], defaults[i]);

            if (used.contains(desired)) {
                // Prefer this control's normal bottom-nav position first.
                if (!used.contains(defaults[i])) {
                    desired = defaults[i];
                } else {
                    // If that is also occupied, keep navigation controls in
                    // the bottom row instead of pushing them into slot 0.
                    desired = -1;
                    for (int candidate=45; candidate<=53; candidate++) {
                        if (!used.contains(candidate)) {
                            desired = candidate;
                            break;
                        }
                    }

                    // Only fall back outside the navbar if every bottom slot
                    // is genuinely occupied by distinct configured controls.
                    if (desired < 0) {
                        for (int candidate=0; candidate<45; candidate++) {
                            if (!used.contains(candidate)) {
                                desired = candidate;
                                break;
                            }
                        }
                    }
                }
            }

            used.add(desired);
            out.put(keys[i], desired);
        }
        return out;
    }

    public int[] itemLayoutSlots() {
        java.util.Set<Integer> reserved = new java.util.HashSet<>(itemControlSlots().values());
        int[] out = new int[ITEMS_PER_PAGE];
        java.util.Set<Integer> used = new java.util.HashSet<>();
        for (int i=0;i<ITEMS_PER_PAGE;i++) {
            int desired = plugin.layouts().slot("items","item:"+i,i);
            if (reserved.contains(desired) || used.contains(desired)) {
                desired = -1;
                for (int candidate=0;candidate<54;candidate++) {
                    if (!reserved.contains(candidate) && !used.contains(candidate)) { desired=candidate; break; }
                }
            }
            out[i]=desired;
            used.add(desired);
        }
        return out;
    }
    private final IncogShopPlugin plugin;

    public ShopGui(IncogShopPlugin plugin) { this.plugin = plugin; }

    public record CategoryHolder(boolean admin) implements InventoryHolder { @Override public Inventory getInventory() { return null; } }
    public record SubcategoryHolder(boolean admin, MarketCategory category) implements InventoryHolder { @Override public Inventory getInventory() { return null; } }
    public record MarketHolder(int page, boolean admin, MarketCategory category, MarketSubcategory subcategory, String query) implements InventoryHolder { @Override public Inventory getInventory() { return null; } }
    public record CustomSubcategoryHolder(String categoryId, boolean admin) implements InventoryHolder { @Override public Inventory getInventory() { return null; } }
    public record CustomMarketHolder(String categoryId, String subcategoryId, int page, boolean admin) implements InventoryHolder { @Override public Inventory getInventory() { return null; } }
    public record PlacementCategoryHolder(Material material) implements InventoryHolder { @Override public Inventory getInventory() { return null; } }
    public record PlacementSubcategoryHolder(Material material, MarketCategory category) implements InventoryHolder { @Override public Inventory getInventory() { return null; } }
    public record PlayerShopHolder(UUID shopId) implements InventoryHolder { @Override public Inventory getInventory() { return null; } }

    public void openCategories(Player player, boolean admin) {
        Inventory inv = Bukkit.createInventory(new CategoryHolder(admin), 54,
                Text.color(admin ? "&8Incog-Shop &7• &dAdmin Browse" : "&8Incog-Shop &7• &6Market"));
        fancyFrame(inv, admin ? Material.PURPLE_STAINED_GLASS_PANE : Material.YELLOW_STAINED_GLASS_PANE);

        List<String> keys = new ArrayList<>();
        List<Material> icons = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<List<String>> lores = new ArrayList<>();

        for (MarketCategory c : MarketCategory.values()) {
            long count = filtered(c, null, "", admin).size();
            keys.add("builtin:" + c.name());
            icons.add(c.icon());
            names.add("&6" + c.display());
            lores.add(List.of("&7" + count + " market items",
                    c == MarketCategory.ALL ? "&8Browse the entire market." : "&8Open its subcategories.",
                    "", "&eClick to browse"));
        }
        for (CustomCategory c : plugin.customCategories().all()) {
            long count = plugin.customCategories().materials(c.id()).stream()
                    .filter(m -> admin || plugin.market().isMarketEnabled(m)).count();
            keys.add("custom:" + c.id());
            icons.add(c.icon());
            names.add("&d" + c.display());
            lores.add(List.of("&7" + count + " market items", "&8Custom category", "", "&eClick to browse"));
        }

        int[] defaults = centeredSlots(keys.size());
        for (int i = 0; i < keys.size(); i++) {
            int fallback = i < defaults.length ? defaults[i] : 10 + i;
            int slot = plugin.layouts().slot("categories", keys.get(i), fallback);
            inv.setItem(slot, named(icons.get(i), names.get(i), lores.get(i)));
        }

        int searchSlot = plugin.layouts().slot("categories", "search", 46);
        int ordersSlot = plugin.layouts().slot("categories", "orders", 48);
        int helpSlot = plugin.layouts().slot("categories", "help", 52);
        int balanceSlot = plugin.layouts().slot("categories", "balance", 50);
        inv.setItem(searchSlot, named(Material.COMPASS, "&b&lSEARCH", List.of(
                "&7Smart matching supports aliases,",
                "&7partial names, and minor typos.", "", "&eClick to search")));

        if (!admin) {
            inv.setItem(ordersSlot, named(Material.WRITABLE_BOOK, "&bMarket Orders", List.of(
                    "&7Browse all active Buy and Sell Orders.", "", "&eClick to browse")));
            inv.setItem(balanceSlot, named(Material.GOLD_INGOT, "&6Balance: &f" + plugin.money(plugin.wallets().get(player.getUniqueId())),
                    List.of("&7Buy and sell directly from the market.")));
            inv.setItem(helpSlot, named(Material.BOOK, "&eMarket Guide", List.of(
                    "&7Stock comes from player sales.",
                    "&7Prices react to supply and demand.", "", "&eClick categories above to begin")));
        } else {
            inv.setItem(ordersSlot, named(Material.NETHER_STAR, "&d&lADMIN STUDIO", List.of(
                    "&7Create/remove categories, organize items,",
                    "&7add materials, and edit the GUI.", "", "&eClick to open")));
            inv.setItem(balanceSlot, infiniteStockButton());
            inv.setItem(helpSlot, named(Material.ARMOR_STAND, "&5Layout Designer", List.of(
                    "&7Move buttons and item slots visually.", "", "&eClick to customize")));
        }
        fillEmpty(inv, Material.GRAY_STAINED_GLASS_PANE);
        player.openInventory(inv);
    }

    public void openSubcategories(Player player, boolean admin, MarketCategory category) {
        if (category == MarketCategory.ALL) { openMarket(player, 0, admin, MarketCategory.ALL, null, ""); return; }
        Inventory inv = Bukkit.createInventory(new SubcategoryHolder(admin, category), 54, Text.color("&8" + (admin ? "Incog-Shop Admin" : "Incog-Shop") + " &7• &f" + category.display()));
        fancyFrame(inv, admin ? Material.PURPLE_STAINED_GLASS_PANE : Material.ORANGE_STAINED_GLASS_PANE);
        inv.setItem(4, named(category.icon(), "&6&l" + category.display(), List.of("&7Choose a section below.", "&8All buttons are centered for quick scanning.")));
        List<MarketSubcategory> subs = MarketSubcategory.forCategory(category);
        int[] subcategorySlots = subcategoryLayoutSlots(subs.size());
        for (int i = 0; i < subs.size() && i < subcategorySlots.length; i++) {
            MarketSubcategory sub = subs.get(i);
            long count = filtered(category, sub, "", admin).size();
            inv.setItem(subcategorySlots[i], named(sub.icon(), "&e" + sub.display(), List.of("&7" + count + " items", "", "&eClick to browse")));
        }
        inv.setItem(plugin.layouts().slot("subcategories","all",47), named(Material.CHEST, "&6All " + category.display(), List.of("&7Show every item in this category.", "", "&eClick to browse")));
        inv.setItem(plugin.layouts().slot("subcategories","search",49), named(Material.COMPASS, "&bSearch " + category.display(), List.of("&7Smart search within this category.", "&7Typos and partial names are supported.", "", "&eClick to search")));
        inv.setItem(plugin.layouts().slot("subcategories","back",45), named(Material.ARROW, "&eBack", List.of("&7Return to categories.")));
        inv.setItem(plugin.layouts().slot("subcategories","status",51), named(admin ? Material.COMMAND_BLOCK : Material.GOLD_INGOT, admin ? "&cAdmin Mode" : "&6Balance: &f" + plugin.money(plugin.wallets().get(player.getUniqueId())), admin ? List.of("&7Category management is available", "&7from the item list using Q.") : List.of("&7Select a subcategory to continue.")));
        if (admin) inv.setItem(50, infiniteStockButton());
        fillEmpty(inv, Material.GRAY_STAINED_GLASS_PANE);
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

        inv.setItem(itemControlSlots().get("previous"), named(Material.ARROW, "&ePrevious Page", page > 0 ? List.of("&7Go to page &f" + page) : List.of("&8Already on the first page.")));
        inv.setItem(itemControlSlots().get("back"), named(Material.CHEST, category == MarketCategory.ALL ? "&6Categories" : "&6Subcategories", List.of(category == MarketCategory.ALL ? "&7Return to category selection." : "&7Return to " + category.display() + " subcategories.")));
        inv.setItem(itemControlSlots().get("search"), named(Material.COMPASS, "&bSearch", List.of("&7Current: &f" + ((query == null || query.isBlank()) ? "None" : query), "", "&eClick to search on a sign")));
        if (query != null && !query.isBlank()) inv.setItem(itemControlSlots().get("clear"), named(Material.BARRIER, "&cClear Search", List.of("&7Return to the current section.")));
        inv.setItem(itemControlSlots().get("status"), named(admin ? Material.COMMAND_BLOCK : Material.GOLD_INGOT, admin ? "&cAdmin Controls" : "&6Balance: &f" + plugin.money(plugin.wallets().get(player.getUniqueId())), admin ? List.of("&aLeft &7+64 stock", "&cRight &7-64 stock", "&aShift-left &7+10% base price", "&cShift-right &7-10% base price", "&eMiddle &7reset price/pressure", "&bF &7cycle Buy+Sell / Sell Only / Disabled", "&dQ &7change category") : List.of("&aLeft-click &7Buy 1", "&aShift-left &7Buy a stack", "&cRight-click &7Sell 1", "&cShift-right &7Sell a stack", "&bF / Middle &7Order Book")));
        if (admin) inv.setItem(itemControlSlots().get("orders"), infiniteStockButton());
        else inv.setItem(itemControlSlots().get("orders"), named(Material.WRITABLE_BOOK, "&bAll Market Orders",
                List.of("&7Browse all active Buy/Sell Orders.", "", "&eClick to browse")));
        inv.setItem(itemControlSlots().get("section"), named(category.icon(), "&6" + section, List.of("&7Current market section.")));
        inv.setItem(itemControlSlots().get("page"), named(Material.PAPER, "&fPage &e" + (page + 1) + "&7/&e" + pages, List.of("&7Items shown: &f" + materials.size())));
        inv.setItem(itemControlSlots().get("next"), named(Material.ARROW, "&eNext Page", page + 1 < pages ? List.of("&7Go to page &f" + (page + 2)) : List.of("&8Already on the last page.")));
        player.openInventory(inv);
    }


    public void openCustomCategory(Player player, String categoryId, int requestedPage, boolean admin) {
        CustomCategory category = plugin.customCategories().get(categoryId);
        if (category == null) { openCategories(player, admin); return; }
        List<CustomSubcategory> subs = plugin.customCategories().subcategories(category.id());
        if (!subs.isEmpty()) {
            openCustomSubcategories(player, category.id(), admin);
            return;
        }
        openCustomMarket(player, category.id(), "", requestedPage, admin);
    }

    public void openCustomSubcategories(Player player, String categoryId, boolean admin) {
        CustomCategory category = plugin.customCategories().get(categoryId);
        if (category == null) { openCategories(player, admin); return; }
        Inventory inv = Bukkit.createInventory(new CustomSubcategoryHolder(category.id(), admin), 54,
                Text.color("&8" + (admin ? "Incog-Shop Admin" : "Incog-Shop") + " &7• &d" + category.display()));
        decorate(inv, Material.GRAY_STAINED_GLASS_PANE);
        inv.setItem(4, named(category.icon(), "&d" + category.display(), List.of("&7Choose a custom subcategory.")));
        List<CustomSubcategory> subs = plugin.customCategories().subcategories(category.id());
        int[] slots = centeredSlots(subs.size());
        for (int i=0;i<subs.size()&&i<slots.length;i++) {
            CustomSubcategory sub=subs.get(i);
            long count=plugin.customCategories().materials(category.id(),sub.id()).stream()
                    .filter(m->admin||plugin.market().isMarketEnabled(m)).count();
            inv.setItem(slots[i],named(sub.icon(),"&e"+sub.display(),List.of("&7"+count+" items","","&eClick to browse")));
        }
        inv.setItem(39,named(Material.CHEST,"&6All "+category.display(),List.of("&7Show all items in this custom category.","","&eClick")));
        inv.setItem(45,named(Material.ARROW,"&eBack",List.of("&7Return to categories.")));
        player.openInventory(inv);
    }

    public void openCustomMarket(Player player, String categoryId, String subcategoryId, int requestedPage, boolean admin) {
        CustomCategory category = plugin.customCategories().get(categoryId);
        if (category == null) { openCategories(player, admin); return; }
        boolean specific=subcategoryId!=null&&!subcategoryId.isBlank();
        CustomSubcategory sub=specific?plugin.customCategories().subcategory(category.id(),subcategoryId):null;
        List<Material> materials=(specific&&sub!=null
                ? plugin.customCategories().materials(category.id(),sub.id())
                : plugin.customCategories().materials(category.id())).stream()
                .filter(m->admin||plugin.market().isMarketEnabled(m)).toList();
        int pages=Math.max(1,(int)Math.ceil(materials.size()/(double)ITEMS_PER_PAGE));
        int page=Math.max(0,Math.min(pages-1,requestedPage));
        String title=sub!=null?sub.display():category.display();
        Inventory inv=Bukkit.createInventory(new CustomMarketHolder(category.id(),sub==null?"":sub.id(),page,admin),54,
                Text.color("&8"+(admin?"Incog-Shop Admin":"Incog-Shop")+" &7• &d"+title));
        ItemStack filler=named(Material.BLACK_STAINED_GLASS_PANE," ",List.of());
        for(int i=36;i<54;i++)inv.setItem(i,filler);
        int start=page*ITEMS_PER_PAGE;
        for(int slot=0;slot<ITEMS_PER_PAGE&&start+slot<materials.size();slot++)inv.setItem(slot,marketIcon(materials.get(start+slot),admin));
        inv.setItem(45,named(Material.ARROW,"&ePrevious Page",List.of()));
        inv.setItem(46,named(Material.CHEST,specific?"&6Subcategories":"&6Categories",List.of("&7Go back.")));
        inv.setItem(49,named(sub!=null?sub.icon():category.icon(),"&d"+title,List.of("&7Items: &f"+materials.size())));
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

    private static final Map<String, List<String>> SEARCH_ALIASES = Map.ofEntries(
            Map.entry("gap", List.of("golden apple")),
            Map.entry("gapple", List.of("golden apple")),
            Map.entry("god apple", List.of("enchanted golden apple")),
            Map.entry("notch apple", List.of("enchanted golden apple")),
            Map.entry("xp bottle", List.of("experience bottle")),
            Map.entry("exp bottle", List.of("experience bottle")),
            Map.entry("bottle o enchanting", List.of("experience bottle")),
            Map.entry("rocket", List.of("firework rocket")),
            Map.entry("firework", List.of("firework rocket")),
            Map.entry("totem", List.of("totem of undying")),
            Map.entry("ender pearl", List.of("ender pearl")),
            Map.entry("pearl", List.of("ender pearl")),
            Map.entry("elytra", List.of("elytra")),
            Map.entry("netherite sword", List.of("netherite sword")),
            Map.entry("netherite pick", List.of("netherite pickaxe")),
            Map.entry("diamond pick", List.of("diamond pickaxe")),
            Map.entry("iron pick", List.of("iron pickaxe")),
            Map.entry("gold pick", List.of("golden pickaxe")),
            Map.entry("wood pick", List.of("wooden pickaxe")),
            Map.entry("redstone dust", List.of("redstone")),
            Map.entry("lapis", List.of("lapis lazuli")),
            Map.entry("cobble", List.of("cobblestone")),
            Map.entry("logs", List.of("log")),
            Map.entry("planks", List.of("planks")),
            Map.entry("wool", List.of("wool"))
    );

    public List<Material> filtered(MarketCategory category, MarketSubcategory subcategory, String query, boolean includeDisabled) {
        List<Material> candidates = plugin.market().tradableMaterials().stream()
                .filter(m -> includeDisabled || plugin.market().isMarketEnabled(m))
                .filter(m -> category == MarketCategory.ALL || plugin.customCategories().assigned(m) == null)
                .filter(m -> category == MarketCategory.ALL || plugin.market().categoryOf(m) == category)
                .filter(m -> subcategory == null || plugin.market().subcategoryOf(m) == subcategory)
                .toList();

        String q = normalizeSearch(query);
        if (q.isBlank()) return candidates;

        return candidates.stream()
                .map(m -> new AbstractMap.SimpleEntry<>(m, searchScore(m, q)))
                .filter(e -> e.getValue() < Integer.MAX_VALUE)
                .sorted(Comparator
                        .comparingInt((AbstractMap.SimpleEntry<Material, Integer> e) -> e.getValue())
                        .thenComparing(e -> e.getKey().name()))
                .map(AbstractMap.SimpleEntry::getKey)
                .toList();
    }

    private int searchScore(Material material, String query) {
        String pretty = normalizeSearch(Text.prettyEnum(material.name()));
        String raw = normalizeSearch(material.name());

        // Exact and nearly-exact matches always win.
        if (pretty.equals(query) || raw.equals(query)) return 0;
        if (pretty.startsWith(query) || raw.startsWith(query)) return 10;
        if (containsWholeWordSequence(pretty, query)) return 20;

        // Alias resolution. An alias can point to one or more normal search phrases.
        for (var alias : SEARCH_ALIASES.entrySet()) {
            String aliasKey = normalizeSearch(alias.getKey());
            if (!aliasKey.equals(query) && !aliasKey.startsWith(query) && !query.startsWith(aliasKey)) continue;
            for (String target : alias.getValue()) {
                String normalizedTarget = normalizeSearch(target);
                if (pretty.equals(normalizedTarget)) return 5;
                if (pretty.contains(normalizedTarget) || raw.contains(normalizedTarget)) return 15;
            }
        }

        // Multi-word searches work in any useful partial form:
        // "red wo" -> Red Wool, "diamond sw" -> Diamond Sword.
        String[] terms = query.split(" ");
        boolean everyTermMatches = true;
        int partialPenalty = 0;
        for (String term : terms) {
            if (term.isBlank()) continue;
            if (pretty.contains(term) || raw.contains(term)) partialPenalty += 2;
            else {
                boolean fuzzyWord = false;
                for (String word : pretty.split(" ")) {
                    if (editDistance(term, word) <= allowedTypos(term.length())) {
                        fuzzyWord = true;
                        partialPenalty += 10;
                        break;
                    }
                }
                if (!fuzzyWord) {
                    everyTermMatches = false;
                    break;
                }
            }
        }
        if (everyTermMatches) return 30 + partialPenalty;

        if (pretty.contains(query) || raw.contains(query)) return 40;

        // Whole-name typo tolerance. Conservative enough that random searches
        // do not flood the results with unrelated materials.
        int distance = editDistance(query, pretty);
        int allowed = Math.max(1, Math.min(3, query.length() / 4));
        if (distance <= allowed) return 60 + distance * 5;

        return Integer.MAX_VALUE;
    }

    private static String normalizeSearch(String input) {
        if (input == null) return "";
        return input.toLowerCase(Locale.ROOT)
                .replace('_', ' ')
                .replace('-', ' ')
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static boolean containsWholeWordSequence(String text, String query) {
        return (" " + text + " ").contains(" " + query + " ");
    }

    private static int allowedTypos(int length) {
        if (length <= 3) return 0;
        if (length <= 6) return 1;
        return 2;
    }

    private static int editDistance(String a, String b) {
        if (a.equals(b)) return 0;
        if (a.isEmpty()) return b.length();
        if (b.isEmpty()) return a.length();

        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) previous[j] = j;

        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(
                        Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost
                );
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
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

    private static void fillEmpty(Inventory inv, Material material) {
        ItemStack filler = named(material, " ", List.of());
        for (int i = 0; i < inv.getSize(); i++) if (inv.getItem(i) == null) inv.setItem(i, filler);
    }

    private static void fancyFrame(Inventory inv, Material accent) {
        ItemStack trim = named(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        ItemStack accentPane = named(accent, " ", List.of());
        int rows = inv.getSize() / 9;
        for (int slot = 0; slot < inv.getSize(); slot++) {
            int row = slot / 9, col = slot % 9;
            if (row == 0 || row == rows - 1 || col == 0 || col == 8) inv.setItem(slot, trim);
        }
        if (inv.getSize() >= 45) {
            inv.setItem(1, accentPane); inv.setItem(7, accentPane);
            inv.setItem(inv.getSize() - 8, accentPane); inv.setItem(inv.getSize() - 2, accentPane);
        }
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

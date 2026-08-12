package com.snipeyfresh.incogshop.gui;

import com.snipeyfresh.incogshop.IncogShopPlugin;
import com.snipeyfresh.incogshop.custom.CustomCategoryManager.CustomCategory;
import com.snipeyfresh.incogshop.custom.CustomCategoryManager.CustomSubcategory;
import com.snipeyfresh.incogshop.market.MarketMode;
import com.snipeyfresh.incogshop.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class AdminSetupGui {
    private static final int[] GRID_28 = {
            10,11,12,13,14,15,16,
            19,20,21,22,23,24,25,
            28,29,30,31,32,33,34,
            37,38,39,40,41,42,43
    };

    public record Holder(String screen, String categoryId, int page) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
    public record MoveCategoryHolder(Material material) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
    public record MoveCustomSubHolder(Material material, String categoryId) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
    public record ConfirmDeleteHolder(String type, String categoryId, String subcategoryId, int returnPage) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private final IncogShopPlugin plugin;
    public AdminSetupGui(IncogShopPlugin plugin) { this.plugin = plugin; }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(new Holder("menu", "", 0), 45,
                Text.color("&8Incog-Shop &7• &dAdmin Studio"));
        frame(inv, Material.PURPLE_STAINED_GLASS_PANE, Material.BLACK_STAINED_GLASS_PANE);

        inv.setItem(4, ShopGui.named(Material.NETHER_STAR, "&d&lMARKET CONTROL CENTER", List.of(
                "&7Everything you need to organize",
                "&7and maintain the server market.")));

        inv.setItem(11, ShopGui.named(Material.BOOKSHELF, "&dCategories & Sections", List.of(
                "&7Create, browse, assign, and remove",
                "&7custom categories and subcategories.", "", "&eClick to manage")));
        inv.setItem(13, ShopGui.named(Material.HOPPER, "&bOrganize Market Items", List.of(
                "&7Browse every market material and",
                "&7move it to a built-in or custom section.", "", "&eClick to organize")));
        ItemStack held = player.getInventory().getItemInMainHand();
        Material heldMat = held == null || held.getType().isAir() ? Material.ITEM_FRAME : held.getType();
        inv.setItem(15, ShopGui.named(heldMat, "&aAdd Held Item", List.of(
                "&7Held: &f" + (held == null || held.getType().isAir() ? "Nothing" : Text.prettyEnum(held.getType().name())),
                "&7Adds/enables it with the default",
                "&7price and &aBuy & Sell &7mode.", "", "&eClick to add")));

        inv.setItem(29, ShopGui.named(Material.ARMOR_STAND, "&5GUI Layout Designer", List.of(
                "&7Reposition categories, subcategories,",
                "&7item slots, and navigation controls.", "", "&eClick to customize")));
        inv.setItem(31, ShopGui.named(Material.COMMAND_BLOCK, "&6Open Admin Market", List.of(
                "&7Fine-tune stock, prices, modes,",
                "&7and individual market items.", "", "&eClick to open")));
        boolean infinite = plugin.market().isInfiniteStockEnabled();
        inv.setItem(33, ShopGui.named(infinite ? Material.LIME_DYE : Material.GRAY_DYE,
                infinite ? "&aInfinite Stock: ON" : "&7Infinite Stock: OFF", List.of(
                        "&7Global server-stock behavior.", "",
                        infinite ? "&eClick to disable" : "&eClick to enable")));

        inv.setItem(40, ShopGui.named(Material.BARRIER, "&cClose", List.of()));
        player.openInventory(inv);
    }

    public void openCategories(Player player, int requestedPage) {
        List<CustomCategory> categories = new ArrayList<>(plugin.customCategories().all());
        int perPage = GRID_28.length;
        int pages = Math.max(1, (categories.size() + perPage - 1) / perPage);
        int page = Math.max(0, Math.min(pages - 1, requestedPage));
        Inventory inv = Bukkit.createInventory(new Holder("categories", "", page), 54,
                Text.color("&8Admin Studio &7• &dCategories"));
        frame(inv, Material.PURPLE_STAINED_GLASS_PANE, Material.BLACK_STAINED_GLASS_PANE);
        inv.setItem(4, ShopGui.named(Material.BOOKSHELF, "&d&lCUSTOM CATEGORIES", List.of(
                "&7Left-click: &fManage subcategories",
                "&7Shift-click: &fAssign held item",
                "&7Right-click: &cRemove category")));

        int start = page * perPage;
        for (int i = 0; i < perPage && start + i < categories.size(); i++) {
            CustomCategory c = categories.get(start + i);
            inv.setItem(GRID_28[i], ShopGui.named(c.icon(), "&d" + c.display(), List.of(
                    "&7ID: &f" + c.id(),
                    "&7Subcategories: &f" + plugin.customCategories().subcategories(c.id()).size(),
                    "&7Items: &f" + plugin.customCategories().materials(c.id()).size(),
                    "", "&eLeft-click &7Manage sections",
                    "&bShift-click &7Assign held item",
                    "&cRight-click &7Delete")));
        }

        inv.setItem(45, ShopGui.named(Material.ARROW, "&eBack", List.of("&7Return to Admin Studio.")));
        if (page > 0) inv.setItem(47, ShopGui.named(Material.ARROW, "&ePrevious", List.of("&7Page " + page + " / " + pages)));
        inv.setItem(49, ShopGui.named(Material.LIME_CONCRETE, "&a&l+ CREATE CATEGORY", List.of(
                "&7Hold an item to use as the icon.",
                "&7You will name it on a sign.", "", "&eClick to create")));
        inv.setItem(51, ShopGui.named(Material.PAPER, "&fPage &d" + (page + 1) + "&7/&d" + pages,
                List.of("&7Custom categories: &f" + categories.size())));
        if (page < pages - 1) inv.setItem(53, ShopGui.named(Material.ARROW, "&eNext", List.of("&7Page " + (page + 2) + " / " + pages)));
        player.openInventory(inv);
    }

    public void openSubcategories(Player player, String categoryId, int requestedPage) {
        CustomCategory category = plugin.customCategories().get(categoryId);
        if (category == null) { openCategories(player, 0); return; }
        List<CustomSubcategory> subs = plugin.customCategories().subcategories(category.id());
        int perPage = GRID_28.length;
        int pages = Math.max(1, (subs.size() + perPage - 1) / perPage);
        int page = Math.max(0, Math.min(pages - 1, requestedPage));
        Inventory inv = Bukkit.createInventory(new Holder("subcategories", category.id(), page), 54,
                Text.color("&8Admin Studio &7• &d" + category.display()));
        frame(inv, Material.MAGENTA_STAINED_GLASS_PANE, Material.BLACK_STAINED_GLASS_PANE);
        inv.setItem(4, ShopGui.named(category.icon(), "&d&l" + category.display(), List.of(
                "&7Left-click a section to assign held item.",
                "&7Right-click a section to remove it.")));

        int start = page * perPage;
        for (int i = 0; i < perPage && start + i < subs.size(); i++) {
            CustomSubcategory sub = subs.get(start + i);
            inv.setItem(GRID_28[i], ShopGui.named(sub.icon(), "&e" + sub.display(), List.of(
                    "&7ID: &f" + sub.id(),
                    "&7Items: &f" + plugin.customCategories().materials(category.id(), sub.id()).size(),
                    "", "&eLeft-click &7Assign held item",
                    "&cRight-click &7Delete")));
        }

        inv.setItem(45, ShopGui.named(Material.ARROW, "&eBack to Categories", List.of()));
        if (page > 0) inv.setItem(47, ShopGui.named(Material.ARROW, "&ePrevious", List.of()));
        inv.setItem(49, ShopGui.named(Material.LIME_CONCRETE, "&a&l+ CREATE SUBCATEGORY", List.of(
                "&7Hold an item to use as the icon.",
                "&7You will name it on a sign.", "", "&eClick to create")));
        inv.setItem(51, ShopGui.named(Material.PAPER, "&fPage &d" + (page + 1) + "&7/&d" + pages,
                List.of("&7Sections: &f" + subs.size())));
        if (page < pages - 1) inv.setItem(53, ShopGui.named(Material.ARROW, "&eNext", List.of()));
        player.openInventory(inv);
    }

    public void openItems(Player player, int requestedPage) {
        List<Material> materials = plugin.market().tradableMaterials();
        int perPage = GRID_28.length;
        int pages = Math.max(1, (materials.size() + perPage - 1) / perPage);
        int page = Math.max(0, Math.min(pages - 1, requestedPage));
        Inventory inv = Bukkit.createInventory(new Holder("items", "", page), 54,
                Text.color("&8Admin Studio &7• &bItem Organizer"));
        frame(inv, Material.CYAN_STAINED_GLASS_PANE, Material.BLACK_STAINED_GLASS_PANE);
        inv.setItem(4, ShopGui.named(Material.HOPPER, "&b&lITEM ORGANIZER", List.of(
                "&7Left-click: &fMove item",
                "&7Right-click: &fCycle market mode",
                "&7Current location is shown on each item.")));

        int start = page * perPage;
        for (int i = 0; i < perPage && start + i < materials.size(); i++) {
            Material material = materials.get(start + i);
            String custom = plugin.customCategories().assigned(material);
            String customSub = plugin.customCategories().assignedSubcategory(material);
            String location;
            if (custom != null) {
                CustomCategory cat = plugin.customCategories().get(custom);
                CustomSubcategory sub = customSub == null ? null : plugin.customCategories().subcategory(custom, customSub);
                location = cat == null ? "Custom" : cat.display() + (sub == null ? "" : " → " + sub.display());
            } else {
                location = plugin.market().categoryOf(material).display() + " → " + plugin.market().subcategoryOf(material).display();
            }
            MarketMode mode = plugin.market().marketMode(material);
            String modeText = mode == MarketMode.BUY_SELL ? "&aBuy & Sell" : mode == MarketMode.SELL_ONLY ? "&eSell Only" : "&cDisabled";
            inv.setItem(GRID_28[i], ShopGui.named(material, "&f" + Text.prettyEnum(material.name()), List.of(
                    "&7Location: &f" + location,
                    "&7Mode: " + modeText,
                    "&7Stock: &f" + plugin.market().entry(material).stock(),
                    "", "&eLeft-click &7Move item",
                    "&bRight-click &7Cycle mode")));
        }
        inv.setItem(45, ShopGui.named(Material.ARROW, "&eBack", List.of("&7Return to Admin Studio.")));
        if (page > 0) inv.setItem(47, ShopGui.named(Material.ARROW, "&ePrevious", List.of()));
        inv.setItem(49, ShopGui.named(Material.ITEM_FRAME, "&aAdd Held Item", List.of(
                "&7Add/enable the material in your hand.", "", "&eClick to add")));
        inv.setItem(51, ShopGui.named(Material.PAPER, "&fPage &b" + (page + 1) + "&7/&b" + pages,
                List.of("&7Market materials: &f" + materials.size())));
        if (page < pages - 1) inv.setItem(53, ShopGui.named(Material.ARROW, "&eNext", List.of()));
        player.openInventory(inv);
    }

    public void openMoveCategories(Player player, Material material) {
        Inventory inv = Bukkit.createInventory(new MoveCategoryHolder(material), 54,
                Text.color("&8Move Item &7• &f" + Text.prettyEnum(material.name())));
        frame(inv, Material.BLUE_STAINED_GLASS_PANE, Material.BLACK_STAINED_GLASS_PANE);
        inv.setItem(4, ShopGui.named(material, "&b&lCHOOSE DESTINATION", List.of(
                "&7Pick a built-in or custom category.",
                "&7Subcategories are chosen next when needed.")));

        List<MarketCategory> built = java.util.Arrays.stream(MarketCategory.values()).filter(c -> c != MarketCategory.ALL).toList();
        int[] builtSlots = ShopGui.centeredSlots(built.size());
        for (int i = 0; i < built.size() && i < builtSlots.length; i++) {
            MarketCategory c = built.get(i);
            inv.setItem(builtSlots[i], ShopGui.named(c.icon(), "&6" + c.display(), List.of("&7Built-in category", "", "&eClick to choose section")));
        }

        List<CustomCategory> custom = new ArrayList<>(plugin.customCategories().all());
        int[] customSlots = ShopGui.centeredSlots(custom.size());
        for (int i = 0; i < custom.size() && i < customSlots.length; i++) {
            int shifted = customSlots[i] + 18;
            if (shifted >= 45) break;
            CustomCategory c = custom.get(i);
            inv.setItem(shifted, ShopGui.named(c.icon(), "&d" + c.display(), List.of("&7Custom category", "", "&eClick to move here")));
        }

        inv.setItem(45, ShopGui.named(Material.ARROW, "&eBack to Items", List.of()));
        inv.setItem(49, ShopGui.named(Material.RECOVERY_COMPASS, "&bReset to Automatic", List.of(
                "&7Clears custom and built-in overrides.", "", "&eClick to reset")));
        player.openInventory(inv);
    }

    public void openMoveCustomSubcategories(Player player, Material material, String categoryId) {
        CustomCategory category = plugin.customCategories().get(categoryId);
        if (category == null) { openMoveCategories(player, material); return; }
        List<CustomSubcategory> subs = plugin.customCategories().subcategories(category.id());
        Inventory inv = Bukkit.createInventory(new MoveCustomSubHolder(material, category.id()), 54,
                Text.color("&8Move Item &7• &d" + category.display()));
        frame(inv, Material.MAGENTA_STAINED_GLASS_PANE, Material.BLACK_STAINED_GLASS_PANE);
        inv.setItem(4, ShopGui.named(material, "&d&lCHOOSE SUBCATEGORY", List.of("&7Destination: &f" + category.display())));
        int[] slots = ShopGui.centeredSlots(subs.size());
        for (int i = 0; i < subs.size() && i < slots.length; i++) {
            CustomSubcategory sub = subs.get(i);
            inv.setItem(slots[i], ShopGui.named(sub.icon(), "&e" + sub.display(), List.of("&eClick to move item here")));
        }
        inv.setItem(39, ShopGui.named(category.icon(), "&dPlace in Category Root", List.of("&7Do not use a subcategory.", "", "&eClick to move")));
        inv.setItem(45, ShopGui.named(Material.ARROW, "&eBack", List.of()));
        player.openInventory(inv);
    }

    public void openDeleteCategory(Player player, String categoryId, int returnPage) {
        CustomCategory category = plugin.customCategories().get(categoryId);
        if (category == null) { openCategories(player, returnPage); return; }
        Inventory inv = Bukkit.createInventory(new ConfirmDeleteHolder("category", category.id(), "", returnPage), 27,
                Text.color("&8Confirm Removal &7• &cCategory"));
        fill(inv, Material.BLACK_STAINED_GLASS_PANE);
        inv.setItem(4, ShopGui.named(category.icon(), "&cRemove " + category.display() + "?", List.of(
                "&7Its items are NOT deleted.",
                "&7They return to automatic built-in placement.",
                "&7Its custom subcategories are removed too.")));
        inv.setItem(11, ShopGui.named(Material.LIME_CONCRETE, "&aKeep Category", List.of("&7Cancel removal.")));
        inv.setItem(15, ShopGui.named(Material.RED_CONCRETE, "&c&lREMOVE CATEGORY", List.of("&cThis cannot be undone.", "", "&eClick to confirm")));
        player.openInventory(inv);
    }

    public void openDeleteSubcategory(Player player, String categoryId, String subcategoryId, int returnPage) {
        CustomSubcategory sub = plugin.customCategories().subcategory(categoryId, subcategoryId);
        if (sub == null) { openSubcategories(player, categoryId, returnPage); return; }
        Inventory inv = Bukkit.createInventory(new ConfirmDeleteHolder("subcategory", categoryId, sub.id(), returnPage), 27,
                Text.color("&8Confirm Removal &7• &cSubcategory"));
        fill(inv, Material.BLACK_STAINED_GLASS_PANE);
        inv.setItem(4, ShopGui.named(sub.icon(), "&cRemove " + sub.display() + "?", List.of(
                "&7Items remain in the parent custom category.",
                "&7Only this subcategory is removed.")));
        inv.setItem(11, ShopGui.named(Material.LIME_CONCRETE, "&aKeep Subcategory", List.of("&7Cancel removal.")));
        inv.setItem(15, ShopGui.named(Material.RED_CONCRETE, "&c&lREMOVE SUBCATEGORY", List.of("&cThis cannot be undone.", "", "&eClick to confirm")));
        player.openInventory(inv);
    }

    private void frame(Inventory inv, Material accent, Material trim) {
        ItemStack accentPane = ShopGui.named(accent, " ", List.of());
        ItemStack trimPane = ShopGui.named(trim, " ", List.of());
        for (int slot = 0; slot < inv.getSize(); slot++) {
            int row = slot / 9, col = slot % 9;
            if (row == 0 || row == inv.getSize() / 9 - 1 || col == 0 || col == 8) inv.setItem(slot, trimPane);
        }
        if (inv.getSize() >= 45) {
            inv.setItem(1, accentPane); inv.setItem(7, accentPane);
            inv.setItem(inv.getSize() - 8, accentPane); inv.setItem(inv.getSize() - 2, accentPane);
        }
    }

    private void fill(Inventory inv, Material material) {
        ItemStack filler = ShopGui.named(material, " ", List.of());
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
    }
}

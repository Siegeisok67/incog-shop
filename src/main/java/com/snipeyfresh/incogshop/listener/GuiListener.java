package com.snipeyfresh.incogshop.listener;

import com.snipeyfresh.incogshop.IncogShopPlugin;
import com.snipeyfresh.incogshop.gui.MarketCategory;
import com.snipeyfresh.incogshop.gui.MarketSubcategory;
import com.snipeyfresh.incogshop.gui.ShopGui;
import com.snipeyfresh.incogshop.market.MarketEntry;
import com.snipeyfresh.incogshop.shop.PlayerShop;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.Inventory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GuiListener implements Listener {
    private record SearchContext(boolean admin, MarketCategory category, MarketSubcategory subcategory) {}
    private final IncogShopPlugin plugin;
    private final Map<UUID, SearchContext> searches = new ConcurrentHashMap<>();

    public GuiListener(IncogShopPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (top.getHolder() instanceof ShopGui.CategoryHolder holder) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot < 0 || slot >= top.getSize()) return;

            int searchSlot = plugin.layouts().slot("categories", "search", 40);
            int ordersSlot = plugin.layouts().slot("categories", "orders", 41);
            if (slot == searchSlot) { beginSearch(player, holder.admin(), MarketCategory.ALL, null); return; }
            if (!holder.admin() && slot == ordersSlot) { plugin.orderGui().openGlobal(player, "ALL", 0); return; }
            if (holder.admin() && slot == 50) {
                if (!player.hasPermission("incogshop.admin.stock")) return;
                toggleInfiniteStock(player);
                plugin.gui().openCategories(player, true);
                return;
            }
            if (holder.admin() && slot == 51) {
                if (!player.hasPermission("incogshop.admin.layout")) return;
                plugin.layoutEditor().open(player, null);
                return;
            }
            if (holder.admin() && slot == 52) {
                player.closeInventory();
                player.sendMessage(plugin.prefix()+"§dCustom categories: §f/marketadmin createcategory <id> <icon> <display name>");
                player.sendMessage("§7Assign items with §f/marketadmin setcategory <material> <id|auto>§7.");
                return;
            }
            if (holder.admin() && slot == 53) {
                player.closeInventory();
                player.sendMessage(plugin.prefix()+"§dAdd/configure: §f/marketadmin additem <material> <price> [buy_sell|sell_only|disabled]");
                return;
            }

            MarketCategory[] cats = MarketCategory.values();
            int[] defaults = ShopGui.centeredSlots(cats.length);
            for (int i = 0; i < cats.length; i++) {
                int fallback = i < defaults.length ? defaults[i] : 10 + i;
                int categorySlot = plugin.layouts().slot("categories", "builtin:" + cats[i].name(), fallback);
                if (slot == categorySlot) {
                    MarketCategory category = cats[i];
                    if (category == MarketCategory.ALL) plugin.gui().openMarket(player, 0, holder.admin(), category, null, "");
                    else plugin.gui().openSubcategories(player, holder.admin(), category);
                    return;
                }
            }

            int customIndex = 0;
            for (var category : plugin.customCategories().all()) {
                int categorySlot = plugin.layouts().slot("categories", "custom:" + category.id(), 10 + (customIndex++ % 27));
                if (slot == categorySlot) {
                    plugin.gui().openCustomCategory(player, category.id(), 0, holder.admin());
                    return;
                }
            }
            return;
        }

        if (top.getHolder() instanceof ShopGui.SubcategoryHolder holder) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot < 0 || slot >= top.getSize()) return;
            if (slot == plugin.layouts().slot("subcategories","all",39)) { plugin.gui().openMarket(player, 0, holder.admin(), holder.category(), null, ""); return; }
            if (slot == plugin.layouts().slot("subcategories","search",40)) { beginSearch(player, holder.admin(), holder.category(), null); return; }
            if (slot == plugin.layouts().slot("subcategories","back",45)) { plugin.gui().openCategories(player, holder.admin()); return; }
            if (holder.admin() && slot == 50) {
                if (!player.hasPermission("incogshop.admin.stock")) return;
                toggleInfiniteStock(player);
                plugin.gui().openSubcategories(player, true, holder.category());
                return;
            }
            List<MarketSubcategory> subs = MarketSubcategory.forCategory(holder.category());
            int[] subcategorySlots = plugin.gui().subcategoryLayoutSlots(subs.size());
            for (int i = 0; i < subcategorySlots.length && i < subs.size(); i++) {
                if (slot == subcategorySlots[i]) {
                    plugin.gui().openMarket(player, 0, holder.admin(), holder.category(), subs.get(i), "");
                    return;
                }
            }
            return;
        }

        if (top.getHolder() instanceof ShopGui.PlacementCategoryHolder holder) {
            event.setCancelled(true);
            if (!player.hasPermission("incogshop.admin.gui")) { player.closeInventory(); return; }
            int slot = event.getRawSlot();
            if (slot < 0 || slot >= top.getSize()) return;
            if (slot == 40) {
                plugin.market().clearClassificationOverride(holder.material(), player.getName());
                player.sendMessage(plugin.prefix() + "§aReset §f" + holder.material().name() + " §ato automatic market placement.");
                plugin.gui().openPlacementCategories(player, holder.material());
                return;
            }
            if (slot == 45) { plugin.gui().openCategories(player, true); return; }
            List<MarketCategory> categories = Arrays.stream(MarketCategory.values()).filter(c -> c != MarketCategory.ALL).toList();
            int[] categorySlots = ShopGui.centeredSlots(categories.size());
            for (int i = 0; i < categorySlots.length && i < categories.size(); i++) {
                if (slot == categorySlots[i]) {
                    plugin.gui().openPlacementSubcategories(player, holder.material(), categories.get(i));
                    return;
                }
            }
            return;
        }

        if (top.getHolder() instanceof ShopGui.PlacementSubcategoryHolder holder) {
            event.setCancelled(true);
            if (!player.hasPermission("incogshop.admin.gui")) { player.closeInventory(); return; }
            int slot = event.getRawSlot();
            if (slot < 0 || slot >= top.getSize()) return;
            if (slot == 45) { plugin.gui().openPlacementCategories(player, holder.material()); return; }
            List<MarketSubcategory> subs = MarketSubcategory.forCategory(holder.category());
            int[] subcategorySlots = ShopGui.centeredSlots(subs.size());
            for (int i = 0; i < subcategorySlots.length && i < subs.size(); i++) {
                if (slot == subcategorySlots[i]) {
                    MarketSubcategory sub = subs.get(i);
                    if (plugin.market().setClassification(holder.material(), holder.category(), sub, player.getName())) {
                        player.sendMessage(plugin.prefix() + "§aMoved §f" + holder.material().name() + " §ato §f" + holder.category().display() + " §7→ §f" + sub.display() + "§a.");
                        plugin.gui().openMarket(player, 0, true, holder.category(), sub, "");
                    }
                    return;
                }
            }
            return;
        }

        if (top.getHolder() instanceof ShopGui.CustomMarketHolder holder) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot < 0 || slot >= top.getSize()) return;
            if (slot == 45) { if (holder.page() > 0) plugin.gui().openCustomCategory(player, holder.categoryId(), holder.page()-1, holder.admin()); return; }
            if (slot == 46) { plugin.gui().openCategories(player, holder.admin()); return; }
            if (slot == 53) { plugin.gui().openCustomCategory(player, holder.categoryId(), holder.page()+1, holder.admin()); return; }
            if (slot >= ShopGui.ITEMS_PER_PAGE) return;

            var materials = plugin.customCategories().materials(holder.categoryId()).stream()
                    .filter(m -> holder.admin() || plugin.market().isMarketEnabled(m)).toList();
            int index = holder.page() * ShopGui.ITEMS_PER_PAGE + slot;
            if (index < 0 || index >= materials.size()) return;
            Material material = materials.get(index);

            if (holder.admin()) {
                if (!player.hasPermission("incogshop.admin.gui")) return;
                if (event.getClick() == ClickType.SWAP_OFFHAND) {
                    var next = plugin.market().marketMode(material).next();
                    plugin.market().setMarketMode(material, next, player.getName());
                    player.sendMessage(plugin.prefix()+"§a"+material.name()+" market mode: §f"+next.name().replace('_',' '));
                } else if (event.getClick() == ClickType.LEFT) plugin.market().addStock(material,64,player.getName());
                else if (event.getClick() == ClickType.RIGHT) plugin.market().addStock(material,-64,player.getName());
                else if (event.getClick() == ClickType.SHIFT_LEFT) plugin.market().setBasePrice(material, plugin.market().entry(material).basePrice()*1.10, player.getName());
                else if (event.getClick() == ClickType.SHIFT_RIGHT) plugin.market().setBasePrice(material, plugin.market().entry(material).basePrice()*0.90, player.getName());
                else return;
                plugin.gui().openCustomCategory(player, holder.categoryId(), holder.page(), true);
                return;
            }

            if (event.getClick() == ClickType.MIDDLE || event.getClick() == ClickType.SWAP_OFFHAND) { plugin.orderGui().open(player, material); return; }
            int amount = event.isShiftClick() ? Math.max(1, material.getMaxStackSize()) : 1;
            var result = event.isLeftClick() ? plugin.market().buy(player,material,amount) : event.isRightClick() ? plugin.market().sell(player,material,amount) : null;
            if (result != null) {
                player.sendMessage(plugin.prefix()+(result.success()?"§a":"§c")+result.message()+(result.total()>0?" §7("+plugin.money(result.total())+")":""));
                plugin.gui().openCustomCategory(player, holder.categoryId(), holder.page(), false);
            }
            return;
        }

        if (top.getHolder() instanceof ShopGui.MarketHolder holder) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot < 0 || slot >= top.getSize()) return;
            if (slot == plugin.layouts().slot("items","previous",45)) {
                if (holder.page() > 0) plugin.gui().openMarket(player, holder.page() - 1, holder.admin(), holder.category(), holder.subcategory(), holder.query());
                return;
            }
            if (slot == plugin.layouts().slot("items","back",46)) {
                if (holder.subcategory() != null && holder.category() != MarketCategory.ALL) plugin.gui().openSubcategories(player, holder.admin(), holder.category());
                else plugin.gui().openCategories(player, holder.admin());
                return;
            }
            if (slot == plugin.layouts().slot("items","search",47)) { beginSearch(player, holder.admin(), holder.category(), holder.subcategory()); return; }
            if (slot == plugin.layouts().slot("items","clear",48) && holder.query() != null && !holder.query().isBlank()) {
                plugin.gui().openMarket(player, 0, holder.admin(), holder.category(), holder.subcategory(), "");
                return;
            }
            if (!holder.admin() && slot == plugin.layouts().slot("items","orders",50)) {
                plugin.orderGui().openGlobal(player, "ALL", 0);
                return;
            }
            if (holder.admin() && slot == plugin.layouts().slot("items","orders",50)) {
                if (!player.hasPermission("incogshop.admin.stock")) return;
                toggleInfiniteStock(player);
                plugin.gui().openMarket(player, holder.page(), true, holder.category(), holder.subcategory(), holder.query());
                return;
            }
            if (slot == plugin.layouts().slot("items","next",53)) {
                plugin.gui().openMarket(player, holder.page() + 1, holder.admin(), holder.category(), holder.subcategory(), holder.query());
                return;
            }

            int logicalSlot = -1;
            int[] itemSlots = plugin.gui().itemLayoutSlots();
            for (int i=0;i<itemSlots.length;i++) if (itemSlots[i] == slot) { logicalSlot=i; break; }
            if (logicalSlot < 0) return;
            List<Material> materials = plugin.gui().filtered(holder.category(), holder.subcategory(), holder.query(), holder.admin());
            int index = holder.page() * ShopGui.ITEMS_PER_PAGE + logicalSlot;
            if (index < 0 || index >= materials.size()) return;
            Material material = materials.get(index);

            if (holder.admin()) {
                if (!player.hasPermission("incogshop.admin.gui")) { player.closeInventory(); return; }
                if (event.getClick() == ClickType.DROP || event.getClick() == ClickType.CONTROL_DROP) {
                    plugin.gui().openPlacementCategories(player, material);
                    return;
                }
                MarketEntry entry = plugin.market().entry(material);
                switch (event.getClick()) {
                    case LEFT -> plugin.market().addStock(material, 64, player.getName());
                    case RIGHT -> plugin.market().addStock(material, -64, player.getName());
                    case SHIFT_LEFT -> plugin.market().setBasePrice(material, entry.basePrice() * 1.10, player.getName());
                    case SHIFT_RIGHT -> plugin.market().setBasePrice(material, entry.basePrice() * 0.90, player.getName());
                    case MIDDLE -> plugin.market().resetPrice(material, player.getName());
                    case SWAP_OFFHAND -> {
                        var next = plugin.market().marketMode(material).next();
                        plugin.market().setMarketMode(material, next, player.getName());
                        player.sendMessage(plugin.prefix() + "§a" + material.name() + " market mode: §f" + next.name().replace('_', ' '));
                    }
                    default -> { return; }
                }
                plugin.gui().openMarket(player, holder.page(), true, holder.category(), holder.subcategory(), holder.query());
                return;
            }

            if (event.getClick() == ClickType.MIDDLE || event.getClick() == ClickType.SWAP_OFFHAND) {
                plugin.orderGui().open(player, material);
                return;
            }
            int amount = event.isShiftClick() ? Math.max(1, material.getMaxStackSize()) : 1;
            var result = event.isLeftClick() ? plugin.market().buy(player, material, amount) : event.isRightClick() ? plugin.market().sell(player, material, amount) : null;
            if (result != null) {
                player.sendMessage(plugin.prefix() + (result.success() ? "§a" : "§c") + result.message() + (result.total() > 0 ? " §7(" + plugin.money(result.total()) + ")" : ""));
                plugin.gui().openMarket(player, holder.page(), false, holder.category(), holder.subcategory(), holder.query());
            }
            return;
        }

        if (top.getHolder() instanceof ShopGui.PlayerShopHolder holder) {
            event.setCancelled(true);
            if (event.getRawSlot() < 0 || event.getRawSlot() >= top.getSize()) return;
            PlayerShop shop = plugin.shops().byId(holder.shopId());
            if (shop == null) { player.closeInventory(); player.sendMessage(plugin.prefix() + "§cThat shop no longer exists."); return; }
            int amount;
            if (event.getRawSlot() == 29) amount = 1;
            else if (event.getRawSlot() == 31) amount = Math.max(1, shop.item().getMaxStackSize());
            else return;
            var result = plugin.shops().buy(player, shop, amount);
            player.sendMessage(plugin.prefix() + (result.success() ? "§a" : "§c") + result.message() + (result.total() > 0 ? " §7(" + plugin.money(result.total()) + ")" : ""));
            plugin.gui().openPlayerShop(player, shop);
        }
    }


    private void toggleInfiniteStock(Player player) {
        boolean enabled = !plugin.market().isInfiniteStockEnabled();
        plugin.market().setInfiniteStockEnabled(enabled, player.getName());
        player.sendMessage(plugin.prefix() + (enabled
                ? "§aInfinite stock enabled globally. Players can now buy regardless of stored supply."
                : "§cInfinite stock disabled globally. Purchases now require actual market stock."));
    }

    private void beginSearch(Player player, boolean admin, MarketCategory category, MarketSubcategory subcategory) {
        searches.put(player.getUniqueId(), new SearchContext(admin, category, subcategory));
        player.closeInventory();
        String where = subcategory != null ? subcategory.display() : category == MarketCategory.ALL ? "the market" : category.display();
        player.sendMessage(plugin.prefix() + "§bType an item name in chat to search §f" + where + "§b. §7Type §fcancel §7to stop.");
    }

    @EventHandler
    public void onSearchChat(AsyncChatEvent event) {
        SearchContext context = searches.remove(event.getPlayer().getUniqueId());
        if (context == null) return;
        event.setCancelled(true);
        String rawQuery = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        final String query = rawQuery.length() > 40 ? rawQuery.substring(0, 40) : rawQuery;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player player = event.getPlayer();
            if (!player.isOnline()) return;
            if (query.equalsIgnoreCase("cancel")) {
                if (context.subcategory() != null) plugin.gui().openMarket(player, 0, context.admin(), context.category(), context.subcategory(), "");
                else if (context.category() != MarketCategory.ALL) plugin.gui().openSubcategories(player, context.admin(), context.category());
                else plugin.gui().openCategories(player, context.admin());
                return;
            }
            List<Material> matches = plugin.gui().filtered(context.category(), context.subcategory(), query, context.admin());
            if (matches.isEmpty()) player.sendMessage(plugin.prefix() + "§cNo market items matched §f" + query + "§c.");
            plugin.gui().openMarket(player, 0, context.admin(), context.category(), context.subcategory(), query);
        });
    }
}

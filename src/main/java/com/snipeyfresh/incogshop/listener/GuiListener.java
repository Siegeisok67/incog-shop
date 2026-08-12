package com.snipeyfresh.incogshop.listener;

import com.snipeyfresh.incogshop.IncogShopPlugin;
import com.snipeyfresh.incogshop.gui.MarketCategory;
import com.snipeyfresh.incogshop.gui.MarketSubcategory;
import com.snipeyfresh.incogshop.gui.ShopGui;
import com.snipeyfresh.incogshop.market.MarketEntry;
import com.snipeyfresh.incogshop.shop.PlayerShop;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.block.sign.Side;
import io.papermc.paper.math.BlockPosition;
import io.papermc.paper.math.Position;
import io.papermc.paper.event.packet.UncheckedSignChangeEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
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
    private final Map<UUID, SearchSign> searchSigns = new ConcurrentHashMap<>();
    private record SearchSign(Location location, BlockPosition position) {}

    public GuiListener(IncogShopPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (top.getHolder() instanceof ShopGui.CategoryHolder holder) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot < 0 || slot >= top.getSize()) return;

            int searchSlot = plugin.layouts().slot("categories", "search", 46);
            int ordersSlot = plugin.layouts().slot("categories", "orders", 48);
            if (slot == searchSlot) { beginSearch(player, holder.admin(), MarketCategory.ALL, null); return; }
            if (!holder.admin() && slot == ordersSlot) { plugin.orderGui().openGlobal(player, "ALL", 0); return; }
            if (holder.admin() && slot == ordersSlot) {
                if (!player.hasPermission("incogshop.admin.gui")) return;
                plugin.adminSetupGui().open(player);
                return;
            }
            if (holder.admin() && slot == plugin.layouts().slot("categories", "balance", 50)) {
                if (!player.hasPermission("incogshop.admin.stock")) return;
                toggleInfiniteStock(player);
                plugin.gui().openCategories(player, true);
                return;
            }
            if (holder.admin() && slot == plugin.layouts().slot("categories", "help", 52)) {
                if (!player.hasPermission("incogshop.admin.layout")) return;
                plugin.layoutEditor().open(player, "menu", null);
                return;
            }

            MarketCategory[] cats = MarketCategory.values();
            int total = cats.length + plugin.customCategories().all().size();
            int[] defaults = ShopGui.centeredSlots(total);
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
                int logical = cats.length + customIndex++;
                int fallback = logical < defaults.length ? defaults[logical] : 10 + logical;
                int categorySlot = plugin.layouts().slot("categories", "custom:" + category.id(), fallback);
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
            if (slot == plugin.layouts().slot("subcategories","all",47)) { plugin.gui().openMarket(player, 0, holder.admin(), holder.category(), null, ""); return; }
            if (slot == plugin.layouts().slot("subcategories","search",49)) { beginSearch(player, holder.admin(), holder.category(), null); return; }
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
                plugin.customCategories().clearAssignment(holder.material());
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
                    plugin.customCategories().clearAssignment(holder.material());
                    if (plugin.market().setClassification(holder.material(), holder.category(), sub, player.getName())) {
                        player.sendMessage(plugin.prefix() + "§aMoved §f" + holder.material().name() + " §ato §f" + holder.category().display() + " §7→ §f" + sub.display() + "§a.");
                        plugin.gui().openMarket(player, 0, true, holder.category(), sub, "");
                    }
                    return;
                }
            }
            return;
        }

        if (top.getHolder() instanceof ShopGui.CustomSubcategoryHolder holder) {
            event.setCancelled(true);
            int slot=event.getRawSlot();
            if(slot<0||slot>=top.getSize())return;
            if(slot==39){plugin.gui().openCustomMarket(player,holder.categoryId(),"",0,holder.admin());return;}
            if(slot==45){plugin.gui().openCategories(player,holder.admin());return;}
            var subs=plugin.customCategories().subcategories(holder.categoryId());
            int[] slots=ShopGui.centeredSlots(subs.size());
            for(int i=0;i<subs.size()&&i<slots.length;i++)if(slot==slots[i]){
                plugin.gui().openCustomMarket(player,holder.categoryId(),subs.get(i).id(),0,holder.admin());return;
            }
            return;
        }

        if (top.getHolder() instanceof ShopGui.CustomMarketHolder holder) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot < 0 || slot >= top.getSize()) return;
            if (slot == 45) { if (holder.page() > 0) plugin.gui().openCustomMarket(player, holder.categoryId(), holder.subcategoryId(), holder.page()-1, holder.admin()); return; }
            if (slot == 46) {
                if (!holder.subcategoryId().isBlank()) plugin.gui().openCustomSubcategories(player, holder.categoryId(), holder.admin());
                else plugin.gui().openCategories(player, holder.admin());
                return;
            }
            if (slot == 53) { plugin.gui().openCustomMarket(player, holder.categoryId(), holder.subcategoryId(), holder.page()+1, holder.admin()); return; }
            if (slot >= ShopGui.ITEMS_PER_PAGE) return;

            var materials = (holder.subcategoryId().isBlank()
                    ? plugin.customCategories().materials(holder.categoryId())
                    : plugin.customCategories().materials(holder.categoryId(), holder.subcategoryId())).stream()
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
                plugin.gui().openCustomMarket(player, holder.categoryId(), holder.subcategoryId(), holder.page(), true);
                return;
            }

            if (event.getClick() == ClickType.MIDDLE || event.getClick() == ClickType.SWAP_OFFHAND) { plugin.orderGui().open(player, material); return; }
            int amount = event.isShiftClick() ? Math.max(1, material.getMaxStackSize()) : 1;
            var result = event.isLeftClick() ? plugin.market().buy(player,material,amount) : event.isRightClick() ? plugin.market().sell(player,material,amount) : null;
            if (result != null) {
                player.sendMessage(plugin.prefix()+(result.success()?"§a":"§c")+result.message()+(result.total()>0?" §7("+plugin.money(result.total())+")":""));
                plugin.gui().openCustomMarket(player, holder.categoryId(), holder.subcategoryId(), holder.page(), false);
            }
            return;
        }

        if (top.getHolder() instanceof ShopGui.MarketHolder holder) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot < 0 || slot >= top.getSize()) return;
            if (slot == plugin.gui().itemControlSlots().get("previous")) {
                if (holder.page() > 0) plugin.gui().openMarket(player, holder.page() - 1, holder.admin(), holder.category(), holder.subcategory(), holder.query());
                return;
            }
            if (slot == plugin.gui().itemControlSlots().get("back")) {
                if (holder.category() != MarketCategory.ALL) {
                    plugin.gui().openSubcategories(player, holder.admin(), holder.category());
                } else {
                    plugin.gui().openCategories(player, holder.admin());
                }
                return;
            }
            if (slot == plugin.gui().itemControlSlots().get("search")) { beginSearch(player, holder.admin(), holder.category(), holder.subcategory()); return; }
            if (slot == plugin.gui().itemControlSlots().get("clear") && holder.query() != null && !holder.query().isBlank()) {
                plugin.gui().openMarket(player, 0, holder.admin(), holder.category(), holder.subcategory(), "");
                return;
            }
            if (!holder.admin() && slot == plugin.gui().itemControlSlots().get("orders")) {
                plugin.orderGui().openGlobal(player, "ALL", 0);
                return;
            }
            if (holder.admin() && slot == plugin.gui().itemControlSlots().get("orders")) {
                if (!player.hasPermission("incogshop.admin.stock")) return;
                toggleInfiniteStock(player);
                plugin.gui().openMarket(player, holder.page(), true, holder.category(), holder.subcategory(), holder.query());
                return;
            }
            if (slot == plugin.gui().itemControlSlots().get("next")) {
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

        Location location = player.getLocation().getBlock().getLocation().add(0, 2, 0);
        BlockPosition position = Position.block(location);
        searchSigns.put(player.getUniqueId(), new SearchSign(location, position));

        player.sendBlockChange(location, Material.OAK_SIGN.createBlockData());
        player.sendSignChange(location, new String[]{"Search item", "", "", ""});
        player.openVirtualSign(position, Side.FRONT);
        player.sendMessage(plugin.prefix() + "§bType your market search on the sign and click Done.");
    }

    @EventHandler
    public void onVirtualSignSearch(UncheckedSignChangeEvent event) {
        Player player = event.getPlayer();
        SearchContext context = searches.remove(player.getUniqueId());
        SearchSign sign = searchSigns.remove(player.getUniqueId());
        if (context == null || sign == null) return;

        BlockPosition edited = event.getEditedBlockPosition();
        if (edited.blockX() != sign.position().blockX()
                || edited.blockY() != sign.position().blockY()
                || edited.blockZ() != sign.position().blockZ()) {
            searches.put(player.getUniqueId(), context);
            searchSigns.put(player.getUniqueId(), sign);
            return;
        }

        event.setCancelled(true);
        player.sendBlockChange(sign.location(), sign.location().getBlock().getBlockData());

        StringBuilder joined = new StringBuilder();
        for (var component : event.lines()) {
            String line = PlainTextComponentSerializer.plainText().serialize(component).trim();
            if (!line.isBlank()) {
                if (!joined.isEmpty()) joined.append(' ');
                joined.append(line);
            }
        }
        String query = joined.toString().trim();
        if (query.length() > 40) query = query.substring(0, 40);
        final String finalQuery = query;

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (finalQuery.isBlank() || finalQuery.equalsIgnoreCase("cancel")) {
                if (context.subcategory() != null) plugin.gui().openMarket(player, 0, context.admin(), context.category(), context.subcategory(), "");
                else if (context.category() != MarketCategory.ALL) plugin.gui().openSubcategories(player, context.admin(), context.category());
                else plugin.gui().openCategories(player, context.admin());
                return;
            }
            plugin.gui().openMarket(player, 0, context.admin(), context.category(), context.subcategory(), finalQuery);
        });
    }

}

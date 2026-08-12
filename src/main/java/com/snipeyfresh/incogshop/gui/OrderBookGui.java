package com.snipeyfresh.incogshop.gui;

import com.snipeyfresh.incogshop.IncogShopPlugin;
import com.snipeyfresh.incogshop.order.MarketOrder;
import com.snipeyfresh.incogshop.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class OrderBookGui {
    private final IncogShopPlugin plugin;
    public OrderBookGui(IncogShopPlugin plugin) { this.plugin = plugin; }

    public record BookHolder(Material material) implements InventoryHolder { @Override public Inventory getInventory() { return null; } }
    public record MyOrdersHolder(int page) implements InventoryHolder { @Override public Inventory getInventory() { return null; } }
    public record GlobalOrdersHolder(String filter, int page) implements InventoryHolder { @Override public Inventory getInventory() { return null; } }

    public void open(Player player, Material material) {
        Inventory inv = Bukkit.createInventory(new BookHolder(material), 54, Text.color("&8Incog-Shop &7• &fOrder Book"));
        fill(inv);
        ItemStack center = new ItemStack(material);
        ItemMeta meta = center.getItemMeta();
        meta.setDisplayName(Text.color("&f" + Text.prettyEnum(material.name())));
        List<String> lore = new ArrayList<>();
        double bestBuy = plugin.orders().bestBuy(material);
        double bestSell = plugin.orders().bestSell(material);
        lore.add(Text.color("&7Best Buy Order: " + (bestBuy > 0 ? "&a" + plugin.money(bestBuy) : "&8None")));
        lore.add(Text.color("&7Best Sell Order: " + (bestSell > 0 ? "&c" + plugin.money(bestSell) : "&8None")));
        lore.add("");
        lore.add(Text.color("&7Server instant buy: &f" + plugin.money(plugin.market().buyUnitPrice(material))));
        lore.add(Text.color("&7Server instant sell: &f" + plugin.money(plugin.market().sellUnitPrice(material))));
        var entry = plugin.market().entry(material);
        if (entry != null) {
            lore.add(Text.color("&7Server Stock: &f" + entry.stock()));
            double minMult = Math.max(0, plugin.getConfig().getDouble("market-orders.sell-price-min-multiplier", 0.5));
            double maxMult = Math.max(minMult, plugin.getConfig().getDouble("market-orders.sell-price-max-multiplier", 2.0));
            lore.add(Text.color("&7Sell Order price range: &f" + plugin.money(entry.basePrice() * minMult) + " - " + plugin.money(entry.basePrice() * maxMult)));
        }
        meta.setLore(lore); center.setItemMeta(meta); inv.setItem(4, center);

        List<MarketOrder> buys = plugin.orders().buyOrders(material);
        List<MarketOrder> sells = plugin.orders().sellOrders(material);
        int[] buySlots = {10,11,12,13,14,15,16};
        int[] sellSlots = {28,29,30,31,32,33,34};
        for (int i = 0; i < Math.min(buySlots.length, buys.size()); i++) inv.setItem(buySlots[i], orderIcon(buys.get(i), true));
        for (int i = 0; i < Math.min(sellSlots.length, sells.size()); i++) inv.setItem(sellSlots[i], orderIcon(sells.get(i), false));

        inv.setItem(9, ShopGui.named(Material.LIME_STAINED_GLASS_PANE, "&aTop Buy Orders", List.of("&7Highest price first", "&7Older orders win ties.")));
        inv.setItem(27, ShopGui.named(Material.RED_STAINED_GLASS_PANE, "&cTop Sell Orders", List.of("&7Lowest price first", "&7Older orders win ties.")));
        inv.setItem(45, ShopGui.named(Material.ARROW, "&eBack to Market", List.of("&7Return to market categories.")));
        inv.setItem(47, ShopGui.named(Material.EMERALD, "&aCreate Buy Order", List.of("&7Escrow money now and wait", "&7for matching Sell Orders.", "", "&eClick to enter amount + price")));
        inv.setItem(49, ShopGui.named(Material.GOLD_INGOT, "&6Balance: &f" + plugin.money(plugin.wallets().get(player.getUniqueId())), List.of("&7Economy: &f" + plugin.wallets().providerName())));
        inv.setItem(51, ShopGui.named(Material.REDSTONE, "&cCreate Sell Order", List.of("&7Escrow plain items now and wait", "&7for matching Buy Orders.", "", "&eClick to enter amount + price")));
        inv.setItem(52, ShopGui.named(Material.BOOK, "&bMy Orders", List.of("&7Active orders: &f" + plugin.orders().owned(player.getUniqueId()).size(), "&eClick to manage")));
        inv.setItem(53, ShopGui.named(Material.ENDER_CHEST, "&aClaim Filled Items", List.of("&7Claimable items: &f" + plugin.orders().claimCount(player.getUniqueId()), "&eClick to claim")));
        player.openInventory(inv);
    }


    public void openGlobal(Player player, String requestedFilter, int requestedPage) {
        String normalizedFilter = requestedFilter == null ? "ALL" : requestedFilter.toUpperCase(java.util.Locale.ROOT);
        if (!normalizedFilter.equals("BUY") && !normalizedFilter.equals("SELL")) normalizedFilter = "ALL";
        final String filter = normalizedFilter;

        List<MarketOrder> all = plugin.orders().all().stream()
                .filter(o -> filter.equals("ALL") || o.type().name().equals(filter))
                .sorted(java.util.Comparator
                        .comparing((MarketOrder o) -> o.type() == MarketOrder.Type.BUY ? 0 : 1)
                        .thenComparing(MarketOrder::material)
                        .thenComparingLong(MarketOrder::createdAt))
                .toList();

        int perPage = 45;
        int pages = Math.max(1, (int)Math.ceil(all.size() / (double)perPage));
        int page = Math.max(0, Math.min(pages - 1, requestedPage));

        Inventory inv = Bukkit.createInventory(new GlobalOrdersHolder(filter, page), 54,
                Text.color("&8Incog-Shop &7• &fAll Market Orders"));
        fill(inv);

        int start = page * perPage;
        for (int slot = 0; slot < perPage && start + slot < all.size(); slot++) {
            MarketOrder order = all.get(start + slot);
            ItemStack icon = orderIcon(order, order.type() == MarketOrder.Type.BUY);
            ItemMeta meta = icon.getItemMeta();
            List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
            lore.add("");
            lore.add(Text.color("&eClick to open this item's Order Book"));
            meta.setLore(lore);
            icon.setItemMeta(meta);
            inv.setItem(slot, icon);
        }

        if (page > 0) inv.setItem(45, ShopGui.named(Material.ARROW, "&ePrevious Page", List.of()));
        inv.setItem(46, ShopGui.named(Material.BOOK, filter.equals("ALL") ? "&aAll Orders" : "&7All Orders",
                List.of("&7Show Buy and Sell Orders.", "", "&eClick to filter")));
        inv.setItem(47, ShopGui.named(Material.LIME_CONCRETE, filter.equals("BUY") ? "&aBuy Orders ✓" : "&aBuy Orders",
                List.of("&7Show only active Buy Orders.", "", "&eClick to filter")));
        inv.setItem(48, ShopGui.named(Material.RED_CONCRETE, filter.equals("SELL") ? "&cSell Orders ✓" : "&cSell Orders",
                List.of("&7Show only active Sell Orders.", "", "&eClick to filter")));
        inv.setItem(49, ShopGui.named(Material.CHEST, "&eBack to Market",
                List.of("&7Return to market categories.")));
        inv.setItem(50, ShopGui.named(Material.PAPER, "&fPage &e" + (page + 1) + "&7/&e" + pages,
                List.of("&7Matching active orders: &f" + all.size())));
        inv.setItem(51, ShopGui.named(Material.WRITABLE_BOOK, "&bMy Orders",
                List.of("&7Your active orders: &f" + plugin.orders().owned(player.getUniqueId()).size(), "", "&eClick to manage")));
        inv.setItem(52, ShopGui.named(Material.ENDER_CHEST, "&aClaim Filled Items",
                List.of("&7Waiting: &f" + plugin.orders().claimCount(player.getUniqueId()), "", "&eClick to claim")));
        if (page + 1 < pages) inv.setItem(53, ShopGui.named(Material.ARROW, "&eNext Page", List.of()));

        player.openInventory(inv);
    }

    public MarketOrder globalOrderAt(String filter, int page, int slot) {
        if (slot < 0 || slot >= 45) return null;
        String f = filter == null ? "ALL" : filter.toUpperCase(java.util.Locale.ROOT);
        List<MarketOrder> all = plugin.orders().all().stream()
                .filter(o -> f.equals("ALL") || o.type().name().equals(f))
                .sorted(java.util.Comparator
                        .comparing((MarketOrder o) -> o.type() == MarketOrder.Type.BUY ? 0 : 1)
                        .thenComparing(MarketOrder::material)
                        .thenComparingLong(MarketOrder::createdAt))
                .toList();
        int index = page * 45 + slot;
        return index >= 0 && index < all.size() ? all.get(index) : null;
    }

    public void openMy(Player player, int requestedPage) {
        List<MarketOrder> own = plugin.orders().owned(player.getUniqueId());
        int pages = Math.max(1, (int)Math.ceil(own.size() / 45.0));
        int page = Math.max(0, Math.min(pages - 1, requestedPage));
        Inventory inv = Bukkit.createInventory(new MyOrdersHolder(page), 54, Text.color("&8Incog-Shop &7• &fMy Orders"));
        fill(inv);
        int start = page * 45;
        for (int slot = 0; slot < 45 && start + slot < own.size(); slot++) {
            MarketOrder order = own.get(start + slot);
            ItemStack icon = orderIcon(order, order.type() == MarketOrder.Type.BUY);
            ItemMeta meta = icon.getItemMeta();
            List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
            lore.add(""); lore.add(Text.color("&cClick to cancel and return escrow"));
            meta.setLore(lore); icon.setItemMeta(meta); inv.setItem(slot, icon);
        }
        if (page > 0) inv.setItem(45, ShopGui.named(Material.ARROW, "&ePrevious Page", List.of()));
        inv.setItem(49, ShopGui.named(Material.BOOK, "&bMy Market Orders", List.of("&7Active: &f" + own.size(), "&7Page: &f" + (page + 1) + "/" + pages)));
        inv.setItem(50, ShopGui.named(Material.ENDER_CHEST, "&aClaim Items", List.of("&7Waiting: &f" + plugin.orders().claimCount(player.getUniqueId()))));
        inv.setItem(48, ShopGui.named(Material.CHEST, "&eBack to Market", List.of()));
        if (page + 1 < pages) inv.setItem(53, ShopGui.named(Material.ARROW, "&eNext Page", List.of()));
        player.openInventory(inv);
    }

    public MarketOrder orderAt(Player player, int page, int slot) {
        if (slot < 0 || slot >= 45) return null;
        List<MarketOrder> own = plugin.orders().owned(player.getUniqueId());
        int index = page * 45 + slot;
        return index >= 0 && index < own.size() ? own.get(index) : null;
    }

    private ItemStack orderIcon(MarketOrder order, boolean buy) {
        Material iconMaterial = buy ? Material.LIME_CONCRETE : Material.RED_CONCRETE;
        ItemStack stack = new ItemStack(iconMaterial);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(Text.color((buy ? "&aBuy " : "&cSell ") + Text.prettyEnum(order.material().name())));
        meta.setLore(List.of(
                Text.color("&7Price each: &f" + plugin.money(order.unitPrice())),
                Text.color("&7Remaining: &f" + order.remaining() + "&7/&f" + order.originalAmount()),
                Text.color("&7Owner: &f" + order.ownerName()),
                Text.color("&7ID: &8" + order.id().toString().substring(0, 8))
        ));
        stack.setItemMeta(meta);
        return stack;
    }

    private void fill(Inventory inv) {
        ItemStack filler = ShopGui.named(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
    }
}

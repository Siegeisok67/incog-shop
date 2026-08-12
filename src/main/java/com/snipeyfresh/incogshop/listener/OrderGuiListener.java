package com.snipeyfresh.incogshop.listener;

import com.snipeyfresh.incogshop.IncogShopPlugin;
import com.snipeyfresh.incogshop.gui.OrderBookGui;
import com.snipeyfresh.incogshop.order.MarketOrder;
import com.snipeyfresh.incogshop.util.Money;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class OrderGuiListener implements Listener {
    private record Prompt(Material material, MarketOrder.Type type) {}
    private final IncogShopPlugin plugin;
    private final Map<UUID, Prompt> prompts = new ConcurrentHashMap<>();

    public OrderGuiListener(IncogShopPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof OrderBookGui.BookHolder holder) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot == 45) { plugin.gui().openCategories(player, false); return; }
            if (slot == 47) { begin(player, holder.material(), MarketOrder.Type.BUY); return; }
            if (slot == 51) { begin(player, holder.material(), MarketOrder.Type.SELL); return; }
            if (slot == 52) { plugin.orderGui().openMy(player, 0); return; }
            if (slot == 53) {
                int claimed = plugin.orders().claim(player);
                player.sendMessage(plugin.prefix() + (claimed > 0 ? "§aClaimed §f" + claimed + " §amarket-order item(s)." : "§eNo claimable items, or your inventory is full."));
                plugin.orderGui().open(player, holder.material());
            }
            return;
        }

        if (top.getHolder() instanceof OrderBookGui.GlobalOrdersHolder holder) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot >= 0 && slot < 45) {
                MarketOrder order = plugin.orderGui().globalOrderAt(holder.filter(), holder.page(), slot);
                if (order != null) plugin.orderGui().open(player, order.material());
                return;
            }
            if (slot == 45 && holder.page() > 0) plugin.orderGui().openGlobal(player, holder.filter(), holder.page() - 1);
            else if (slot == 46) plugin.orderGui().openGlobal(player, "ALL", 0);
            else if (slot == 47) plugin.orderGui().openGlobal(player, "BUY", 0);
            else if (slot == 48) plugin.orderGui().openGlobal(player, "SELL", 0);
            else if (slot == 49) plugin.gui().openCategories(player, false);
            else if (slot == 51) plugin.orderGui().openMy(player, 0);
            else if (slot == 52) {
                int claimed = plugin.orders().claim(player);
                player.sendMessage(plugin.prefix() + (claimed > 0 ? "§aClaimed §f" + claimed + " §amarket-order item(s)." : "§eNo claimable items, or your inventory is full."));
                plugin.orderGui().openGlobal(player, holder.filter(), holder.page());
            } else if (slot == 53) plugin.orderGui().openGlobal(player, holder.filter(), holder.page() + 1);
            return;
        }

        if (top.getHolder() instanceof OrderBookGui.MyOrdersHolder holder) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot >= 0 && slot < 45) {
                MarketOrder order = plugin.orderGui().orderAt(player, holder.page(), slot);
                if (order != null) {
                    var result = plugin.orders().cancel(player, order);
                    player.sendMessage(plugin.prefix() + (result.success() ? "§a" : "§c") + result.message());
                    plugin.orderGui().openMy(player, holder.page());
                }
                return;
            }
            if (slot == 45 && holder.page() > 0) plugin.orderGui().openMy(player, holder.page() - 1);
            else if (slot == 48) plugin.gui().openCategories(player, false);
            else if (slot == 50) {
                int claimed = plugin.orders().claim(player);
                player.sendMessage(plugin.prefix() + (claimed > 0 ? "§aClaimed §f" + claimed + " §amarket-order item(s)." : "§eNo claimable items, or your inventory is full."));
                plugin.orderGui().openMy(player, holder.page());
            } else if (slot == 53) plugin.orderGui().openMy(player, holder.page() + 1);
        }
    }

    private void begin(Player player, Material material, MarketOrder.Type type) {
        if (!player.hasPermission(type == MarketOrder.Type.BUY ? "incogshop.orders.buy" : "incogshop.orders.sell")) {
            player.sendMessage(plugin.prefix() + "§cYou do not have permission to create that order type.");
            return;
        }
        prompts.put(player.getUniqueId(), new Prompt(material, type));
        player.closeInventory();
        player.sendMessage(plugin.prefix() + "§eType §f<amount> <price each> §ein chat for your " + (type == MarketOrder.Type.BUY ? "Buy" : "Sell") + " Order.");
        player.sendMessage("§7Example: §f64 10k §8| §7Type §fcancel §7to stop.");
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Prompt prompt = prompts.remove(event.getPlayer().getUniqueId());
        if (prompt == null) return;
        event.setCancelled(true);
        String input = event.getMessage().trim();
        plugin.getServer().getScheduler().runTask(plugin, () -> handle(event.getPlayer(), prompt, input));
    }

    private void handle(Player player, Prompt prompt, String input) {
        if (!player.isOnline()) return;
        if (input.equalsIgnoreCase("cancel")) {
            player.sendMessage(plugin.prefix() + "§7Market order creation cancelled.");
            plugin.orderGui().open(player, prompt.material());
            return;
        }
        String[] parts = input.split("\\s+");
        if (parts.length != 2) {
            player.sendMessage(plugin.prefix() + "§cUse: <amount> <price each>. Example: 64 10k");
            plugin.orderGui().open(player, prompt.material());
            return;
        }
        int amount;
        try { amount = Integer.parseInt(parts[0].replace(",", "")); }
        catch (NumberFormatException ex) { amount = -1; }
        double price = Money.parsePositive(parts[1]);
        if (amount <= 0 || price <= 0) {
            player.sendMessage(plugin.prefix() + "§cInvalid amount or price.");
            plugin.orderGui().open(player, prompt.material());
            return;
        }
        var result = prompt.type() == MarketOrder.Type.BUY
                ? plugin.orders().createBuy(player, prompt.material(), amount, price)
                : plugin.orders().createSell(player, prompt.material(), amount, price);
        player.sendMessage(plugin.prefix() + (result.success() ? "§a" : "§c") + result.message());
        plugin.orderGui().open(player, prompt.material());
    }
}

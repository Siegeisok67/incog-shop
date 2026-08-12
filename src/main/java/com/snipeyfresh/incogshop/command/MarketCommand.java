package com.snipeyfresh.incogshop.command;

import com.snipeyfresh.incogshop.IncogShopPlugin;
import com.snipeyfresh.incogshop.gui.MarketCategory;
import com.snipeyfresh.incogshop.util.Money;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class MarketCommand implements TabExecutor {
    private final IncogShopPlugin plugin;
    public MarketCommand(IncogShopPlugin plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
        if (!player.hasPermission("incogshop.market")) {
            player.sendMessage(plugin.prefix()+"§cYou do not have permission: §fincogshop.market");
            return true;
        }
        if (args.length == 0) { plugin.gui().openCategories(player, false); return true; }
        String sub = args[0].toLowerCase();
        if (sub.equals("search") && args.length >= 2) {
            String query = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            plugin.gui().openMarket(player, 0, false, MarketCategory.ALL, query);
            return true;
        }
        if (sub.equals("orders")) {
            if (args.length >= 2) {
                Material material = Material.matchMaterial(String.join("_", Arrays.copyOfRange(args, 1, args.length)));
                if (material == null || !plugin.market().isTradable(material)) player.sendMessage(plugin.prefix() + "§cUnknown or unavailable market material.");
                else plugin.orderGui().open(player, material);
            } else plugin.orderGui().openMy(player, 0);
            return true;
        }
        if (sub.equals("myorders")) { plugin.orderGui().openMy(player, 0); return true; }
        if (sub.equals("claim")) {
            int count = plugin.orders().claim(player);
            player.sendMessage(plugin.prefix() + (count > 0 ? "§aClaimed §f" + count + " §amarket-order item(s)." : "§eNo claimable market-order items, or your inventory is full."));
            return true;
        }
        if (sub.equals("cancelorder")) {
            if (args.length != 2) { player.sendMessage(plugin.prefix() + "§eUsage: /market cancelorder <id>"); return true; }
            var result = plugin.orders().cancel(player, plugin.orders().find(args[1]));
            player.sendMessage(plugin.prefix() + (result.success() ? "§a" : "§c") + result.message());
            return true;
        }
        if (sub.equals("buyorder") || sub.equals("sellorder")) {
            boolean buy = sub.equals("buyorder");
            if (!player.hasPermission(buy ? "incogshop.orders.buy" : "incogshop.orders.sell")) { player.sendMessage(plugin.prefix() + "§cYou do not have permission."); return true; }
            if (args.length != 4) { player.sendMessage(plugin.prefix() + "§eUsage: /market " + sub + " <material> <amount> <price-each>"); return true; }
            Material material = Material.matchMaterial(args[1]);
            int amount; try { amount = Integer.parseInt(args[2].replace(",", "")); } catch (NumberFormatException ex) { amount = -1; }
            double price = Money.parsePositive(args[3]);
            if (material == null || amount <= 0 || price <= 0) { player.sendMessage(plugin.prefix() + "§cInvalid material, amount, or price. Price examples: 10k, 2.5m."); return true; }
            var result = buy ? plugin.orders().createBuy(player, material, amount, price) : plugin.orders().createSell(player, material, amount, price);
            player.sendMessage(plugin.prefix() + (result.success() ? "§a" : "§c") + result.message());
            return true;
        }
        player.sendMessage(plugin.prefix() + "§6Market commands: §f/market§7, §f/market orders [item]§7, §f/market buyorder <item> <amount> <price>§7, §f/market sellorder <item> <amount> <price>§7, §f/market myorders§7, §f/market cancelorder <id>§7, §f/market claim");
        return true;
    }

    @Override public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return filter(List.of("search","orders","buyorder","sellorder","myorders","cancelorder","claim"), args[0]);
        if (args.length == 2 && List.of("orders","buyorder","sellorder").contains(args[0].toLowerCase())) {
            List<String> mats = plugin.market().tradableMaterials().stream().filter(plugin.market()::isTradable).map(m -> m.name().toLowerCase()).toList();
            return filter(mats, args[1]);
        }
        return List.of();
    }
    private static List<String> filter(List<String> values, String prefix) { String p = prefix.toLowerCase(); List<String> out = new ArrayList<>(); for (String v : values) if (v.startsWith(p)) out.add(v); return out; }
}

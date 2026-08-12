package com.snipeyfresh.incogshop.command;

import com.snipeyfresh.incogshop.IncogShopPlugin;
import com.snipeyfresh.incogshop.shop.PlayerShop;
import com.snipeyfresh.incogshop.util.Text;
import com.snipeyfresh.incogshop.util.Money;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.TabExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class PlayerShopCommand implements TabExecutor {
    private final IncogShopPlugin plugin;
    public PlayerShopCommand(IncogShopPlugin plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
        if (args.length == 0) {
            help(player); return true;
        }
        String sub = args[0].toLowerCase();
        Block target = player.getTargetBlockExact(6);
        PlayerShop shop = target == null ? null : plugin.shops().at(target);
        switch (sub) {
            case "create" -> {
                if (!player.hasPermission("incogshop.playershop.create")) { noPerm(player); return true; }
                if (args.length != 2) { player.sendMessage(plugin.prefix() + "§eUsage: /pshop create <price-each>"); return true; }
                double price = Money.parsePositive(args[1]);
                if (price <= 0) { player.sendMessage(plugin.prefix() + "§cPrice must be a positive number."); return true; }
                ItemStack held = player.getInventory().getItemInMainHand();
                String result = plugin.shops().create(player, target, held, price);
                player.sendMessage(plugin.prefix() + (result.startsWith("Created") ? "§a" : "§c") + result);
            }
            case "remove" -> {
                if (!player.hasPermission("incogshop.playershop.remove")) { noPerm(player); return true; }
                if (shop == null) { player.sendMessage(plugin.prefix() + "§cLook at a registered shop container."); return true; }
                if (plugin.shops().remove(player, shop)) player.sendMessage(plugin.prefix() + "§aShop removed. The container and stock were left untouched.");
                else player.sendMessage(plugin.prefix() + "§cYou do not own that shop.");
            }
            case "price" -> {
                if (shop == null) { player.sendMessage(plugin.prefix() + "§cLook at one of your registered shops."); return true; }
                if (!shop.owner().equals(player.getUniqueId()) && !player.hasPermission("incogshop.playershop.bypass")) { noPerm(player); return true; }
                if (args.length != 2) { player.sendMessage(plugin.prefix() + "§eUsage: /pshop price <price-each>"); return true; }
                double price = Money.parsePositive(args[1]);
                if (price <= 0) { player.sendMessage(plugin.prefix() + "§cPrice must be positive."); return true; }
                plugin.shops().setPrice(player, shop, price);
                player.sendMessage(plugin.prefix() + "§aShop price changed to §f" + plugin.money(price) + "§a each.");
            }
            case "item" -> {
                if (shop == null) { player.sendMessage(plugin.prefix() + "§cLook at one of your registered shops."); return true; }
                if (!shop.owner().equals(player.getUniqueId()) && !player.hasPermission("incogshop.playershop.bypass")) { noPerm(player); return true; }
                ItemStack held = player.getInventory().getItemInMainHand();
                if (held.getType().isAir()) { player.sendMessage(plugin.prefix() + "§cHold the exact replacement item in your main hand."); return true; }
                shop.item(held); plugin.shops().save();
                plugin.market().audit("PLAYER_SHOP_ITEM", player.getUniqueId(), player.getName(), shop.id().toString(), 0, shop.price(), "item=" + held.getType().name());
                player.sendMessage(plugin.prefix() + "§aShop item template updated to the exact item in your hand.");
            }
            case "info" -> {
                if (shop == null) { player.sendMessage(plugin.prefix() + "§cLook at a registered shop container."); return true; }
                player.sendMessage(plugin.prefix() + "§6Shop §f" + shop.id());
                player.sendMessage("§7Owner: §f" + shop.ownerName() + " §8| §7Item: §f" + Text.prettyEnum(shop.item().getType().name()));
                player.sendMessage("§7Price: §f" + plugin.money(shop.price()) + " §8| §7Stock: §f" + plugin.shops().stock(shop));
            }
            case "list" -> {
                List<PlayerShop> owned = plugin.shops().all().stream().filter(s -> s.owner().equals(player.getUniqueId())).toList();
                player.sendMessage(plugin.prefix() + "§6Your shops: §f" + owned.size());
                for (PlayerShop s : owned) player.sendMessage("§8- §f" + Text.prettyEnum(s.item().getType().name()) + " §7@ " + plugin.money(s.price()) + " §8(" + s.location().getWorld().getName() + " " + s.location().getBlockX() + "," + s.location().getBlockY() + "," + s.location().getBlockZ() + ")");
            }
            default -> help(player);
        }
        return true;
    }

    private void help(Player p) {
        p.sendMessage(plugin.prefix() + "§6Player shops: §f/pshop create <price>§7, §fremove§7, §fprice <price>§7, §fitem§7, §finfo§7, §flist");
        p.sendMessage("§7Create a shop while looking at a single chest/barrel and holding the exact item to sell. Sneak-right-click your shop to access stock.");
    }
    private void noPerm(Player p) { p.sendMessage(plugin.prefix() + "§cYou do not have permission."); }

    @Override public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return filter(List.of("create", "remove", "price", "item", "info", "list"), args[0]);
        return List.of();
    }
    private static List<String> filter(List<String> values, String prefix) {
        String p = prefix.toLowerCase(); List<String> out = new ArrayList<>(); for (String v : values) if (v.startsWith(p)) out.add(v); return out;
    }
}

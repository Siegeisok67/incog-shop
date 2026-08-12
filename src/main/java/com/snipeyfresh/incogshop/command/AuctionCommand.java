package com.snipeyfresh.incogshop.command;

import com.snipeyfresh.incogshop.IncogShopPlugin;
import com.snipeyfresh.incogshop.auction.AuctionListing;
import com.snipeyfresh.incogshop.auction.AuctionManager;
import com.snipeyfresh.incogshop.util.Money;
import com.snipeyfresh.incogshop.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class AuctionCommand implements TabExecutor {
    private final IncogShopPlugin plugin;
    public AuctionCommand(IncogShopPlugin plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
        if (args.length == 0 || args[0].equalsIgnoreCase("browse")) { plugin.auctionGui().open(player, 0); return true; }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "sell" -> sell(player, args);
            case "bid" -> bid(player, args);
            case "buy" -> buy(player, args);
            case "cancel" -> cancel(player, args);
            case "claim" -> {
                int count = plugin.auctions().claim(player);
                player.sendMessage(plugin.prefix() + (count > 0 ? "§aClaimed §f" + count + " §aitem stack(s)." : "§eYou have no claimable items, or your inventory is full."));
            }
            case "my" -> {
                var own = plugin.auctions().activeListings().stream().filter(l -> l.seller().equals(player.getUniqueId())).toList();
                player.sendMessage(plugin.prefix() + "§6Your active Auction House listings: §f" + own.size());
                for (var l : own) player.sendMessage("§8- §f" + AuctionManager.shortId(l.id()) + " §7| §f" + Text.prettyEnum(l.item().getType().name()) + " x" + l.item().getAmount() + " §7| §f" + (l.mode() == AuctionListing.Mode.BUY_NOW ? plugin.money(l.buyNowPrice()) : (l.hasBid() ? plugin.money(l.highBid()) : plugin.money(l.startPrice()))));
            }
            default -> help(player);
        }
        return true;
    }

    private void sell(Player p, String[] args) {
        if (!p.hasPermission("incogshop.auction.sell")) { noPerm(p); return; }
        if (args.length < 3) { p.sendMessage(plugin.prefix() + "§eUsage: /ah sell <auction|bin> <price> [hours]"); return; }
        AuctionListing.Mode mode;
        if (args[1].equalsIgnoreCase("auction")) mode = AuctionListing.Mode.AUCTION;
        else if (args[1].equalsIgnoreCase("bin") || args[1].equalsIgnoreCase("buynow")) mode = AuctionListing.Mode.BUY_NOW;
        else { p.sendMessage(plugin.prefix() + "§cMode must be auction or bin."); return; }
        double price = Money.parsePositive(args[2]);
        if (price <= 0) { p.sendMessage(plugin.prefix() + "§cInvalid price. Examples: 10k, 2.5m, 1b."); return; }
        int hours = plugin.getConfig().getInt("auction-house.default-duration-hours", 24);
        if (args.length >= 4) try { hours = Integer.parseInt(args[3]); } catch (NumberFormatException ex) { p.sendMessage(plugin.prefix() + "§cHours must be a whole number."); return; }
        var r = plugin.auctions().create(p, mode, price, hours);
        p.sendMessage(plugin.prefix() + (r.success() ? "§a" : "§c") + r.message());
    }

    private void bid(Player p, String[] args) {
        if (!p.hasPermission("incogshop.auction.bid")) { noPerm(p); return; }
        if (args.length != 3) { p.sendMessage(plugin.prefix() + "§eUsage: /ah bid <id> <amount>"); return; }
        var listing = plugin.auctions().find(args[1]);
        double amount = Money.parsePositive(args[2]);
        if (amount <= 0) { p.sendMessage(plugin.prefix() + "§cInvalid bid amount."); return; }
        var r = plugin.auctions().bid(p, listing, amount);
        p.sendMessage(plugin.prefix() + (r.success() ? "§a" : "§c") + r.message());
    }

    private void buy(Player p, String[] args) {
        if (!p.hasPermission("incogshop.auction.buy")) { noPerm(p); return; }
        if (args.length != 2) { p.sendMessage(plugin.prefix() + "§eUsage: /ah buy <id>"); return; }
        var r = plugin.auctions().buyNow(p, plugin.auctions().find(args[1]));
        p.sendMessage(plugin.prefix() + (r.success() ? "§a" : "§c") + r.message());
    }

    private void cancel(Player p, String[] args) {
        if (args.length != 2) { p.sendMessage(plugin.prefix() + "§eUsage: /ah cancel <id>"); return; }
        var r = plugin.auctions().cancel(p, plugin.auctions().find(args[1]));
        p.sendMessage(plugin.prefix() + (r.success() ? "§a" : "§c") + r.message());
    }

    private void help(Player p) {
        p.sendMessage(plugin.prefix() + "§6Auction House commands:");
        p.sendMessage("§f/ah §7- Browse listings");
        p.sendMessage("§f/ah sell auction <start> [hours] §7- List held item/stack as an auction");
        p.sendMessage("§f/ah sell bin <price> [hours] §7- List held item/stack as Buy It Now");
        p.sendMessage("§f/ah bid <id> <amount> §7- Bid on an auction");
        p.sendMessage("§f/ah buy <id> §7- Buy a Buy It Now listing");
        p.sendMessage("§f/ah my §7- Show your listing IDs §8| §f/ah cancel <id> §8| §f/ah claim");
        p.sendMessage("§7Prices support shorthand such as §f10k§7, §f2.5m§7, and §f1b§7.");
    }
    private void noPerm(Player p) { p.sendMessage(plugin.prefix() + "§cYou do not have permission."); }

    @Override public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return filter(List.of("browse", "sell", "bid", "buy", "cancel", "claim", "my"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("sell")) return filter(List.of("auction", "bin"), args[1]);
        return List.of();
    }
    private static List<String> filter(List<String> values, String prefix) { String p = prefix.toLowerCase(); List<String> out = new ArrayList<>(); for (String v : values) if (v.startsWith(p)) out.add(v); return out; }
}

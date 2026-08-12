package com.snipeyfresh.incogshop.gui;

import com.snipeyfresh.incogshop.IncogShopPlugin;
import com.snipeyfresh.incogshop.auction.AuctionListing;
import com.snipeyfresh.incogshop.auction.AuctionManager;
import com.snipeyfresh.incogshop.economy.WalletManager;
import com.snipeyfresh.incogshop.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AuctionGui {
    private final IncogShopPlugin plugin;
    public AuctionGui(IncogShopPlugin plugin) { this.plugin = plugin; }

    public record Holder(int page) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
    public record MyHolder(UUID owner, int page) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
    public record DetailHolder(UUID listingId, int returnPage, boolean fromMyListings) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player, int page) {
        List<AuctionListing> listings = plugin.auctions().activeListings();
        int perPage = 45;
        int pages = Math.max(1, (listings.size() + perPage - 1) / perPage);
        page = Math.max(0, Math.min(page, pages - 1));
        Inventory inv = Bukkit.createInventory(new Holder(page), 54, Text.color("&8Incog-Shop &7• &6Auction House"));
        int start = page * perPage;
        for (int i = 0; i < perPage && start + i < listings.size(); i++) inv.setItem(i, icon(listings.get(start + i)));
        ItemStack filler = ShopGui.named(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);
        if (page > 0) inv.setItem(45, ShopGui.named(Material.ARROW, "&ePrevious Page", List.of("&7Page " + page + " / " + pages)));
        inv.setItem(47, ShopGui.named(Material.CHEST, "&6My Auctions", List.of("&7View and manage your active", "&7Auction House listings.", "", "&eClick to open")));
        inv.setItem(49, ShopGui.named(Material.GOLD_INGOT, "&6Balance: &f" + plugin.money(plugin.wallets().get(player.getUniqueId())), List.of("&7Economy: &f" + plugin.wallets().providerName())));
        if (player.hasPermission("incogshop.auction.admin")) {
            boolean permanent = plugin.auctions().permanentMode(player);
            inv.setItem(50, ShopGui.named(permanent ? Material.CLOCK : Material.RECOVERY_COMPASS,
                    permanent ? "&dAdmin Permanent Listings: ON" : "&7Admin Permanent Listings: OFF",
                    permanent ? List.of("&7New Auction House listings you create", "&7will never expire until sold/cancelled.", "", "&eClick to disable")
                              : List.of("&7Admin-only setting.", "&7When enabled, new listings never expire.", "", "&eClick to enable")));
        }
        inv.setItem(51, ShopGui.named(Material.ENDER_CHEST, "&aClaims: &f" + plugin.auctions().claimCount(player.getUniqueId()), List.of("&7Use &f/ah claim &7to collect", "&7won or returned items.")));
        if (page < pages - 1) inv.setItem(53, ShopGui.named(Material.ARROW, "&eNext Page", List.of("&7Page " + (page + 2) + " / " + pages)));
        player.openInventory(inv);
    }

    public void openMy(Player player, int page) {
        List<AuctionListing> listings = plugin.auctions().activeListings().stream()
                .filter(l -> l.seller().equals(player.getUniqueId()))
                .toList();
        int perPage = 45;
        int pages = Math.max(1, (listings.size() + perPage - 1) / perPage);
        page = Math.max(0, Math.min(page, pages - 1));
        Inventory inv = Bukkit.createInventory(new MyHolder(player.getUniqueId(), page), 54, Text.color("&8Incog-Shop &7• &6My Auctions"));
        int start = page * perPage;
        for (int i = 0; i < perPage && start + i < listings.size(); i++) {
            AuctionListing listing = listings.get(start + i);
            ItemStack item = icon(listing);
            ItemMeta meta = item.getItemMeta();
            List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add(Text.color(listing.mode() == AuctionListing.Mode.AUCTION && listing.hasBid() ? "&cThis auction has a bid and cannot be cancelled." : "&cClick to manage / cancel"));
            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.setItem(i, item);
        }
        ItemStack filler = ShopGui.named(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);
        if (page > 0) inv.setItem(45, ShopGui.named(Material.ARROW, "&ePrevious Page", List.of()));
        inv.setItem(49, ShopGui.named(Material.BARRIER, "&cBack to Auction House", List.of("&7Return to all listings.")));
        inv.setItem(51, ShopGui.named(Material.ENDER_CHEST, "&aClaims: &f" + plugin.auctions().claimCount(player.getUniqueId()), List.of("&7Cancelled items are returned", "&7through your claim queue.")));
        if (page < pages - 1) inv.setItem(53, ShopGui.named(Material.ARROW, "&eNext Page", List.of()));
        player.openInventory(inv);
    }

    public void openDetail(Player player, AuctionListing listing, int returnPage) {
        openDetail(player, listing, returnPage, false);
    }

    public void openDetail(Player player, AuctionListing listing, int returnPage, boolean fromMyListings) {
        if (listing == null) {
            if (fromMyListings) openMy(player, returnPage); else open(player, returnPage);
            return;
        }
        Inventory inv = Bukkit.createInventory(new DetailHolder(listing.id(), returnPage, fromMyListings), 45, Text.color("&8Incog-Shop &7• &6Listing Details"));
        ItemStack filler = ShopGui.named(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
        inv.setItem(13, icon(listing));
        inv.setItem(40, ShopGui.named(Material.ARROW, fromMyListings ? "&eBack to My Auctions" : "&eBack to Auction House", List.of(fromMyListings ? "&7Return to your listings." : "&7Return to the listing browser.")));
        boolean owner = listing.seller().equals(player.getUniqueId());
        if (owner) {
            boolean canCancel = listing.mode() != AuctionListing.Mode.AUCTION || !listing.hasBid();
            inv.setItem(36, ShopGui.named(canCancel ? Material.BARRIER : Material.RED_STAINED_GLASS_PANE,
                    canCancel ? "&cCancel Listing" : "&cCancellation Locked",
                    canCancel ? List.of("&7The item will be moved to", "&7your Auction House claims.", "", "&cClick to cancel this listing.") : List.of("&7This auction already has a bid.", "&7It can no longer be cancelled.")));
        }

        if (listing.mode() == AuctionListing.Mode.BUY_NOW) {
            inv.setItem(31, ShopGui.named(Material.EMERALD_BLOCK, "&aBuy It Now", List.of(
                    "&7Price: &f" + plugin.money(listing.buyNowPrice()),
                    "&7Your balance: &f" + plugin.money(plugin.wallets().get(player.getUniqueId())),
                    "",
                    "&eClick to purchase"
            )));
        } else {
            double minimum = minimumBid(listing);
            inv.setItem(28, ShopGui.named(Material.GOLD_NUGGET, "&eMinimum Bid", List.of(
                    "&7Bid: &f" + plugin.money(minimum), "", "&eClick to place bid")));
            inv.setItem(30, ShopGui.named(Material.GOLD_INGOT, "&6Bid +10%", List.of(
                    "&7Bid: &f" + plugin.money(WalletManager.round(minimum * 1.10)), "", "&eClick to place bid")));
            inv.setItem(32, ShopGui.named(Material.GOLD_BLOCK, "&6Bid +25%", List.of(
                    "&7Bid: &f" + plugin.money(WalletManager.round(minimum * 1.25)), "", "&eClick to place bid")));
            inv.setItem(34, ShopGui.named(Material.OAK_SIGN, "&bCustom Bid", List.of(
                    "&7Enter any bid at or above", "&f" + plugin.money(minimum), "", "&eClick, then type amount in chat")));
        }
        player.openInventory(inv);
    }

    public double minimumBid(AuctionListing listing) {
        if (!listing.hasBid()) return listing.startPrice();
        double increment = Math.max(plugin.getConfig().getDouble("auction-house.minimum-bid-increment", 1),
                listing.highBid() * Math.max(0, plugin.getConfig().getDouble("auction-house.minimum-bid-increment-percent", 5)) / 100.0);
        return WalletManager.round(listing.highBid() + increment);
    }

    private ItemStack icon(AuctionListing listing) {
        ItemStack stack = listing.item();
        ItemMeta meta = stack.getItemMeta();
        List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        if (!lore.isEmpty()) lore.add("");
        lore.add(Text.color("&8--------------------"));
        lore.add(Text.color("&7Seller: &f" + listing.sellerName()));
        lore.add(Text.color("&7ID: &f" + AuctionManager.shortId(listing.id())));
        if (listing.mode() == AuctionListing.Mode.BUY_NOW) {
            lore.add(Text.color("&7Mode: &aBuy It Now"));
            lore.add(Text.color("&7Price: &f" + plugin.money(listing.buyNowPrice())));
            lore.add("");
            lore.add(Text.color("&eClick to view / purchase"));
        } else {
            lore.add(Text.color("&7Mode: &6Auction"));
            lore.add(Text.color(listing.hasBid() ? "&7Current bid: &f" + plugin.money(listing.highBid()) : "&7Starting bid: &f" + plugin.money(listing.startPrice())));
            lore.add(Text.color(listing.hasBid() ? "&7High bidder: &f" + listing.highBidderName() : "&7No bids yet"));
            lore.add(Text.color("&7Minimum next bid: &f" + plugin.money(minimumBid(listing))));
            lore.add("");
            lore.add(Text.color("&eClick to open bidding menu"));
        }
        lore.add(Text.color(listing.neverExpires() ? "&7Expires: &dNever" : "&7Ends in: &f" + remaining(listing.expiresAt())));
        meta.setLore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private static String remaining(long end) {
        long sec = Math.max(0, Duration.ofMillis(end - System.currentTimeMillis()).toSeconds());
        long h = sec / 3600; long m = (sec % 3600) / 60; long s = sec % 60;
        return h > 0 ? h + "h " + m + "m" : m > 0 ? m + "m " + s + "s" : s + "s";
    }
}

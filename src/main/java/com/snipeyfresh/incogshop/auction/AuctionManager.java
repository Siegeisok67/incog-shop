package com.snipeyfresh.incogshop.auction;

import com.snipeyfresh.incogshop.IncogShopPlugin;
import com.snipeyfresh.incogshop.economy.WalletManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class AuctionManager {
    public record Result(boolean success, String message) {}

    private final IncogShopPlugin plugin;
    private final File file;
    private final Map<UUID, AuctionListing> listings = new LinkedHashMap<>();
    private final Map<UUID, List<ItemStack>> claims = new HashMap<>();
    private final Set<UUID> adminPermanentMode = new HashSet<>();

    public AuctionManager(IncogShopPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "auctions.yml");
    }

    public Collection<AuctionListing> listings() { return Collections.unmodifiableCollection(listings.values()); }
    public AuctionListing get(UUID id) { return listings.get(id); }
    public boolean permanentMode(Player player) {
        return player != null && player.hasPermission("incogshop.auction.admin") && adminPermanentMode.contains(player.getUniqueId());
    }
    public boolean togglePermanentMode(Player player) {
        if (player == null || !player.hasPermission("incogshop.auction.admin")) return false;
        UUID id = player.getUniqueId();
        if (!adminPermanentMode.add(id)) adminPermanentMode.remove(id);
        return adminPermanentMode.contains(id);
    }
    public List<AuctionListing> activeListings() {
        settleExpired();
        return listings.values().stream()
                .sorted(Comparator.comparing(AuctionListing::neverExpires)
                        .thenComparingLong(AuctionListing::expiresAt))
                .toList();
    }

    public void load() {
        listings.clear(); claims.clear();
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        var section = yml.getConfigurationSection("listings");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    String root = "listings." + key;
                    UUID seller = UUID.fromString(Objects.requireNonNull(yml.getString(root + ".seller")));
                    String sellerName = yml.getString(root + ".seller-name", "Unknown");
                    ItemStack item = yml.getItemStack(root + ".item");
                    AuctionListing.Mode mode = AuctionListing.Mode.valueOf(yml.getString(root + ".mode", "AUCTION"));
                    double start = yml.getDouble(root + ".start-price", 1);
                    double buyNow = yml.getDouble(root + ".buy-now-price", 0);
                    long created = yml.getLong(root + ".created-at", System.currentTimeMillis());
                    long expires = yml.getLong(root + ".expires-at", created + 86400000L);
                    String bidderRaw = yml.getString(root + ".high-bidder");
                    UUID bidder = bidderRaw == null || bidderRaw.isBlank() ? null : UUID.fromString(bidderRaw);
                    String bidderName = yml.getString(root + ".high-bidder-name", "");
                    double highBid = yml.getDouble(root + ".high-bid", 0);
                    if (item != null && !item.getType().isAir()) listings.put(id, new AuctionListing(id, seller, sellerName, item, mode, start, buyNow, created, expires, bidder, bidderName, highBid));
                } catch (Exception ex) {
                    plugin.getLogger().warning("Skipping invalid auction listing " + key + ": " + ex.getMessage());
                }
            }
        }
        var claimSection = yml.getConfigurationSection("claims");
        if (claimSection != null) {
            for (String key : claimSection.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    List<ItemStack> items = new ArrayList<>();
                    for (Object obj : yml.getList("claims." + key, List.of())) if (obj instanceof ItemStack stack) items.add(stack);
                    if (!items.isEmpty()) claims.put(id, items);
                } catch (IllegalArgumentException ignored) {}
            }
        }
        settleExpired();
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (AuctionListing listing : listings.values()) {
            String root = "listings." + listing.id();
            yml.set(root + ".seller", listing.seller().toString());
            yml.set(root + ".seller-name", listing.sellerName());
            yml.set(root + ".item", listing.item());
            yml.set(root + ".mode", listing.mode().name());
            yml.set(root + ".start-price", listing.startPrice());
            yml.set(root + ".buy-now-price", listing.buyNowPrice());
            yml.set(root + ".created-at", listing.createdAt());
            yml.set(root + ".expires-at", listing.expiresAt());
            yml.set(root + ".high-bidder", listing.highBidder() == null ? null : listing.highBidder().toString());
            yml.set(root + ".high-bidder-name", listing.highBidderName());
            yml.set(root + ".high-bid", listing.highBid());
        }
        for (var e : claims.entrySet()) yml.set("claims." + e.getKey(), e.getValue());
        try { yml.save(file); }
        catch (IOException ex) { plugin.getLogger().severe("Could not save auctions.yml: " + ex.getMessage()); }
    }

    public Result create(Player seller, AuctionListing.Mode mode, double price, int durationHours) {
        if (!plugin.getConfig().getBoolean("auction-house.enabled", true)) return new Result(false, "The Auction House is disabled.");
        ItemStack held = seller.getInventory().getItemInMainHand();
        if (held.getType().isAir()) return new Result(false, "Hold the item or stack you want to list in your main hand.");
        long owned = listings.values().stream().filter(l -> l.seller().equals(seller.getUniqueId())).count();
        int max = plugin.getConfig().getInt("auction-house.max-active-listings-per-player", 12);
        if (owned >= max && !seller.hasPermission("incogshop.auction.bypasslimit")) return new Result(false, "You already have the maximum of " + max + " active listings.");
        if (price <= 0) return new Result(false, "Price must be positive.");
        int maxHours = Math.max(1, plugin.getConfig().getInt("auction-house.maximum-duration-hours", 168));
        durationHours = Math.max(1, Math.min(maxHours, durationHours));
        double fee = Math.max(0, plugin.getConfig().getDouble("auction-house.listing-fee", 25));
        if (!plugin.wallets().withdraw(seller.getUniqueId(), fee)) return new Result(false, "You need " + plugin.money(fee) + " for the listing fee.");

        ItemStack item = held.clone();
        seller.getInventory().setItemInMainHand(null);
        long now = System.currentTimeMillis();
        UUID id = UUID.randomUUID();
        double buyNow = mode == AuctionListing.Mode.BUY_NOW ? price : 0;
        boolean permanent = permanentMode(seller);
        long expiresAt = permanent ? 0L : now + durationHours * 3600000L;
        listings.put(id, new AuctionListing(id, seller.getUniqueId(), seller.getName(), item, mode, price, buyNow, now, expiresAt, null, "", 0));
        save();
        plugin.market().audit("AH_LIST", seller.getUniqueId(), seller.getName(), id.toString(), item.getAmount(), price, "mode=" + mode + ";fee=" + fee + ";permanent=" + permanent);
        return new Result(true, "Listed " + item.getAmount() + "x " + item.getType().name() + " for " + plugin.money(price)
                + " (" + mode.name().replace('_', ' ') + (permanent ? ", NEVER EXPIRES" : "") + "). ID: " + shortId(id));
    }

    public Result bid(Player bidder, AuctionListing listing, double amount) {
        settleExpired();
        if (listing == null || !listings.containsKey(listing.id())) return new Result(false, "That listing no longer exists.");
        if (listing.mode() != AuctionListing.Mode.AUCTION) return new Result(false, "That listing is Buy It Now, not an auction.");
        if (listing.seller().equals(bidder.getUniqueId())) return new Result(false, "You cannot bid on your own auction.");
        double increment = Math.max(plugin.getConfig().getDouble("auction-house.minimum-bid-increment", 1),
                listing.highBid() * Math.max(0, plugin.getConfig().getDouble("auction-house.minimum-bid-increment-percent", 5)) / 100.0);
        double minimum = listing.hasBid() ? WalletManager.round(listing.highBid() + increment) : listing.startPrice();
        if (amount + 1e-9 < minimum) return new Result(false, "Minimum bid is " + plugin.money(minimum) + ".");
        if (!plugin.wallets().withdraw(bidder.getUniqueId(), amount)) return new Result(false, "You do not have enough money.");
        if (listing.highBidder() != null && !plugin.wallets().deposit(listing.highBidder(), listing.highBid())) {
            plugin.wallets().deposit(bidder.getUniqueId(), amount);
            return new Result(false, "Could not refund the previous bidder; bid cancelled safely.");
        }
        UUID previous = listing.highBidder();
        double oldBid = listing.highBid();
        listing.setHighBid(bidder.getUniqueId(), bidder.getName(), amount);
        save();
        plugin.market().audit("AH_BID", bidder.getUniqueId(), bidder.getName(), listing.id().toString(), listing.item().getAmount(), amount, "previous=" + previous + ";old=" + oldBid);
        return new Result(true, "You are now the high bidder at " + plugin.money(amount) + ".");
    }

    public Result buyNow(Player buyer, AuctionListing listing) {
        settleExpired();
        if (listing == null || !listings.containsKey(listing.id())) return new Result(false, "That listing no longer exists.");
        if (listing.mode() != AuctionListing.Mode.BUY_NOW) return new Result(false, "That listing is an auction.");
        if (listing.seller().equals(buyer.getUniqueId())) return new Result(false, "You cannot buy your own listing.");
        double price = listing.buyNowPrice();
        if (!plugin.wallets().withdraw(buyer.getUniqueId(), price)) return new Result(false, "You do not have enough money.");
        double payout = taxed(price);
        if (!plugin.wallets().deposit(listing.seller(), payout)) {
            plugin.wallets().deposit(buyer.getUniqueId(), price);
            return new Result(false, "Seller payment failed; purchase was cancelled safely.");
        }
        listings.remove(listing.id());
        giveOrClaim(buyer, listing.item());
        save();
        plugin.market().audit("AH_BUY_NOW", buyer.getUniqueId(), buyer.getName(), listing.id().toString(), listing.item().getAmount(), price, "seller=" + listing.seller() + ";payout=" + payout);
        return new Result(true, "Purchased for " + plugin.money(price) + ".");
    }

    public Result cancel(Player seller, AuctionListing listing) {
        settleExpired();
        if (listing == null || !listings.containsKey(listing.id())) return new Result(false, "That listing no longer exists.");
        if (!listing.seller().equals(seller.getUniqueId()) && !seller.hasPermission("incogshop.auction.admin")) return new Result(false, "You do not own that listing.");
        if (listing.mode() == AuctionListing.Mode.AUCTION && listing.hasBid()) return new Result(false, "An auction with bids cannot be cancelled.");
        listings.remove(listing.id());
        addClaim(listing.seller(), listing.item());
        save();
        plugin.market().audit("AH_CANCEL", seller.getUniqueId(), seller.getName(), listing.id().toString(), listing.item().getAmount(), listing.mode() == AuctionListing.Mode.BUY_NOW ? listing.buyNowPrice() : listing.startPrice(), "mode=" + listing.mode());
        return new Result(true, "Listing cancelled. The item was returned to your Auction House claims.");
    }

    public List<ItemStack> claimItems(UUID player) {
        return claims.getOrDefault(player, List.of()).stream().map(ItemStack::clone).toList();
    }

    public boolean claimIndex(Player player, int index) {
        List<ItemStack> pending = claims.get(player.getUniqueId());
        if (pending == null || index < 0 || index >= pending.size()) return false;
        ItemStack item = pending.remove(index);
        int overflow = plugin.stash().deliverOrStash(player, item);
        if (overflow > 0) player.sendMessage(plugin.prefix() + "§dYour inventory was full; auction claim overflow was moved to /stash.");
        if (pending.isEmpty()) claims.remove(player.getUniqueId());
        save();
        return true;
    }

    public int claim(Player player) {
        List<ItemStack> pending = claims.remove(player.getUniqueId());
        if (pending == null || pending.isEmpty()) return 0;
        int delivered = 0;
        int stashed = 0;
        for (ItemStack item : pending) {
            stashed += plugin.stash().deliverOrStash(player, item);
            delivered++;
        }
        save();
        if (stashed > 0) player.sendMessage(plugin.prefix() + "§dSome claimed auction items did not fit and were moved to /stash.");
        return delivered;
    }

    public int claimCount(UUID player) { return claims.getOrDefault(player, List.of()).size(); }

    public AuctionListing find(String idOrPrefix) {
        String raw = idOrPrefix.toLowerCase(Locale.ROOT);
        for (AuctionListing l : listings.values()) {
            String full = l.id().toString().toLowerCase(Locale.ROOT);
            if (full.equals(raw) || full.startsWith(raw)) return l;
        }
        return null;
    }

    public void settleExpired() {
        List<AuctionListing> expired = listings.values().stream().filter(AuctionListing::expired).toList();
        if (expired.isEmpty()) return;
        for (AuctionListing listing : expired) {
            listings.remove(listing.id());
            if (listing.mode() == AuctionListing.Mode.AUCTION && listing.hasBid()) {
                double payout = taxed(listing.highBid());
                if (!plugin.wallets().deposit(listing.seller(), payout)) {
                    // If seller payment fails, unwind escrow and return the item.
                    plugin.wallets().deposit(listing.highBidder(), listing.highBid());
                    addClaim(listing.seller(), listing.item());
                    plugin.getLogger().warning("Auction " + listing.id() + " settlement failed; bidder refunded and item returned.");
                } else {
                    addClaim(listing.highBidder(), listing.item());
                    plugin.market().audit("AH_SETTLE", listing.highBidder(), listing.highBidderName(), listing.id().toString(), listing.item().getAmount(), listing.highBid(), "seller=" + listing.seller() + ";payout=" + payout);
                }
            } else addClaim(listing.seller(), listing.item());
        }
        save();
    }

    private double taxed(double gross) {
        double tax = Math.max(0, Math.min(100, plugin.getConfig().getDouble("auction-house.sales-tax-percent", 3)));
        return WalletManager.round(gross * (1.0 - tax / 100.0));
    }

    private void giveOrClaim(Player player, ItemStack item) {
        int overflow = plugin.stash().deliverOrStash(player, item);
        if (overflow > 0) player.sendMessage(plugin.prefix() + "§dYour inventory was full; purchase overflow was moved to /stash.");
    }

    private void addClaim(UUID player, ItemStack item) { claims.computeIfAbsent(player, x -> new ArrayList<>()).add(item.clone()); }
    public static String shortId(UUID id) { return id.toString().substring(0, 8); }
}

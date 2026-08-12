package com.snipeyfresh.incogshop.auction;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class AuctionListing {
    public enum Mode { AUCTION, BUY_NOW }

    private final UUID id;
    private final UUID seller;
    private final String sellerName;
    private final ItemStack item;
    private final Mode mode;
    private final double startPrice;
    private final double buyNowPrice;
    private final long createdAt;
    private final long expiresAt;
    private UUID highBidder;
    private String highBidderName;
    private double highBid;

    public AuctionListing(UUID id, UUID seller, String sellerName, ItemStack item, Mode mode,
                          double startPrice, double buyNowPrice, long createdAt, long expiresAt,
                          UUID highBidder, String highBidderName, double highBid) {
        this.id = id; this.seller = seller; this.sellerName = sellerName; this.item = item.clone();
        this.mode = mode; this.startPrice = startPrice; this.buyNowPrice = buyNowPrice;
        this.createdAt = createdAt; this.expiresAt = expiresAt;
        this.highBidder = highBidder; this.highBidderName = highBidderName; this.highBid = highBid;
    }

    public UUID id() { return id; }
    public UUID seller() { return seller; }
    public String sellerName() { return sellerName; }
    public ItemStack item() { return item.clone(); }
    public Mode mode() { return mode; }
    public double startPrice() { return startPrice; }
    public double buyNowPrice() { return buyNowPrice; }
    public long createdAt() { return createdAt; }
    public long expiresAt() { return expiresAt; }
    public UUID highBidder() { return highBidder; }
    public String highBidderName() { return highBidderName; }
    public double highBid() { return highBid; }
    public boolean hasBid() { return highBidder != null && highBid > 0; }
    public boolean neverExpires() { return expiresAt <= 0; }
    public boolean expired() { return !neverExpires() && System.currentTimeMillis() >= expiresAt; }
    public void setHighBid(UUID bidder, String bidderName, double amount) {
        this.highBidder = bidder; this.highBidderName = bidderName; this.highBid = amount;
    }
}

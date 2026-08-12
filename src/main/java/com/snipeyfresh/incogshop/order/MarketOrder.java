package com.snipeyfresh.incogshop.order;

import org.bukkit.Material;

import java.util.UUID;

public final class MarketOrder {
    public enum Type { BUY, SELL }

    private final UUID id;
    private final Type type;
    private final UUID owner;
    private final String ownerName;
    private final Material material;
    private final int originalAmount;
    private int remaining;
    private final double unitPrice;
    private final long createdAt;

    public MarketOrder(UUID id, Type type, UUID owner, String ownerName, Material material,
                       int originalAmount, int remaining, double unitPrice, long createdAt) {
        this.id = id;
        this.type = type;
        this.owner = owner;
        this.ownerName = ownerName;
        this.material = material;
        this.originalAmount = originalAmount;
        this.remaining = remaining;
        this.unitPrice = unitPrice;
        this.createdAt = createdAt;
    }

    public UUID id() { return id; }
    public Type type() { return type; }
    public UUID owner() { return owner; }
    public String ownerName() { return ownerName; }
    public Material material() { return material; }
    public int originalAmount() { return originalAmount; }
    public int remaining() { return remaining; }
    public double unitPrice() { return unitPrice; }
    public long createdAt() { return createdAt; }
    public int filled() { return originalAmount - remaining; }
    public void fill(int amount) { remaining = Math.max(0, remaining - Math.max(0, amount)); }
}

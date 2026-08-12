package com.snipeyfresh.incogshop.shop;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class PlayerShop {
    private final UUID id;
    private final UUID owner;
    private final String ownerName;
    private final Location location;
    private ItemStack item;
    private double price;

    public PlayerShop(UUID id, UUID owner, String ownerName, Location location, ItemStack item, double price) {
        this.id = id; this.owner = owner; this.ownerName = ownerName; this.location = location;
        this.item = item.clone(); this.item.setAmount(1); this.price = price;
    }
    public UUID id() { return id; }
    public UUID owner() { return owner; }
    public String ownerName() { return ownerName; }
    public Location location() { return location; }
    public ItemStack item() { return item.clone(); }
    public double price() { return price; }
    public void price(double price) { this.price = price; }
    public void item(ItemStack item) { this.item = item.clone(); this.item.setAmount(1); }
}

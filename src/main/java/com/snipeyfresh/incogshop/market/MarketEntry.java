package com.snipeyfresh.incogshop.market;

public final class MarketEntry {
    private long stock;
    private double basePrice;
    private double pressure;

    public MarketEntry(long stock, double basePrice, double pressure) {
        this.stock = Math.max(0, stock);
        this.basePrice = Math.max(0.01, basePrice);
        this.pressure = pressure;
    }

    public long stock() { return stock; }
    public double basePrice() { return basePrice; }
    public double pressure() { return pressure; }
    public void stock(long value) { stock = Math.max(0, value); }
    public void basePrice(double value) { basePrice = Math.max(0.01, value); }
    public void pressure(double value) { pressure = value; }
}

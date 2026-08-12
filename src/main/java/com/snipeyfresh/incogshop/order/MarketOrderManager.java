package com.snipeyfresh.incogshop.order;

import com.snipeyfresh.incogshop.IncogShopPlugin;
import com.snipeyfresh.incogshop.economy.WalletManager;
import com.snipeyfresh.incogshop.market.MarketEntry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class MarketOrderManager {
    public record Result(boolean success, String message) {}

    private final IncogShopPlugin plugin;
    private final File file;
    private final Map<UUID, MarketOrder> orders = new LinkedHashMap<>();
    private final Map<UUID, EnumMap<Material, Integer>> itemClaims = new HashMap<>();

    public MarketOrderManager(IncogShopPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "market-orders.yml");
    }

    public void load() {
        orders.clear();
        itemClaims.clear();
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        var section = yml.getConfigurationSection("orders");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    String root = "orders." + key;
                    UUID id = UUID.fromString(key);
                    MarketOrder.Type type = MarketOrder.Type.valueOf(yml.getString(root + ".type", "BUY"));
                    UUID owner = UUID.fromString(Objects.requireNonNull(yml.getString(root + ".owner")));
                    String ownerName = yml.getString(root + ".owner-name", "Unknown");
                    Material material = Material.matchMaterial(Objects.requireNonNull(yml.getString(root + ".material")));
                    int original = yml.getInt(root + ".original-amount", 0);
                    int remaining = yml.getInt(root + ".remaining", original);
                    double unitPrice = yml.getDouble(root + ".unit-price", 0);
                    long createdAt = yml.getLong(root + ".created-at", System.currentTimeMillis());
                    if (material != null && original > 0 && remaining > 0 && unitPrice > 0) {
                        orders.put(id, new MarketOrder(id, type, owner, ownerName, material, original, remaining, unitPrice, createdAt));
                    }
                } catch (Exception ex) {
                    plugin.getLogger().warning("Skipping invalid market order " + key + ": " + ex.getMessage());
                }
            }
        }
        var claims = yml.getConfigurationSection("item-claims");
        if (claims != null) {
            for (String uuidRaw : claims.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidRaw);
                    EnumMap<Material, Integer> map = new EnumMap<>(Material.class);
                    var playerSection = yml.getConfigurationSection("item-claims." + uuidRaw);
                    if (playerSection != null) {
                        for (String materialRaw : playerSection.getKeys(false)) {
                            Material material = Material.matchMaterial(materialRaw);
                            int amount = playerSection.getInt(materialRaw, 0);
                            if (material != null && amount > 0) map.put(material, amount);
                        }
                    }
                    if (!map.isEmpty()) itemClaims.put(uuid, map);
                } catch (IllegalArgumentException ignored) {}
            }
        }
        matchAll();
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (MarketOrder order : orders.values()) {
            String root = "orders." + order.id();
            yml.set(root + ".type", order.type().name());
            yml.set(root + ".owner", order.owner().toString());
            yml.set(root + ".owner-name", order.ownerName());
            yml.set(root + ".material", order.material().name());
            yml.set(root + ".original-amount", order.originalAmount());
            yml.set(root + ".remaining", order.remaining());
            yml.set(root + ".unit-price", order.unitPrice());
            yml.set(root + ".created-at", order.createdAt());
        }
        for (var entry : itemClaims.entrySet()) {
            for (var claim : entry.getValue().entrySet()) {
                yml.set("item-claims." + entry.getKey() + "." + claim.getKey().name(), claim.getValue());
            }
        }
        try { yml.save(file); }
        catch (IOException ex) { plugin.getLogger().severe("Could not save market-orders.yml: " + ex.getMessage()); }
    }

    public Collection<MarketOrder> all() { return Collections.unmodifiableCollection(orders.values()); }
    public MarketOrder get(UUID id) { return orders.get(id); }
    public MarketOrder find(String shortOrFull) {
        if (shortOrFull == null) return null;
        String q = shortOrFull.toLowerCase(Locale.ROOT);
        for (MarketOrder order : orders.values()) {
            String full = order.id().toString().toLowerCase(Locale.ROOT);
            if (full.equals(q) || full.startsWith(q)) return order;
        }
        return null;
    }

    public List<MarketOrder> buyOrders(Material material) {
        return orders.values().stream().filter(o -> o.type() == MarketOrder.Type.BUY && o.material() == material && o.remaining() > 0)
                .sorted(Comparator.comparingDouble(MarketOrder::unitPrice).reversed().thenComparingLong(MarketOrder::createdAt)).toList();
    }

    public List<MarketOrder> sellOrders(Material material) {
        return orders.values().stream().filter(o -> o.type() == MarketOrder.Type.SELL && o.material() == material && o.remaining() > 0)
                .sorted(Comparator.comparingDouble(MarketOrder::unitPrice).thenComparingLong(MarketOrder::createdAt)).toList();
    }

    public List<MarketOrder> owned(UUID owner) {
        return orders.values().stream().filter(o -> o.owner().equals(owner))
                .sorted(Comparator.comparingLong(MarketOrder::createdAt)).toList();
    }

    public double bestBuy(Material material) {
        return buyOrders(material).stream().findFirst().map(MarketOrder::unitPrice).orElse(0.0);
    }
    public double bestSell(Material material) {
        return sellOrders(material).stream().findFirst().map(MarketOrder::unitPrice).orElse(0.0);
    }

    public Result createBuy(Player player, Material material, int amount, double unitPrice) {
        Result valid = validateCreate(player, material, amount, unitPrice);
        if (!valid.success()) return valid;
        if (!plugin.market().isBuyAllowed(material)) return new Result(false, "That material is Sell Only and cannot be bought - not even with a Buy Order.");
        double escrow = WalletManager.round(unitPrice * amount);
        if (!Double.isFinite(escrow) || escrow <= 0) return new Result(false, "That order total is invalid or too large.");
        if (!plugin.wallets().withdraw(player.getUniqueId(), escrow)) return new Result(false, "You need " + plugin.money(escrow) + " available to fund this Buy Order.");
        MarketOrder order = new MarketOrder(UUID.randomUUID(), MarketOrder.Type.BUY, player.getUniqueId(), player.getName(), material, amount, amount, unitPrice, System.currentTimeMillis());
        orders.put(order.id(), order);
        plugin.market().audit("ORDER_BUY_CREATE", player.getUniqueId(), player.getName(), material.name(), amount, escrow, "price=" + unitPrice + ";id=" + order.id());
        plugin.transactionLog().createdBuyOffer(player, material, amount, escrow, unitPrice);
        matchBuy(order);
        if (order.remaining() <= 0) orders.remove(order.id());
        save();
        return new Result(true, order.remaining() <= 0
                ? "Your Buy Order filled immediately."
                : "Buy Order placed for " + order.remaining() + "x " + material.name() + " at up to " + plugin.money(unitPrice) + " each. ID: " + shortId(order.id()));
    }

    public Result createSell(Player player, Material material, int amount, double unitPrice) {
        Result valid = validateCreate(player, material, amount, unitPrice);
        if (!valid.success()) return valid;
        MarketEntry reference = plugin.market().entry(material);
        if (reference != null) {
            double minMult = Math.max(0, plugin.getConfig().getDouble("market-orders.sell-price-min-multiplier", 0.5));
            double maxMult = Math.max(minMult, plugin.getConfig().getDouble("market-orders.sell-price-max-multiplier", 2.0));
            double min = reference.basePrice() * minMult;
            double max = reference.basePrice() * maxMult;
            if (unitPrice < min - 1e-9 || unitPrice > max + 1e-9) {
                return new Result(false, "Sell Order price must be between " + plugin.money(min) + " and " + plugin.money(max) + " per item for " + material.name() + ".");
            }
        }
        if (!removePlain(player, material, amount)) return new Result(false, "You do not have " + amount + " eligible plain " + material.name() + " item(s) to escrow.");
        MarketOrder order = new MarketOrder(UUID.randomUUID(), MarketOrder.Type.SELL, player.getUniqueId(), player.getName(), material, amount, amount, unitPrice, System.currentTimeMillis());
        orders.put(order.id(), order);
        plugin.market().audit("ORDER_SELL_CREATE", player.getUniqueId(), player.getName(), material.name(), amount, unitPrice * amount, "price=" + unitPrice + ";id=" + order.id());
        plugin.transactionLog().createdSellOffer(player, material, amount, WalletManager.round(unitPrice * amount), unitPrice);
        matchSell(order);
        if (order.remaining() <= 0) orders.remove(order.id());
        save();
        return new Result(true, order.remaining() <= 0
                ? "Your Sell Order filled immediately."
                : "Sell Order placed for " + order.remaining() + "x " + material.name() + " at " + plugin.money(unitPrice) + " each. ID: " + shortId(order.id()));
    }

    private Result validateCreate(Player player, Material material, int amount, double unitPrice) {
        if (!plugin.getConfig().getBoolean("market-orders.enabled", true)) return new Result(false, "Market Orders are disabled.");
        if (material == null || !plugin.market().isTradable(material)) return new Result(false, "That material is not available in the market.");
        if (amount <= 0) return new Result(false, "Amount must be positive.");
        int maxAmount = Math.max(1, plugin.getConfig().getInt("market-orders.maximum-items-per-order", 100000));
        if (amount > maxAmount) return new Result(false, "An order can contain at most " + maxAmount + " items.");
        if (unitPrice <= 0 || !Double.isFinite(unitPrice)) return new Result(false, "Unit price must be positive.");
        int maxOrders = Math.max(1, plugin.getConfig().getInt("market-orders.max-active-orders-per-player", 20));
        if (owned(player.getUniqueId()).size() >= maxOrders && !player.hasPermission("incogshop.orders.bypasslimit")) return new Result(false, "You already have the maximum of " + maxOrders + " active market orders.");
        return new Result(true, "OK");
    }

    public Result cancel(Player player, MarketOrder order) {
        if (order == null || !orders.containsKey(order.id())) return new Result(false, "That market order no longer exists.");
        if (!order.owner().equals(player.getUniqueId()) && !player.hasPermission("incogshop.orders.admin")) return new Result(false, "That is not your market order.");
        orders.remove(order.id());
        if (order.type() == MarketOrder.Type.BUY) {
            double refund = WalletManager.round(order.unitPrice() * order.remaining());
            plugin.wallets().depositOrQueue(order.owner(), refund);
        } else {
            addClaim(order.owner(), order.material(), order.remaining());
        }
        save();
        plugin.market().audit("ORDER_CANCEL", player.getUniqueId(), player.getName(), order.material().name(), order.remaining(), order.unitPrice(), "id=" + order.id() + ";type=" + order.type());
        return new Result(true, "Cancelled order " + shortId(order.id()) + ". Escrow was returned.");
    }

    private void matchBuy(MarketOrder buy) {
        List<MarketOrder> candidates = new ArrayList<>(sellOrders(buy.material()));
        for (MarketOrder sell : candidates) {
            if (buy.remaining() <= 0) break;
            if (sell.unitPrice() > buy.unitPrice() + 1e-9) break;
            int qty = Math.min(buy.remaining(), sell.remaining());
            double tradePrice = sell.unitPrice(); // resting/older sell order is maker
            if (!settleFill(buy, sell, qty, tradePrice)) break;
        }
    }

    private void matchSell(MarketOrder sell) {
        List<MarketOrder> candidates = new ArrayList<>(buyOrders(sell.material()));
        for (MarketOrder buy : candidates) {
            if (sell.remaining() <= 0) break;
            if (buy.unitPrice() + 1e-9 < sell.unitPrice()) break;
            int qty = Math.min(sell.remaining(), buy.remaining());
            double tradePrice = buy.unitPrice(); // resting/older buy order is maker
            if (!settleFill(buy, sell, qty, tradePrice)) break;
        }
    }

    private boolean settleFill(MarketOrder buy, MarketOrder sell, int qty, double tradePrice) {
        double gross = WalletManager.round(tradePrice * qty);
        double taxPct = Math.max(0, plugin.getConfig().getDouble("market-orders.sales-tax-percent", 1.0));
        double sellerPayout = WalletManager.round(gross * (1.0 - taxPct / 100.0));
        double buyerRefund = WalletManager.round(Math.max(0, (buy.unitPrice() - tradePrice) * qty));

        plugin.wallets().depositOrQueue(sell.owner(), sellerPayout);
        if (buyerRefund > 0) plugin.wallets().depositOrQueue(buy.owner(), buyerRefund);

        buy.fill(qty);
        sell.fill(qty);
        addClaim(buy.owner(), buy.material(), qty);
        if (buy.remaining() <= 0) orders.remove(buy.id());
        if (sell.remaining() <= 0) orders.remove(sell.id());
        plugin.market().audit("ORDER_FILL", buy.owner(), buy.ownerName(), buy.material().name(), qty, gross,
                "buy=" + buy.id() + ";sell=" + sell.id() + ";unit=" + tradePrice + ";seller=" + sell.ownerName());
        return true;
    }

    private void matchAll() {
        List<Material> materials = orders.values().stream().map(MarketOrder::material).distinct().toList();
        for (Material material : materials) {
            boolean progress;
            do {
                progress = false;
                MarketOrder buy = buyOrders(material).stream().findFirst().orElse(null);
                MarketOrder sell = sellOrders(material).stream().findFirst().orElse(null);
                if (buy != null && sell != null && buy.unitPrice() + 1e-9 >= sell.unitPrice()) {
                    int before = buy.remaining() + sell.remaining();
                    // Trade at the older order's price.
                    double price = buy.createdAt() <= sell.createdAt() ? buy.unitPrice() : sell.unitPrice();
                    int qty = Math.min(buy.remaining(), sell.remaining());
                    if (settleFill(buy, sell, qty, price)) progress = before > buy.remaining() + sell.remaining();
                }
            } while (progress);
        }
        save();
    }

    private boolean removePlain(Player player, Material material, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != material || !plugin.market().hasOnlyAllowedMarketMeta(item)) continue;
            int take = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - take);
            if (item.getAmount() <= 0) contents[i] = null;
            remaining -= take;
        }
        if (remaining != 0) return false;
        player.getInventory().setStorageContents(contents);
        return true;
    }

    private void addClaim(UUID owner, Material material, int amount) {
        if (amount <= 0) return;
        EnumMap<Material, Integer> map = itemClaims.computeIfAbsent(owner, ignored -> new EnumMap<>(Material.class));
        map.merge(material, amount, (a, b) -> Math.addExact(a, b));
        Player online = Bukkit.getPlayer(owner);
        if (online != null) claim(online);
    }

    public int claimCount(UUID owner) {
        return itemClaims.getOrDefault(owner, new EnumMap<>(Material.class)).values().stream().mapToInt(Integer::intValue).sum();
    }

    public int claim(Player player) {
        EnumMap<Material, Integer> map = itemClaims.get(player.getUniqueId());
        if (map == null || map.isEmpty()) return 0;
        int delivered = 0;
        for (Material material : new ArrayList<>(map.keySet())) {
            int remaining = map.getOrDefault(material, 0);
            int max = Math.max(1, material.getMaxStackSize());
            while (remaining > 0) {
                int amount = Math.min(max, remaining);
                ItemStack stack = new ItemStack(material, amount);
                Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack);
                int left = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
                int given = amount - left;
                if (given <= 0) break;
                remaining -= given;
                delivered += given;
                if (left > 0) break;
            }
            if (remaining <= 0) map.remove(material); else map.put(material, remaining);
        }
        if (map.isEmpty()) itemClaims.remove(player.getUniqueId());
        save();
        return delivered;
    }

    public static String shortId(UUID id) { return id.toString().substring(0, 8); }
}

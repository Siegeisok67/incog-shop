package com.snipeyfresh.incogshop.market;

import com.snipeyfresh.incogshop.IncogShopPlugin;
import com.snipeyfresh.incogshop.economy.WalletManager;
import com.snipeyfresh.incogshop.gui.MarketCategory;
import com.snipeyfresh.incogshop.gui.MarketSubcategory;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

public final class MarketManager {
    public record TradeResult(boolean success, String message, int amount, double total) {}

    private static final Set<String> HARD_EXCLUDED = Set.of(
            "AIR", "CAVE_AIR", "VOID_AIR", "ANCIENT_DEBRIS", "BEDROCK", "BARRIER", "COMMAND_BLOCK", "CHAIN_COMMAND_BLOCK",
            "REPEATING_COMMAND_BLOCK", "COMMAND_BLOCK_MINECART", "STRUCTURE_BLOCK", "STRUCTURE_VOID", "JIGSAW",
            "LIGHT", "DEBUG_STICK", "END_PORTAL_FRAME", "SPAWNER", "TRIAL_SPAWNER", "VAULT", "KNOWLEDGE_BOOK"
    );

    private final IncogShopPlugin plugin;
    private final File file;
    private final File auditFile;
    private final Map<Material, MarketEntry> entries = new EnumMap<>(Material.class);
    private final EnumMap<Material, MarketMode> modes = new EnumMap<>(Material.class);
    private final Map<Material, MarketCategory> categoryOverrides = new EnumMap<>(Material.class);
    private final Map<Material, MarketSubcategory> subcategoryOverrides = new EnumMap<>(Material.class);
    private List<Material> tradable = List.of();
    private boolean stockBootstrapApplied;
    private long lastRestockAt;

    /** Rolling in-memory log of completed trades per material, used to compute recent buy/sell volume
     *  (e.g. for custom-sell price negotiation). Not persisted - it only needs to cover a short window. */
    private record VolumeRecord(long timestampMillis, int signedAmount) {}
    private final Map<Material, Deque<VolumeRecord>> volumeLog = new EnumMap<>(Material.class);

    private void recordVolume(Material material, int signedAmount) {
        if (signedAmount == 0) return;
        volumeLog.computeIfAbsent(material, m -> new ArrayDeque<>())
                .addLast(new VolumeRecord(System.currentTimeMillis(), signedAmount));
    }

    /**
     * Net buy/sell volume for a material within the trailing window. Positive signedAmount entries are
     * items players bought FROM the market (demand); negative entries are items players sold TO the
     * market (supply). Also prunes entries older than the window as a side effect.
     * @return {boughtQuantity, soldQuantity}, both non-negative.
     */
    public double[] recentVolume(Material material, long windowMillis) {
        Deque<VolumeRecord> log = volumeLog.get(material);
        if (log == null) return new double[]{0, 0};
        long cutoff = System.currentTimeMillis() - Math.max(0, windowMillis);
        while (!log.isEmpty() && log.peekFirst().timestampMillis() < cutoff) log.pollFirst();
        double bought = 0, sold = 0;
        for (VolumeRecord record : log) {
            if (record.signedAmount() > 0) bought += record.signedAmount();
            else sold += -record.signedAmount();
        }
        return new double[]{bought, sold};
    }

    public MarketManager(IncogShopPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "market.yml");
        this.auditFile = new File(plugin.getDataFolder(), "audit.log");
    }

    public void load() {
        entries.clear();
        modes.clear();
        categoryOverrides.clear();
        subcategoryOverrides.clear();
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        int initial = plugin.getConfig().getInt("market.initial-stock", 1000);
        stockBootstrapApplied = yml.getBoolean("meta.initial-stock-bootstrap-1_5", false);
        lastRestockAt = yml.getLong("meta.last-low-stock-restock", 0L);
        boolean initializeRestockClock = lastRestockAt <= 0;
        if (initializeRestockClock) lastRestockAt = System.currentTimeMillis();
        Set<String> configExcluded = new HashSet<>(plugin.getConfig().getStringList("market.excluded-materials"));
        List<Material> materials = new ArrayList<>();
        for (Material material : Material.values()) {
            if (!material.isItem() || HARD_EXCLUDED.contains(material.name()) || configExcluded.contains(material.name())) continue;
            double base = yml.getDouble(material.name() + ".base-price", defaultBasePrice(material));
            long stock = yml.getLong(material.name() + ".stock", initial);
            double pressure = yml.getDouble(material.name() + ".pressure", 0.0);
            entries.put(material, new MarketEntry(stock, base, pressure));
            String modeRaw = yml.getString(material.name() + ".mode", null);
            MarketMode mode;
            try { mode = modeRaw == null ? (yml.getBoolean(material.name() + ".enabled", true) ? MarketMode.BUY_SELL : MarketMode.DISABLED) : MarketMode.valueOf(modeRaw); }
            catch (IllegalArgumentException ex) { mode = MarketMode.BUY_SELL; }
            modes.put(material, mode);
            String savedCategory = yml.getString(material.name() + ".category", "");
            String savedSubcategory = yml.getString(material.name() + ".subcategory", "");
            try {
                if (savedCategory != null && !savedCategory.isBlank()) {
                    MarketCategory category = MarketCategory.valueOf(savedCategory);
                    if (category != MarketCategory.ALL) categoryOverrides.put(material, category);
                }
            } catch (IllegalArgumentException ignored) {}
            try {
                if (savedSubcategory != null && !savedSubcategory.isBlank()) {
                    MarketSubcategory subcategory = MarketSubcategory.valueOf(savedSubcategory);
                    MarketCategory category = categoryOverrides.get(material);
                    if (category != null && subcategory.parent() == category) subcategoryOverrides.put(material, subcategory);
                }
            } catch (IllegalArgumentException ignored) {}
            materials.add(material);
        }
        if (!stockBootstrapApplied) {
            for (MarketEntry entry : entries.values()) if (entry.stock() < initial) entry.stock(initial);
            stockBootstrapApplied = true;
            plugin.getLogger().info("Applied one-time market stock bootstrap: every tradable material now has at least " + initial + " stock.");
            save();
        }
        materials.sort(Comparator.comparing(Material::name));
        tradable = Collections.unmodifiableList(materials);
        if (initializeRestockClock && stockBootstrapApplied) save();
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("meta.initial-stock-bootstrap-1_5", stockBootstrapApplied);
        yml.set("meta.last-low-stock-restock", lastRestockAt);
        for (var e : entries.entrySet()) {
            String root = e.getKey().name();
            yml.set(root + ".stock", e.getValue().stock());
            yml.set(root + ".base-price", WalletManager.round(e.getValue().basePrice()));
            yml.set(root + ".pressure", e.getValue().pressure());
            MarketMode mode = modes.getOrDefault(e.getKey(), MarketMode.BUY_SELL);
            yml.set(root + ".mode", mode.name());
            yml.set(root + ".enabled", mode != MarketMode.DISABLED); // legacy compatibility
            MarketCategory category = categoryOverrides.get(e.getKey());
            MarketSubcategory subcategory = subcategoryOverrides.get(e.getKey());
            yml.set(root + ".category", category == null ? null : category.name());
            yml.set(root + ".subcategory", subcategory == null ? null : subcategory.name());
        }
        try { yml.save(file); }
        catch (IOException ex) { plugin.getLogger().severe("Could not save market.yml: " + ex.getMessage()); }
    }

    public List<Material> tradableMaterials() { return tradable; }
    public boolean isTradable(Material m) { return entries.containsKey(m) && marketMode(m) != MarketMode.DISABLED; }
    public boolean isMarketEnabled(Material m) { return isTradable(m); }
    public boolean isBuyAllowed(Material m) { return entries.containsKey(m) && marketMode(m) == MarketMode.BUY_SELL; }
    public boolean isSellAllowed(Material m) { return entries.containsKey(m) && marketMode(m) != MarketMode.DISABLED; }
    public MarketMode marketMode(Material m) { return modes.getOrDefault(m, MarketMode.BUY_SELL); }

    public boolean setMarketMode(Material material, MarketMode mode, String actor) {
        if (!entries.containsKey(material) || mode == null) return false;
        MarketMode old = marketMode(material);
        modes.put(material, mode);
        audit("ADMIN_MARKET_MODE", null, actor, material.name(), 0, 0, "old=" + old + ";new=" + mode);
        save();
        return true;
    }

    public boolean setMarketEnabled(Material material, boolean enabled, String actor) {
        return setMarketMode(material, enabled ? MarketMode.BUY_SELL : MarketMode.DISABLED, actor);
    }
    public boolean toggleMarketEnabled(Material material, String actor) {
        return setMarketMode(material, marketMode(material).next(), actor);
    }
    public MarketEntry entry(Material m) { return entries.get(m); }

    public boolean addOrEnableMaterial(Material material, double basePrice, MarketMode mode, String actor) {
        if (material == null || !material.isItem() || HARD_EXCLUDED.contains(material.name()) || material == Material.ANCIENT_DEBRIS) return false;
        if (!entries.containsKey(material)) {
            long initial = plugin.getConfig().getLong("market.initial-stock", 1000);
            entries.put(material, new MarketEntry(initial, basePrice > 0 ? basePrice : defaultBasePrice(material), 0));
            List<Material> rebuilt = new ArrayList<>(entries.keySet());
            rebuilt.sort(Comparator.comparing(Material::name));
            tradable = Collections.unmodifiableList(rebuilt);
        }
        if (basePrice > 0 && Double.isFinite(basePrice)) entries.get(material).basePrice(basePrice);
        modes.put(material, mode == null ? MarketMode.BUY_SELL : mode);
        audit("ADMIN_MARKET_ADD", null, actor, material.name(), 0, entries.get(material).basePrice(), "mode=" + marketMode(material));
        save();
        return true;
    }

    public MarketCategory categoryOf(Material material) {
        MarketCategory override = categoryOverrides.get(material);
        if (override != null) return override;
        MarketCategory[] priority = {
                MarketCategory.REDSTONE, MarketCategory.TOOLS, MarketCategory.FARMING,
                MarketCategory.MOB_DROPS, MarketCategory.NETHER_END, MarketCategory.ORES,
                MarketCategory.DECORATION, MarketCategory.BUILDING, MarketCategory.BLOCKS
        };
        for (MarketCategory category : priority) if (category.matches(material)) return category;
        return MarketCategory.MISC;
    }

    public MarketSubcategory subcategoryOf(Material material) {
        MarketCategory category = categoryOf(material);
        MarketSubcategory override = subcategoryOverrides.get(material);
        if (override != null && override.parent() == category) return override;
        return MarketSubcategory.defaultFor(material, category);
    }

    public boolean hasCategoryOverride(Material material) { return categoryOverrides.containsKey(material); }

    public boolean setClassification(Material material, MarketCategory category, MarketSubcategory subcategory, String actor) {
        if (!entries.containsKey(material) || category == null || category == MarketCategory.ALL || subcategory == null || subcategory.parent() != category) return false;
        MarketCategory oldCategory = categoryOf(material);
        MarketSubcategory oldSubcategory = subcategoryOf(material);
        categoryOverrides.put(material, category);
        subcategoryOverrides.put(material, subcategory);
        audit("ADMIN_CATEGORY", null, actor, material.name(), 0, 0, "old=" + oldCategory.name() + "/" + oldSubcategory.name() + ";new=" + category.name() + "/" + subcategory.name());
        save();
        return true;
    }

    public boolean clearClassificationOverride(Material material, String actor) {
        if (!entries.containsKey(material)) return false;
        MarketCategory oldCategory = categoryOf(material);
        MarketSubcategory oldSubcategory = subcategoryOf(material);
        categoryOverrides.remove(material);
        subcategoryOverrides.remove(material);
        MarketCategory newCategory = categoryOf(material);
        MarketSubcategory newSubcategory = subcategoryOf(material);
        audit("ADMIN_CATEGORY_RESET", null, actor, material.name(), 0, 0, "old=" + oldCategory.name() + "/" + oldSubcategory.name() + ";new=" + newCategory.name() + "/" + newSubcategory.name());
        save();
        return true;
    }

    public boolean isInfiniteStockEnabled() {
        return plugin.getConfig().getBoolean("market.infinite-stock-enabled", false);
    }

    public void setInfiniteStockEnabled(boolean enabled, String actor) {
        boolean old = isInfiniteStockEnabled();
        plugin.getConfig().set("market.infinite-stock-enabled", enabled);
        plugin.saveConfig();
        audit("ADMIN_INFINITE_STOCK", null, actor, "GLOBAL", 0, enabled ? 1 : 0, "old=" + old);
    }

    public double buyUnitPrice(Material m) {
        MarketEntry e = entries.get(m);
        if (e == null) return 0;
        double target = Math.max(1, plugin.getConfig().getDouble("market.target-stock", 512));
        double floor = plugin.getConfig().getDouble("market.minimum-price-multiplier", 0.35);
        double ceiling = plugin.getConfig().getDouble("market.maximum-price-multiplier", 4.0);
        double scarcityStock = Math.max(e.stock(), target * 0.05);
        double scarcity = Math.sqrt(target / scarcityStock);
        double demand = Math.exp(e.pressure());
        double multiplier = Math.max(floor, Math.min(ceiling, scarcity * demand));
        double scale = Math.max(0.01, plugin.getConfig().getDouble("market.price-scale", 1.0));
        return Math.max(0.01, WalletManager.round(e.basePrice() * multiplier * scale));
    }

    public double sellUnitPrice(Material m) {
        double spread = clamp(plugin.getConfig().getDouble("market.buy-sell-spread", 0.22), 0, 0.95);
        return Math.max(0.01, WalletManager.round(buyUnitPrice(m) * (1.0 - spread)));
    }

    public TradeResult buy(Player player, Material material, int requested) {
        MarketEntry e = entries.get(material);
        if (e == null || !isBuyAllowed(material)) return new TradeResult(false,
                marketMode(material) == MarketMode.SELL_ONLY ? "That item is Sell Only and cannot be bought from server stock." : "That material is not currently available in the market.", 0, 0);
        int amount;
        if (isInfiniteStockEnabled()) {
            amount = Math.max(1, requested);
        } else {
            amount = (int) Math.min(Math.max(1, requested), Math.min(Integer.MAX_VALUE, e.stock()));
            if (amount <= 0) return new TradeResult(false, "That item is out of stock.", 0, 0);
        }
        amount = fitInventory(player, material, amount);
        if (amount <= 0) return new TradeResult(false, "You do not have enough inventory space.", 0, 0);

        double subtotal = buyUnitPrice(material) * amount;
        double feePct = Math.max(0, plugin.getConfig().getDouble("market.transaction-fee-percent", 2.0));
        double total = WalletManager.round(subtotal * (1 + feePct / 100.0));
        if (!plugin.wallets().withdraw(player.getUniqueId(), total))
            return new TradeResult(false, "You do not have enough money.", 0, total);

        if (!isInfiniteStockEnabled()) e.stock(e.stock() - amount);
        pushPressure(e, amount);
        recordVolume(material, amount);
        player.getInventory().addItem(new ItemStack(material, amount));
        audit("MARKET_BUY", player.getUniqueId(), player.getName(), material.name(), amount, total, "");
        return new TradeResult(true, "Bought " + amount + "x " + material.name() + ".", amount, total);
    }

    public TradeResult sell(Player player, Material material, int requested) { return sell(player, material, requested, null); }

    /** @param unitPriceOverride when non-null, this exact per-unit price is used instead of the standard
     *  dynamic sellUnitPrice(material) - used by the /sell custom-price negotiation flow, which computes
     *  its own balanced price ahead of time. */
    public TradeResult sell(Player player, Material material, int requested, Double unitPriceOverride) {
        MarketEntry e = entries.get(material);
        if (e == null || !isSellAllowed(material)) return new TradeResult(false, "That material cannot currently be sold to the market.", 0, 0);
        long maxStock = Math.max(1, plugin.getConfig().getLong("market.maximum-stock-per-material", 1_000_000));
        int available = countPlain(player, material);
        int amount = Math.min(Math.max(1, requested), available);
        amount = (int) Math.min(amount, Math.max(0, maxStock - e.stock()));
        if (amount <= 0) return new TradeResult(false, available <= 0 ? "You have no eligible plain items to sell." : "The market is at maximum stock for this item.", 0, 0);

        double unitPrice = unitPriceOverride != null ? Math.max(0.01, unitPriceOverride) : sellUnitPrice(material);
        double gross = unitPrice * amount;
        double feePct = Math.max(0, plugin.getConfig().getDouble("market.transaction-fee-percent", 2.0));
        double payout = WalletManager.round(gross * (1 - feePct / 100.0));
        if (!removePlain(player, material, amount)) return new TradeResult(false, "Your inventory changed before the sale completed.", 0, 0);

        e.stock(e.stock() + amount);
        pushPressure(e, -amount);
        recordVolume(material, -amount);
        plugin.wallets().deposit(player.getUniqueId(), payout);
        audit(unitPriceOverride != null ? "MARKET_SELL_CUSTOM" : "MARKET_SELL", player.getUniqueId(), player.getName(), material.name(), amount, payout, "");
        return new TradeResult(true, "Sold " + amount + "x " + material.name() + ".", amount, payout);
    }

    /** Sells only the ItemStack currently in the player's main hand (not other matching stacks elsewhere
     *  in the inventory), for /sell hand. See {@link #sell(Player, Material, int, Double)} for unitPriceOverride. */
    public TradeResult sellHand(Player player, Double unitPriceOverride) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!isEligibleStack(hand)) return new TradeResult(false, "Hold an eligible, plain item in your hand to sell it.", 0, 0);
        Material material = hand.getType();
        MarketEntry e = entries.get(material);
        long maxStock = Math.max(1, plugin.getConfig().getLong("market.maximum-stock-per-material", 1_000_000));
        int amount = (int) Math.min(hand.getAmount(), Math.max(0, maxStock - e.stock()));
        if (amount <= 0) return new TradeResult(false, "The market is at maximum stock for this item.", 0, 0);

        double unitPrice = unitPriceOverride != null ? Math.max(0.01, unitPriceOverride) : sellUnitPrice(material);
        double gross = unitPrice * amount;
        double feePct = Math.max(0, plugin.getConfig().getDouble("market.transaction-fee-percent", 2.0));
        double payout = WalletManager.round(gross * (1 - feePct / 100.0));

        e.stock(e.stock() + amount);
        pushPressure(e, -amount);
        recordVolume(material, -amount);
        if (!plugin.wallets().deposit(player.getUniqueId(), payout)) {
            e.stock(e.stock() - amount);
            pushPressure(e, amount);
            recordVolume(material, amount);
            return new TradeResult(false, "The economy provider rejected the payment.", 0, 0);
        }
        if (amount >= hand.getAmount()) player.getInventory().setItemInMainHand(null);
        else hand.setAmount(hand.getAmount() - amount);
        audit(unitPriceOverride != null ? "MARKET_SELL_CUSTOM" : "MARKET_SELL_HAND", player.getUniqueId(), player.getName(), material.name(), amount, payout, "");
        return new TradeResult(true, "Sold " + amount + "x " + material.name() + ".", amount, payout);
    }

    public boolean isEligibleStack(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        if (!isSellAllowed(item.getType())) return false;
        boolean rejectCustom = plugin.getConfig().getBoolean("market.reject-custom-items", true);
        return !rejectCustom || hasOnlyAllowedMarketMeta(item);
    }

    /**
     * The global market ignores ordinary durability damage, but still rejects
     * meaningful/custom metadata such as names, lore, enchantments, custom
     * model/data components, unbreakable state, attributes, etc.
     */
    public boolean hasOnlyAllowedMarketMeta(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        if (!item.hasItemMeta()) return true;

        ItemStack normalized = item.clone();
        normalized.setAmount(1);
        ItemMeta meta = normalized.getItemMeta();

        if (meta instanceof Damageable damageable && damageable.hasDamage()) {
            damageable.setDamage(0);
            normalized.setItemMeta(meta);
        }

        ItemStack pristine = new ItemStack(item.getType(), 1);
        return normalized.isSimilar(pristine);
    }

    /** Sells an ItemStack already held in an escrow GUI. Unsold remainder is returned in the result amount difference. */
    public TradeResult sellEscrowStack(Player player, ItemStack item) {
        if (!isEligibleStack(item)) return new TradeResult(false, "That item cannot be sold to the server market.", 0, 0);
        Material material = item.getType();
        MarketEntry e = entries.get(material);
        long maxStock = Math.max(1, plugin.getConfig().getLong("market.maximum-stock-per-material", 1_000_000));
        int amount = (int)Math.min(item.getAmount(), Math.max(0, maxStock - e.stock()));
        if (amount <= 0) return new TradeResult(false, "The market is at maximum stock for this item.", 0, 0);
        double gross = sellUnitPrice(material) * amount;
        double feePct = Math.max(0, plugin.getConfig().getDouble("market.transaction-fee-percent", 2.0));
        double payout = WalletManager.round(gross * (1 - feePct / 100.0));
        e.stock(e.stock() + amount);
        pushPressure(e, -amount);
        recordVolume(material, -amount);
        if (!plugin.wallets().deposit(player.getUniqueId(), payout)) {
            e.stock(e.stock() - amount);
            pushPressure(e, amount);
            recordVolume(material, amount);
            return new TradeResult(false, "The economy provider rejected the payment.", 0, 0);
        }
        audit("MARKET_SELL_GUI", player.getUniqueId(), player.getName(), material.name(), amount, payout, "");
        return new TradeResult(true, "Sold " + amount + "x " + material.name() + ".", amount, payout);
    }

    /** How many plain (non-custom-metadata, per market.reject-custom-items) items of this material the
     *  player is carrying and could sell right now via /sell &lt;item&gt;. */
    public int sellableCount(Player player, Material material) { return countPlain(player, material); }

    private int countPlain(Player player, Material material) {
        int count = 0;
        boolean rejectCustom = plugin.getConfig().getBoolean("market.reject-custom-items", true);
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() != material) continue;
            if (rejectCustom && !hasOnlyAllowedMarketMeta(item)) continue;
            count += item.getAmount();
        }
        return count;
    }

    private boolean removePlain(Player player, Material material, int amount) {
        boolean rejectCustom = plugin.getConfig().getBoolean("market.reject-custom-items", true);
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != material || (rejectCustom && !hasOnlyAllowedMarketMeta(item))) continue;
            int take = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - take);
            if (item.getAmount() <= 0) contents[i] = null;
            remaining -= take;
        }
        if (remaining != 0) return false;
        player.getInventory().setStorageContents(contents);
        return true;
    }

    private int fitInventory(Player player, Material material, int desired) {
        int max = material.getMaxStackSize();
        int capacity = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType().isAir()) capacity += max;
            else if (item.getType() == material && !item.hasItemMeta()) capacity += Math.max(0, max - item.getAmount());
            if (capacity >= desired) return desired;
        }
        return Math.min(desired, capacity);
    }

    private void pushPressure(MarketEntry e, int signedItems) {
        double impact = plugin.getConfig().getDouble("market.demand-impact-per-item", 0.0035);
        double max = Math.max(0.01, plugin.getConfig().getDouble("market.maximum-demand-pressure", 1.25));
        e.pressure(clamp(e.pressure() + signedItems * impact, -max, max));
    }


    public void checkScheduledRestock() {
        if (!plugin.getConfig().getBoolean("market.auto-restock.enabled", true)) return;
        long intervalHours = Math.max(1, plugin.getConfig().getLong("market.auto-restock.interval-hours", 72));
        long interval = intervalHours * 60L * 60L * 1000L;
        long now = System.currentTimeMillis();
        if (now - lastRestockAt < interval) return;

        long threshold = Math.max(0, plugin.getConfig().getLong("market.auto-restock.below-stock", 100));
        long min = Math.max(0, plugin.getConfig().getLong("market.auto-restock.minimum-stock", 500));
        long max = Math.max(min, plugin.getConfig().getLong("market.auto-restock.maximum-stock", 1000));
        int changed = 0;
        java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();

        for (var entry : entries.entrySet()) {
            Material material = entry.getKey();
            MarketEntry marketEntry = entry.getValue();
            if (!isBuyAllowed(material)) continue;
            if (marketEntry.stock() >= threshold) continue;
            long old = marketEntry.stock();
            long target = min == max ? min : random.nextLong(min, max + 1);
            marketEntry.stock(target);
            changed++;
            audit("AUTO_RESTOCK", null, "SYSTEM", material.name(), 0, target, "old=" + old);
        }

        lastRestockAt = now;
        save();
        plugin.getLogger().info("3-day market restock completed: " + changed + " low-stock material(s) reset to " + min + "-" + max + " stock.");
    }

    public void decayDemand() {
        double decay = clamp(plugin.getConfig().getDouble("market.demand-decay-per-minute", 0.985), 0, 1);
        for (MarketEntry e : entries.values()) e.pressure(e.pressure() * decay);
    }

    public boolean setBasePrice(Material material, double price, String actor) {
        MarketEntry e = entries.get(material);
        if (e == null || price <= 0 || !Double.isFinite(price)) return false;
        double old = e.basePrice(); e.basePrice(price);
        audit("ADMIN_PRICE", null, actor, material.name(), 0, price, "old=" + old);
        return true;
    }

    public boolean setStock(Material material, long amount, String actor) {
        MarketEntry e = entries.get(material);
        if (e == null) return false;
        long old = e.stock(); e.stock(Math.max(0, amount));
        audit("ADMIN_STOCK_SET", null, actor, material.name(), 0, amount, "old=" + old);
        return true;
    }

    public boolean addStock(Material material, long delta, String actor) {
        MarketEntry e = entries.get(material);
        if (e == null) return false;
        long max = Math.max(1, plugin.getConfig().getLong("market.maximum-stock-per-material", 1_000_000));
        long old = e.stock();
        long next;
        try { next = Math.addExact(old, delta); } catch (ArithmeticException ex) { next = delta > 0 ? max : 0; }
        e.stock(Math.max(0, Math.min(max, next)));
        audit("ADMIN_STOCK_ADD", null, actor, material.name(), (int)Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, delta)), e.stock(), "old=" + old);
        return true;
    }

    public void resetPrice(Material material, String actor) {
        MarketEntry e = entries.get(material);
        if (e == null) return;
        double old = e.basePrice(); e.basePrice(defaultBasePrice(material)); e.pressure(0);
        audit("ADMIN_PRICE_RESET", null, actor, material.name(), 0, e.basePrice(), "old=" + old);
    }

    public void audit(String action, UUID uuid, String actor, String subject, int amount, double money, String extra) {
        try (FileWriter fw = new FileWriter(auditFile, true)) {
            fw.write(Instant.now() + "\t" + action + "\t" + (uuid == null ? "-" : uuid) + "\t" + actor.replace('\t',' ') + "\t" + subject + "\t" + amount + "\t" + WalletManager.round(money) + "\t" + extra.replace('\t',' ') + "\n");
        } catch (IOException ex) { plugin.getLogger().warning("Could not write audit.log: " + ex.getMessage()); }
    }

    private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }

    public static double defaultBasePrice(Material m) {
        String n = m.name();
        Map<String, Double> special = SPECIAL_PRICES;
        Double exact = special.get(n);
        if (exact != null) return exact;
        if (n.endsWith("_SPAWN_EGG")) return 350;
        if (n.endsWith("_ORE")) return n.contains("DIAMOND") ? 1000 : n.contains("EMERALD") ? 320 : n.contains("GOLD") ? 45 : n.contains("IRON") ? 32 : 12;
        if (n.endsWith("_LOG") || n.endsWith("_WOOD") || n.endsWith("_STEM") || n.endsWith("_HYPHAE")) return 7;
        if (n.endsWith("_PLANKS")) return 2.0;
        if (n.endsWith("_LEAVES") || n.endsWith("_SAPLING")) return 2.5;
        if (n.endsWith("_WOOL")) return 6;
        if (n.endsWith("_CONCRETE") || n.endsWith("_TERRACOTTA")) return 5;
        if (n.endsWith("_GLASS") || n.endsWith("_GLASS_PANE")) return 3.5;
        if (n.endsWith("_SWORD")) return 90;
        if (n.endsWith("_PICKAXE")) return 80;
        if (n.endsWith("_AXE")) return 75;
        if (n.endsWith("_SHOVEL") || n.endsWith("_HOE")) return 45;
        if (n.endsWith("_HELMET")) return 110;
        if (n.endsWith("_CHESTPLATE")) return 180;
        if (n.endsWith("_LEGGINGS")) return 150;
        if (n.endsWith("_BOOTS")) return 90;
        if (n.endsWith("_BOAT") || n.endsWith("_RAFT")) return 18;
        if (n.endsWith("_SIGN") || n.endsWith("_HANGING_SIGN")) return 10;
        if (n.endsWith("_DOOR") || n.endsWith("_TRAPDOOR")) return 8;
        if (n.endsWith("_STAIRS")) return 3;
        if (n.endsWith("_SLAB")) return 1.5;
        if (n.endsWith("_WALL") || n.endsWith("_FENCE") || n.endsWith("_FENCE_GATE")) return 3;
        if (n.endsWith("_BANNER") || n.endsWith("_CARPET")) return 5;
        if (n.endsWith("_CANDLE")) return 5;
        if (n.contains("POTTERY_SHERD") || n.contains("SMITHING_TEMPLATE")) return 175;
        if (n.endsWith("_MUSIC_DISC") || n.startsWith("MUSIC_DISC_")) return 250;
        return 8.0;
    }

    private static final Map<String, Double> SPECIAL_PRICES = Map.ofEntries(
            Map.entry("COBBLESTONE", 1.0), Map.entry("STONE", 1.5), Map.entry("DIRT", 0.75), Map.entry("SAND", 1.5),
            Map.entry("GRAVEL", 1.5), Map.entry("CLAY_BALL", 2.0), Map.entry("COAL", 6.0), Map.entry("CHARCOAL", 6.0),
            Map.entry("RAW_IRON", 18.0), Map.entry("IRON_INGOT", 24.0), Map.entry("IRON_BLOCK", 216.0),
            Map.entry("RAW_GOLD", 24.0), Map.entry("GOLD_INGOT", 34.0), Map.entry("GOLD_BLOCK", 306.0),
            Map.entry("RAW_COPPER", 3.0), Map.entry("COPPER_INGOT", 4.0), Map.entry("COPPER_BLOCK", 36.0),
            Map.entry("DIAMOND", 900.0), Map.entry("DIAMOND_BLOCK", 8100.0), Map.entry("EMERALD", 240.0), Map.entry("EMERALD_BLOCK", 2160.0),
            Map.entry("NETHERITE_SCRAP", 1400.0), Map.entry("NETHERITE_INGOT", 6000.0), Map.entry("NETHERITE_BLOCK", 54000.0),
            Map.entry("NETHERITE_SWORD", 8200.0), Map.entry("NETHERITE_PICKAXE", 8600.0), Map.entry("NETHERITE_AXE", 8400.0),
            Map.entry("NETHERITE_SHOVEL", 6800.0), Map.entry("NETHERITE_HOE", 7200.0), Map.entry("NETHERITE_HELMET", 12000.0),
            Map.entry("NETHERITE_CHESTPLATE", 18000.0), Map.entry("NETHERITE_LEGGINGS", 16000.0), Map.entry("NETHERITE_BOOTS", 10000.0),
            Map.entry("NETHERITE_UPGRADE_SMITHING_TEMPLATE", 4500.0),
            Map.entry("REDSTONE", 3.0), Map.entry("LAPIS_LAZULI", 5.0), Map.entry("QUARTZ", 8.0), Map.entry("AMETHYST_SHARD", 12.0),
            Map.entry("OBSIDIAN", 18.0), Map.entry("CRYING_OBSIDIAN", 35.0), Map.entry("ENDER_PEARL", 28.0), Map.entry("ENDER_EYE", 70.0),
            Map.entry("BLAZE_ROD", 35.0), Map.entry("BLAZE_POWDER", 18.0), Map.entry("GHAST_TEAR", 110.0), Map.entry("MAGMA_CREAM", 18.0),
            Map.entry("SLIME_BALL", 12.0), Map.entry("GUNPOWDER", 8.0), Map.entry("STRING", 3.0), Map.entry("BONE", 3.0),
            Map.entry("LEATHER", 8.0), Map.entry("FEATHER", 2.0), Map.entry("INK_SAC", 3.0), Map.entry("GLOW_INK_SAC", 8.0),
            Map.entry("SHULKER_SHELL", 175.0), Map.entry("PHANTOM_MEMBRANE", 65.0), Map.entry("PRISMARINE_SHARD", 8.0),
            Map.entry("PRISMARINE_CRYSTALS", 10.0), Map.entry("NAUTILUS_SHELL", 80.0), Map.entry("HEART_OF_THE_SEA", 850.0),
            Map.entry("NETHER_STAR", 3500.0), Map.entry("BEACON", 8000.0), Map.entry("ELYTRA", 5000.0), Map.entry("TRIDENT", 3500.0),
            Map.entry("TOTEM_OF_UNDYING", 2500.0), Map.entry("ENCHANTED_GOLDEN_APPLE", 6000.0), Map.entry("GOLDEN_APPLE", 180.0),
            Map.entry("DRAGON_EGG", 25000.0), Map.entry("WITHER_SKELETON_SKULL", 650.0), Map.entry("CREEPER_HEAD", 300.0), Map.entry("PLAYER_HEAD", 250.0),
            Map.entry("HEAVY_CORE", 4500.0), Map.entry("MACE", 6000.0), Map.entry("BREEZE_ROD", 55.0), Map.entry("WIND_CHARGE", 8.0),
            Map.entry("TRIAL_KEY", 180.0), Map.entry("OMINOUS_TRIAL_KEY", 500.0), Map.entry("SADDLE", 80.0), Map.entry("NAME_TAG", 120.0),
            Map.entry("EXPERIENCE_BOTTLE", 25.0), Map.entry("BOOK", 8.0), Map.entry("BOOKSHELF", 45.0), Map.entry("PAPER", 2.0),
            Map.entry("SUGAR_CANE", 3.0), Map.entry("WHEAT", 3.0), Map.entry("CARROT", 3.0), Map.entry("POTATO", 3.0),
            Map.entry("BEETROOT", 3.0), Map.entry("MELON_SLICE", 1.5), Map.entry("PUMPKIN", 5.0), Map.entry("CACTUS", 3.0),
            Map.entry("BAMBOO", 1.0), Map.entry("CHORUS_FRUIT", 6.0), Map.entry("APPLE", 4.0), Map.entry("EGG", 2.0),
            Map.entry("HONEY_BOTTLE", 8.0), Map.entry("HONEYCOMB", 6.0), Map.entry("SCULK", 10.0), Map.entry("ECHO_SHARD", 160.0)
    );
}

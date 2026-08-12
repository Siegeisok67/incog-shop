package com.snipeyfresh.incogshop.shop;

import com.snipeyfresh.incogshop.IncogShopPlugin;
import com.snipeyfresh.incogshop.economy.WalletManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class ShopManager {
    public record ShopResult(boolean success, String message, int amount, double total) {}

    private final IncogShopPlugin plugin;
    private final File file;
    private final Map<String, PlayerShop> byLocation = new HashMap<>();
    private final Map<UUID, PlayerShop> byId = new HashMap<>();

    public ShopManager(IncogShopPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "shops.yml");
    }

    public void load() {
        byLocation.clear(); byId.clear();
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yml.getConfigurationSection("shops");
        if (root == null) return;
        for (String idString : root.getKeys(false)) {
            try {
                UUID id = UUID.fromString(idString);
                UUID owner = UUID.fromString(root.getString(idString + ".owner"));
                String ownerName = root.getString(idString + ".owner-name", "Unknown");
                String worldId = root.getString(idString + ".world");
                var world = worldId == null ? null : Bukkit.getWorld(UUID.fromString(worldId));
                if (world == null) continue;
                Location loc = new Location(world, root.getInt(idString + ".x"), root.getInt(idString + ".y"), root.getInt(idString + ".z"));
                ItemStack item = root.getItemStack(idString + ".item");
                double price = root.getDouble(idString + ".price");
                if (item == null || item.getType().isAir() || price <= 0) continue;
                PlayerShop shop = new PlayerShop(id, owner, ownerName, loc, item, price);
                byId.put(id, shop); byLocation.put(key(loc), shop);
            } catch (Exception ex) {
                plugin.getLogger().warning("Skipping invalid player shop " + idString + ": " + ex.getMessage());
            }
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (PlayerShop shop : byId.values()) {
            String root = "shops." + shop.id();
            yml.set(root + ".owner", shop.owner().toString());
            yml.set(root + ".owner-name", shop.ownerName());
            yml.set(root + ".world", shop.location().getWorld().getUID().toString());
            yml.set(root + ".x", shop.location().getBlockX());
            yml.set(root + ".y", shop.location().getBlockY());
            yml.set(root + ".z", shop.location().getBlockZ());
            yml.set(root + ".item", shop.item());
            yml.set(root + ".price", WalletManager.round(shop.price()));
        }
        try { yml.save(file); }
        catch (IOException ex) { plugin.getLogger().severe("Could not save shops.yml: " + ex.getMessage()); }
    }

    public PlayerShop at(Location location) { return byLocation.get(key(location)); }
    public PlayerShop at(Block block) { return at(block.getLocation()); }
    public PlayerShop byId(UUID id) { return byId.get(id); }
    public Collection<PlayerShop> all() { return Collections.unmodifiableCollection(byId.values()); }

    public String create(Player player, Block block, ItemStack held, double price) {
        if (!plugin.getConfig().getBoolean("player-shops.enabled", true)) return "Player shops are disabled.";
        if (!player.hasPermission("incogshop.playershop.create")) return "You do not have permission to create shops.";
        if (block == null || !(block.getState() instanceof Container container)) return "Look directly at a chest or barrel first.";
        List<String> allowed = plugin.getConfig().getStringList("player-shops.allowed-containers");
        if (!allowed.contains(block.getType().name())) return "That container type cannot be used as a shop.";
        if (byLocation.containsKey(key(block.getLocation()))) return "That container is already a player shop.";
        if (block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST) {
            if (container.getInventory().getSize() > 27) return "Double chests cannot be shops. Use a single chest or barrel to prevent stock exploits.";
        }
        if (held == null || held.getType().isAir()) return "Hold the exact item you want this shop to sell.";
        if (!Double.isFinite(price) || price <= 0) return "Price must be a positive number.";
        int max = Math.max(1, plugin.getConfig().getInt("player-shops.max-shops-per-player", 12));
        long owned = byId.values().stream().filter(s -> s.owner().equals(player.getUniqueId())).count();
        if (owned >= max && !player.hasPermission("incogshop.playershop.bypass")) return "You have reached your player-shop limit of " + max + ".";
        double fee = Math.max(0, plugin.getConfig().getDouble("player-shops.creation-fee", 25.0));
        if (!plugin.wallets().withdraw(player.getUniqueId(), fee)) return "You need " + plugin.money(fee) + " to create a shop.";

        PlayerShop shop = new PlayerShop(UUID.randomUUID(), player.getUniqueId(), player.getName(), block.getLocation(), held, price);
        byId.put(shop.id(), shop); byLocation.put(key(shop.location()), shop);
        plugin.market().audit("PLAYER_SHOP_CREATE", player.getUniqueId(), player.getName(), shop.id().toString(), 0, price, "fee=" + fee);
        save();
        return "Created a shop selling " + held.getType().name() + " for " + plugin.money(price) + " each. Put matching items inside the container to stock it.";
    }

    public boolean remove(Player actor, PlayerShop shop) {
        if (shop == null) return false;
        if (!shop.owner().equals(actor.getUniqueId()) && !actor.hasPermission("incogshop.playershop.bypass")) return false;
        byId.remove(shop.id()); byLocation.remove(key(shop.location()));
        plugin.market().audit("PLAYER_SHOP_REMOVE", actor.getUniqueId(), actor.getName(), shop.id().toString(), 0, shop.price(), "owner=" + shop.owner());
        save(); return true;
    }

    public int stock(PlayerShop shop) {
        Inventory inv = inventory(shop);
        if (inv == null) return 0;
        int total = 0; ItemStack template = shop.item();
        for (ItemStack stack : inv.getStorageContents()) if (stack != null && stack.isSimilar(template)) total += stack.getAmount();
        return total;
    }

    public ShopResult buy(Player buyer, PlayerShop shop, int requested) {
        if (shop == null) return new ShopResult(false, "That shop no longer exists.", 0, 0);
        if (buyer.getUniqueId().equals(shop.owner())) return new ShopResult(false, "You cannot buy from your own shop.", 0, 0);
        Inventory inv = inventory(shop);
        if (inv == null) return new ShopResult(false, "The shop container is missing or unavailable.", 0, 0);
        int available = stock(shop);
        int amount = Math.min(Math.max(1, requested), available);
        if (amount <= 0) return new ShopResult(false, "This shop is out of stock.", 0, 0);
        amount = Math.min(amount, fitInventory(buyer, shop.item(), amount));
        if (amount <= 0) return new ShopResult(false, "You do not have enough inventory space.", 0, 0);
        double total = WalletManager.round(shop.price() * amount);
        if (plugin.wallets().get(buyer.getUniqueId()) + 1e-9 < total) return new ShopResult(false, "You do not have enough money.", 0, total);
        if (!removeSimilar(inv, shop.item(), amount)) return new ShopResult(false, "The shop stock changed before purchase completed.", 0, 0);
        if (!plugin.wallets().withdraw(buyer.getUniqueId(), total)) {
            inv.addItem(stackOf(shop.item(), amount));
            return new ShopResult(false, "Your balance changed before purchase completed.", 0, total);
        }
        buyer.getInventory().addItem(stackOf(shop.item(), amount));
        double taxPct = Math.max(0, plugin.getConfig().getDouble("player-shops.sales-tax-percent", 3.0));
        double ownerPayout = WalletManager.round(total * (1 - Math.min(100, taxPct) / 100.0));
        plugin.wallets().deposit(shop.owner(), ownerPayout);
        plugin.market().audit("PLAYER_SHOP_BUY", buyer.getUniqueId(), buyer.getName(), shop.id().toString(), amount, total, "owner=" + shop.owner() + ",payout=" + ownerPayout);
        return new ShopResult(true, "Purchased " + amount + " item(s).", amount, total);
    }

    public void setPrice(Player actor, PlayerShop shop, double price) {
        if (shop == null || price <= 0 || !Double.isFinite(price)) return;
        double old = shop.price(); shop.price(price); save();
        plugin.market().audit("PLAYER_SHOP_PRICE", actor.getUniqueId(), actor.getName(), shop.id().toString(), 0, price, "old=" + old);
    }

    private Inventory inventory(PlayerShop shop) {
        if (!shop.location().isWorldLoaded()) return null;
        if (!(shop.location().getBlock().getState() instanceof Container c)) return null;
        return c.getInventory();
    }

    private static boolean removeSimilar(Inventory inv, ItemStack template, int amount) {
        int remaining = amount;
        ItemStack[] contents = inv.getStorageContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || !stack.isSimilar(template)) continue;
            int take = Math.min(remaining, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            if (stack.getAmount() <= 0) contents[i] = null;
            remaining -= take;
        }
        if (remaining != 0) return false;
        inv.setStorageContents(contents); return true;
    }

    private static int fitInventory(Player player, ItemStack template, int desired) {
        int max = template.getMaxStackSize(); int capacity = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.getType().isAir()) capacity += max;
            else if (stack.isSimilar(template)) capacity += Math.max(0, max - stack.getAmount());
            if (capacity >= desired) return desired;
        }
        return Math.min(desired, capacity);
    }

    private static ItemStack stackOf(ItemStack template, int amount) {
        ItemStack out = template.clone(); out.setAmount(amount); return out;
    }

    private static String key(Location l) {
        return l.getWorld().getUID() + ":" + l.getBlockX() + ":" + l.getBlockY() + ":" + l.getBlockZ();
    }
}

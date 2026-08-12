package com.snipeyfresh.incogshop.listener;

import com.snipeyfresh.incogshop.IncogShopPlugin;
import com.snipeyfresh.incogshop.market.MarketManager;
import com.snipeyfresh.incogshop.util.SellWand;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.Map;

/**
 * Handles /sellwand: right-clicking a chest, trapped chest, barrel, or any shulker box with the
 * wand instantly sells every eligible plain item inside to the server market, the same way the
 * /sell GUI would - without needing to open the container and transfer stacks by hand.
 */
public final class SellWandListener implements Listener {
    private final IncogShopPlugin plugin;

    public SellWandListener(IncogShopPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        if (event.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND) return;
        Player player = event.getPlayer();
        if (!SellWand.isSellWand(plugin, event.getItem())) return;

        Block block = event.getClickedBlock();
        if (plugin.shops().at(block) != null) return; // registered player shops keep their own click behavior

        if (!isSellWandContainer(block.getType())) return;
        if (!(block.getState() instanceof Container container)) return;

        event.setCancelled(true);
        if (!plugin.getConfig().getBoolean("sell-wand.enabled", true)) {
            player.sendMessage(plugin.prefix() + "§cThe Sell Wand is disabled.");
            return;
        }
        sellContainer(player, container.getInventory());
    }

    private boolean isSellWandContainer(Material type) {
        if (type.name().endsWith("SHULKER_BOX")) return true;
        return plugin.getConfig().getStringList("sell-wand.containers").contains(type.name());
    }

    private void sellContainer(Player player, Inventory inventory) {
        MarketManager market = plugin.market();
        Map<Material, Integer> amountByMaterial = new EnumMap<>(Material.class);
        Map<Material, Double> totalByMaterial = new EnumMap<>(Material.class);
        double grandTotal = 0;

        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || stack.getType().isAir()) continue;
            MarketManager.TradeResult result = market.sellEscrowStack(player, stack);
            if (!result.success() || result.amount() <= 0) continue;

            int remaining = stack.getAmount() - result.amount();
            contents[slot] = remaining > 0 ? withAmount(stack, remaining) : null;
            amountByMaterial.merge(stack.getType(), result.amount(), Integer::sum);
            totalByMaterial.merge(stack.getType(), result.total(), Double::sum);
            grandTotal += result.total();
        }

        if (amountByMaterial.isEmpty()) {
            player.sendMessage(plugin.prefix() + "§eNo eligible plain market items were found in that container.");
            return;
        }
        inventory.setContents(contents);

        int totalItems = amountByMaterial.values().stream().mapToInt(Integer::intValue).sum();
        player.sendMessage(plugin.prefix() + "§aSold §f" + totalItems + " §aitem(s) for §f" + plugin.money(grandTotal) + "§a.");
    }

    private static ItemStack withAmount(ItemStack template, int amount) {
        ItemStack clone = template.clone();
        clone.setAmount(amount);
        return clone;
    }
}

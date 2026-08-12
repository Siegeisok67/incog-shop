package com.snipeyfresh.incogshop.listener;

import com.snipeyfresh.incogshop.IncogShopPlugin;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class GameplayOverflowListener implements Listener {
    private final IncogShopPlugin plugin;

    public GameplayOverflowListener(IncogShopPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.getConfig().getBoolean("stash.gameplay-overflow.block-drops", true)) return;
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || !event.isDropItems()) return;

        ItemStack tool = player.getInventory().getItemInMainHand();
        List<ItemStack> drops = new ArrayList<>(event.getBlock().getDrops(tool, player));
        if (drops.isEmpty()) return;

        event.setDropItems(false);
        int stashed = 0;
        for (ItemStack drop : drops) {
            if (drop == null || drop.getType().isAir() || drop.getAmount() <= 0) continue;
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(drop.clone());
            if (!leftovers.isEmpty()) {
                plugin.stash().addAll(player.getUniqueId(), leftovers.values());
                stashed += leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
            }
        }

        if (stashed > 0 && plugin.getConfig().getBoolean("stash.gameplay-overflow.notify", true)) {
            player.sendMessage(plugin.prefix() + "§d" + stashed + " mined item(s) did not fit and were moved to /stash.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!plugin.getConfig().getBoolean("stash.gameplay-overflow.mob-drops", true)) return;
        Player killer = event.getEntity().getKiller();
        if (killer == null || killer.getGameMode() == GameMode.CREATIVE) return;

        List<ItemStack> drops = new ArrayList<>(event.getDrops());
        if (drops.isEmpty()) return;
        event.getDrops().clear();

        int stashed = 0;
        for (ItemStack drop : drops) {
            if (drop == null || drop.getType().isAir() || drop.getAmount() <= 0) continue;
            Map<Integer, ItemStack> leftovers = killer.getInventory().addItem(drop.clone());
            if (!leftovers.isEmpty()) {
                plugin.stash().addAll(killer.getUniqueId(), leftovers.values());
                stashed += leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
            }
        }

        if (stashed > 0 && plugin.getConfig().getBoolean("stash.gameplay-overflow.notify", true)) {
            killer.sendMessage(plugin.prefix() + "§d" + stashed + " mob-drop item(s) did not fit and were moved to /stash.");
        }
    }
}

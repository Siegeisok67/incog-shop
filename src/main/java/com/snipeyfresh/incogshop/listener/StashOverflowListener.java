package com.snipeyfresh.incogshop.listener;

import com.snipeyfresh.incogshop.IncogShopPlugin;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.inventory.ItemStack;

public final class StashOverflowListener implements Listener {
    private final IncogShopPlugin plugin;

    public StashOverflowListener(IncogShopPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttemptPickup(PlayerAttemptPickupItemEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("incogshop.stash")) return;
        if (!plugin.getConfig().getBoolean("stash.gameplay-overflow.enabled", true)) return;

        Item itemEntity = event.getItem();
        ItemStack groundStack = itemEntity.getItemStack().clone();

        // Paper calculates this for us. It is exactly how many items would
        // remain on the ground after vanilla tries to fill the inventory.
        int remaining = Math.max(0, event.getRemaining());
        if (remaining <= 0) return; // Vanilla can take all of it.

        int accepted = Math.max(0, groundStack.getAmount() - remaining);

        // Take complete control of this pickup so the overflow cannot remain
        // on the ground or be handled twice by another pickup event.
        event.setCancelled(true);
        event.setFlyAtPlayer(false);

        if (accepted > 0) {
            ItemStack inventoryPart = groundStack.clone();
            inventoryPart.setAmount(accepted);

            // Paper told us this amount fits. If another plugin changes the
            // inventory during the same tick, send any surprise leftovers
            // to the stash as a final safety net.
            var unexpected = player.getInventory().addItem(inventoryPart);
            if (!unexpected.isEmpty()) {
                plugin.stash().addAll(player.getUniqueId(), unexpected.values());
            }
        }

        ItemStack stashPart = groundStack.clone();
        stashPart.setAmount(remaining);
        plugin.stash().add(player.getUniqueId(), stashPart);

        itemEntity.remove();

        if (plugin.getConfig().getBoolean("stash.gameplay-overflow.notify", true)) {
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    remaining + " item" + (remaining == 1 ? "" : "s") + " sent to /stash"
            ));
        }
    }
}

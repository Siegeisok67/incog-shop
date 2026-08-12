package com.snipeyfresh.incogshop.listener;

import com.snipeyfresh.incogshop.IncogShopPlugin;
import com.snipeyfresh.incogshop.shop.PlayerShop;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;

public final class ShopProtectionListener implements Listener {
    private final IncogShopPlugin plugin;
    public ShopProtectionListener(IncogShopPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        PlayerShop shop = plugin.shops().at(event.getClickedBlock());
        if (shop == null) return;
        Player player = event.getPlayer();
        if (shop.owner().equals(player.getUniqueId()) || player.hasPermission("incogshop.playershop.bypass")) {
            if (player.isSneaking()) return; // owner/admin can sneak-right-click to open the physical stock container
        }
        event.setCancelled(true);
        plugin.gui().openPlayerShop(player, shop);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        PlayerShop shop = plugin.shops().at(event.getBlock());
        if (shop == null) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(plugin.prefix() + "§cThis is a registered player shop. Use §f/pshop remove §cwhile looking at it before breaking the container.");
    }

    @EventHandler
    public void onExplode(BlockExplodeEvent event) { event.blockList().removeIf(block -> plugin.shops().at(block) != null); }
    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) { event.blockList().removeIf(block -> plugin.shops().at(block) != null); }

    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) if (plugin.shops().at(block) != null) { event.setCancelled(true); return; }
    }
    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) if (plugin.shops().at(block) != null) { event.setCancelled(true); return; }
    }

    @EventHandler
    public void onInventoryMove(InventoryMoveItemEvent event) {
        var source = event.getSource().getLocation();
        var destination = event.getDestination().getLocation();
        if ((source != null && plugin.shops().at(source) != null) || (destination != null && plugin.shops().at(destination) != null)) event.setCancelled(true);
    }
}

package com.snipeyfresh.incogshop.listener;

import com.snipeyfresh.incogshop.IncogShopPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class PlayerListener implements Listener {
    private final IncogShopPlugin plugin;
    public PlayerListener(IncogShopPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.wallets().touch(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }
}

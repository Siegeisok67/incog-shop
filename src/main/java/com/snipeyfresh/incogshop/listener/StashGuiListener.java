package com.snipeyfresh.incogshop.listener;
import com.snipeyfresh.incogshop.IncogShopPlugin;
import com.snipeyfresh.incogshop.gui.StashGui;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
public final class StashGuiListener implements Listener {
    private final IncogShopPlugin plugin;
    public StashGuiListener(IncogShopPlugin plugin){this.plugin=plugin;}
    @EventHandler public void click(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p))return;
        if(!(e.getView().getTopInventory().getHolder() instanceof StashGui.Holder h))return;
        e.setCancelled(true); int slot=e.getRawSlot(); if(slot<0||slot>=54)return;
        if(slot<45){
            int index=h.page()*45+slot;
            plugin.stash().claimIndex(p,index);
            plugin.stashGui().open(p,h.page()); return;
        }
        if(slot==45&&h.page()>0){plugin.stashGui().open(p,h.page()-1);return;}
        if(slot==47){
            int moved=plugin.stash().claimAll(p);
            p.sendMessage(plugin.prefix()+(moved>0?"§aClaimed §f"+moved+" §aitem(s) from your stash.":"§eNothing could be moved; your inventory may be full."));
            plugin.stashGui().open(p,h.page()); return;
        }
        if(slot==51){
            var r=plugin.stash().sellAllEligible(p);
            p.sendMessage(plugin.prefix()+(r.soldItems()>0?"§aSold §f"+r.soldItems()+" §astashed item(s) for §f"+plugin.money(r.payout())+"§a.":"§eNo eligible stash items could be sold."));
            plugin.stashGui().open(p,h.page()); return;
        }
        if(slot==53) plugin.stashGui().open(p,h.page()+1);
    }
}

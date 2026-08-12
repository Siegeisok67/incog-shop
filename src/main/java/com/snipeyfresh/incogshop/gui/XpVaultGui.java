package com.snipeyfresh.incogshop.gui;

import com.snipeyfresh.incogshop.IncogShopPlugin;
import com.snipeyfresh.incogshop.util.Text;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import java.util.List;

public final class XpVaultGui {
    public static final int DEPOSIT_25 = 10;
    public static final int DEPOSIT_50 = 11;
    public static final int DEPOSIT_ALL = 12;
    public static final int WITHDRAW_25 = 14;
    public static final int WITHDRAW_50 = 15;
    public static final int WITHDRAW_ALL = 16;

    public static final class Holder implements InventoryHolder {
        public Inventory getInventory(){ return null; }
    }

    private final IncogShopPlugin plugin;
    public XpVaultGui(IncogShopPlugin plugin){ this.plugin = plugin; }

    public void open(Player player) {
        Inventory inv=Bukkit.createInventory(new Holder(),27, Text.color("&8Incog-Shop &7• &aXP Vault"));
        ItemStack filler=ShopGui.named(Material.BLACK_STAINED_GLASS_PANE," ",List.of());
        for(int i=0;i<27;i++) inv.setItem(i,filler);

        long current=plugin.xpVault().current(player);
        long stored=plugin.xpVault().stored(player.getUniqueId());

        inv.setItem(4,ShopGui.named(Material.EXPERIENCE_BOTTLE,"&aXP Vault",
                List.of("&7Current XP: &f"+current,
                        "&7Stored XP: &f"+stored,
                        "&7Current Level: &f"+player.getLevel(),
                        "",
                        "&7XP is stored as raw points,",
                        "&7not just levels.")));

        inv.setItem(DEPOSIT_25,ShopGui.named(Material.LIME_DYE,"&aDeposit 25%",
                List.of("&7Deposit about &f"+Math.max(0,current/4)+" XP","", "&eClick")));
        inv.setItem(DEPOSIT_50,ShopGui.named(Material.LIME_CONCRETE,"&aDeposit 50%",
                List.of("&7Deposit about &f"+Math.max(0,current/2)+" XP","", "&eClick")));
        inv.setItem(DEPOSIT_ALL,ShopGui.named(Material.EMERALD_BLOCK,"&aDeposit All",
                List.of("&7Deposit all &f"+current+" XP","", "&eClick")));

        inv.setItem(WITHDRAW_25,ShopGui.named(Material.YELLOW_DYE,"&eWithdraw 25%",
                List.of("&7Withdraw about &f"+Math.max(0,stored/4)+" XP","", "&eClick")));
        inv.setItem(WITHDRAW_50,ShopGui.named(Material.GOLD_INGOT,"&6Withdraw 50%",
                List.of("&7Withdraw about &f"+Math.max(0,stored/2)+" XP","", "&eClick")));
        inv.setItem(WITHDRAW_ALL,ShopGui.named(Material.GOLD_BLOCK,"&6Withdraw All",
                List.of("&7Withdraw all &f"+stored+" XP","", "&eClick")));

        inv.setItem(22,ShopGui.named(Material.BARRIER,"&cClose",List.of()));
        player.openInventory(inv);
    }
}

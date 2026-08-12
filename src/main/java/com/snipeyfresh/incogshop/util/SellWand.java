package com.snipeyfresh.incogshop.util;

import com.snipeyfresh.incogshop.IncogShopPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** Creates and identifies the /sellwand item using a persistent data tag. */
public final class SellWand {
    private SellWand() {}

    private static NamespacedKey key(IncogShopPlugin plugin) {
        return new NamespacedKey(plugin, "sellwand");
    }

    public static ItemStack create(IncogShopPlugin plugin) {
        ItemStack wand = new ItemStack(Material.STICK);
        ItemMeta meta = wand.getItemMeta();
        meta.setDisplayName(Text.color("&aIncog-Shop Sell Wand"));
        meta.setLore(List.of(
                Text.color("&7Right-click a chest, barrel, or"),
                Text.color("&7shulker box to instantly sell every"),
                Text.color("&7eligible plain item inside to the"),
                Text.color("&7server market."),
                "",
                Text.color("&8Custom, enchanted, or renamed items"),
                Text.color("&8are skipped and left in the container.")
        ));
        meta.addItemFlags(ItemFlag.values());
        meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.BYTE, (byte) 1);
        wand.setItemMeta(meta);
        return wand;
    }

    public static boolean isSellWand(IncogShopPlugin plugin, ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) return false;
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(key(plugin), PersistentDataType.BYTE);
    }
}

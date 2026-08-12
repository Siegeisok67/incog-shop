package com.snipeyfresh.incogshop.util;

import com.snipeyfresh.incogshop.IncogShopPlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Writes a simple, human-readable transaction log to console and to a dedicated
 * {@code transactions.log} file, in addition to the existing tab-separated audit.log used for
 * admin tooling. Format:
 * <pre>
 * [Incog-Shop] =|= 'user' (uuid) | Sold ItemName x64 for $576.00 at $9.00 per &lt;|&gt; 2026-08-08 14:23:01
 * </pre>
 */
public final class TransactionLog {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final IncogShopPlugin plugin;
    private final File file;

    public TransactionLog(IncogShopPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "transactions.log");
    }

    public void sold(Player player, Material material, int amount, double total, double perItem) {
        write(player.getName(), player.getUniqueId(),
                "Sold " + name(material) + " x" + amount + " for " + plugin.money(total) + " at " + plugin.money(perItem) + " per");
    }

    public void bought(Player player, Material material, int amount, double total, double perItem) {
        write(player.getName(), player.getUniqueId(),
                "Bought " + name(material) + " x" + amount + " for " + plugin.money(total) + " at " + plugin.money(perItem) + " per");
    }

    public void createdSellOffer(Player player, Material material, int amount, double total, double perItem) {
        write(player.getName(), player.getUniqueId(),
                "Created Sell Offer " + name(material) + " x" + amount + " for " + plugin.money(total) + " at " + plugin.money(perItem) + " per");
    }

    public void createdBuyOffer(Player player, Material material, int amount, double total, double perItem) {
        write(player.getName(), player.getUniqueId(),
                "Created Buy Offer " + name(material) + " x" + amount + " for " + plugin.money(total) + " at " + plugin.money(perItem) + " per");
    }

    public void bidOn(Player player, Material material, double total, UUID auctionId) {
        write(player.getName(), player.getUniqueId(),
                "Bid on " + name(material) + " for " + plugin.money(total) + " [" + shortId(auctionId) + "]");
    }

    public void createdAuction(Player player, Material material, double startingBid, UUID auctionId) {
        write(player.getName(), player.getUniqueId(),
                "Created Auction for " + name(material) + " at starting bid of " + plugin.money(startingBid) + " ~|~ " + shortId(auctionId));
    }

    private static String name(Material material) { return Text.prettyEnum(material.name()); }
    private static String shortId(UUID id) { return id.toString().substring(0, 8); }
    private static String stamp() { return LocalDateTime.now().format(FORMAT); }

    private void write(String playerName, UUID uuid, String body) {
        // The plugin logger already prepends "[Incog-Shop] " to console lines, so the console
        // copy skips that prefix to avoid doubling it; the file copy adds it back since the file
        // has no other prefix.
        String suffix = "=|= '" + playerName + "' (" + uuid + ") | " + body + " <|> " + stamp();
        plugin.getLogger().info(suffix);
        try (FileWriter fw = new FileWriter(file, true)) {
            fw.write("[Incog-Shop] " + suffix + System.lineSeparator());
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not write transactions.log: " + ex.getMessage());
        }
    }
}

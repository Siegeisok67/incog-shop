package com.snipeyfresh.incogshop.listener;

import com.snipeyfresh.incogshop.IncogShopPlugin;
import com.snipeyfresh.incogshop.auction.AuctionListing;
import com.snipeyfresh.incogshop.economy.WalletManager;
import com.snipeyfresh.incogshop.gui.AuctionGui;
import com.snipeyfresh.incogshop.util.Money;
import io.papermc.paper.event.packet.UncheckedSignChangeEvent;
import io.papermc.paper.math.BlockPosition;
import io.papermc.paper.math.Position;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AuctionGuiListener implements Listener {
    private final IncogShopPlugin plugin;
    private final Map<UUID, PendingBid> pendingBids = new ConcurrentHashMap<>();
    private final Map<UUID, PendingCreatePrice> pendingCreatePrices = new ConcurrentHashMap<>();

    private record PendingBid(UUID listingId, int returnPage) {}
    private record PendingCreatePrice(AuctionListing.Mode mode, int hours, Location location, BlockPosition position) {}

    public AuctionGuiListener(IncogShopPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (event.getView().getTopInventory().getHolder() instanceof AuctionGui.Holder holder) {
            event.setCancelled(true);
            int raw = event.getRawSlot();
            if (raw < 0 || raw >= event.getView().getTopInventory().getSize()) return;
            if (raw == 45 && holder.page() > 0) { plugin.auctionGui().open(player, holder.page() - 1); return; }
            if (raw == 46) {
                if (!player.hasPermission("incogshop.auction.sell")) { noPerm(player); return; }
                plugin.auctionGui().openCreate(player);
                return;
            }
            if (raw == 47) { plugin.auctionGui().openMy(player, 0); return; }
            if (raw == 51) { plugin.auctionGui().openClaims(player, 0); return; }
            if (raw == 50 && player.hasPermission("incogshop.auction.admin")) {
                boolean enabled = plugin.auctions().togglePermanentMode(player);
                player.sendMessage(plugin.prefix() + (enabled
                        ? "§dAdmin permanent-listing mode enabled. New AH listings will never expire."
                        : "§7Admin permanent-listing mode disabled. New AH listings use normal durations."));
                plugin.auctionGui().open(player, holder.page());
                return;
            }
            if (raw == 53) { plugin.auctionGui().open(player, holder.page() + 1); return; }
            if (raw >= 45) return;
            var listings = plugin.auctions().activeListings();
            int index = holder.page() * 45 + raw;
            if (index < 0 || index >= listings.size()) return;
            plugin.auctionGui().openDetail(player, listings.get(index), holder.page());
            return;
        }

        if (event.getView().getTopInventory().getHolder() instanceof AuctionGui.CreateHolder holder) {
            event.setCancelled(true);
            int raw = event.getRawSlot();
            if (raw < 0 || raw >= 45) return;

            if (raw == 13 || raw == 40) {
                plugin.auctionGui().openCreate(player, holder.mode(), holder.price(), holder.hours());
                return;
            }

            if (raw == 20) {
                AuctionListing.Mode next = holder.mode() == AuctionListing.Mode.AUCTION
                        ? AuctionListing.Mode.BUY_NOW : AuctionListing.Mode.AUCTION;
                plugin.auctionGui().openCreate(player, next, holder.price(), holder.hours());
                return;
            }

            if (raw == 22) {
                beginCreatePrice(player, holder.mode(), holder.hours());
                return;
            }

            if (raw == 24 && !(player.hasPermission("incogshop.auction.admin") && plugin.auctions().permanentMode(player))) {
                int max = Math.max(1, plugin.getConfig().getInt("auction-house.maximum-duration-hours", 168));
                int next = holder.hours();
                if (event.isShiftClick() && event.isLeftClick()) next += 24;
                else if (event.isShiftClick() && event.isRightClick()) next -= 24;
                else if (event.isLeftClick()) next += 1;
                else if (event.isRightClick()) next -= 1;
                next = Math.max(1, Math.min(max, next));
                plugin.auctionGui().openCreate(player, holder.mode(), holder.price(), next);
                return;
            }

            if (raw == 31) {
                if (!player.hasPermission("incogshop.auction.sell")) { noPerm(player); return; }
                if (holder.price() <= 0) {
                    player.sendMessage(plugin.prefix() + "§cSet a price first.");
                    return;
                }
                if (player.getInventory().getItemInMainHand().getType().isAir()) {
                    player.sendMessage(plugin.prefix() + "§cPut the item/stack you want to list in your main hand.");
                    plugin.auctionGui().openCreate(player, holder.mode(), holder.price(), holder.hours());
                    return;
                }
                var result = plugin.auctions().create(player, holder.mode(), holder.price(), holder.hours());
                player.sendMessage(plugin.prefix() + (result.success() ? "§a" : "§c") + result.message());
                if (result.success()) plugin.auctionGui().openMy(player, 0);
                else plugin.auctionGui().openCreate(player, holder.mode(), holder.price(), holder.hours());
                return;
            }

            if (raw == 36) {
                plugin.auctionGui().open(player, 0);
                return;
            }
            return;
        }

        if (event.getView().getTopInventory().getHolder() instanceof AuctionGui.MyHolder holder) {
            event.setCancelled(true);
            int raw = event.getRawSlot();
            if (raw < 0 || raw >= event.getView().getTopInventory().getSize()) return;
            if (!holder.owner().equals(player.getUniqueId())) { player.closeInventory(); return; }
            if (raw == 45 && holder.page() > 0) { plugin.auctionGui().openMy(player, holder.page() - 1); return; }
            if (raw == 49) { plugin.auctionGui().open(player, 0); return; }
            if (raw == 51) { plugin.auctionGui().openClaims(player, 0); return; }
            if (raw == 53) { plugin.auctionGui().openMy(player, holder.page() + 1); return; }
            if (raw >= 45) return;
            var listings = plugin.auctions().activeListings().stream().filter(l -> l.seller().equals(player.getUniqueId())).toList();
            int index = holder.page() * 45 + raw;
            if (index < 0 || index >= listings.size()) return;
            plugin.auctionGui().openDetail(player, listings.get(index), holder.page(), true);
            return;
        }

        if (event.getView().getTopInventory().getHolder() instanceof AuctionGui.ClaimsHolder holder) {
            event.setCancelled(true);
            int raw = event.getRawSlot();
            if (raw < 0 || raw >= 54) return;
            if (!holder.owner().equals(player.getUniqueId())) { player.closeInventory(); return; }
            if (raw < 45) {
                int index = holder.page() * 45 + raw;
                plugin.auctions().claimIndex(player, index);
                plugin.auctionGui().openClaims(player, holder.page());
                return;
            }
            if (raw == 45 && holder.page() > 0) { plugin.auctionGui().openClaims(player, holder.page()-1); return; }
            if (raw == 47) {
                int count = plugin.auctions().claim(player);
                player.sendMessage(plugin.prefix() + (count > 0 ? "§aClaimed §f" + count + " §aAuction House stack(s)." : "§eNo auction claims available."));
                plugin.auctionGui().openClaims(player, 0);
                return;
            }
            if (raw == 49) { plugin.auctionGui().open(player, 0); return; }
            if (raw == 53) { plugin.auctionGui().openClaims(player, holder.page()+1); return; }
            return;
        }

        if (!(event.getView().getTopInventory().getHolder() instanceof AuctionGui.DetailHolder holder)) return;
        event.setCancelled(true);
        int raw = event.getRawSlot();
        if (raw < 0 || raw >= event.getView().getTopInventory().getSize()) return;
        if (raw == 40) {
            if (holder.fromMyListings()) plugin.auctionGui().openMy(player, holder.returnPage()); else plugin.auctionGui().open(player, holder.returnPage());
            return;
        }

        AuctionListing listing = plugin.auctions().find(holder.listingId().toString());
        if (listing == null) {
            player.sendMessage(plugin.prefix() + "§cThat listing is no longer available.");
            plugin.auctionGui().open(player, holder.returnPage());
            return;
        }

        if (raw == 36 && listing.seller().equals(player.getUniqueId())) {
            var result = plugin.auctions().cancel(player, listing);
            player.sendMessage(plugin.prefix() + (result.success() ? "§a" : "§c") + result.message());
            if (holder.fromMyListings()) plugin.auctionGui().openMy(player, holder.returnPage()); else plugin.auctionGui().open(player, holder.returnPage());
            return;
        }

        if (listing.mode() == AuctionListing.Mode.BUY_NOW) {
            if (raw != 31) return;
            if (!player.hasPermission("incogshop.auction.buy")) { noPerm(player); return; }
            var result = plugin.auctions().buyNow(player, listing);
            player.sendMessage(plugin.prefix() + (result.success() ? "§a" : "§c") + result.message());
            plugin.auctionGui().open(player, holder.returnPage());
            return;
        }

        if (!player.hasPermission("incogshop.auction.bid")) { noPerm(player); return; }
        double minimum = plugin.auctionGui().minimumBid(listing);
        double amount;
        if (raw == 28) amount = minimum;
        else if (raw == 30) amount = WalletManager.round(minimum * 1.10);
        else if (raw == 32) amount = WalletManager.round(minimum * 1.25);
        else if (raw == 34) {
            pendingBids.put(player.getUniqueId(), new PendingBid(listing.id(), holder.returnPage()));
            player.closeInventory();
            player.sendMessage(plugin.prefix() + "§bType your bid amount in chat §7(example: §f10k§7, §f2.5m§7). Type §fcancel §7to stop.");
            player.sendMessage(plugin.prefix() + "§7Minimum bid: §f" + plugin.money(minimum));
            return;
        } else return;

        placeBid(player, listing, amount, holder.returnPage());
    }

    private void beginCreatePrice(Player player, AuctionListing.Mode mode, int hours) {
        player.closeInventory();
        Location location = player.getLocation().getBlock().getLocation().add(0, 2, 0);
        BlockPosition position = Position.block(location);
        pendingCreatePrices.put(player.getUniqueId(), new PendingCreatePrice(mode, hours, location, position));

        player.sendBlockChange(location, Material.OAK_SIGN.createBlockData());
        player.sendSignChange(location, new String[]{"Auction price", "", "", ""});
        player.openVirtualSign(position, Side.FRONT);
        player.sendMessage(plugin.prefix() + "§bType the listing price on the sign. §7Examples: §f10k§7, §f2.5m§7, §f1b§7.");
    }

    @EventHandler
    public void onCreatePriceSign(UncheckedSignChangeEvent event) {
        Player player = event.getPlayer();
        PendingCreatePrice pending = pendingCreatePrices.get(player.getUniqueId());
        if (pending == null) return;

        BlockPosition edited = event.getEditedBlockPosition();
        if (edited.blockX() != pending.position().blockX()
                || edited.blockY() != pending.position().blockY()
                || edited.blockZ() != pending.position().blockZ()) return;

        pendingCreatePrices.remove(player.getUniqueId());
        event.setCancelled(true);
        player.sendBlockChange(pending.location(), pending.location().getBlock().getBlockData());

        StringBuilder joined = new StringBuilder();
        for (var line : event.lines()) {
            String value = PlainTextComponentSerializer.plainText().serialize(line).trim();
            if (!value.isBlank()) {
                if (!joined.isEmpty()) joined.append(' ');
                joined.append(value);
            }
        }
        String input = joined.toString().trim();

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (input.isBlank() || input.equalsIgnoreCase("cancel")) {
                player.sendMessage(plugin.prefix() + "§ePrice entry cancelled.");
                plugin.auctionGui().openCreate(player, pending.mode(), 0, pending.hours());
                return;
            }

            double price = Money.parsePositive(input);
            if (price <= 0) {
                player.sendMessage(plugin.prefix() + "§cInvalid price. Examples: 10k, 2.5m, 1b.");
                plugin.auctionGui().openCreate(player, pending.mode(), 0, pending.hours());
                return;
            }

            plugin.auctionGui().openCreate(player, pending.mode(), price, pending.hours());
        });
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        PendingBid pending = pendingBids.remove(event.getPlayer().getUniqueId());
        if (pending == null) return;
        event.setCancelled(true);
        String input = event.getMessage().trim();
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (input.equalsIgnoreCase("cancel")) {
                player.sendMessage(plugin.prefix() + "§eCustom bid cancelled.");
                plugin.auctionGui().open(player, pending.returnPage());
                return;
            }
            double amount = Money.parsePositive(input);
            if (amount <= 0) {
                player.sendMessage(plugin.prefix() + "§cInvalid amount. Examples: 10k, 2.5m, 1b.");
                plugin.auctionGui().open(player, pending.returnPage());
                return;
            }
            AuctionListing listing = plugin.auctions().find(pending.listingId().toString());
            if (listing == null) {
                player.sendMessage(plugin.prefix() + "§cThat listing is no longer available.");
                plugin.auctionGui().open(player, pending.returnPage());
                return;
            }
            placeBid(player, listing, amount, pending.returnPage());
        });
    }

    private void placeBid(Player player, AuctionListing listing, double amount, int returnPage) {
        var result = plugin.auctions().bid(player, listing, amount);
        player.sendMessage(plugin.prefix() + (result.success() ? "§a" : "§c") + result.message());
        AuctionListing refreshed = plugin.auctions().find(listing.id().toString());
        if (refreshed != null) plugin.auctionGui().openDetail(player, refreshed, returnPage);
        else plugin.auctionGui().open(player, returnPage);
    }

    private void noPerm(Player player) {
        player.sendMessage(plugin.prefix() + "§cYou do not have permission.");
    }
}

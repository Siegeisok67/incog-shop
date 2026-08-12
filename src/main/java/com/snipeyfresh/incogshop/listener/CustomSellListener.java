package com.snipeyfresh.incogshop.listener;

import com.snipeyfresh.incogshop.IncogShopPlugin;
import com.snipeyfresh.incogshop.economy.WalletManager;
import com.snipeyfresh.incogshop.market.MarketManager;
import com.snipeyfresh.incogshop.sell.CustomPriceManager;
import com.snipeyfresh.incogshop.util.Money;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles /sell hand and /sell &lt;item&gt;: shows the player what the standard market price would pay,
 * then lets them type "confirm" to take it, "cancel" to back out, or a price-per-item to propose a
 * custom price. Follows the same chat-capture pattern as {@link OrderGuiListener}.
 */
public final class CustomSellListener implements Listener {
    private record Quote(Material material, int amount, boolean fromHand) {}

    private final IncogShopPlugin plugin;
    private final Map<UUID, Quote> quotes = new ConcurrentHashMap<>();

    public CustomSellListener(IncogShopPlugin plugin) { this.plugin = plugin; }

    /** @param amount the eligible quantity already determined by the caller (whole hand stack, or every
     *  matching eligible item in the inventory for the "sell by name" form). */
    public void beginQuote(Player player, Material material, int amount, boolean fromHand) {
        if (amount <= 0) {
            player.sendMessage(plugin.prefix() + "§cYou have no eligible " + material.name() + " to sell.");
            return;
        }
        double unitPrice = plugin.market().sellUnitPrice(material);
        double feePct = Math.max(0, plugin.getConfig().getDouble("market.transaction-fee-percent", 2.0));
        double estimatedPayout = WalletManager.round(unitPrice * amount * (1 - feePct / 100.0));
        quotes.put(player.getUniqueId(), new Quote(material, amount, fromHand));
        player.sendMessage(plugin.prefix() + "§eSelling §f" + amount + "x " + material.name()
                + "§e at the current market price would pay §f" + plugin.money(estimatedPayout) + "§e.");
        player.sendMessage("§7Type §fconfirm §7to sell at that price, type a price per item (e.g. §f50 §7or §f2.5k§7)"
                + " to propose a custom price, or §fcancel§7.");
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Quote quote = quotes.remove(event.getPlayer().getUniqueId());
        if (quote == null) return;
        event.setCancelled(true);
        String input = event.getMessage().trim();
        plugin.getServer().getScheduler().runTask(plugin, () -> handle(event.getPlayer(), quote, input));
    }

    private void handle(Player player, Quote quote, String input) {
        if (!player.isOnline()) return;

        if (input.equalsIgnoreCase("cancel")) {
            player.sendMessage(plugin.prefix() + "§7Sale cancelled.");
            return;
        }

        if (input.equalsIgnoreCase("confirm") || input.equalsIgnoreCase("yes")) {
            MarketManager.TradeResult result = quote.fromHand()
                    ? plugin.market().sellHand(player, null)
                    : plugin.market().sell(player, quote.material(), quote.amount(), null);
            player.sendMessage(plugin.prefix() + (result.success() ? "§a" : "§c") + result.message());
            return;
        }

        double requested = Money.parsePositive(input);
        if (requested <= 0) {
            player.sendMessage(plugin.prefix() + "§cType §fconfirm§c, §fcancel§c, or a positive price per item.");
            return;
        }
        if (!plugin.customPrices().enabled()) {
            player.sendMessage(plugin.prefix() + "§cCustom sell pricing is disabled right now; type §fconfirm §cto sell at the market price.");
            return;
        }

        CustomPriceManager.Negotiation quoted = plugin.customPrices().negotiate(quote.material(), requested, quote.amount());
        MarketManager.TradeResult result = quote.fromHand()
                ? plugin.market().sellHand(player, quoted.balancedUnitPrice())
                : plugin.market().sell(player, quote.material(), quote.amount(), quoted.balancedUnitPrice());

        if (!result.success()) {
            player.sendMessage(plugin.prefix() + "§c" + result.message());
            return;
        }

        CustomPriceManager.Negotiation actual = plugin.customPrices().withActualAmount(quoted, result.amount());
        plugin.customPrices().commit(player, actual);

        String voidedNote = "";
        if (Math.abs(actual.voidedDifference()) >= 0.01) {
            voidedNote = actual.voidedDifference() > 0
                    ? " §7(you proposed §f" + plugin.money(actual.requestedTotal()) + "§7; §f" + plugin.money(actual.voidedDifference()) + " §7above the balanced price was voided)"
                    : " §7(the balanced price came out §f" + plugin.money(-actual.voidedDifference()) + " §7higher than what you proposed)";
        }
        player.sendMessage(plugin.prefix() + "§aSold " + result.amount() + "x " + quote.material().name()
                + " for §f" + plugin.money(result.total()) + "§a at the balanced price." + voidedNote);
    }
}

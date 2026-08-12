package com.snipeyfresh.incogshop.sell;

import com.snipeyfresh.incogshop.IncogShopPlugin;
import com.snipeyfresh.incogshop.economy.WalletManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Map;

/**
 * Backs the "propose a custom price" branch of /sell hand and /sell &lt;item&gt;. A player's proposed
 * price is never paid out directly - it's one input into a balanced price that also considers the
 * current dynamic market price, other recent custom-price proposals for the same material, current
 * stock, and how much of that material has actually been bought/sold in the trailing window. The
 * transaction always executes at the balanced price; whatever the player asked for beyond that is
 * voided (simply not paid), never pocketed by anyone.
 *
 * Every negotiation - accepted or not - is written to its own log file (custom-sell.log) alongside the
 * standard audit.log, since this is a much more detailed, ad-hoc record than the plugin's normal
 * one-line-per-action audit trail.
 */
public final class CustomPriceManager {

    /** Everything the negotiation formula looked at and decided, for one proposal. */
    public record Negotiation(
            Material material, double requestedUnitPrice, double standardUnitPrice, double recentCustomAverage,
            double boughtVolume6h, double soldVolume6h, double balancedUnitPrice, int amount,
            double requestedTotal, double balancedTotal, double voidedDifference
    ) {}

    private record Proposal(long timestampMillis, double unitPrice) {}

    private final IncogShopPlugin plugin;
    private final File logFile;
    private final Map<Material, Deque<Proposal>> recentProposals = new EnumMap<>(Material.class);

    public CustomPriceManager(IncogShopPlugin plugin) {
        this.plugin = plugin;
        this.logFile = new File(plugin.getDataFolder(), "custom-sell.log");
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("custom-sell.enabled", true);
    }

    private long windowMillis() {
        return Math.max(1, plugin.getConfig().getLong("custom-sell.window-hours", 6)) * 60L * 60L * 1000L;
    }

    /**
     * Works out the balanced price for a proposed per-unit price on a material, without recording or
     * executing anything. Call {@link #commit(Player, Negotiation)} afterwards if the sale goes through.
     */
    public Negotiation negotiate(Material material, double requestedUnitPrice, int amount) {
        double standard = plugin.market().sellUnitPrice(material);
        double customAverage = recentCustomAverage(material);
        double[] volume = plugin.market().recentVolume(material, windowMillis());
        double bought = volume[0], sold = volume[1];

        double marketWeight = plugin.getConfig().getDouble("custom-sell.market-weight", 0.55);
        double customWeight = plugin.getConfig().getDouble("custom-sell.custom-weight", 0.30);
        double volumeWeight = plugin.getConfig().getDouble("custom-sell.volume-weight", 0.15);
        double weightSum = Math.max(0.0001, marketWeight + customWeight + volumeWeight);

        // Net demand imbalance over the window: more buying than selling nudges the balanced price up,
        // more selling than buying nudges it down. Normalized against total volume so a busy item and a
        // quiet item are treated on the same -1..1 scale rather than raw item counts.
        double totalFlow = bought + sold;
        double imbalance = totalFlow <= 0 ? 0 : (bought - sold) / totalFlow;
        double volumePrice = standard * (1 + imbalance * 0.5);

        // customAverage falls back to the standard price when nobody has proposed anything recently, so
        // an item with zero history isn't skewed by a weight pointing at nothing.
        double effectiveCustomAverage = customAverage > 0 ? customAverage : standard;

        double balanced = (standard * marketWeight + effectiveCustomAverage * customWeight + volumePrice * volumeWeight) / weightSum;

        double minMult = Math.max(0.01, plugin.getConfig().getDouble("custom-sell.min-deviation-multiplier", 0.4));
        double maxMult = Math.max(minMult, plugin.getConfig().getDouble("custom-sell.max-deviation-multiplier", 2.5));
        balanced = Math.max(standard * minMult, Math.min(standard * maxMult, balanced));
        balanced = Math.max(0.01, WalletManager.round(balanced));

        double requestedTotal = WalletManager.round(requestedUnitPrice * amount);
        double balancedTotal = WalletManager.round(balanced * amount);
        double voided = WalletManager.round(requestedTotal - balancedTotal);

        return new Negotiation(material, requestedUnitPrice, standard, customAverage, bought, sold, balanced, amount, requestedTotal, balancedTotal, voided);
    }

    /** Recomputes requestedTotal/balancedTotal/voidedDifference against the amount the market actually
     *  sold (which can be less than what was quoted, e.g. stock caps or an inventory change), keeping the
     *  same per-unit prices. Use this right before {@link #commit} so the log reflects what really happened. */
    public Negotiation withActualAmount(Negotiation n, int actualAmount) {
        if (actualAmount == n.amount()) return n;
        double requestedTotal = WalletManager.round(n.requestedUnitPrice() * actualAmount);
        double balancedTotal = WalletManager.round(n.balancedUnitPrice() * actualAmount);
        double voided = WalletManager.round(requestedTotal - balancedTotal);
        return new Negotiation(n.material(), n.requestedUnitPrice(), n.standardUnitPrice(), n.recentCustomAverage(),
                n.boughtVolume6h(), n.soldVolume6h(), n.balancedUnitPrice(), actualAmount, requestedTotal, balancedTotal, voided);
    }

    private double recentCustomAverage(Material material) {
        Deque<Proposal> log = recentProposals.get(material);
        if (log == null) return 0;
        long cutoff = System.currentTimeMillis() - windowMillis();
        while (!log.isEmpty() && log.peekFirst().timestampMillis() < cutoff) log.pollFirst();
        if (log.isEmpty()) return 0;
        double sum = 0;
        for (Proposal proposal : log) sum += proposal.unitPrice();
        return sum / log.size();
    }

    /** Records the proposal into recent history (so it counts toward future negotiations for this
     *  material) and writes the full negotiation to custom-sell.log. Call once, after the sale is
     *  actually executed. */
    public void commit(Player player, Negotiation negotiation) {
        recentProposals.computeIfAbsent(negotiation.material(), m -> new ArrayDeque<>())
                .addLast(new Proposal(System.currentTimeMillis(), negotiation.requestedUnitPrice()));
        log(player, negotiation);
    }

    private void log(Player player, Negotiation n) {
        String line = Instant.now() + " | player=" + player.getName() + " (" + player.getUniqueId() + ")"
                + " | material=" + n.material().name()
                + " | amount=" + n.amount()
                + " | requestedUnitPrice=" + n.requestedUnitPrice()
                + " | standardUnitPrice=" + n.standardUnitPrice()
                + " | recentCustomAverage=" + n.recentCustomAverage()
                + " | boughtVolume6h=" + n.boughtVolume6h()
                + " | soldVolume6h=" + n.soldVolume6h()
                + " | balancedUnitPrice=" + n.balancedUnitPrice()
                + " | requestedTotal=" + n.requestedTotal()
                + " | balancedTotal(paid)=" + n.balancedTotal()
                + " | voidedDifference=" + n.voidedDifference();
        try (FileWriter fw = new FileWriter(logFile, true)) {
            fw.write(line + System.lineSeparator());
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not write custom-sell.log: " + ex.getMessage());
        }
    }
}

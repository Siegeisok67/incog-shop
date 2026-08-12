package com.snipeyfresh.incogshop;

import com.snipeyfresh.incogshop.auction.AuctionManager;
import com.snipeyfresh.incogshop.command.*;
import com.snipeyfresh.incogshop.economy.WalletManager;
import com.snipeyfresh.incogshop.history.PriceHistoryManager;
import com.snipeyfresh.incogshop.discord.DiscordPriceBridge;
import com.snipeyfresh.incogshop.gui.AuctionGui;
import com.snipeyfresh.incogshop.gui.SellGui;
import com.snipeyfresh.incogshop.gui.ShopGui;
import com.snipeyfresh.incogshop.listener.*;
import com.snipeyfresh.incogshop.market.MarketManager;
import com.snipeyfresh.incogshop.order.MarketOrderManager;
import com.snipeyfresh.incogshop.shop.ShopManager;
import com.snipeyfresh.incogshop.util.Text;
import com.snipeyfresh.incogshop.stash.StashManager;
import com.snipeyfresh.incogshop.xp.XpVaultManager;
import com.snipeyfresh.incogshop.gui.StashGui;
import com.snipeyfresh.incogshop.gui.XpVaultGui;
import com.snipeyfresh.incogshop.gui.AdminSetupGui;
import com.snipeyfresh.incogshop.custom.CustomCategoryManager;
import com.snipeyfresh.incogshop.custom.GuiLayoutManager;
import com.snipeyfresh.incogshop.gui.LayoutEditorGui;
import com.snipeyfresh.incogshop.sell.CustomPriceManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class IncogShopPlugin extends JavaPlugin {
    private WalletManager wallets;
    private MarketManager market;
    private ShopManager shops;
    private AuctionManager auctions;
    private MarketOrderManager orders;
    private PriceHistoryManager history;
    private DiscordPriceBridge discordPriceBridge;
    private ShopGui gui;
    private SellGui sellGui;
    private AuctionGui auctionGui;
    private com.snipeyfresh.incogshop.gui.OrderBookGui orderGui;
    private CustomCategoryManager customCategories;
    private GuiLayoutManager layouts;
    private LayoutEditorGui layoutEditor;
    private StashManager stash;
    private StashGui stashGui;
    private XpVaultManager xpVault;
    private XpVaultGui xpVaultGui;
    private AdminSetupGui adminSetupGui;
    private CustomPriceManager customPrices;
    private CustomSellListener customSellListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        wallets = new WalletManager(this);
        wallets.load();
        if (!wallets.ready()) {
            getLogger().severe("Incog-Shop requires a working Vault economy provider in VAULT mode. Disabling to protect player balances.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        market = new MarketManager(this);
        customCategories = new CustomCategoryManager(this);
        layouts = new GuiLayoutManager(this);
        shops = new ShopManager(this);
        auctions = new AuctionManager(this);
        orders = new MarketOrderManager(this);
        gui = new ShopGui(this);
        sellGui = new SellGui(this);
        auctionGui = new AuctionGui(this);
        orderGui = new com.snipeyfresh.incogshop.gui.OrderBookGui(this);
        layoutEditor = new LayoutEditorGui(this);
        stash = new StashManager(this);
        stashGui = new StashGui(this);
        xpVault = new XpVaultManager(this);
        xpVaultGui = new XpVaultGui(this);
        adminSetupGui = new AdminSetupGui(this);
        customPrices = new CustomPriceManager(this);
        customSellListener = new CustomSellListener(this);

        market.load();
        customCategories.load();
        layouts.load();
        stash.load();
        xpVault.load();
        shops.load();
        auctions.load();
        orders.load();

        history = new PriceHistoryManager(this);
        history.load();
        history.captureChangedPrices();

        discordPriceBridge = new DiscordPriceBridge(this);
        discordPriceBridge.start();
        for (Player player : Bukkit.getOnlinePlayers()) wallets.touch(player.getUniqueId(), player.getName());

        MarketCommand marketCommand = new MarketCommand(this);
        PluginCommand marketPluginCommand = Objects.requireNonNull(getCommand("market"));
        marketPluginCommand.setExecutor(marketCommand); marketPluginCommand.setTabCompleter(marketCommand);
        Objects.requireNonNull(getCommand("sell")).setExecutor(new SellCommand(this));
        Objects.requireNonNull(getCommand("stash")).setExecutor(new StashCommand(this));
        Objects.requireNonNull(getCommand("xpvault")).setExecutor(new XpVaultCommand(this));

        PlayerShopCommand pshop = new PlayerShopCommand(this);
        PluginCommand pshopCommand = Objects.requireNonNull(getCommand("pshop"));
        pshopCommand.setExecutor(pshop); pshopCommand.setTabCompleter(pshop);

        AuctionCommand auction = new AuctionCommand(this);
        PluginCommand auctionCommand = Objects.requireNonNull(getCommand("ah"));
        auctionCommand.setExecutor(auction); auctionCommand.setTabCompleter(auction);

        AdminCommand admin = new AdminCommand(this);
        PluginCommand adminCommand = Objects.requireNonNull(getCommand("marketadmin"));
        adminCommand.setExecutor(admin); adminCommand.setTabCompleter(admin);

        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new LayoutEditorListener(this), this);
        getServer().getPluginManager().registerEvents(new AdminSetupListener(this), this);
        getServer().getPluginManager().registerEvents(new SellGuiListener(this), this);
        getServer().getPluginManager().registerEvents(new StashGuiListener(this), this);
        getServer().getPluginManager().registerEvents(new StashOverflowListener(this), this);
        getServer().getPluginManager().registerEvents(new XpVaultGuiListener(this), this);
        getServer().getPluginManager().registerEvents(new AuctionGuiListener(this), this);
        getServer().getPluginManager().registerEvents(new OrderGuiListener(this), this);
        getServer().getPluginManager().registerEvents(new ShopProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(customSellListener, this);

        getServer().getScheduler().runTaskTimer(this, () -> {
            market.decayDemand();
        }, 20L * 60, 20L * 60);

        long historyTicks = Math.max(20L * 60L,
                getConfig().getLong("discord-price-check.history-resolution-minutes", 5) * 20L * 60L);
        getServer().getScheduler().runTaskTimer(this, history::captureChangedPrices, historyTicks, historyTicks);
        getServer().getScheduler().runTaskTimer(this, history::pruneAndRewrite, 20L * 60L * 60L, 20L * 60L * 60L);
        getServer().getScheduler().runTaskTimer(this, auctions::settleExpired, 20L * 30, 20L * 30);
        getServer().getScheduler().runTaskTimer(this, market::checkScheduledRestock, 20L * 60L, 20L * 60L);
        long saveTicks = Math.max(20L, getConfig().getLong("saving.autosave-seconds", 60) * 20L);
        getServer().getScheduler().runTaskTimer(this, this::saveAllData, saveTicks, saveTicks);

        getLogger().info("Incog-Shop 1.8.4 by SnipeyFresh enabled. Economy provider: " + wallets.providerName() + ". Tradable materials: " + market.tradableMaterials().size() + ".");
    }

    @Override public void onDisable() {
        if (discordPriceBridge != null) discordPriceBridge.stop();
        if (history != null) history.pruneAndRewrite();
        saveAllData();
    }

    public WalletManager wallets() { return wallets; }
    public MarketManager market() { return market; }
    public ShopManager shops() { return shops; }
    public AuctionManager auctions() { return auctions; }
    public MarketOrderManager orders() { return orders; }
    public PriceHistoryManager history() { return history; }
    public ShopGui gui() { return gui; }
    public SellGui sellGui() { return sellGui; }
    public AuctionGui auctionGui() { return auctionGui; }
    public com.snipeyfresh.incogshop.gui.OrderBookGui orderGui() { return orderGui; }
    public CustomCategoryManager customCategories() { return customCategories; }
    public GuiLayoutManager layouts() { return layouts; }
    public LayoutEditorGui layoutEditor() { return layoutEditor; }
    public StashManager stash() { return stash; }
    public StashGui stashGui() { return stashGui; }
    public XpVaultManager xpVault() { return xpVault; }
    public XpVaultGui xpVaultGui() { return xpVaultGui; }
    public AdminSetupGui adminSetupGui() { return adminSetupGui; }
    public CustomPriceManager customPrices() { return customPrices; }
    public CustomSellListener customSell() { return customSellListener; }

    public String prefix() { return Text.color(getConfig().getString("messages.prefix", "&6[Incog-Shop]&r ")); }
    public String money(double amount) { return getConfig().getString("economy.currency-symbol", "$") + String.format("%,.2f", amount); }

    public void saveAllData() {
        if (wallets != null) wallets.save();
        if (market != null) market.save();
        if (shops != null) shops.save();
        if (auctions != null) auctions.save();
        if (orders != null) orders.save();
        if (customCategories != null) customCategories.save();
        if (stash != null) stash.save();
        if (xpVault != null) xpVault.save();
    }

    public void reloadAll() {
        saveAllData();
        reloadConfig();
        wallets.load();
        market.load();
        customCategories.load();
        layouts.load();
        stash.load();
        xpVault.load();
        shops.load();
        auctions.load();
        orders.load();

        if (discordPriceBridge != null) discordPriceBridge.stop();
        if (history == null) history = new PriceHistoryManager(this);
        history.load();
        history.captureChangedPrices();

        discordPriceBridge = new DiscordPriceBridge(this);
        discordPriceBridge.start();
        for (Player player : Bukkit.getOnlinePlayers()) wallets.touch(player.getUniqueId(), player.getName());
    }
}

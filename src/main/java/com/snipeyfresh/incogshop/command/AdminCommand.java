package com.snipeyfresh.incogshop.command;

import com.snipeyfresh.incogshop.IncogShopPlugin;
import com.snipeyfresh.incogshop.util.Money;
import com.snipeyfresh.incogshop.market.MarketMode;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.TabExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class AdminCommand implements TabExecutor {
    private final IncogShopPlugin plugin;
    public AdminCommand(IncogShopPlugin plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) { help(sender); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "gui" -> {
                if (!perm(sender, "incogshop.admin.gui")) return true;
                if (!(sender instanceof Player p)) { sender.sendMessage("Players only for the GUI."); return true; }
                plugin.gui().openCategories(p, true);
            }
            case "layout" -> {
                if (!perm(sender, "incogshop.admin.layout")) return true;
                if (!(sender instanceof Player p)) { sender.sendMessage("Players only for the layout editor."); return true; }
                String screen = args.length >= 2 ? args[1] : "menu";
                plugin.layoutEditor().open(p, screen, null);
            }
            case "createcategory" -> {
                if (!perm(sender, "incogshop.admin.category")) return true;
                if (args.length < 4) { sender.sendMessage("Usage: /marketadmin createcategory <id> <icon-material> <display name...>"); return true; }
                Material icon = material(args[2]);
                if (icon == null || !icon.isItem()) { sender.sendMessage(plugin.prefix()+"§cInvalid icon material."); return true; }
                String display = String.join(" ", java.util.Arrays.copyOfRange(args,3,args.length));
                boolean ok = plugin.customCategories().create(args[1], display, icon);
                sender.sendMessage(plugin.prefix() + (ok ? "§aCreated custom category §f"+display+"§a." : "§cThat category ID already exists or is invalid."));
            }
            case "deletecategory" -> {
                if (!perm(sender, "incogshop.admin.category")) return true;
                if (args.length != 2) { sender.sendMessage("Usage: /marketadmin deletecategory <id>"); return true; }
                boolean ok = plugin.customCategories().delete(args[1]);
                sender.sendMessage(plugin.prefix()+(ok?"§aDeleted category. Its items returned to automatic built-in categories.":"§cUnknown custom category."));
            }
            case "setcategory" -> {
                if (!perm(sender, "incogshop.admin.category")) return true;
                if (args.length != 3) { sender.sendMessage("Usage: /marketadmin setcategory <material> <custom-category-id|auto>"); return true; }
                Material m=material(args[1]);
                if (m==null || plugin.market().entry(m)==null) { sender.sendMessage(plugin.prefix()+"§cUnknown market material."); return true; }
                if (args[2].equalsIgnoreCase("auto")) {
                    plugin.customCategories().clearAssignment(m);
                    sender.sendMessage(plugin.prefix()+"§a"+m.name()+" returned to automatic built-in categorization.");
                } else if (plugin.customCategories().assign(m,args[2])) {
                    sender.sendMessage(plugin.prefix()+"§aMoved "+m.name()+" to custom category §f"+args[2]+"§a.");
                } else sender.sendMessage(plugin.prefix()+"§cUnknown custom category.");
            }
            case "additem" -> {
                if (!perm(sender, "incogshop.admin.item")) return true;
                if (args.length < 3 || args.length > 4) { sender.sendMessage("Usage: /marketadmin additem <material> <base-price> [buy_sell|sell_only|disabled]"); return true; }
                Material m=material(args[1]);
                double price=Money.parsePositive(args[2]);
                if(m==null || price<=0){sender.sendMessage(plugin.prefix()+"§cInvalid material or price.");return true;}
                MarketMode mode=MarketMode.BUY_SELL;
                if(args.length==4) try{mode=MarketMode.valueOf(args[3].toUpperCase(Locale.ROOT));}catch(IllegalArgumentException ex){sender.sendMessage(plugin.prefix()+"§cMode must be BUY_SELL, SELL_ONLY, or DISABLED.");return true;}
                boolean ok=plugin.market().addOrEnableMaterial(m,price,mode,sender.getName());
                sender.sendMessage(plugin.prefix()+(ok?"§aAdded/enabled "+m.name()+" at "+plugin.money(price)+" in mode "+mode+".":"§cThat material cannot be added. Ancient Debris and unsafe/admin-only materials remain blocked."));
            }
            case "mode" -> {
                if (!perm(sender, "incogshop.admin.item")) return true;
                if(args.length!=3){sender.sendMessage("Usage: /marketadmin mode <material> <buy_sell|sell_only|disabled>");return true;}
                Material m=material(args[1]); MarketMode mode;
                try{mode=MarketMode.valueOf(args[2].toUpperCase(Locale.ROOT));}catch(Exception ex){sender.sendMessage(plugin.prefix()+"§cMode must be BUY_SELL, SELL_ONLY, or DISABLED.");return true;}
                if(m==null || !plugin.market().setMarketMode(m,mode,sender.getName())) sender.sendMessage(plugin.prefix()+"§cUnknown market material.");
                else sender.sendMessage(plugin.prefix()+"§a"+m.name()+" market mode set to §f"+mode.name().replace('_',' ')+"§a.");
            }
            case "price" -> {
                if (!perm(sender, "incogshop.admin.price")) return true;
                if (args.length < 3) { sender.sendMessage("Usage: /marketadmin price <material> <amount|reset>"); return true; }
                Material m = material(args[1]);
                if (m == null || !plugin.market().isTradable(m)) { sender.sendMessage(plugin.prefix() + "§cUnknown or excluded material."); return true; }
                if (args[2].equalsIgnoreCase("reset")) {
                    plugin.market().resetPrice(m, sender.getName()); sender.sendMessage(plugin.prefix() + "§aReset " + m.name() + " to its default base price and cleared demand pressure."); return true;
                }
                double price = Money.parsePositive(args[2]);
                if (!(price > 0) || !plugin.market().setBasePrice(m, price, sender.getName())) { sender.sendMessage(plugin.prefix() + "§cPrice must be a positive number."); return true; }
                sender.sendMessage(plugin.prefix() + "§aBase price for " + m.name() + " set to " + plugin.money(price) + ". Dynamic supply/demand still applies around this base.");
            }
            case "infinitestock", "infinite" -> {
                if (!perm(sender, "incogshop.admin.stock")) return true;
                if (args.length < 2 || args[1].equalsIgnoreCase("status")) {
                    sender.sendMessage(plugin.prefix() + "§7Infinite stock is currently " + (plugin.market().isInfiniteStockEnabled() ? "§aENABLED" : "§cDISABLED") + "§7.");
                    return true;
                }
                boolean current = plugin.market().isInfiniteStockEnabled();
                boolean next;
                if (args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("enable")) next = true;
                else if (args[1].equalsIgnoreCase("off") || args[1].equalsIgnoreCase("disable")) next = false;
                else if (args[1].equalsIgnoreCase("toggle")) next = !current;
                else {
                    sender.sendMessage("Usage: /marketadmin infinitestock <on|off|toggle|status>");
                    return true;
                }
                plugin.market().setInfiniteStockEnabled(next, sender.getName());
                sender.sendMessage(plugin.prefix() + (next ? "§aInfinite stock enabled globally." : "§cInfinite stock disabled globally."));
                return true;
            }
            case "stock" -> {
                if (!perm(sender, "incogshop.admin.stock")) return true;
                if (args.length != 4 || !(args[2].equalsIgnoreCase("add") || args[2].equalsIgnoreCase("set"))) { sender.sendMessage("Usage: /marketadmin stock <material> <add|set> <amount>"); return true; }
                Material m = material(args[1]);
                if (m == null || !plugin.market().isTradable(m)) { sender.sendMessage(plugin.prefix() + "§cUnknown or excluded material."); return true; }
                long amount; try { amount = Long.parseLong(args[3]); } catch (NumberFormatException ex) { sender.sendMessage(plugin.prefix() + "§cInvalid integer amount."); return true; }
                boolean ok = args[2].equalsIgnoreCase("add") ? plugin.market().addStock(m, amount, sender.getName()) : plugin.market().setStock(m, amount, sender.getName());
                sender.sendMessage(plugin.prefix() + (ok ? "§aStock for " + m.name() + " is now " + plugin.market().entry(m).stock() + "." : "§cCould not change stock."));
            }
            case "money" -> {
                if (!perm(sender, "incogshop.admin.money")) return true;
                if (args.length < 3) { sender.sendMessage("Usage: /marketadmin money <get|give|take|set> <player> [amount]"); return true; }
                UUID id = resolve(args[2]);
                if (id == null) { sender.sendMessage(plugin.prefix() + "§cPlayer must be online or have a stored Incog-Shop wallet."); return true; }
                String op = args[1].toLowerCase(Locale.ROOT);
                if (op.equals("get")) { sender.sendMessage(plugin.prefix() + "§6Balance: §f" + plugin.money(plugin.wallets().get(id))); return true; }
                if (args.length != 4) { sender.sendMessage("Usage: /marketadmin money <give|take|set> <player> <amount>"); return true; }
                double amount = args[3].equals("0") ? 0 : Money.parsePositive(args[3]);
                if (!Double.isFinite(amount) || amount < 0) { sender.sendMessage(plugin.prefix() + "§cAmount must be zero or greater."); return true; }
                switch (op) {
                    case "give" -> plugin.wallets().deposit(id, amount);
                    case "take" -> plugin.wallets().set(id, Math.max(0, plugin.wallets().get(id) - amount));
                    case "set" -> plugin.wallets().set(id, amount);
                    default -> { sender.sendMessage(plugin.prefix() + "§cUnknown money action."); return true; }
                }
                plugin.market().audit("ADMIN_MONEY_" + op.toUpperCase(Locale.ROOT), null, sender.getName(), id.toString(), 0, amount, "");
                sender.sendMessage(plugin.prefix() + "§aWallet updated. New balance: §f" + plugin.money(plugin.wallets().get(id)));
            }
            case "save" -> {
                if (!perm(sender, "incogshop.admin.save")) return true;
                plugin.saveAllData(); sender.sendMessage(plugin.prefix() + "§aAll Incog-Shop data saved.");
            }
            case "reload" -> {
                if (!perm(sender, "incogshop.admin.reload")) return true;
                plugin.reloadAll(); sender.sendMessage(plugin.prefix() + "§aConfig and persistent data reloaded.");
            }
            default -> help(sender);
        }
        return true;
    }

    private boolean perm(CommandSender s, String node) { if (s.hasPermission(node)) return true; s.sendMessage(plugin.prefix() + "§cNo permission: " + node); return false; }
    private void help(CommandSender s) { s.sendMessage(plugin.prefix() + "§6Admin: §f/marketadmin gui§7, §flayout§7, §fcreatecategory§7, §fdeletecategory§7, §fsetcategory§7, §fadditem§7, §fmode§7, §fprice§7, §fstock§7, §fmoney§7, §fsave§7, §freload"); }
    private static Material material(String s) { return Material.matchMaterial(s.toUpperCase(Locale.ROOT)); }
    private UUID resolve(String name) { Player p = plugin.getServer().getPlayerExact(name); return p != null ? p.getUniqueId() : plugin.wallets().findByName(name); }

    @Override public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return filter(List.of("gui", "layout", "createcategory", "deletecategory", "setcategory", "additem", "mode", "infinitestock", "price", "stock", "money", "save", "reload"), args[0]);
        if (args[0].equalsIgnoreCase("layout") && args.length == 2)
            return filter(List.of("categories","subcategories","items"), args[1]);
        if ((args[0].equalsIgnoreCase("infinitestock") || args[0].equalsIgnoreCase("infinite")) && args.length == 2)
            return filter(List.of("on", "off", "toggle", "status"), args[1]);
        if ((args[0].equalsIgnoreCase("price") || args[0].equalsIgnoreCase("stock") || args[0].equalsIgnoreCase("mode") || args[0].equalsIgnoreCase("setcategory") || args[0].equalsIgnoreCase("additem")) && args.length == 2)
            return filter(plugin.market().tradableMaterials().stream().map(m -> m.name().toLowerCase(Locale.ROOT)).toList(), args[1]);
        if (args[0].equalsIgnoreCase("price") && args.length == 3) return filter(List.of("reset"), args[2]);
        if (args[0].equalsIgnoreCase("stock") && args.length == 3) return filter(List.of("add", "set"), args[2]);
        if (args[0].equalsIgnoreCase("money") && args.length == 2) return filter(List.of("get", "give", "take", "set"), args[1]);
        if (args[0].equalsIgnoreCase("mode") && args.length == 3) return filter(List.of("buy_sell","sell_only","disabled"),args[2]);
        if (args[0].equalsIgnoreCase("setcategory") && args.length == 3) {
            List<String> ids = new ArrayList<>(); ids.add("auto"); for (var c : plugin.customCategories().all()) ids.add(c.id());
            return filter(ids,args[2]);
        }
        if (args[0].equalsIgnoreCase("deletecategory") && args.length == 2) return filter(plugin.customCategories().all().stream().map(c->c.id()).toList(),args[1]);
        return List.of();
    }
    private static List<String> filter(List<String> values, String prefix) { String p = prefix.toLowerCase(Locale.ROOT); List<String> out = new ArrayList<>(); for (String v : values) if (v.startsWith(p)) out.add(v); return out; }
}

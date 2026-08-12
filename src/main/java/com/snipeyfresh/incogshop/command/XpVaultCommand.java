package com.snipeyfresh.incogshop.command;

import com.snipeyfresh.incogshop.IncogShopPlugin;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class XpVaultCommand implements CommandExecutor {
    private final IncogShopPlugin plugin;
    public XpVaultCommand(IncogShopPlugin plugin){this.plugin=plugin;}

    @Override
    public boolean onCommand(@NotNull CommandSender sender,@NotNull Command command,@NotNull String label,@NotNull String[] args){
        if(!(sender instanceof Player player)){sender.sendMessage("Players only.");return true;}
        if(!player.hasPermission("incogshop.xpvault")){
            player.sendMessage(plugin.prefix()+"§cYou do not have permission: §fincogshop.xpvault");
            return true;
        }

        if(args.length==0){
            plugin.xpVaultGui().open(player);
            return true;
        }

        if(args.length!=2){
            player.sendMessage(plugin.prefix()+"§eUsage: /xpvault [deposit|withdraw] <amount|all>");
            return true;
        }

        String mode=args[0].toLowerCase();
        long available = mode.equals("deposit") ? plugin.xpVault().current(player) : plugin.xpVault().stored(player.getUniqueId());
        long amount;
        if(args[1].equalsIgnoreCase("all")) amount=available;
        else {
            try { amount=Long.parseLong(args[1].replace(",","")); }
            catch(NumberFormatException ex){ amount=-1; }
        }
        if(amount<=0){
            player.sendMessage(plugin.prefix()+"§cAmount must be a positive XP number or 'all'.");
            return true;
        }

        if(mode.equals("deposit")){
            long moved=plugin.xpVault().deposit(player,amount);
            player.sendMessage(plugin.prefix()+(moved>0?"§aDeposited §f"+moved+" XP§a.":"§eYou do not have XP to deposit."));
        } else if(mode.equals("withdraw")){
            long moved=plugin.xpVault().withdraw(player,amount);
            player.sendMessage(plugin.prefix()+(moved>0?"§aWithdrew §f"+moved+" XP§a.":"§eYour XP Vault is empty."));
        } else {
            player.sendMessage(plugin.prefix()+"§eUsage: /xpvault [deposit|withdraw] <amount|all>");
        }
        return true;
    }
}

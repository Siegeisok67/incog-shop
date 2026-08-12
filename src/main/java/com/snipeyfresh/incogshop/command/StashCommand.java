package com.snipeyfresh.incogshop.command;
import com.snipeyfresh.incogshop.IncogShopPlugin;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
public final class StashCommand implements CommandExecutor {
    private final IncogShopPlugin plugin;
    public StashCommand(IncogShopPlugin plugin){this.plugin=plugin;}
    public boolean onCommand(@NotNull CommandSender sender,@NotNull Command command,@NotNull String label,@NotNull String[] args){
        if(!(sender instanceof Player p)){sender.sendMessage("Players only.");return true;}
        if(!p.hasPermission("incogshop.stash")){p.sendMessage(plugin.prefix()+"§cYou do not have permission.");return true;}
        plugin.stashGui().open(p,0); return true;
    }
}

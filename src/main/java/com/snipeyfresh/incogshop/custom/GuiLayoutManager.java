package com.snipeyfresh.incogshop.custom;

import com.snipeyfresh.incogshop.IncogShopPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;

public final class GuiLayoutManager {
    private final IncogShopPlugin plugin;
    private final File file;
    private YamlConfiguration y;
    public GuiLayoutManager(IncogShopPlugin plugin) { this.plugin=plugin; file=new File(plugin.getDataFolder(),"gui-layout.yml"); }
    public void load(){ y=YamlConfiguration.loadConfiguration(file); }
    public int slot(String screen,String key,int fallback){ return Math.max(0,Math.min(53,y.getInt(screen+"."+key,fallback))); }
    public void set(String screen,String key,int slot){ y.set(screen+"."+key,Math.max(0,Math.min(53,slot))); save(); }
    public void reset(String screen){ y.set(screen,null); save(); }
    private void save(){ try{y.save(file);}catch(IOException e){plugin.getLogger().severe("Could not save gui-layout.yml: "+e.getMessage());}}
}

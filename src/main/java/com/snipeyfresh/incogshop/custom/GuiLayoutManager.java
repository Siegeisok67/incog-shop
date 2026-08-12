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
    public void load(){
        y=YamlConfiguration.loadConfiguration(file);
        migrateDynamicSubcategoryCentering();
        migrateItemBackNavbar();
    }

    private void migrateDynamicSubcategoryCentering(){
        if (y.getBoolean("meta.dynamic-subcategory-centering-v2", false)) return;

        // Older Incog-Shop versions stored 21 generic subcategory positions.
        // Those coordinates override the newer dynamic centering and make
        // categories with only a few sections look left-aligned. Remove only
        // the legacy subcategory item positions while preserving Back/Search/
        // All/Status and all other GUI customizations.
        for (int i=0;i<21;i++) y.set("subcategories.sub:"+i, null);
        y.set("meta.dynamic-subcategory-centering-v2", true);
        save();
    }

    private void migrateItemBackNavbar(){
        if (y.getBoolean("meta.item-back-navbar-v3", false)) return;

        // A few older layouts could leave the item-screen Back control in an
        // item slot (including slot 0). Put it back on the bottom navigation
        // row. Admins can still move it later through the Layout Designer.
        int saved = y.getInt("items.back", 46);
        if (saved < 45 || saved > 53) {
            y.set("items.back", 46);
        }

        // Also eliminate a common legacy collision where Previous and Back
        // were both saved to the same position.
        int previous = y.getInt("items.previous", 45);
        int back = y.getInt("items.back", 46);
        if (previous == back) {
            y.set("items.previous", 45);
            y.set("items.back", 46);
        }

        y.set("meta.item-back-navbar-v3", true);
        save();
    }

    public int slot(String screen,String key,int fallback){ return Math.max(0,Math.min(53,y.getInt(screen+"."+key,fallback))); }
    public void set(String screen,String key,int slot){ y.set(screen+"."+key,Math.max(0,Math.min(53,slot))); save(); }
    public void reset(String screen){ y.set(screen,null); save(); }
    private void save(){ try{y.save(file);}catch(IOException e){plugin.getLogger().severe("Could not save gui-layout.yml: "+e.getMessage());}}
}

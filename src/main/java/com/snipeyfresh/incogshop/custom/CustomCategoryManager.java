package com.snipeyfresh.incogshop.custom;

import com.snipeyfresh.incogshop.IncogShopPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class CustomCategoryManager {
    public record CustomCategory(String id, String display, Material icon) {}
    private final IncogShopPlugin plugin;
    private final File file;
    private final LinkedHashMap<String, CustomCategory> categories = new LinkedHashMap<>();
    private final EnumMap<Material, String> assignments = new EnumMap<>(Material.class);

    public CustomCategoryManager(IncogShopPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "custom-categories.yml");
    }

    public void load() {
        categories.clear(); assignments.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        var sec = y.getConfigurationSection("categories");
        if (sec != null) for (String id : sec.getKeys(false)) {
            String clean = normalize(id);
            String display = y.getString("categories."+id+".display", id);
            Material icon = Material.matchMaterial(y.getString("categories."+id+".icon", "CHEST"));
            if (!clean.isBlank()) categories.put(clean, new CustomCategory(clean, display, icon == null ? Material.CHEST : icon));
        }
        var as = y.getConfigurationSection("assignments");
        if (as != null) for (String mat : as.getKeys(false)) {
            Material m = Material.matchMaterial(mat);
            String id = normalize(y.getString("assignments."+mat, ""));
            if (m != null && categories.containsKey(id)) assignments.put(m,id);
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (CustomCategory c : categories.values()) {
            y.set("categories."+c.id()+".display", c.display());
            y.set("categories."+c.id()+".icon", c.icon().name());
        }
        for (var e : assignments.entrySet()) y.set("assignments."+e.getKey().name(), e.getValue());
        try { y.save(file); } catch (IOException e) { plugin.getLogger().severe("Could not save custom-categories.yml: "+e.getMessage()); }
    }

    public Collection<CustomCategory> all() { return Collections.unmodifiableCollection(categories.values()); }
    public CustomCategory get(String id) { return categories.get(normalize(id)); }
    public String assigned(Material material) { return assignments.get(material); }
    public boolean create(String id, String display, Material icon) {
        String key=normalize(id);
        if (key.isBlank() || categories.containsKey(key)) return false;
        categories.put(key,new CustomCategory(key, display == null || display.isBlank() ? id : display, icon == null ? Material.CHEST : icon));
        save(); return true;
    }
    public boolean delete(String id) {
        String key=normalize(id);
        if (categories.remove(key)==null) return false;
        assignments.entrySet().removeIf(e -> e.getValue().equals(key));
        save(); return true;
    }
    public boolean assign(Material material, String id) {
        String key=normalize(id);
        if (!categories.containsKey(key)) return false;
        assignments.put(material,key); save(); return true;
    }
    public void clearAssignment(Material material) { assignments.remove(material); save(); }
    public List<Material> materials(String id) {
        String key=normalize(id);
        return assignments.entrySet().stream().filter(e->e.getValue().equals(key)).map(Map.Entry::getKey).sorted(Comparator.comparing(Material::name)).toList();
    }
    public static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]","_");
    }
}

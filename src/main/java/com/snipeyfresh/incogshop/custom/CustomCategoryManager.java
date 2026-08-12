package com.snipeyfresh.incogshop.custom;

import com.snipeyfresh.incogshop.IncogShopPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class CustomCategoryManager {
    public record CustomCategory(String id, String display, Material icon) {}
    public record CustomSubcategory(String id, String categoryId, String display, Material icon) {}

    private final IncogShopPlugin plugin;
    private final File file;
    private final LinkedHashMap<String, CustomCategory> categories = new LinkedHashMap<>();
    private final LinkedHashMap<String, CustomSubcategory> subcategories = new LinkedHashMap<>();
    private final EnumMap<Material, String> assignments = new EnumMap<>(Material.class);
    private final EnumMap<Material, String> subAssignments = new EnumMap<>(Material.class);

    public CustomCategoryManager(IncogShopPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "custom-categories.yml");
    }

    public void load() {
        categories.clear(); subcategories.clear(); assignments.clear(); subAssignments.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);

        var sec = y.getConfigurationSection("categories");
        if (sec != null) for (String id : sec.getKeys(false)) {
            String key=normalize(id);
            if (key.isBlank()) continue;
            String display=y.getString("categories."+id+".display",id);
            Material icon=Material.matchMaterial(y.getString("categories."+id+".icon","CHEST"));
            categories.put(key,new CustomCategory(key,display,icon==null?Material.CHEST:icon));
        }

        var sub = y.getConfigurationSection("subcategories");
        if (sub != null) for (String keyRaw : sub.getKeys(false)) {
            String categoryId=normalize(y.getString("subcategories."+keyRaw+".category",""));
            String id=normalize(y.getString("subcategories."+keyRaw+".id",keyRaw));
            if (!categories.containsKey(categoryId) || id.isBlank()) continue;
            String display=y.getString("subcategories."+keyRaw+".display",id);
            Material icon=Material.matchMaterial(y.getString("subcategories."+keyRaw+".icon","CHEST"));
            subcategories.put(subKey(categoryId,id),new CustomSubcategory(id,categoryId,display,icon==null?Material.CHEST:icon));
        }

        var as=y.getConfigurationSection("assignments");
        if(as!=null) for(String mat:as.getKeys(false)){
            Material m=Material.matchMaterial(mat);
            String cat=normalize(y.getString("assignments."+mat+".category", y.getString("assignments."+mat,"")));
            String subId=normalize(y.getString("assignments."+mat+".subcategory",""));
            if(m!=null && categories.containsKey(cat)){
                assignments.put(m,cat);
                if(!subId.isBlank() && subcategories.containsKey(subKey(cat,subId))) subAssignments.put(m,subId);
            }
        }
    }

    public void save() {
        YamlConfiguration y=new YamlConfiguration();
        for(CustomCategory c:categories.values()){
            y.set("categories."+c.id()+".display",c.display());
            y.set("categories."+c.id()+".icon",c.icon().name());
        }
        int n=0;
        for(CustomSubcategory s:subcategories.values()){
            String key="sub"+(n++);
            y.set("subcategories."+key+".category",s.categoryId());
            y.set("subcategories."+key+".id",s.id());
            y.set("subcategories."+key+".display",s.display());
            y.set("subcategories."+key+".icon",s.icon().name());
        }
        for(var e:assignments.entrySet()){
            y.set("assignments."+e.getKey().name()+".category",e.getValue());
            String sub=subAssignments.get(e.getKey());
            if(sub!=null) y.set("assignments."+e.getKey().name()+".subcategory",sub);
        }
        try{y.save(file);}catch(IOException e){plugin.getLogger().severe("Could not save custom-categories.yml: "+e.getMessage());}
    }

    public Collection<CustomCategory> all(){return Collections.unmodifiableCollection(categories.values());}
    public CustomCategory get(String id){return categories.get(normalize(id));}
    public String assigned(Material material){return assignments.get(material);}
    public String assignedSubcategory(Material material){return subAssignments.get(material);}

    public boolean create(String id,String display,Material icon){
        String key=normalize(id);
        if(key.isBlank()||categories.containsKey(key))return false;
        categories.put(key,new CustomCategory(key,display==null||display.isBlank()?id:display,icon==null?Material.CHEST:icon));
        save(); return true;
    }

    public boolean createSubcategory(String categoryId,String id,String display,Material icon){
        String cat=normalize(categoryId), key=normalize(id);
        if(!categories.containsKey(cat)||key.isBlank()||subcategories.containsKey(subKey(cat,key)))return false;
        subcategories.put(subKey(cat,key),new CustomSubcategory(key,cat,display==null||display.isBlank()?id:display,icon==null?Material.CHEST:icon));
        save(); return true;
    }

    public List<CustomSubcategory> subcategories(String categoryId){
        String cat=normalize(categoryId);
        return subcategories.values().stream().filter(s->s.categoryId().equals(cat)).toList();
    }

    public CustomSubcategory subcategory(String categoryId,String id){
        return subcategories.get(subKey(normalize(categoryId),normalize(id)));
    }

    public boolean delete(String id){
        String key=normalize(id);
        if(categories.remove(key)==null)return false;
        subcategories.entrySet().removeIf(e->e.getValue().categoryId().equals(key));
        for(Material m:new ArrayList<>(assignments.keySet())) if(assignments.get(m).equals(key)){assignments.remove(m);subAssignments.remove(m);}
        save(); return true;
    }

    public boolean deleteSubcategory(String categoryId,String id){
        String cat=normalize(categoryId), sub=normalize(id);
        if(subcategories.remove(subKey(cat,sub))==null)return false;
        subAssignments.entrySet().removeIf(e->sub.equals(e.getValue()) && cat.equals(assignments.get(e.getKey())));
        save(); return true;
    }

    public boolean assign(Material material,String id){
        String key=normalize(id);
        if(!categories.containsKey(key))return false;
        assignments.put(material,key); subAssignments.remove(material); save(); return true;
    }

    public boolean assign(Material material,String categoryId,String subcategoryId){
        String cat=normalize(categoryId), sub=normalize(subcategoryId);
        if(!categories.containsKey(cat) || !subcategories.containsKey(subKey(cat,sub)))return false;
        assignments.put(material,cat); subAssignments.put(material,sub); save(); return true;
    }

    public void clearAssignment(Material material){assignments.remove(material);subAssignments.remove(material);save();}

    public List<Material> materials(String id){
        String key=normalize(id);
        return assignments.entrySet().stream().filter(e->e.getValue().equals(key)).map(Map.Entry::getKey).sorted(Comparator.comparing(Material::name)).toList();
    }

    public List<Material> materials(String categoryId,String subcategoryId){
        String cat=normalize(categoryId), sub=normalize(subcategoryId);
        return assignments.entrySet().stream()
                .filter(e->e.getValue().equals(cat) && sub.equals(subAssignments.get(e.getKey())))
                .map(Map.Entry::getKey).sorted(Comparator.comparing(Material::name)).toList();
    }

    public static String normalize(String s){
        return s==null?"":s.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]","_");
    }
    private static String subKey(String categoryId,String id){return categoryId+"::"+id;}
}

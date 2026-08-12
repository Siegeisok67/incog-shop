package com.snipeyfresh.incogshop.xp;

import com.snipeyfresh.incogshop.IncogShopPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class XpVaultManager {
    private final IncogShopPlugin plugin;
    private final File file;
    private final Map<UUID, Long> stored = new HashMap<>();

    public XpVaultManager(IncogShopPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "xp-vault.yml");
    }

    public void load() {
        stored.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        var sec = y.getConfigurationSection("players");
        if (sec == null) return;
        for (String raw : sec.getKeys(false)) {
            try {
                UUID id = UUID.fromString(raw);
                long xp = Math.max(0L, sec.getLong(raw, 0L));
                if (xp > 0) stored.put(id, xp);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (var e : stored.entrySet()) y.set("players." + e.getKey(), e.getValue());
        try { y.save(file); }
        catch (IOException ex) { plugin.getLogger().severe("Could not save xp-vault.yml: " + ex.getMessage()); }
    }

    public long stored(UUID player) { return stored.getOrDefault(player, 0L); }

    public long current(Player player) {
        return totalExperience(player);
    }

    public long deposit(Player player, long requested) {
        long current = totalExperience(player);
        long amount = Math.max(0L, Math.min(current, requested));
        if (amount <= 0) return 0;
        long next = Math.addExact(stored(player.getUniqueId()), amount);
        setTotalExperience(player, current - amount);
        stored.put(player.getUniqueId(), next);
        save();
        return amount;
    }

    public long withdraw(Player player, long requested) {
        long saved = stored(player.getUniqueId());
        long amount = Math.max(0L, Math.min(saved, requested));
        if (amount <= 0) return 0;
        long current = totalExperience(player);
        long target = Math.addExact(current, amount);
        setTotalExperience(player, target);
        long remain = saved - amount;
        if (remain <= 0) stored.remove(player.getUniqueId()); else stored.put(player.getUniqueId(), remain);
        save();
        return amount;
    }

    public static long totalExperience(Player player) {
        int level = Math.max(0, player.getLevel());
        long base = xpAtLevel(level);
        int next = xpToNext(level);
        long progress = Math.round(Math.max(0f, Math.min(1f, player.getExp())) * next);
        return Math.max(0L, base + progress);
    }

    public static void setTotalExperience(Player player, long total) {
        long xp = Math.max(0L, total);

        // Binary-search the player's level from total XP.
        int low = 0;
        int high = 1;
        while (xpAtLevel(high) <= xp && high < 2_000_000) high *= 2;
        while (low + 1 < high) {
            int mid = low + (high - low) / 2;
            if (xpAtLevel(mid) <= xp) low = mid;
            else high = mid;
        }

        int level = low;
        long into = xp - xpAtLevel(level);
        int needed = xpToNext(level);
        float progress = needed <= 0 ? 0f : (float) into / (float) needed;

        player.setLevel(level);
        player.setExp(Math.max(0f, Math.min(0.999999f, progress)));
        player.setTotalExperience((int)Math.min(Integer.MAX_VALUE, xp));
    }

    public static long xpAtLevel(int level) {
        if (level <= 0) return 0;
        if (level <= 16) return (long) level * level + 6L * level;
        if (level <= 31) {
            return Math.round(2.5 * level * level - 40.5 * level + 360);
        }
        return Math.round(4.5 * level * level - 162.5 * level + 2220);
    }

    public static int xpToNext(int level) {
        if (level <= 15) return 2 * level + 7;
        if (level <= 30) return 5 * level - 38;
        return 9 * level - 158;
    }
}

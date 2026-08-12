package com.snipeyfresh.incogshop.history;

import com.snipeyfresh.incogshop.IncogShopPlugin;
import org.bukkit.Material;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;

public final class PriceHistoryManager {
    public record PricePoint(long timestamp, double buyPrice, double sellPrice, long stock) {}

    private final IncogShopPlugin plugin;
    private final File file;
    private final Map<Material, NavigableMap<Long, PricePoint>> history = new EnumMap<>(Material.class);
    private final Map<Material, PricePoint> lastWritten = new EnumMap<>(Material.class);

    public PriceHistoryManager(IncogShopPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "price-history.tsv");
    }

    public void load() {
        history.clear();
        lastWritten.clear();
        if (!file.exists()) return;

        long cutoff = System.currentTimeMillis() - retentionMillis();
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] p = line.split("\t");
                if (p.length < 5) continue;
                try {
                    long time = Long.parseLong(p[0]);
                    if (time < cutoff) continue;
                    Material material = Material.matchMaterial(p[1]);
                    if (material == null) continue;
                    PricePoint point = new PricePoint(
                            time,
                            Double.parseDouble(p[2]),
                            Double.parseDouble(p[3]),
                            Long.parseLong(p[4])
                    );
                    history.computeIfAbsent(material, k -> new TreeMap<>()).put(time, point);
                    PricePoint old = lastWritten.get(material);
                    if (old == null || point.timestamp() > old.timestamp()) lastWritten.put(material, point);
                } catch (RuntimeException ignored) {}
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not load price-history.tsv: " + ex.getMessage());
        }
    }

    public void captureChangedPrices() {
        long now = System.currentTimeMillis();
        for (Material material : plugin.market().tradableMaterials()) {
            captureIfChanged(material, now);
        }
    }

    public synchronized void captureIfChanged(Material material) {
        captureIfChanged(material, System.currentTimeMillis());
    }

    private void captureIfChanged(Material material, long now) {
        if (!plugin.market().isTradable(material)) return;

        double buy = plugin.market().buyUnitPrice(material);
        double sell = plugin.market().sellUnitPrice(material);
        long stock = plugin.market().entry(material) == null ? 0 : plugin.market().entry(material).stock();
        PricePoint previous = lastWritten.get(material);

        // Price history is primarily about price. Stock-only changes do not need a point
        // unless there is no previous point yet.
        if (previous != null
                && Math.abs(previous.buyPrice() - buy) < 0.005
                && Math.abs(previous.sellPrice() - sell) < 0.005) {
            return;
        }

        PricePoint point = new PricePoint(now, buy, sell, stock);
        history.computeIfAbsent(material, k -> new TreeMap<>()).put(now, point);
        lastWritten.put(material, point);
        append(material, point);
    }

    private void append(Material material, PricePoint point) {
        try {
            plugin.getDataFolder().mkdirs();
            try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(file, true), StandardCharsets.UTF_8))) {
                out.write(point.timestamp() + "\t" + material.name() + "\t"
                        + point.buyPrice() + "\t" + point.sellPrice() + "\t" + point.stock());
                out.newLine();
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not append price history: " + ex.getMessage());
        }
    }

    public PricePoint atOrBefore(Material material, long timestamp) {
        NavigableMap<Long, PricePoint> map = history.get(material);
        if (map == null || map.isEmpty()) return null;
        Map.Entry<Long, PricePoint> entry = map.floorEntry(timestamp);
        return entry == null ? null : entry.getValue();
    }

    public PricePoint oldest(Material material) {
        NavigableMap<Long, PricePoint> map = history.get(material);
        return map == null || map.isEmpty() ? null : map.firstEntry().getValue();
    }

    public boolean hasHistory(Material material) {
        NavigableMap<Long, PricePoint> map = history.get(material);
        return map != null && !map.isEmpty();
    }

    public synchronized void pruneAndRewrite() {
        long cutoff = System.currentTimeMillis() - retentionMillis();
        for (Iterator<Map.Entry<Material, NavigableMap<Long, PricePoint>>> it = history.entrySet().iterator(); it.hasNext();) {
            Map.Entry<Material, NavigableMap<Long, PricePoint>> e = it.next();
            e.getValue().headMap(cutoff, false).clear();
            if (e.getValue().isEmpty()) it.remove();
        }

        File temp = new File(file.getParentFile(), file.getName() + ".tmp");
        try (BufferedWriter out = Files.newBufferedWriter(temp.toPath(), StandardCharsets.UTF_8)) {
            out.write("# timestamp<TAB>material<TAB>buy<TAB>sell<TAB>stock");
            out.newLine();
            for (Map.Entry<Material, NavigableMap<Long, PricePoint>> e : history.entrySet()) {
                for (PricePoint point : e.getValue().values()) {
                    out.write(point.timestamp() + "\t" + e.getKey().name() + "\t"
                            + point.buyPrice() + "\t" + point.sellPrice() + "\t" + point.stock());
                    out.newLine();
                }
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not rewrite price history: " + ex.getMessage());
            return;
        }

        try {
            Files.move(temp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not replace price history file: " + ex.getMessage());
        }
    }

    public long parseWindowMillis(String input) {
        if (input == null) return -1;
        String s = input.trim().toLowerCase(Locale.ROOT);
        try {
            if (s.endsWith("m")) return Duration.ofMinutes(Long.parseLong(s.substring(0, s.length() - 1))).toMillis();
            if (s.endsWith("h")) return Duration.ofHours(Long.parseLong(s.substring(0, s.length() - 1))).toMillis();
            if (s.endsWith("d")) return Duration.ofDays(Long.parseLong(s.substring(0, s.length() - 1))).toMillis();
        } catch (NumberFormatException ignored) {}
        return -1;
    }

    private long retentionMillis() {
        long days = Math.max(1, plugin.getConfig().getLong("discord-price-check.history-retention-days", 30));
        return Duration.ofDays(days).toMillis();
    }
}

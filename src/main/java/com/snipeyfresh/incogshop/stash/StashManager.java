package com.snipeyfresh.incogshop.stash;

import com.snipeyfresh.incogshop.IncogShopPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class StashManager {
    public record SellResult(int soldItems, double payout) {}
    private final IncogShopPlugin plugin;
    private final File file;
    private final Map<UUID, List<ItemStack>> items = new HashMap<>();

    public StashManager(IncogShopPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stash.yml");
    }

    public void load() {
        items.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        var sec = y.getConfigurationSection("players");
        if (sec == null) return;
        for (String raw : sec.getKeys(false)) {
            try {
                UUID id = UUID.fromString(raw);
                List<ItemStack> list = new ArrayList<>();
                for (Object o : y.getList("players."+raw, List.of())) {
                    if (o instanceof ItemStack i && !i.getType().isAir()) mergeInto(list, i.clone());
                }
                if (!list.isEmpty()) items.put(id,list);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (var e : items.entrySet()) y.set("players."+e.getKey(), e.getValue());
        try { y.save(file); } catch (IOException ex) { plugin.getLogger().severe("Could not save stash.yml: "+ex.getMessage()); }
    }

    public List<ItemStack> get(UUID id) {
        return items.getOrDefault(id,List.of()).stream().map(ItemStack::clone).toList();
    }

    public int count(UUID id) { return items.getOrDefault(id,List.of()).size(); }

    public void add(UUID id, ItemStack stack) {
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) return;
        List<ItemStack> list = items.computeIfAbsent(id, x -> new ArrayList<>());
        mergeInto(list, stack.clone());
        save();
    }

    public void addAll(UUID id, Collection<ItemStack> stacks) {
        List<ItemStack> list = items.computeIfAbsent(id, x -> new ArrayList<>());
        for (ItemStack stack : stacks) {
            if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) continue;
            mergeInto(list, stack.clone());
        }
        cleanup(id);
        save();
    }

    private static void mergeInto(List<ItemStack> list, ItemStack incoming) {
        if (incoming == null || incoming.getType().isAir() || incoming.getAmount() <= 0) return;

        int remaining = incoming.getAmount();
        int max = Math.max(1, incoming.getMaxStackSize());

        // First fill existing compatible stacks.
        for (ItemStack existing : list) {
            if (remaining <= 0) break;
            if (existing == null || existing.getType().isAir()) continue;
            if (!existing.isSimilar(incoming)) continue;

            int existingMax = Math.max(1, existing.getMaxStackSize());
            int limit = Math.min(max, existingMax);
            int room = Math.max(0, limit - existing.getAmount());
            if (room <= 0) continue;

            int moved = Math.min(room, remaining);
            existing.setAmount(existing.getAmount() + moved);
            remaining -= moved;
        }

        // Then create normal-sized stacks for anything left over.
        while (remaining > 0) {
            int amount = Math.min(max, remaining);
            ItemStack created = incoming.clone();
            created.setAmount(amount);
            list.add(created);
            remaining -= amount;
        }
    }

    public int deliverOrStash(Player player, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return 0;
        Map<Integer,ItemStack> left = player.getInventory().addItem(stack.clone());
        if (left.isEmpty()) return 0;
        addAll(player.getUniqueId(), left.values());
        return left.values().stream().mapToInt(ItemStack::getAmount).sum();
    }

    public boolean claimIndex(Player player, int index) {
        List<ItemStack> list = items.get(player.getUniqueId());
        if (list == null || index < 0 || index >= list.size()) return false;
        ItemStack stack = list.get(index).clone();
        Map<Integer,ItemStack> left = player.getInventory().addItem(stack);
        if (left.isEmpty()) list.remove(index);
        else {
            ItemStack remain = left.values().iterator().next();
            list.set(index, remain.clone());
        }
        compact(player.getUniqueId());
        save();
        return true;
    }

    public int claimAll(Player player) {
        List<ItemStack> list = items.get(player.getUniqueId());
        if (list == null || list.isEmpty()) return 0;
        int moved = 0;
        List<ItemStack> remaining = new ArrayList<>();
        for (ItemStack stack : list) {
            int before = stack.getAmount();
            Map<Integer,ItemStack> left = player.getInventory().addItem(stack.clone());
            int leftAmount = left.values().stream().mapToInt(ItemStack::getAmount).sum();
            moved += before-leftAmount;
            remaining.addAll(left.values().stream().map(ItemStack::clone).toList());
        }
        if (remaining.isEmpty()) items.remove(player.getUniqueId()); else items.put(player.getUniqueId(),remaining);
        compact(player.getUniqueId());
        save();
        return moved;
    }

    public SellResult sellAllEligible(Player player) {
        List<ItemStack> list = items.get(player.getUniqueId());
        if (list == null || list.isEmpty()) return new SellResult(0,0);
        List<ItemStack> keep = new ArrayList<>();
        int sold=0; double payout=0;
        for (ItemStack stack : list) {
            if (!plugin.market().isEligibleStack(stack)) { keep.add(stack); continue; }
            var result = plugin.market().sellEscrowStack(player, stack.clone());
            if (!result.success()) { keep.add(stack); continue; }
            sold += result.amount(); payout += result.total();
            int rem = stack.getAmount()-result.amount();
            if (rem>0) { ItemStack left=stack.clone(); left.setAmount(rem); keep.add(left); }
        }
        if (keep.isEmpty()) items.remove(player.getUniqueId()); else items.put(player.getUniqueId(),keep);
        compact(player.getUniqueId());
        save();
        return new SellResult(sold,payout);
    }

    private void compact(UUID id) {
        List<ItemStack> list = items.get(id);
        if (list == null || list.isEmpty()) {
            items.remove(id);
            return;
        }
        List<ItemStack> compacted = new ArrayList<>();
        for (ItemStack stack : list) {
            if (stack != null && !stack.getType().isAir() && stack.getAmount() > 0) {
                mergeInto(compacted, stack.clone());
            }
        }
        if (compacted.isEmpty()) items.remove(id);
        else items.put(id, compacted);
    }

    private void cleanup(UUID id) {
        List<ItemStack> list=items.get(id);
        if (list!=null && list.isEmpty()) items.remove(id);
    }
}

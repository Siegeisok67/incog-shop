package com.snipeyfresh.incogshop.listener;

import com.snipeyfresh.incogshop.IncogShopPlugin;
import com.snipeyfresh.incogshop.custom.CustomCategoryManager;
import com.snipeyfresh.incogshop.gui.AdminSetupGui;
import com.snipeyfresh.incogshop.gui.MarketCategory;
import com.snipeyfresh.incogshop.market.MarketManager;
import com.snipeyfresh.incogshop.market.MarketMode;
import io.papermc.paper.event.packet.UncheckedSignChangeEvent;
import io.papermc.paper.math.BlockPosition;
import io.papermc.paper.math.Position;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AdminSetupListener implements Listener {
    private static final int[] GRID_28 = {
            10,11,12,13,14,15,16,
            19,20,21,22,23,24,25,
            28,29,30,31,32,33,34,
            37,38,39,40,41,42,43
    };

    private enum InputType { CATEGORY, SUBCATEGORY }
    private record Pending(InputType type, String categoryId, Material icon, Location location, BlockPosition position) {}

    private final IncogShopPlugin plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public AdminSetupListener(IncogShopPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (event.getView().getTopInventory().getHolder() instanceof AdminSetupGui.Holder holder) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;

            if (holder.screen().equals("menu")) {
                if (slot == 11) { plugin.adminSetupGui().openCategories(player, 0); return; }
                if (slot == 13) { plugin.adminSetupGui().openItems(player, 0); return; }
                if (slot == 15) { addHeld(player, true); return; }
                if (slot == 29) { plugin.layoutEditor().open(player, "menu", null); return; }
                if (slot == 31) { plugin.gui().openCategories(player, true); return; }
                if (slot == 33) {
                    if (!player.hasPermission("incogshop.admin.stock")) { noPerm(player); return; }
                    boolean next = !plugin.market().isInfiniteStockEnabled();
                    plugin.market().setInfiniteStockEnabled(next, player.getName());
                    player.sendMessage(plugin.prefix() + (next ? "§aInfinite stock enabled." : "§eInfinite stock disabled."));
                    plugin.adminSetupGui().open(player);
                    return;
                }
                if (slot == 40) player.closeInventory();
                return;
            }

            if (holder.screen().equals("categories")) {
                List<CustomCategoryManager.CustomCategory> categories = new ArrayList<>(plugin.customCategories().all());
                int logical = gridIndex(slot);
                int index = logical < 0 ? -1 : holder.page() * GRID_28.length + logical;
                if (index >= 0 && index < categories.size()) {
                    var category = categories.get(index);
                    if (event.isRightClick()) {
                        plugin.adminSetupGui().openDeleteCategory(player, category.id(), holder.page());
                    } else if (event.isShiftClick()) {
                        Material held = heldMaterial(player);
                        if (held == null) { player.sendMessage(plugin.prefix() + "§cHold an item to assign."); return; }
                        if (!ensureMarketItem(player, held)) return;
                        plugin.customCategories().assign(held, category.id());
                        player.sendMessage(plugin.prefix() + "§aMoved §f" + held.name() + " §ato §f" + category.display() + "§a.");
                        plugin.adminSetupGui().openCategories(player, holder.page());
                    } else {
                        plugin.adminSetupGui().openSubcategories(player, category.id(), 0);
                    }
                    return;
                }
                if (slot == 45) { plugin.adminSetupGui().open(player); return; }
                if (slot == 47 && holder.page() > 0) { plugin.adminSetupGui().openCategories(player, holder.page() - 1); return; }
                if (slot == 49) { beginInput(player, InputType.CATEGORY, ""); return; }
                if (slot == 53) { plugin.adminSetupGui().openCategories(player, holder.page() + 1); return; }
                return;
            }

            if (holder.screen().equals("subcategories")) {
                List<CustomCategoryManager.CustomSubcategory> subs = plugin.customCategories().subcategories(holder.categoryId());
                int logical = gridIndex(slot);
                int index = logical < 0 ? -1 : holder.page() * GRID_28.length + logical;
                if (index >= 0 && index < subs.size()) {
                    var sub = subs.get(index);
                    if (event.isRightClick()) {
                        plugin.adminSetupGui().openDeleteSubcategory(player, holder.categoryId(), sub.id(), holder.page());
                    } else {
                        Material held = heldMaterial(player);
                        if (held == null) { player.sendMessage(plugin.prefix() + "§cHold an item to assign to this section."); return; }
                        if (!ensureMarketItem(player, held)) return;
                        plugin.customCategories().assign(held, holder.categoryId(), sub.id());
                        player.sendMessage(plugin.prefix() + "§aMoved §f" + held.name() + " §ato §f" + sub.display() + "§a.");
                        plugin.adminSetupGui().openSubcategories(player, holder.categoryId(), holder.page());
                    }
                    return;
                }
                if (slot == 45) { plugin.adminSetupGui().openCategories(player, 0); return; }
                if (slot == 47 && holder.page() > 0) { plugin.adminSetupGui().openSubcategories(player, holder.categoryId(), holder.page() - 1); return; }
                if (slot == 49) { beginInput(player, InputType.SUBCATEGORY, holder.categoryId()); return; }
                if (slot == 53) { plugin.adminSetupGui().openSubcategories(player, holder.categoryId(), holder.page() + 1); return; }
                return;
            }

            if (holder.screen().equals("items")) {
                List<Material> materials = plugin.market().tradableMaterials();
                int logical = gridIndex(slot);
                int index = logical < 0 ? -1 : holder.page() * GRID_28.length + logical;
                if (index >= 0 && index < materials.size()) {
                    Material material = materials.get(index);
                    if (event.isRightClick()) {
                        MarketMode next = plugin.market().marketMode(material).next();
                        plugin.market().setMarketMode(material, next, player.getName());
                        player.sendMessage(plugin.prefix() + "§a" + material.name() + " mode: §f" + next.name().replace('_', ' '));
                        plugin.adminSetupGui().openItems(player, holder.page());
                    } else {
                        plugin.adminSetupGui().openMoveCategories(player, material);
                    }
                    return;
                }
                if (slot == 45) { plugin.adminSetupGui().open(player); return; }
                if (slot == 47 && holder.page() > 0) { plugin.adminSetupGui().openItems(player, holder.page() - 1); return; }
                if (slot == 49) { addHeld(player, false); return; }
                if (slot == 53) { plugin.adminSetupGui().openItems(player, holder.page() + 1); return; }
                return;
            }
            return;
        }

        if (event.getView().getTopInventory().getHolder() instanceof AdminSetupGui.MoveCategoryHolder holder) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot < 0 || slot >= 54) return;
            Material material = holder.material();
            if (slot == 45) { plugin.adminSetupGui().openItems(player, 0); return; }
            if (slot == 49) {
                plugin.customCategories().clearAssignment(material);
                plugin.market().clearClassificationOverride(material, player.getName());
                player.sendMessage(plugin.prefix() + "§aReset §f" + material.name() + " §ato automatic placement.");
                plugin.adminSetupGui().openItems(player, 0);
                return;
            }

            List<MarketCategory> built = java.util.Arrays.stream(MarketCategory.values()).filter(c -> c != MarketCategory.ALL).toList();
            int[] builtSlots = com.snipeyfresh.incogshop.gui.ShopGui.centeredSlots(built.size());
            for (int i = 0; i < built.size() && i < builtSlots.length; i++) {
                if (slot == builtSlots[i]) {
                    plugin.customCategories().clearAssignment(material);
                    plugin.gui().openPlacementSubcategories(player, material, built.get(i));
                    return;
                }
            }

            List<CustomCategoryManager.CustomCategory> custom = new ArrayList<>(plugin.customCategories().all());
            int[] customSlots = com.snipeyfresh.incogshop.gui.ShopGui.centeredSlots(custom.size());
            for (int i = 0; i < custom.size() && i < customSlots.length; i++) {
                int shifted = customSlots[i] + 18;
                if (shifted >= 45) break;
                if (slot == shifted) {
                    var category = custom.get(i);
                    if (plugin.customCategories().subcategories(category.id()).isEmpty()) {
                        plugin.customCategories().assign(material, category.id());
                        player.sendMessage(plugin.prefix() + "§aMoved §f" + material.name() + " §ato §f" + category.display() + "§a.");
                        plugin.adminSetupGui().openItems(player, 0);
                    } else {
                        plugin.adminSetupGui().openMoveCustomSubcategories(player, material, category.id());
                    }
                    return;
                }
            }
            return;
        }

        if (event.getView().getTopInventory().getHolder() instanceof AdminSetupGui.MoveCustomSubHolder holder) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot < 0 || slot >= 54) return;
            if (slot == 45) { plugin.adminSetupGui().openMoveCategories(player, holder.material()); return; }
            if (slot == 39) {
                plugin.customCategories().assign(holder.material(), holder.categoryId());
                player.sendMessage(plugin.prefix() + "§aMoved §f" + holder.material().name() + " §ato the category root.");
                plugin.adminSetupGui().openItems(player, 0);
                return;
            }
            List<CustomCategoryManager.CustomSubcategory> subs = plugin.customCategories().subcategories(holder.categoryId());
            int[] slots = com.snipeyfresh.incogshop.gui.ShopGui.centeredSlots(subs.size());
            for (int i = 0; i < subs.size() && i < slots.length; i++) {
                if (slot == slots[i]) {
                    var sub = subs.get(i);
                    plugin.customCategories().assign(holder.material(), holder.categoryId(), sub.id());
                    player.sendMessage(plugin.prefix() + "§aMoved §f" + holder.material().name() + " §ato §f" + sub.display() + "§a.");
                    plugin.adminSetupGui().openItems(player, 0);
                    return;
                }
            }
            return;
        }

        if (event.getView().getTopInventory().getHolder() instanceof AdminSetupGui.ConfirmDeleteHolder holder) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot == 11) {
                if (holder.type().equals("category")) plugin.adminSetupGui().openCategories(player, holder.returnPage());
                else plugin.adminSetupGui().openSubcategories(player, holder.categoryId(), holder.returnPage());
                return;
            }
            if (slot == 15) {
                if (holder.type().equals("category")) {
                    var cat = plugin.customCategories().get(holder.categoryId());
                    String name = cat == null ? holder.categoryId() : cat.display();
                    boolean ok = plugin.customCategories().delete(holder.categoryId());
                    player.sendMessage(plugin.prefix() + (ok ? "§cRemoved custom category §f" + name + "§c. Items returned to automatic placement." : "§cCategory no longer exists."));
                    plugin.adminSetupGui().openCategories(player, holder.returnPage());
                } else {
                    var sub = plugin.customCategories().subcategory(holder.categoryId(), holder.subcategoryId());
                    String name = sub == null ? holder.subcategoryId() : sub.display();
                    boolean ok = plugin.customCategories().deleteSubcategory(holder.categoryId(), holder.subcategoryId());
                    player.sendMessage(plugin.prefix() + (ok ? "§cRemoved subcategory §f" + name + "§c. Items remain in the parent category." : "§cSubcategory no longer exists."));
                    plugin.adminSetupGui().openSubcategories(player, holder.categoryId(), holder.returnPage());
                }
            }
        }
    }

    private void addHeld(Player player, boolean returnDashboard) {
        Material held = heldMaterial(player);
        if (held == null) { player.sendMessage(plugin.prefix() + "§cHold the vanilla item you want to add to the market."); return; }
        if (!ensureMarketItem(player, held)) return;
        player.sendMessage(plugin.prefix() + "§aAdded/enabled §f" + held.name() + " §awith its default base price and Buy & Sell mode.");
        plugin.adminSetupGui().openMoveCategories(player, held);
    }

    private boolean ensureMarketItem(Player player, Material material) {
        if (plugin.market().entry(material) != null) return true;
        boolean ok = plugin.market().addOrEnableMaterial(material, MarketManager.defaultBasePrice(material), MarketMode.BUY_SELL, player.getName());
        if (!ok) player.sendMessage(plugin.prefix() + "§cThat material cannot be added to the market.");
        return ok;
    }

    private Material heldMaterial(Player player) {
        Material material = player.getInventory().getItemInMainHand().getType();
        return material.isAir() ? null : material;
    }

    private int gridIndex(int slot) {
        for (int i = 0; i < GRID_28.length; i++) if (GRID_28[i] == slot) return i;
        return -1;
    }

    private void beginInput(Player player, InputType type, String categoryId) {
        Material icon = heldMaterial(player);
        if (icon == null) icon = Material.CHEST;
        player.closeInventory();
        Location location = player.getLocation().getBlock().getLocation().add(0, 2, 0);
        BlockPosition position = Position.block(location);
        pending.put(player.getUniqueId(), new Pending(type, categoryId, icon, location, position));
        player.sendBlockChange(location, Material.OAK_SIGN.createBlockData());
        player.sendSignChange(location, new String[]{type == InputType.CATEGORY ? "Category name" : "Subcategory name", "", "", ""});
        player.openVirtualSign(position, Side.FRONT);
        player.sendMessage(plugin.prefix() + "§dType the new " + (type == InputType.CATEGORY ? "category" : "subcategory") + " name on the sign.");
    }

    @EventHandler
    public void sign(UncheckedSignChangeEvent event) {
        Player player = event.getPlayer();
        Pending p = pending.get(player.getUniqueId());
        if (p == null) return;
        BlockPosition edited = event.getEditedBlockPosition();
        if (edited.blockX() != p.position().blockX() || edited.blockY() != p.position().blockY() || edited.blockZ() != p.position().blockZ()) return;

        pending.remove(player.getUniqueId());
        event.setCancelled(true);
        player.sendBlockChange(p.location(), p.location().getBlock().getBlockData());

        StringBuilder text = new StringBuilder();
        for (var line : event.lines()) {
            String value = PlainTextComponentSerializer.plainText().serialize(line).trim();
            if (!value.isBlank()) { if (!text.isEmpty()) text.append(' '); text.append(value); }
        }
        String display = text.toString().trim();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (display.isBlank() || display.equalsIgnoreCase("cancel")) {
                player.sendMessage(plugin.prefix() + "§eCreation cancelled.");
                if (p.type() == InputType.CATEGORY) plugin.adminSetupGui().openCategories(player, 0);
                else plugin.adminSetupGui().openSubcategories(player, p.categoryId(), 0);
                return;
            }
            String id = CustomCategoryManager.normalize(display);
            boolean ok = p.type() == InputType.CATEGORY
                    ? plugin.customCategories().create(id, display, p.icon())
                    : plugin.customCategories().createSubcategory(p.categoryId(), id, display, p.icon());
            player.sendMessage(plugin.prefix() + (ok ? "§aCreated §f" + display + "§a." : "§cA section with that name already exists."));
            if (p.type() == InputType.CATEGORY) plugin.adminSetupGui().openCategories(player, 0);
            else plugin.adminSetupGui().openSubcategories(player, p.categoryId(), 0);
        });
    }

    private void noPerm(Player player) {
        player.sendMessage(plugin.prefix() + "§cYou do not have permission.");
    }
}

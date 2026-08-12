package com.snipeyfresh.incogshop.gui;

import org.bukkit.Material;

public enum MarketCategory {
    ALL("All Items", Material.NETHER_STAR),
    BLOCKS("Blocks", Material.BRICKS),
    BUILDING("Building", Material.OAK_PLANKS),
    ORES("Ores & Minerals", Material.DIAMOND),
    FARMING("Farming & Food", Material.WHEAT),
    MOB_DROPS("Mob Drops", Material.BONE),
    REDSTONE("Redstone", Material.REDSTONE),
    TOOLS("Tools & Combat", Material.DIAMOND_PICKAXE),
    DECORATION("Decoration", Material.PAINTING),
    NETHER_END("Nether & End", Material.ENDER_PEARL),
    MISC("Miscellaneous", Material.CHEST);

    private final String display;
    private final Material icon;
    MarketCategory(String display, Material icon) { this.display = display; this.icon = icon; }
    public String display() { return display; }
    public Material icon() { return icon; }

    public boolean matches(Material m) {
        if (this == ALL) return true;
        String n = m.name();
        return switch (this) {
            case BLOCKS -> m.isBlock() && !BUILDING.matches(m) && !ORES.matches(m) && !REDSTONE.matches(m) && !DECORATION.matches(m) && !NETHER_END.matches(m);
            case BUILDING -> n.endsWith("_PLANKS") || n.endsWith("_LOG") || n.endsWith("_WOOD") || n.endsWith("_STEM") || n.endsWith("_HYPHAE") || n.endsWith("_SLAB") || n.endsWith("_STAIRS") || n.endsWith("_WALL") || n.endsWith("_FENCE") || n.endsWith("_FENCE_GATE") || n.endsWith("_DOOR") || n.endsWith("_TRAPDOOR") || n.endsWith("_BRICKS") || n.equals("BRICKS") || n.contains("STONE_BRICKS") || n.endsWith("_GLASS") || n.endsWith("_GLASS_PANE");
            case ORES -> n.endsWith("_ORE") || n.startsWith("RAW_") || n.endsWith("_INGOT") || n.equals("COAL") || n.equals("CHARCOAL") || n.equals("DIAMOND") || n.equals("EMERALD") || n.equals("REDSTONE") || n.equals("LAPIS_LAZULI") || n.equals("QUARTZ") || n.equals("AMETHYST_SHARD") || n.endsWith("_BLOCK") && (n.contains("IRON") || n.contains("GOLD") || n.contains("COPPER") || n.contains("DIAMOND") || n.contains("EMERALD") || n.contains("COAL") || n.contains("REDSTONE") || n.contains("LAPIS"));
            case FARMING -> n.contains("WHEAT") || n.contains("CARROT") || n.contains("POTATO") || n.contains("BEETROOT") || n.contains("MELON") || n.contains("PUMPKIN") || n.contains("APPLE") || n.contains("BREAD") || n.contains("BEEF") || n.contains("PORK") || n.contains("CHICKEN") || n.contains("MUTTON") || n.contains("RABBIT") || n.contains("COD") || n.contains("SALMON") || n.contains("COOKIE") || n.contains("CAKE") || n.contains("STEW") || n.contains("SOUP") || n.contains("SEEDS") || n.equals("SUGAR_CANE") || n.equals("SUGAR") || n.equals("EGG") || (n.contains("HONEY") && !n.endsWith("_BLOCK")) || n.equals("CACTUS") || n.equals("BAMBOO") || n.contains("BERRIES") || n.equals("KELP") || n.equals("DRIED_KELP");
            case MOB_DROPS -> (n.contains("BONE") && !n.equals("BONE_BLOCK")) || n.contains("ROTTEN_FLESH") || n.contains("GUNPOWDER") || n.contains("STRING") || n.contains("SPIDER_EYE") || n.contains("LEATHER") || n.contains("FEATHER") || n.contains("INK_SAC") || n.contains("SLIME") || n.contains("MAGMA_CREAM") || n.contains("BLAZE") || n.contains("GHAST_TEAR") || n.contains("PHANTOM_MEMBRANE") || n.contains("SHULKER_SHELL") || n.contains("PRISMARINE_SHARD") || n.contains("PRISMARINE_CRYSTALS") || n.contains("NAUTILUS") || n.contains("ENDER_PEARL") || n.endsWith("_HEAD") || n.endsWith("_SKULL");
            case REDSTONE -> n.contains("REDSTONE") || n.contains("REPEATER") || n.contains("COMPARATOR") || n.contains("PISTON") || n.contains("OBSERVER") || n.contains("HOPPER") || n.contains("DISPENSER") || n.contains("DROPPER") || n.contains("LEVER") || n.endsWith("_BUTTON") || n.endsWith("_PRESSURE_PLATE") || n.contains("TRIPWIRE") || n.contains("TARGET") || n.contains("DAYLIGHT_DETECTOR") || n.contains("RAIL") || n.contains("MINECART");
            case TOOLS -> n.endsWith("_SWORD") || n.endsWith("_PICKAXE") || n.endsWith("_AXE") || n.endsWith("_SHOVEL") || n.endsWith("_HOE") || n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS") || n.equals("BOW") || n.equals("CROSSBOW") || n.equals("TRIDENT") || n.equals("MACE") || n.equals("SHIELD") || n.equals("ELYTRA") || n.equals("FISHING_ROD") || n.equals("SHEARS") || n.equals("FLINT_AND_STEEL") || n.equals("BRUSH");
            case DECORATION -> n.endsWith("_WOOL") || n.endsWith("_CARPET") || n.endsWith("_BANNER") || n.endsWith("_CANDLE") || n.endsWith("_TERRACOTTA") || n.endsWith("_CONCRETE") || n.endsWith("_CONCRETE_POWDER") || n.contains("FLOWER") || n.contains("TULIP") || n.contains("CORAL") || n.endsWith("_LEAVES") || n.endsWith("_SAPLING") || n.equals("PAINTING") || n.equals("ITEM_FRAME") || n.equals("GLOW_ITEM_FRAME") || n.contains("POTTERY_SHERD") || n.contains("POTTERY_SHARD");
            case NETHER_END -> n.contains("NETHER") || n.contains("CRIMSON") || n.contains("WARPED") || n.contains("SOUL_") || n.contains("BASALT") || n.contains("BLACKSTONE") || n.contains("END_") || n.contains("ENDER") || n.contains("PURPUR") || n.contains("CHORUS") || n.contains("SHULKER") || n.equals("OBSIDIAN") || n.equals("CRYING_OBSIDIAN");
            case MISC -> true;
            default -> false;
        };
    }
}

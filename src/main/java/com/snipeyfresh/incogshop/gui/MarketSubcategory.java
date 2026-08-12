package com.snipeyfresh.incogshop.gui;

import org.bukkit.Material;

import java.util.Arrays;
import java.util.List;

public enum MarketSubcategory {
    NATURAL_BLOCKS(MarketCategory.BLOCKS, "Natural Blocks", Material.GRASS_BLOCK),
    STONE_BLOCKS(MarketCategory.BLOCKS, "Stone & Earth", Material.STONE),
    ICE_SNOW(MarketCategory.BLOCKS, "Ice & Snow", Material.ICE),
    OTHER_BLOCKS(MarketCategory.BLOCKS, "Other Blocks", Material.COBBLESTONE),

    WOOD(MarketCategory.BUILDING, "Wood", Material.OAK_LOG),
    MASONRY(MarketCategory.BUILDING, "Masonry", Material.STONE_BRICKS),
    GLASS(MarketCategory.BUILDING, "Glass", Material.GLASS),
    BUILDING_PARTS(MarketCategory.BUILDING, "Slabs, Stairs & More", Material.OAK_STAIRS),
    OTHER_BUILDING(MarketCategory.BUILDING, "Other Building", Material.BRICKS),

    ORES_RAW(MarketCategory.ORES, "Ores & Raw Materials", Material.IRON_ORE),
    INGOTS_GEMS(MarketCategory.ORES, "Ingots & Gems", Material.DIAMOND),
    MINERAL_BLOCKS(MarketCategory.ORES, "Storage Blocks", Material.IRON_BLOCK),
    OTHER_ORES(MarketCategory.ORES, "Other Minerals", Material.AMETHYST_SHARD),

    CROPS(MarketCategory.FARMING, "Crops & Seeds", Material.WHEAT),
    FOOD(MarketCategory.FARMING, "Food", Material.COOKED_BEEF),
    FARM_RESOURCES(MarketCategory.FARMING, "Farm Resources", Material.SUGAR_CANE),
    OTHER_FARMING(MarketCategory.FARMING, "Other Farming", Material.HAY_BLOCK),

    HOSTILE_DROPS(MarketCategory.MOB_DROPS, "Hostile Mob Drops", Material.ROTTEN_FLESH),
    PASSIVE_DROPS(MarketCategory.MOB_DROPS, "Passive Mob Drops", Material.LEATHER),
    AQUATIC_DROPS(MarketCategory.MOB_DROPS, "Aquatic Drops", Material.PRISMARINE_SHARD),
    RARE_DROPS(MarketCategory.MOB_DROPS, "Rare Drops", Material.GHAST_TEAR),
    OTHER_DROPS(MarketCategory.MOB_DROPS, "Other Mob Drops", Material.BONE),

    COMPONENTS(MarketCategory.REDSTONE, "Components", Material.REPEATER),
    MACHINES(MarketCategory.REDSTONE, "Machines", Material.PISTON),
    TRANSPORT(MarketCategory.REDSTONE, "Rails & Minecarts", Material.POWERED_RAIL),
    INPUTS(MarketCategory.REDSTONE, "Inputs & Sensors", Material.LEVER),
    OTHER_REDSTONE(MarketCategory.REDSTONE, "Other Redstone", Material.REDSTONE),

    TOOLS(MarketCategory.TOOLS, "Tools", Material.DIAMOND_PICKAXE),
    WEAPONS(MarketCategory.TOOLS, "Weapons", Material.DIAMOND_SWORD),
    ARMOR(MarketCategory.TOOLS, "Armor", Material.DIAMOND_CHESTPLATE),
    UTILITY_GEAR(MarketCategory.TOOLS, "Utility Gear", Material.SHIELD),
    OTHER_TOOLS(MarketCategory.TOOLS, "Other Combat", Material.BOW),

    COLORS(MarketCategory.DECORATION, "Colored Blocks", Material.RED_WOOL),
    PLANTS(MarketCategory.DECORATION, "Plants & Foliage", Material.POPPY),
    DISPLAY(MarketCategory.DECORATION, "Display & Art", Material.PAINTING),
    POTTERY(MarketCategory.DECORATION, "Pottery & Decor", Material.DECORATED_POT),
    OTHER_DECORATION(MarketCategory.DECORATION, "Other Decoration", Material.FLOWER_POT),

    NETHER(MarketCategory.NETHER_END, "Nether", Material.NETHERRACK),
    END(MarketCategory.NETHER_END, "The End", Material.END_STONE),
    DIMENSIONAL_LOOT(MarketCategory.NETHER_END, "Dimensional Loot", Material.ENDER_PEARL),
    OTHER_NETHER_END(MarketCategory.NETHER_END, "Other Nether & End", Material.OBSIDIAN),

    BOOKS(MarketCategory.MISC, "Books & Paper", Material.BOOK),
    TRANSPORT_MISC(MarketCategory.MISC, "Transport", Material.OAK_BOAT),
    UTILITY(MarketCategory.MISC, "Utility", Material.CHEST),
    SPECIAL(MarketCategory.MISC, "Special Items", Material.NETHER_STAR),
    OTHER_MISC(MarketCategory.MISC, "Other", Material.CHEST);

    private final MarketCategory parent;
    private final String display;
    private final Material icon;

    MarketSubcategory(MarketCategory parent, String display, Material icon) {
        this.parent = parent;
        this.display = display;
        this.icon = icon;
    }

    public MarketCategory parent() { return parent; }
    public String display() { return display; }
    public Material icon() { return icon; }

    public static List<MarketSubcategory> forCategory(MarketCategory category) {
        return Arrays.stream(values()).filter(s -> s.parent == category).toList();
    }

    public static MarketSubcategory defaultFor(Material m, MarketCategory category) {
        String n = m.name();
        return switch (category) {
            case BLOCKS -> {
                if (n.contains("ICE") || n.contains("SNOW")) yield ICE_SNOW;
                if (n.contains("STONE") || n.contains("DEEPSLATE") || n.contains("TUFF") || n.contains("DIRT") || n.contains("SAND") || n.contains("GRAVEL") || n.contains("MUD") || n.contains("CLAY")) yield STONE_BLOCKS;
                if (n.contains("GRASS") || n.contains("MOSS") || n.contains("NYLIUM") || n.contains("MYCELIUM")) yield NATURAL_BLOCKS;
                yield OTHER_BLOCKS;
            }
            case BUILDING -> {
                if (n.contains("LOG") || n.contains("WOOD") || n.contains("PLANK") || n.contains("STEM") || n.contains("HYPHAE") || n.contains("BAMBOO")) yield WOOD;
                if (n.contains("GLASS")) yield GLASS;
                if (n.endsWith("_SLAB") || n.endsWith("_STAIRS") || n.endsWith("_WALL") || n.endsWith("_FENCE") || n.endsWith("_FENCE_GATE") || n.endsWith("_DOOR") || n.endsWith("_TRAPDOOR")) yield BUILDING_PARTS;
                if (n.contains("BRICK") || n.contains("STONE") || n.contains("DEEPSLATE") || n.contains("SANDSTONE") || n.contains("QUARTZ")) yield MASONRY;
                yield OTHER_BUILDING;
            }
            case ORES -> {
                if (n.endsWith("_ORE") || n.startsWith("RAW_")) yield ORES_RAW;
                if (n.endsWith("_BLOCK")) yield MINERAL_BLOCKS;
                if (n.endsWith("_INGOT") || n.equals("DIAMOND") || n.equals("EMERALD") || n.equals("COAL") || n.equals("REDSTONE") || n.equals("LAPIS_LAZULI") || n.equals("QUARTZ")) yield INGOTS_GEMS;
                yield OTHER_ORES;
            }
            case FARMING -> {
                if (n.contains("SEEDS") || n.equals("WHEAT") || n.equals("CARROT") || n.equals("POTATO") || n.equals("BEETROOT") || n.contains("PUMPKIN") || n.contains("MELON")) yield CROPS;
                if (n.contains("COOKED") || n.contains("BEEF") || n.contains("PORK") || n.contains("CHICKEN") || n.contains("MUTTON") || n.contains("RABBIT") || n.contains("COD") || n.contains("SALMON") || n.contains("BREAD") || n.contains("APPLE") || n.contains("COOKIE") || n.contains("CAKE") || n.contains("STEW") || n.contains("SOUP")) yield FOOD;
                if (n.contains("SUGAR") || n.contains("HONEY") || n.equals("EGG") || n.equals("CACTUS") || n.equals("BAMBOO") || n.contains("KELP") || n.contains("BERRIES")) yield FARM_RESOURCES;
                yield OTHER_FARMING;
            }
            case MOB_DROPS -> {
                if (n.contains("PRISMARINE") || n.contains("NAUTILUS") || n.contains("INK_SAC")) yield AQUATIC_DROPS;
                if (n.contains("GHAST") || n.contains("SHULKER") || n.contains("PHANTOM") || n.contains("WITHER") || n.endsWith("_HEAD") || n.endsWith("_SKULL")) yield RARE_DROPS;
                if (n.contains("LEATHER") || n.contains("FEATHER") || n.contains("RABBIT") || n.contains("WOOL")) yield PASSIVE_DROPS;
                if (n.contains("ROTTEN") || n.contains("GUNPOWDER") || n.contains("STRING") || n.contains("SPIDER") || n.contains("BONE") || n.contains("SLIME") || n.contains("BLAZE") || n.contains("MAGMA")) yield HOSTILE_DROPS;
                yield OTHER_DROPS;
            }
            case REDSTONE -> {
                if (n.contains("MINECART") || n.contains("RAIL")) yield TRANSPORT;
                if (n.contains("PISTON") || n.contains("HOPPER") || n.contains("DISPENSER") || n.contains("DROPPER")) yield MACHINES;
                if (n.contains("BUTTON") || n.contains("LEVER") || n.contains("PRESSURE_PLATE") || n.contains("OBSERVER") || n.contains("TARGET") || n.contains("DAYLIGHT") || n.contains("TRIPWIRE")) yield INPUTS;
                if (n.contains("REPEATER") || n.contains("COMPARATOR") || n.contains("REDSTONE_TORCH")) yield COMPONENTS;
                yield OTHER_REDSTONE;
            }
            case TOOLS -> {
                if (n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS")) yield ARMOR;
                if (n.endsWith("_SWORD") || n.equals("BOW") || n.equals("CROSSBOW") || n.equals("TRIDENT") || n.equals("MACE")) yield WEAPONS;
                if (n.endsWith("_PICKAXE") || n.endsWith("_AXE") || n.endsWith("_SHOVEL") || n.endsWith("_HOE") || n.equals("SHEARS") || n.equals("BRUSH")) yield TOOLS;
                if (n.equals("SHIELD") || n.equals("ELYTRA") || n.equals("FISHING_ROD") || n.equals("FLINT_AND_STEEL")) yield UTILITY_GEAR;
                yield OTHER_TOOLS;
            }
            case DECORATION -> {
                if (n.endsWith("_WOOL") || n.endsWith("_CARPET") || n.endsWith("_CONCRETE") || n.endsWith("_TERRACOTTA") || n.endsWith("_CANDLE") || n.endsWith("_BANNER")) yield COLORS;
                if (n.contains("FLOWER") || n.contains("TULIP") || n.endsWith("_LEAVES") || n.endsWith("_SAPLING") || n.contains("CORAL")) yield PLANTS;
                if (n.equals("PAINTING") || n.contains("ITEM_FRAME")) yield DISPLAY;
                if (n.contains("POTTERY") || n.contains("DECORATED_POT")) yield POTTERY;
                yield OTHER_DECORATION;
            }
            case NETHER_END -> {
                if (n.contains("END_") || n.contains("ENDER") || n.contains("PURPUR") || n.contains("CHORUS") || n.contains("SHULKER")) yield END;
                if (n.contains("NETHER") || n.contains("CRIMSON") || n.contains("WARPED") || n.contains("SOUL_") || n.contains("BASALT") || n.contains("BLACKSTONE")) yield NETHER;
                if (n.equals("ENDER_PEARL") || n.equals("ENDER_EYE") || n.contains("BLAZE") || n.contains("GHAST") || n.equals("OBSIDIAN") || n.equals("CRYING_OBSIDIAN")) yield DIMENSIONAL_LOOT;
                yield OTHER_NETHER_END;
            }
            case MISC -> {
                if (n.contains("BOOK") || n.equals("PAPER") || n.equals("MAP")) yield BOOKS;
                if (n.contains("BOAT") || n.contains("RAFT") || n.contains("MINECART")) yield TRANSPORT_MISC;
                if (n.equals("CHEST") || n.equals("BARREL") || n.equals("BUNDLE") || n.equals("NAME_TAG") || n.equals("SADDLE")) yield UTILITY;
                if (n.contains("STAR") || n.contains("TOTEM") || n.contains("HEART_OF_THE_SEA") || n.contains("MUSIC_DISC") || n.contains("SMITHING_TEMPLATE")) yield SPECIAL;
                yield OTHER_MISC;
            }
            case ALL -> OTHER_MISC;
        };
    }
}

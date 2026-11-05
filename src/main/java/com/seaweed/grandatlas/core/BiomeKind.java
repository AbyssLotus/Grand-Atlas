package com.seaweed.grandatlas.core;

import java.util.Locale;

/**
 * Every biome the core can ask for. The Minecraft layer maps each one to a biome holder
 * through the biome source's {@code biomes} table (keyed by {@link #id()}), so the core
 * never touches registries. Most are vanilla; Grand Atlas's own biomes carry the
 * {@code grandatlas} namespace (tools/gen_data.py reads both from this file).
 */
public enum BiomeKind {
    DEEP_FROZEN_OCEAN(0x3B4F8F), DEEP_COLD_OCEAN(0x2A4A8A), DEEP_OCEAN(0x1F3F80), DEEP_LUKEWARM_OCEAN(0x1F5A8A),
    FROZEN_OCEAN(0x7090C8), COLD_OCEAN(0x3060B0), OCEAN(0x2A62C0), LUKEWARM_OCEAN(0x2A80C0), WARM_OCEAN(0x30A0C8),
    BEACH(0xF0E0A0), SNOWY_BEACH(0xF0F0E8), STONY_SHORE(0x909090),
    PLAINS(0x8DB360), SUNFLOWER_PLAINS(0xB5C060), FOREST(0x056621), FLOWER_FOREST(0x2D8E49),
    BIRCH_FOREST(0x307444), DARK_FOREST(0x40511A), OLD_GROWTH_BIRCH_FOREST(0x589C6C), PALE_GARDEN(0x696D6C),
    TAIGA(0x0B6659), OLD_GROWTH_SPRUCE_TAIGA(0x596651), OLD_GROWTH_PINE_TAIGA(0x818E79), SNOWY_PLAINS(0xE8F0F0), SNOWY_TAIGA(0x31554A),
    DESERT(0xFA9418), SAVANNA(0xBDB25F), SAVANNA_PLATEAU(0xA79D64), JUNGLE(0x537B09), SPARSE_JUNGLE(0x628B17),
    SWAMP(0x07F9B2), MANGROVE_SWAMP(0x2CCC8E), FROZEN_RIVER(0xA0B8FF), BADLANDS(0xD94515), WOODED_BADLANDS(0xB09765),
    ERODED_BADLANDS(0xFF6D3D),
    MEADOW(0x83BB6D), CHERRY_GROVE(0xF0A0C8), GROVE(0x8AB689), SNOWY_SLOPES(0xC4D0D8), WINDSWEPT_HILLS(0x606060),
    STONY_PEAKS(0xA0A0A0), JAGGED_PEAKS(0xDCE8F0), FROZEN_PEAKS(0xB0C8FF),
    LUSH_CAVES(0x50A050), DRIPSTONE_CAVES(0x806040), DEEP_DARK(0x102030), SULFUR_CAVES(0xB8C030),
    // M7e cave biomes (docs/M7_UNDERGROUND_PLAN.md)
    ROOT_CAVES(0x6A4A2A, "grandatlas"), ICE_CAVES(0x9CD0F0, "grandatlas"), SANDSTONE_CAVES(0xE0C080, "grandatlas"),
    FUNGAL_FOREST(0xB04060, "grandatlas"), CRYSTAL_CAVES(0xD8C8F8, "grandatlas"), MAGMA_CHAMBERS(0xE04010, "grandatlas"),
    ABYSSAL_SHORES(0x303848, "grandatlas"), GLOWWORM_GROTTO(0x2AA0A8, "grandatlas"),
    THE_HOLLOW(0x3A6A50, "grandatlas"), TITANS_GRAVE(0xC8C0A8, "grandatlas"),
    SALT_FLAT(0xF4F4EE, "grandatlas"), RED_DESERT(0xD2733C, "grandatlas"), RIVER(0x3070E0, "grandatlas"),
    SALT_MARSH(0x6E8B4A, "grandatlas"), PEBBLE_BEACH(0xA8A296, "grandatlas"), CRATER_LAKE(0x2A4AD0, "grandatlas");

    private final int previewColor;
    private final String namespace;

    BiomeKind(int previewColor) {
        this(previewColor, "minecraft");
    }

    BiomeKind(int previewColor, String namespace) {
        this.previewColor = previewColor;
        this.namespace = namespace;
    }

    /** Key in the biome source's table. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The biome this kind defaults to, e.g. {@code minecraft:plains} or {@code grandatlas:salt_flat}. */
    public String defaultBiome() {
        return namespace + ":" + id();
    }

    public int previewColor() {
        return previewColor;
    }
}

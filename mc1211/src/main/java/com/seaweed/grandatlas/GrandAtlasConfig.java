package com.seaweed.grandatlas;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/** config/grandatlas-common.toml (M8). */
public final class GrandAtlasConfig {
    private GrandAtlasConfig() {}

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.DoubleValue BIOME_MODS_WEIGHT;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> BIOME_MODS_DENY;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Other mods' biomes (Biomes O' Plenty, Oh The Biomes We've Gone, anything that uses TerraBlender).").push("biomeMods");
        BIOME_MODS_WEIGHT = b.comment("How common modded biomes are. 1 = as common as in an ordinary TerraBlender world (with BOP and",
                        "BWG that is over half the land), 0 = none. The default, 0.6, keeps most of the land Grand Atlas's.",
                        "A biome of ours always keeps at least a tenth of its land.",
                        "Takes effect for newly generated chunks.")
                .defineInRange("weight", 0.6, 0.0, 4.0);
        BIOME_MODS_DENY = b.comment("Mods (\"biomesoplenty\") or single biomes (\"biomeswevegone:cypress_swamplands\") to leave out.")
                .defineListAllowEmpty("deny", List.of(), () -> "", o -> o instanceof String);
        b.pop();
        SPEC = b.build();
    }

    public static double biomeModsWeight() {
        return SPEC.isLoaded() ? BIOME_MODS_WEIGHT.get() : 0.6;
    }

    @SuppressWarnings("unchecked")
    public static List<String> biomeModsDeny() {
        return SPEC.isLoaded() ? (List<String>) BIOME_MODS_DENY.get() : List.of();
    }
}

package com.seaweed.grandatlas.world;

import com.mojang.serialization.MapCodec;
import com.seaweed.grandatlas.GrandAtlas;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Codec registrations. The generated data files (tools/gen_data.py) refer to these by id:
 * <ul>
 *   <li>{@code grandatlas:terrain}: the surface density that replaces vanilla's sloped_cheese</li>
 *   <li>{@code grandatlas:climate}: the router's climate slots and the surface level, read from the same core</li>
 *   <li>{@code grandatlas:cave_damping}: closes vanilla's caves inside tall mountains</li>
 *   <li>{@code grandatlas:caves}: Grand Atlas's own caves and wind-carved rock detail</li>
 *   <li>{@code grandatlas:atlas}: the biome source</li>
 *   <li>{@code grandatlas:perched_water}: the feature that fills lakes and rivers above sea level</li>
 *   <li>{@code grandatlas:micro_detail}: the detail of meso sites and their micro features</li>
 *   <li>{@code grandatlas:fall_detail}: moss, vines and ferns round the river network's waterfalls</li>
 *   <li>{@code grandatlas:floater_cleanup}: clears rock the crags' undercuts cut loose</li>
 *   <li>{@code grandatlas:steep_rock}: bare rock instead of grass and dirt on steep ground</li>
 *   <li>{@code grandatlas:cave_dressing}: roots, icicles and crystal beams for the cave biomes (M7e)</li>
 *   <li>{@code grandatlas:deep_water}: the underground's lakes, seas and flooded caves (M7d)</li>
 *   <li>{@code grandatlas:strata}: the abyss's tuff layers and blackstone base (a material rule)</li>
 * </ul>
 */
public final class ModWorldgen {
    private ModWorldgen() {}

    public static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_FUNCTION_TYPES =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, GrandAtlas.MOD_ID);
    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, GrandAtlas.MOD_ID);
    public static final DeferredRegister<MapCodec<? extends Feature>> FEATURE_TYPES =
            DeferredRegister.create(Registries.FEATURE_TYPE, GrandAtlas.MOD_ID);
    public static final DeferredRegister<MapCodec<? extends net.minecraft.world.level.levelgen.material.rule.MaterialRule>> MATERIAL_RULE_TYPES =
            DeferredRegister.create(Registries.MATERIAL_RULE_TYPE, GrandAtlas.MOD_ID);

    static {
        DENSITY_FUNCTION_TYPES.register("terrain", () -> TerrainDensity.CODEC);
        DENSITY_FUNCTION_TYPES.register("climate", () -> ClimateDensity.CODEC);
        DENSITY_FUNCTION_TYPES.register("cave_damping", () -> CaveDamping.CODEC);
        DENSITY_FUNCTION_TYPES.register("caves", () -> GrandCaves.CODEC);
        BIOME_SOURCES.register("atlas", () -> AtlasBiomeSource.CODEC);
        FEATURE_TYPES.register("perched_water", () -> PerchedWater.CODEC);
        FEATURE_TYPES.register("micro_detail", () -> MicroDetail.CODEC);
        FEATURE_TYPES.register("fall_detail", () -> FallDetail.CODEC);
        FEATURE_TYPES.register("floater_cleanup", () -> FloaterCleanup.CODEC);
        FEATURE_TYPES.register("steep_rock", () -> SteepRock.CODEC);
        FEATURE_TYPES.register("deep_water", () -> DeepWater.CODEC);
        FEATURE_TYPES.register("cave_dressing", () -> CaveDressing.CODEC);
        FEATURE_TYPES.register("set_piece_dressing", () -> SetPieceDressing.CODEC);
        MATERIAL_RULE_TYPES.register("strata", () -> Strata.CODEC);
    }
}

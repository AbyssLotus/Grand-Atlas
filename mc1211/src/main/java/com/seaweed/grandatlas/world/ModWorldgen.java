package com.seaweed.grandatlas.world;

import com.mojang.serialization.MapCodec;
import com.seaweed.grandatlas.GrandAtlas;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.SurfaceRules;
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
 *   <li>{@code grandatlas:atlas_assets}: hand-built trees, god trees and rock formations from the approved packs (docs/CREDITS.md)</li>
 *   <li>{@code grandatlas:country_detail}: boulders, logs and ground cover on ordinary ground between the meso sites</li>
 *   <li>{@code grandatlas:cave_dressing}: roots, icicles and crystal beams for the cave biomes (M7e)</li>
 *   <li>{@code grandatlas:deep_water}: the underground's lakes, seas and flooded caves (M7d)</li>
 *   <li>{@code grandatlas:sky_isles}: the floating isles' density (M6)</li>
 *   <li>{@code grandatlas:fantasy_detail}: lava in the volcanoes, falls and hanging plants on the sky isles (M6)</li>
 *   <li>{@code grandatlas:strata}: the abyss's tuff layers and blackstone base (a material rule)</li>
 * </ul>
 */
public final class ModWorldgen {
    private ModWorldgen() {}

    public static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_FUNCTION_TYPES =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, GrandAtlas.MOD_ID);
    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, GrandAtlas.MOD_ID);
    public static final DeferredRegister<Feature<?>> FEATURE_TYPES =
            DeferredRegister.create(Registries.FEATURE, GrandAtlas.MOD_ID);
    public static final DeferredRegister<MapCodec<? extends SurfaceRules.RuleSource>> MATERIAL_RULE_TYPES =
            DeferredRegister.create(Registries.MATERIAL_RULE, GrandAtlas.MOD_ID);
    /** 1.21.1 only: {@code grandatlas:noise_3d} (26.3 has this built in). */
    public static final DeferredRegister<MapCodec<? extends SurfaceRules.ConditionSource>> MATERIAL_CONDITION_TYPES =
            DeferredRegister.create(Registries.MATERIAL_CONDITION, GrandAtlas.MOD_ID);

    static {
        DENSITY_FUNCTION_TYPES.register("terrain", () -> TerrainDensity.MAP_CODEC);
        DENSITY_FUNCTION_TYPES.register("climate", () -> ClimateDensity.MAP_CODEC);
        DENSITY_FUNCTION_TYPES.register("cave_damping", () -> CaveDamping.MAP_CODEC);
        DENSITY_FUNCTION_TYPES.register("caves", () -> GrandCaves.MAP_CODEC);
        DENSITY_FUNCTION_TYPES.register("sky_isles", () -> SkyIsleDensity.MAP_CODEC);
        BIOME_SOURCES.register("atlas", () -> AtlasBiomeSource.CODEC);
        FEATURE_TYPES.register("perched_water", () -> AtlasFeature.adapt(new PerchedWater()));
        FEATURE_TYPES.register("micro_detail", () -> AtlasFeature.adapt(new MicroDetail()));
        FEATURE_TYPES.register("fall_detail", () -> AtlasFeature.adapt(new FallDetail()));
        FEATURE_TYPES.register("fall_clear", () -> AtlasFeature.adapt(new FallClear()));
        FEATURE_TYPES.register("floater_cleanup", () -> AtlasFeature.adapt(new FloaterCleanup()));
        FEATURE_TYPES.register("steep_rock", () -> AtlasFeature.adapt(new SteepRock()));
        FEATURE_TYPES.register("country_detail", () -> AtlasFeature.adapt(new CountryDetail()));
        FEATURE_TYPES.register("atlas_assets", () -> AtlasFeature.adapt(new AtlasAssets()));
        FEATURE_TYPES.register("deep_water", () -> AtlasFeature.adapt(new DeepWater()));
        FEATURE_TYPES.register("cave_dressing", () -> AtlasFeature.adapt(new CaveDressing()));
        FEATURE_TYPES.register("set_piece_dressing", () -> AtlasFeature.adapt(new SetPieceDressing()));
        FEATURE_TYPES.register("fantasy_detail", () -> AtlasFeature.adapt(new FantasyDetail()));
        MATERIAL_RULE_TYPES.register("strata", () -> Strata.MAP_CODEC);
        MATERIAL_RULE_TYPES.register("rainbow", () -> RainbowBands.MAP_CODEC);
        MATERIAL_RULE_TYPES.register("cave_gate", () -> CaveGate.MAP_CODEC);
        MATERIAL_CONDITION_TYPES.register("noise_3d", () -> Noise3d.MAP_CODEC);
    }
}

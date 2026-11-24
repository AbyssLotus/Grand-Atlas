package com.seaweed.grandatlas;

import com.mojang.logging.LogUtils;
import com.seaweed.grandatlas.world.ModWorldgen;
import com.seaweed.grandatlas.world.RiverWater;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(GrandAtlas.MOD_ID)
public class GrandAtlas {
    public static final String MOD_ID = "grandatlas";
    public static final Logger LOG = LogUtils.getLogger();

    public GrandAtlas(IEventBus modBus, ModContainer container) {
        RiverWater.FLUIDS.register(modBus);
        RiverWater.BLOCKS.register(modBus);
        com.seaweed.grandatlas.world.ModBlocks.register(modBus);
        com.seaweed.grandatlas.atlas.AtlasData.register(modBus);
        com.seaweed.grandatlas.entity.ModEntities.register(modBus);
        com.seaweed.grandatlas.debug.AtlasDebug.register(modBus);
        // The caveSurvey dev run: the cave survey once the server is up, then stop (docs/CAVE_RESEARCH.md).
        if (Boolean.getBoolean("grandatlas.caveSurvey")) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.server.ServerStartedEvent.class,
                    e -> com.seaweed.grandatlas.debug.CaveSurvey.start(e.getServer(), line -> {}, () -> e.getServer().halt(false)));
        }
        // The caveShape dev run: the shape of the open space, vanilla's and ours (debug/CaveShape), then stop.
        if (Boolean.getBoolean("grandatlas.caveShape")) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.server.ServerStartedEvent.class,
                    e -> com.seaweed.grandatlas.debug.CaveShape.start(e.getServer(), () -> e.getServer().halt(false)));
        }
        // The domusCheck dev run: what stands at each of Domus Mortus's places (debug/DomusCheck), then stop.
        if (Boolean.getBoolean("grandatlas.domusCheck")) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.server.ServerStartedEvent.class, e -> {
                com.seaweed.grandatlas.debug.DomusCheck.run(e.getServer());
                e.getServer().halt(false);
            });
        }
        // The structureAudit dev run (M8): every structure against our biomes and the ground it lands on (debug/StructureAudit), then stop.
        if (Boolean.getBoolean("grandatlas.structureAudit")) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.server.ServerStartedEvent.class, e -> {
                com.seaweed.grandatlas.debug.StructureAudit.run(e.getServer());
                e.getServer().halt(false);
            });
        }
        // The biomeModsCheck dev run (M8): TerraBlender biome mods on our land (debug/BiomeModsCheck), then stop.
        if (Boolean.getBoolean("grandatlas.biomeModsCheck")) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.server.ServerStartedEvent.class, e -> {
                com.seaweed.grandatlas.debug.BiomeModsCheck.run(e.getServer());
                e.getServer().halt(false);
            });
        }
        // The deepCheck dev run: generate falls of the deep network, let fluids run, report, stop (debug/DeepCheck).
        if (Boolean.getBoolean("grandatlas.deepCheck")) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.server.ServerStartedEvent.class,
                    e -> com.seaweed.grandatlas.debug.DeepCheck.start(e.getServer(), () -> e.getServer().halt(false)));
        }
        ModWorldgen.DENSITY_FUNCTION_TYPES.register(modBus);
        ModWorldgen.BIOME_SOURCES.register(modBus);
        ModWorldgen.FEATURE_TYPES.register(modBus);
        ModWorldgen.MATERIAL_RULE_TYPES.register(modBus);
        ModWorldgen.MATERIAL_CONDITION_TYPES.register(modBus);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.server.ServerStoppedEvent.class,
                e -> com.seaweed.grandatlas.world.CoreHandle.release());
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, GrandAtlasConfig.SPEC);
        // Biome mods (M8): read their TerraBlender regions before the first chunk is generated.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.server.ServerAboutToStartEvent.class, e -> {
            var server = e.getServer();
            var biomes = server.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
            long seed = server.getWorldData().worldGenOptions().seed();
            for (var stem : server.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.LEVEL_STEM)) {
                if (stem.generator().getBiomeSource() instanceof com.seaweed.grandatlas.world.AtlasBiomeSource source) {
                    source.harvestModded(biomes, seed);
                    if (!source.modded().isEmpty()) com.seaweed.grandatlas.world.ModdedBiomes.enableSurfaceRules(stem.generator());
                }
            }
        });
    }
}

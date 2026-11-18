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
        com.seaweed.grandatlas.debug.AtlasDebug.register(modBus);
        // The caveSurvey dev run: the cave survey once the server is up, then stop (docs/CAVE_RESEARCH.md).
        if (Boolean.getBoolean("grandatlas.caveSurvey")) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.server.ServerStartedEvent.class,
                    e -> com.seaweed.grandatlas.debug.CaveSurvey.start(e.getServer(), line -> {}, () -> e.getServer().halt(false)));
        }
        ModWorldgen.DENSITY_FUNCTION_TYPES.register(modBus);
        ModWorldgen.BIOME_SOURCES.register(modBus);
        ModWorldgen.FEATURE_TYPES.register(modBus);
        ModWorldgen.MATERIAL_RULE_TYPES.register(modBus);
        ModWorldgen.MATERIAL_CONDITION_TYPES.register(modBus);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.server.ServerStoppedEvent.class,
                e -> com.seaweed.grandatlas.world.CoreHandle.release());
    }
}

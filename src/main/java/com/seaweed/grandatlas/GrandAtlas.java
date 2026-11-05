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
        ModWorldgen.DENSITY_FUNCTION_TYPES.register(modBus);
        ModWorldgen.BIOME_SOURCES.register(modBus);
        ModWorldgen.FEATURE_TYPES.register(modBus);
        ModWorldgen.MATERIAL_RULE_TYPES.register(modBus);
    }
}

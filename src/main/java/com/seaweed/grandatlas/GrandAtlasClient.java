package com.seaweed.grandatlas;

import com.seaweed.grandatlas.world.RiverWater;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.fluid.FluidTintSources;

/** Client side: river water draws exactly as vanilla water does (its textures, its biome tint). */
@Mod(value = GrandAtlas.MOD_ID, dist = Dist.CLIENT)
public class GrandAtlasClient {
    public GrandAtlasClient(IEventBus modBus) {
        modBus.addListener(RegisterFluidModelsEvent.class, event -> event.register(new FluidModel.Unbaked(
                new Material(Identifier.withDefaultNamespace("block/water_still")),
                new Material(Identifier.withDefaultNamespace("block/water_flow")),
                new Material(Identifier.withDefaultNamespace("block/water_overlay")),
                FluidTintSources.water()), RiverWater.FLUID));
    }
}

package com.seaweed.grandatlas;

import com.seaweed.grandatlas.world.RiverWater;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client setup. River water is NeoForge's own water type, so it already has water's textures and
 * biome tint; on 1.21.1 it only has to be told to draw in the translucent layer, as water does
 * (26.3 registers a fluid model for the same purpose).
 */
@Mod(value = GrandAtlas.MOD_ID, dist = Dist.CLIENT)
public class GrandAtlasClient {
    public GrandAtlasClient(IEventBus modBus) {
        modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(() ->
                ItemBlockRenderTypes.setRenderLayer(RiverWater.FLUID.get(), RenderType.translucent())));
        if (Boolean.getBoolean("grandatlas.mixinAudit")) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ClientTickEvent.Post.class, e -> audit());
        }
    }

    private static int auditTicks;

    /** The dev "audit" run: see {@link com.seaweed.grandatlas.client.MixinAudit}. Runs once the game has settled, then closes it. */
    private static void audit() {
        if (++auditTicks != 200) return;
        try {
            com.seaweed.grandatlas.client.MixinAudit.run();
        } catch (Throwable t) {
            GrandAtlas.LOG.error("MIXIN AUDIT: stopped by", t);
        }
        net.minecraft.client.Minecraft.getInstance().stop();
    }
}

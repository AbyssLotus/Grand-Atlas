package com.seaweed.grandatlas.myths.client;

import com.seaweed.grandatlas.myths.GrandAtlasMyths;
import com.seaweed.grandatlas.myths.entity.MythsEntities;
import net.minecraft.client.renderer.entity.RavagerRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Client-only setup. A dedicated server has no renderers, models or textures, and loading a
 * class that mentions them crashes it. {@code value = Dist.CLIENT} makes NeoForge load this
 * class on the client only. The annotation also subscribes its static @SubscribeEvent methods
 * (NeoForge picks the bus from each event: renderer registration is a mod-bus event), so nothing needs to call it.
 */
@EventBusSubscriber(modid = GrandAtlasMyths.MOD_ID, value = Dist.CLIENT)
public final class MythsClient {

    private MythsClient() {}

    /** Every entity type needs a renderer. The Minotaur borrows the ravager's until its model exists. */
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(MythsEntities.MINOTAUR.get(), RavagerRenderer::new);
    }
}

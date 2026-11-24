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
        // The Potted Monster (entity/PottedMonster): its Blockbench model and renderer.
        // ... and the Crystal Eel (entity/CrystalEel).
        modBus.addListener(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions.class, e -> {
            e.registerLayerDefinition(com.seaweed.grandatlas.client.PottedMonsterModel.LAYER,
                    com.seaweed.grandatlas.client.PottedMonsterModel::createBodyLayer);
            e.registerLayerDefinition(com.seaweed.grandatlas.client.CrystalEelModel.LAYER,
                    com.seaweed.grandatlas.client.CrystalEelModel::createBodyLayer);
        });
        modBus.addListener(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers.class, e -> {
            e.registerEntityRenderer(com.seaweed.grandatlas.entity.ModEntities.POTTED_MONSTER.get(),
                    com.seaweed.grandatlas.client.PottedMonsterRenderer::new);
            e.registerEntityRenderer(com.seaweed.grandatlas.entity.ModEntities.CRYSTAL_EEL.get(),
                    com.seaweed.grandatlas.client.CrystalEelRenderer::new);
        });
        // True darkness in the deep and abyss (cave rework R3): the lightmap (mixin/LightTextureMixin) and a black fog colour.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ViewportEvent.ComputeFogColor.class,
                com.seaweed.grandatlas.client.DeepDark::colour);
        if (Boolean.getBoolean("grandatlas.debugCheck")) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ClientTickEvent.Post.class,
                    e -> com.seaweed.grandatlas.client.DebugCheck.tick());
        }
        if (Boolean.getBoolean("grandatlas.mobLook")) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ClientTickEvent.Post.class,
                    e -> com.seaweed.grandatlas.client.MobLook.tick());
        }
        if (Boolean.getBoolean("grandatlas.karstCheck")) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ClientTickEvent.Post.class,
                    e -> com.seaweed.grandatlas.client.KarstCheck.tick());
        }
        // The abyss's own air (cave rework R8): particles and sound by the rock round the player.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ClientTickEvent.Post.class,
                e -> com.seaweed.grandatlas.client.AbyssAmbience.tick());
        if (Boolean.getBoolean("grandatlas.domusLook")) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ClientTickEvent.Post.class,
                    e -> com.seaweed.grandatlas.client.DomusLook.tick());
        }
        if (Boolean.getBoolean("grandatlas.surfaceLook")) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ClientTickEvent.Post.class,
                    e -> com.seaweed.grandatlas.client.SurfaceLook.tick());
        }
        if (Boolean.getBoolean("grandatlas.macroLook")) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ClientTickEvent.Post.class,
                    e -> com.seaweed.grandatlas.client.MacroLook.tick());
        }
        if (Boolean.getBoolean("grandatlas.deepLook")) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ClientTickEvent.Post.class,
                    e -> com.seaweed.grandatlas.client.DeepLook.tick());
        }
        if (Boolean.getBoolean("grandatlas.solutionCheck")) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ClientTickEvent.Post.class,
                    e -> com.seaweed.grandatlas.client.SolutionCheck.tick());
        }
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

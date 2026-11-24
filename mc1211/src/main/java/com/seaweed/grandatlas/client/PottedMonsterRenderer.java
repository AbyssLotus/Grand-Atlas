package com.seaweed.grandatlas.client;

import com.seaweed.grandatlas.GrandAtlas;
import com.seaweed.grandatlas.entity.PottedMonster;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Draws the Potted Monster with the user's own texture (embedded in Learning.bbmodel). */
public class PottedMonsterRenderer extends MobRenderer<PottedMonster, PottedMonsterModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(GrandAtlas.MOD_ID, "textures/entity/potted_monster.png");

    public PottedMonsterRenderer(EntityRendererProvider.Context context) {
        super(context, new PottedMonsterModel(context.bakeLayer(PottedMonsterModel.LAYER)), 0.45F);
    }

    @Override
    public ResourceLocation getTextureLocation(PottedMonster entity) {
        return TEXTURE;
    }
}

package com.seaweed.grandatlas.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.seaweed.grandatlas.GrandAtlas;
import com.seaweed.grandatlas.entity.CrystalEel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Draws the Crystal Eel with the user's own texture (embedded in Crystal Eel.bbmodel), pitched along its swim. */
public class CrystalEelRenderer extends MobRenderer<CrystalEel, CrystalEelModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(GrandAtlas.MOD_ID, "textures/entity/crystal_eel.png");

    public CrystalEelRenderer(EntityRendererProvider.Context context) {
        super(context, new CrystalEelModel(context.bakeLayer(CrystalEelModel.LAYER)), 0.3F);
    }

    @Override
    public ResourceLocation getTextureLocation(CrystalEel entity) {
        return TEXTURE;
    }

    @Override
    protected void setupRotations(CrystalEel entity, PoseStack pose, float bob, float yBodyRot, float partialTick, float scale) {
        super.setupRotations(entity, pose, bob, yBodyRot, partialTick, scale);
        // Nose up or down with its swimming, about the middle of the body.
        pose.translate(0.0F, 0.2F, 0.0F);
        pose.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(partialTick, entity.xRotO, entity.getXRot())));
        pose.translate(0.0F, -0.2F, 0.0F);
    }

    /** The death animation rolls and curls it; vanilla's tip-over on top of that is not wanted. */
    @Override
    protected float getFlipDegrees(CrystalEel entity) {
        return 0.0F;
    }
}

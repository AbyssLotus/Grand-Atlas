package com.seaweed.grandatlas.client;

import com.seaweed.grandatlas.GrandAtlas;
import com.seaweed.grandatlas.entity.CrystalEel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * The Crystal Eel's model: the user's Blockbench export (creel.java) on 1.21.1's HierarchicalModel.
 * Cubes, pivots and UVs are exactly the export's; the part names lose their spaces ("Main body" is
 * main_body, "back body" back_body, "Head" head). Four segments, each with a dorsal and a ventral fin.
 */
public class CrystalEelModel extends HierarchicalModel<CrystalEel> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(GrandAtlas.MOD_ID, "crystal_eel"), "main");

    private final ModelPart root;

    public CrystalEelModel(ModelPart root) {
        this.root = root;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition mainBody = root.addOrReplaceChild("main_body", CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, -3.0F, -5.0F, 2.0F, 3.0F, 10.0F, new CubeDeformation(0.0F))
                .texOffs(0, 14).addBox(0.0F, -5.0F, -5.0F, 1.0F, 2.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 20.0F, 0.0F));
        mainBody.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(0, 14).addBox(0.0F, -2.0F, -5.0F, 1.0F, 2.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 1.0F, -3.1416F, 0.0F, 0.0F));

        PartDefinition backBody = root.addOrReplaceChild("back_body", CubeListBuilder.create().texOffs(23, 14).addBox(-1.0F, -1.5F, 0.0F, 2.0F, 3.0F, 7.0F, new CubeDeformation(0.0F))
                .texOffs(25, 0).addBox(0.0F, -3.5F, 0.0F, 1.0F, 2.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 18.5F, 5.0F));
        backBody.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(25, 0).addBox(0.0F, -2.0F, -4.0F, 1.0F, 2.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 1.5F, 3.0F, -3.1416F, 0.0F, 0.0F));

        root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 27).addBox(-1.0F, -1.5F, -2.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 18.5F, -5.0F));

        PartDefinition tail = root.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(23, 25).addBox(-1.0F, -1.5F, 0.0F, 2.0F, 3.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(9, 27).addBox(0.0F, -3.5F, 0.0F, 1.0F, 2.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 18.5F, 12.0F));
        tail.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(9, 27).addBox(0.0F, -2.0F, -4.0F, 1.0F, 2.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 5.5F, 0.0F, -1.5708F, 0.0F, 0.0F));

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(CrystalEel entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        // Out of water it thrashes: the same swim, twice as fast.
        animate(entity.swimAnimationState, CrystalEelAnimation.SWIM, ageInTicks, entity.isInWater() ? 1.0F : 2.0F);
        animate(entity.deathAnimationState, CrystalEelAnimation.DEATH, ageInTicks);
    }
}

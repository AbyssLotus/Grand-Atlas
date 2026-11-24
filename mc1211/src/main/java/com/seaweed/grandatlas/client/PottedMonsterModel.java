package com.seaweed.grandatlas.client;

import com.seaweed.grandatlas.GrandAtlas;
import com.seaweed.grandatlas.entity.PottedMonster;
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
import net.minecraft.util.Mth;

/**
 * The Potted Monster's model: the user's Blockbench export (identibruv.java) on 1.21.1's
 * HierarchicalModel. Cubes, pivots and UVs are exactly the export's. The parts:
 * bone3 the pot (rim and soil), bone4 and bone5 the stem, bone9 the head: bone6 its base,
 * bone8 the jaws (bone and bone2, the second turned 180 degrees) and bone7 the tongue.
 *
 * The Blockbench file has no walk animation, so walking is drawn here: the whole plant hops on its
 * pot and twists a little with each step. The model was built facing backwards, so it is turned
 * round in setupAnim.
 */
public class PottedMonsterModel extends HierarchicalModel<PottedMonster> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(GrandAtlas.MOD_ID, "potted_monster"), "main");

    private final ModelPart root;

    public PottedMonsterModel(ModelPart root) {
        this.root = root;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("bone3", CubeListBuilder.create().texOffs(0, 46).addBox(-6.0F, -12.0F, -6.0F, 12.0F, 12.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(48, 26).addBox(-7.0F, -14.0F, -7.0F, 14.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(1, 23).addBox(5.0F, -12.0F, -5.0F, 1.0F, 12.0F, 11.0F, new CubeDeformation(0.0F))
                .texOffs(0, 0).addBox(-5.0F, -12.0F, -5.0F, 10.0F, 12.0F, 10.0F, new CubeDeformation(0.0F))
                .texOffs(26, 22).addBox(-6.0F, -12.0F, -5.0F, 1.0F, 12.0F, 10.0F, new CubeDeformation(0.0F))
                .texOffs(48, 14).addBox(-7.0F, -14.0F, -5.0F, 2.0F, 2.0F, 10.0F, new CubeDeformation(0.0F))
                .texOffs(27, 45).addBox(-6.0F, -12.0F, 5.0F, 11.0F, 12.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(48, 30).addBox(-7.0F, -14.0F, 5.0F, 12.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(40, 0).addBox(5.0F, -14.0F, -5.0F, 2.0F, 2.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 24.0F, 0.0F));

        root.addOrReplaceChild("bone4", CubeListBuilder.create().texOffs(19, 22).addBox(-2.0F, -5.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 12.0F, 0.0F));

        root.addOrReplaceChild("bone5", CubeListBuilder.create().texOffs(38, 20).addBox(-1.0F, -7.0F, -1.0F, 2.0F, 10.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 7.0F, 0.0F));

        PartDefinition bone9 = root.addOrReplaceChild("bone9", CubeListBuilder.create(), PartPose.offset(0.0F, 1.0F, 0.0F));

        bone9.addOrReplaceChild("bone6", CubeListBuilder.create().texOffs(62, 9).addBox(-4.0F, -2.0F, -4.0F, 8.0F, 2.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition bone8 = bone9.addOrReplaceChild("bone8", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.5F));

        bone8.addOrReplaceChild("bone7", CubeListBuilder.create().texOffs(59, 7).addBox(-1.0F, -6.0F, -1.0F, 2.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        bone8.addOrReplaceChild("bone", CubeListBuilder.create().texOffs(48, 34).addBox(-2.0F, -8.0F, 0.0F, 5.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(36, 58).addBox(2.0F, -7.0F, 0.0F, 1.0F, 5.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(52, 47).addBox(-2.0F, -6.0F, 2.0F, 4.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(49, 55).addBox(-3.0F, -8.0F, 0.0F, 1.0F, 6.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, -0.5F));

        bone8.addOrReplaceChild("bone2", CubeListBuilder.create().texOffs(48, 34).addBox(-2.0F, -8.0F, 0.0F, 5.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(36, 58).addBox(2.0F, -7.0F, 0.0F, 1.0F, 5.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(52, 47).addBox(-2.0F, -6.0F, 2.0F, 4.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(49, 55).addBox(-3.0F, -8.0F, 0.0F, 1.0F, 6.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -0.5F, 0.0F, 3.1416F, 0.0F));

        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(PottedMonster entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        animate(entity.idleAnimationState, PottedMonsterAnimation.IDLE, ageInTicks);
        animate(entity.attackAnimationState, PottedMonsterAnimation.ATTACK, ageInTicks);
        // Walking: a hop per step (up to 2 pixels) and a slight twist of the pot from side to side.
        float amount = Math.min(limbSwingAmount, 1.0F);
        float step = limbSwing * 0.9F;
        root.y -= Math.abs(Mth.sin(step)) * 2.0F * amount;
        // The Blockbench model faces +Z, the way the game calls backwards (the attack lunges the head to
        // +Z): turned half round about its upright axis here, so it faces and bites forward.
        // If the .bbmodel is ever turned round itself, drop the Mth.PI.
        root.yRot += Mth.PI + Mth.sin(step) * 0.2F * amount;
    }
}

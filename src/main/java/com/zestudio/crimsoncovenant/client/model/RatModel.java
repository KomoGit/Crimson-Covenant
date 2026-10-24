package com.zestudio.crimsoncovenant.client.model;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.MeshTransformer;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

/// A rat: a long low body on four short legs, a pointed snout with round ears, and a long bare tail that sways as
/// it scurries. Young rats use the same model at half size.
public class RatModel extends EntityModel<LivingEntityRenderState> {
    /// Half size, and sat back down on the ground
    public static final MeshTransformer BABY_TRANSFORMER = mesh -> mesh.transformed(pose -> pose.scaled(0.55F).translated(0.0F, 24.0F * 0.45F, 0.0F));

    private final ModelPart head;
    private final ModelPart tail;
    private final ModelPart tailTip;
    private final ModelPart frontLeftLeg;
    private final ModelPart frontRightLeg;
    private final ModelPart backLeftLeg;
    private final ModelPart backRightLeg;

    public RatModel(ModelPart root) {
        super(root);
        ModelPart body = root.getChild("body");
        this.head = root.getChild("head");
        this.tail = body.getChild("tail");
        this.tailTip = this.tail.getChild("tail_tip");
        this.frontLeftLeg = root.getChild("front_left_leg");
        this.frontRightLeg = root.getChild("front_right_leg");
        this.backLeftLeg = root.getChild("back_left_leg");
        this.backRightLeg = root.getChild("back_right_leg");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, -1.5F, -3.5F, 4.0F, 3.0F, 7.0F),
                PartPose.offset(0.0F, 20.5F, 0.0F));
        PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(0, 16).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 4.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 3.5F, -0.15F, 0.0F, 0.0F));
        tail.addOrReplaceChild("tail_tip", CubeListBuilder.create().texOffs(10, 16).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 4.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 4.0F, 0.25F, 0.0F, 0.0F));
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 10).addBox(-1.5F, -1.5F, -3.0F, 3.0F, 3.0F, 3.0F)
                        .texOffs(12, 10).addBox(-1.0F, -0.5F, -5.0F, 2.0F, 2.0F, 2.0F),
                PartPose.offset(0.0F, 20.0F, -3.5F));
        head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(20, 10).addBox(-1.0F, -2.0F, 0.0F, 2.0F, 2.0F, 1.0F),
                PartPose.offset(1.3F, -1.2F, -1.2F));
        head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(20, 10).mirror().addBox(-1.0F, -2.0F, 0.0F, 2.0F, 2.0F, 1.0F),
                PartPose.offset(-1.3F, -1.2F, -1.2F));
        CubeListBuilder leg = CubeListBuilder.create().texOffs(20, 16).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 2.0F, 1.0F);
        root.addOrReplaceChild("front_left_leg", leg, PartPose.offset(1.4F, 22.0F, -2.2F));
        root.addOrReplaceChild("front_right_leg", leg, PartPose.offset(-1.4F, 22.0F, -2.2F));
        root.addOrReplaceChild("back_left_leg", leg, PartPose.offset(1.4F, 22.0F, 2.4F));
        root.addOrReplaceChild("back_right_leg", leg, PartPose.offset(-1.4F, 22.0F, 2.4F));
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public void setupAnim(LivingEntityRenderState state) {
        super.setupAnim(state);
        this.head.yRot = state.yRot * Mth.DEG_TO_RAD;
        this.head.xRot = state.xRot * Mth.DEG_TO_RAD;
        // A quick scurry: legs flicking in pairs
        float stride = state.walkAnimationPos * 1.8F;
        float reach = Math.min(1.0F, state.walkAnimationSpeed) * 1.2F;
        this.frontLeftLeg.xRot = Mth.cos(stride) * reach;
        this.backRightLeg.xRot = Mth.cos(stride) * reach;
        this.frontRightLeg.xRot = Mth.cos(stride + Mth.PI) * reach;
        this.backLeftLeg.xRot = Mth.cos(stride + Mth.PI) * reach;
        // The tail sways, more when it's running
        float sway = Mth.sin(state.ageInTicks * 0.15F) * 0.25F + Mth.sin(stride) * reach * 0.3F;
        this.tail.yRot = sway;
        this.tailTip.yRot = sway * 0.8F;
        // Sniffing
        this.head.y += Mth.sin(state.ageInTicks * 0.6F) * 0.08F;
    }
}

package com.zestudio.crimsoncovenant.client.model;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.util.Mth;

/// The Plague Doctor: a tall, thin figure in a broad-brimmed, tall-crowned hat over a hood, a long leather beak
/// curving down from a mask with brass-rimmed goggles, a capelet over the shoulders of an ankle-length waxed coat,
/// leather gloves and boots, and a walking cane in his right hand. He walks stiffly, coat swaying, the cane kept
/// upright, and shakes his head (beak and all) when he won't do as asked.
public class PlagueDoctorModel extends EntityModel<VillagerRenderState> {
    private final ModelPart head;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart cane;
    private final ModelPart coat;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public PlagueDoctorModel(ModelPart root) {
        super(root);
        this.head = root.getChild("head");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.cane = this.rightArm.getChild("cane");
        this.coat = root.getChild("body").getChild("coat");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F),
                PartPose.ZERO);
        head.addOrReplaceChild("crown", CubeListBuilder.create().texOffs(32, 0).addBox(-4.0F, -13.0F, -4.0F, 8.0F, 5.0F, 8.0F), PartPose.ZERO);
        head.addOrReplaceChild("brim", CubeListBuilder.create().texOffs(0, 49).addBox(-7.0F, -8.5F, -7.0F, 14.0F, 1.0F, 14.0F), PartPose.ZERO);
        // The beak: a stout base from the face, and a tip curving down
        PartDefinition beak = head.addOrReplaceChild("beak", CubeListBuilder.create().texOffs(28, 41).addBox(-1.5F, -1.5F, -4.0F, 3.0F, 3.0F, 4.0F),
                PartPose.offsetAndRotation(0.0F, -3.0F, -4.0F, 0.2F, 0.0F, 0.0F));
        beak.addOrReplaceChild("beak_tip", CubeListBuilder.create().texOffs(42, 41).addBox(-1.0F, -1.0F, -4.0F, 2.0F, 2.0F, 4.0F),
                PartPose.offsetAndRotation(0.0F, 0.3F, -3.8F, 0.45F, 0.0F, 0.0F));

        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(16, 16).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F),
                PartPose.ZERO);
        body.addOrReplaceChild("capelet", CubeListBuilder.create().texOffs(28, 32).addBox(-5.0F, -0.5F, -3.0F, 10.0F, 3.0F, 6.0F), PartPose.ZERO);
        // The long skirt of the coat, hanging from the waist to just above the boots
        body.addOrReplaceChild("coat", CubeListBuilder.create().texOffs(0, 32).addBox(-4.5F, 0.0F, -2.5F, 9.0F, 10.0F, 5.0F),
                PartPose.offset(0.0F, 11.0F, 0.0F));

        PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 16).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                PartPose.offset(-5.0F, 2.0F, 0.0F));
        rightArm.addOrReplaceChild("cane", CubeListBuilder.create().texOffs(56, 16).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 14.0F, 1.0F),
                PartPose.offset(-1.0F, 9.0F, -2.5F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(40, 16).mirror().addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                PartPose.offset(5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                PartPose.offset(-2.0F, 12.0F, 0.0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 16).mirror().addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                PartPose.offset(2.0F, 12.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(VillagerRenderState state) {
        super.setupAnim(state);
        this.head.yRot = state.yRot * Mth.DEG_TO_RAD;
        this.head.xRot = state.xRot * Mth.DEG_TO_RAD;
        if (state.isUnhappy) {
            this.head.zRot = 0.3F * Mth.sin(0.45F * state.ageInTicks);
            this.head.xRot = 0.4F;
        }
        // A stiff, measured walk
        float stride = state.walkAnimationPos * 0.6662F;
        float reach = state.walkAnimationSpeed;
        this.rightLeg.xRot = Mth.cos(stride) * 1.0F * reach;
        this.leftLeg.xRot = Mth.cos(stride + Mth.PI) * 1.0F * reach;
        this.rightArm.xRot = -0.35F + Mth.cos(stride + Mth.PI) * 0.4F * reach;
        this.leftArm.xRot = Mth.cos(stride) * 0.6F * reach;
        // The cane stays upright in his grip, planted with each step
        this.cane.xRot = -this.rightArm.xRot;
        // The coat sways about his legs
        this.coat.xRot = Math.abs(Mth.cos(stride)) * 0.12F * reach;
    }
}

package com.zestudio.crimsoncovenant.client.model;

import com.zestudio.crimsoncovenant.client.renderer.GargoyleRenderState;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/// A gargoyle: a humanoid (classic 64x32 limb layout, left limbs mirrored) with horns swept back from the head,
/// a pair of stone wings on its shoulders and a tail. The extra parts use the lower half of the 64x64 texture.
///
/// Awake, it reaches for its prey with both claws like a zombie, and spreads its wings when it fights or glides.
/// Turned to stone, it sits hunched on its perch with its wings folded down its back and its claws on its knees.
public class GargoyleModel extends HumanoidModel<GargoyleRenderState> {
    private static final float WINGS_FOLDED = 1.25F;
    private static final float WINGS_SPREAD = 0.35F;
    private static final float WINGS_RAISED = 0.45F;

    private final ModelPart leftWing;
    private final ModelPart rightWing;
    private final ModelPart tail;

    public GargoyleModel(ModelPart root) {
        super(root);
        this.leftWing = this.body.getChild("left_wing");
        this.rightWing = this.body.getChild("right_wing");
        this.tail = this.body.getChild("tail");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.getChild("head");
        head.addOrReplaceChild("left_horn", CubeListBuilder.create().texOffs(0, 32).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F),
                PartPose.offsetAndRotation(2.5F, -7.5F, -1.0F, -0.6F, 0.0F, 0.25F));
        head.addOrReplaceChild("right_horn", CubeListBuilder.create().texOffs(4, 32).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F),
                PartPose.offsetAndRotation(-2.5F, -7.5F, -1.0F, -0.6F, 0.0F, -0.25F));
        PartDefinition body = root.getChild("body");
        // The left wing reaches out along +x from the shoulder blade; the right is its mirror image
        body.addOrReplaceChild("left_wing", CubeListBuilder.create().texOffs(0, 44).addBox(0.0F, -4.0F, 0.0F, 12.0F, 14.0F, 1.0F),
                PartPose.offset(1.5F, 1.0F, 2.0F));
        body.addOrReplaceChild("right_wing", CubeListBuilder.create().texOffs(26, 44).mirror().addBox(-12.0F, -4.0F, 0.0F, 12.0F, 14.0F, 1.0F),
                PartPose.offset(-1.5F, 1.0F, 2.0F));
        body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(8, 32).addBox(-1.0F, 0.0F, 0.0F, 2.0F, 2.0F, 10.0F),
                PartPose.offsetAndRotation(0.0F, 10.0F, 1.5F, -0.9F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(GargoyleRenderState state) {
        super.setupAnim(state);
        float t = state.ageInTicks;
        if (state.stone) {
            // Hunched on its perch, claws on its knees, looking out
            this.head.xRot = -0.25F;
            this.head.yRot = 0.0F;
            this.rightArm.xRot = -0.75F;
            this.leftArm.xRot = -0.75F;
            this.rightArm.zRot = 0.1F;
            this.leftArm.zRot = -0.1F;
            this.rightArm.yRot = 0.0F;
            this.leftArm.yRot = 0.0F;
            this.foldWings(WINGS_FOLDED, 0.1F);
            this.tail.yRot = 0.0F;
            return;
        }
        if (state.aggressive) {
            // Both claws out, like a zombie
            float swing = Mth.sin(state.swingAnimation * Mth.PI);
            this.rightArm.xRot = -Mth.HALF_PI + swing * 0.6F;
            this.leftArm.xRot = -Mth.HALF_PI + swing * 0.6F;
            this.rightArm.yRot = -0.1F;
            this.leftArm.yRot = 0.1F;
        }
        if (state.airborne || state.aggressive) {
            float beat = Mth.sin(t * (state.airborne ? 0.45F : 0.15F)) * (state.airborne ? 0.35F : 0.1F);
            this.foldWings(WINGS_SPREAD, WINGS_RAISED + beat);
        } else {
            this.foldWings(WINGS_FOLDED - 0.1F + Mth.sin(t * 0.05F) * 0.05F, 0.15F);
        }
        this.tail.yRot = Mth.sin(t * 0.08F) * 0.25F + Mth.cos(state.walkAnimationPos * 0.6662F) * 0.3F * state.walkAnimationSpeed;
    }

    /// `back` swings the wings back along the body (0 is straight out to the sides), `raise` lifts their tips
    private void foldWings(float back, float raise) {
        this.leftWing.yRot = -back;
        this.leftWing.zRot = -raise;
        this.rightWing.yRot = back;
        this.rightWing.zRot = raise;
    }
}

package com.zestudio.crimsoncovenant.client.model;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/// The Crimson Shroud's wings: a giant bat's, hung from the shoulders and moving like an elytra (folded down the
/// back, swept wide for gliding). Each is a membrane with some thickness to it, stretched over solid bones: the arm
/// along the leading edge with a claw at the wrist, and three fingers fanning out from it to the scalloped trailing
/// edge.
public class BatWingsModel extends EntityModel<AvatarRenderState> {
    // Where the fingers spring from, on the arm, and where each one ends (x out from the shoulder, y down)
    private static final float JOINT_X = -6.0F;
    private static final float JOINT_Y = 0.5F;
    private static final float[][] FINGER_TIPS = {{-11.5F, 9.0F}, {-9.5F, 17.0F}, {-5.0F, 21.5F}};
    /// A slow wingbeat while gliding
    private static final float BEAT = 0.12F;

    private final ModelPart leftWing;
    private final ModelPart rightWing;

    public BatWingsModel(ModelPart root) {
        // Not culled: the membrane is seen from both sides
        super(root, RenderTypes::entityCutout);
        this.leftWing = root.getChild("left_wing");
        this.rightWing = root.getChild("right_wing");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        wing(root, "left_wing", false, 5.0F, -Mth.PI / 12);
        wing(root, "right_wing", true, -5.0F, Mth.PI / 12);
        return LayerDefinition.create(mesh, 64, 32);
    }

    // The left wing reaches out along -x from the shoulder; the right is its mirror image
    private static void wing(PartDefinition root, String name, boolean mirror, float shoulder, float zRot) {
        float side = mirror ? -1.0F : 1.0F;
        CubeListBuilder membrane = CubeListBuilder.create().texOffs(0, 0).mirror(mirror)
                .addBox(mirror ? 0.0F : -12.0F, 0.0F, 0.0F, 12.0F, 22.0F, 1.0F)
                // The arm, a thick bone along the top, and the wrist's claw
                .texOffs(0, 24).addBox(mirror ? 0.0F : -12.5F, -1.0F, -0.25F, 12.5F, 1.5F, 1.5F)
                .texOffs(40, 0).addBox(mirror ? 12.5F : -13.5F, -2.0F, 0.0F, 1.0F, 2.0F, 1.0F);
        PartDefinition wing = root.addOrReplaceChild(name, membrane, PartPose.offsetAndRotation(shoulder, 0.0F, 0.0F, Mth.PI / 12, 0.0F, zRot));
        for (int i = 0; i < FINGER_TIPS.length; i++) {
            float dx = FINGER_TIPS[i][0] - JOINT_X;
            float dy = FINGER_TIPS[i][1] - JOINT_Y;
            float length = Mth.sqrt(dx * dx + dy * dy);
            // A bone along +y, turned to point at its tip
            float angle = (float) Math.atan2(-dx, dy);
            wing.addOrReplaceChild("finger_" + i, CubeListBuilder.create().texOffs(32, 0).addBox(-0.5F, 0.0F, -0.25F, 1.0F, length, 1.5F),
                    PartPose.offsetAndRotation(side * JOINT_X, JOINT_Y, 0.0F, 0.0F, 0.0F, side * angle));
        }
    }

    @Override
    public void setupAnim(AvatarRenderState state) {
        super.setupAnim(state);
        // Posed the way an elytra would be, with a slow beat on top while gliding
        float beat = state.isFallFlying ? Mth.sin(state.ageInTicks * 0.25F) * BEAT * state.fallFlyingScale() : 0.0F;
        this.leftWing.y = state.isCrouching ? 3.0F : 0.0F;
        this.leftWing.xRot = state.elytraRotX;
        this.leftWing.yRot = state.elytraRotY;
        this.leftWing.zRot = state.elytraRotZ + beat;
        this.rightWing.y = this.leftWing.y;
        this.rightWing.xRot = this.leftWing.xRot;
        this.rightWing.yRot = -this.leftWing.yRot;
        this.rightWing.zRot = -this.leftWing.zRot;
    }
}

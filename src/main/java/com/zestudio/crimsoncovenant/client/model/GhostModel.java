package com.zestudio.crimsoncovenant.client.model;

import com.zestudio.crimsoncovenant.client.renderer.GhostRenderState;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/// A tall cartoon "bedsheet" ghost: a rounded body with a face, two stubby arms and four floppy tail flaps.
/// Drawn translucent. Bobs gently, waves its arms, and throws them forward when swooping.
public class GhostModel extends EntityModel<GhostRenderState> {
    private static final String[] TAILS = {"tail_front_left", "tail_front_right", "tail_back_left", "tail_back_right"};

    private final ModelPart body;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart[] tails = new ModelPart[TAILS.length];

    public GhostModel(ModelPart root) {
        super(root, RenderTypes::entityTranslucent);
        this.body = root.getChild("body");
        this.rightArm = this.body.getChild("right_arm");
        this.leftArm = this.body.getChild("left_arm");
        for (int i = 0; i < TAILS.length; i++) {
            this.tails[i] = this.body.getChild(TAILS[i]);
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-5.0F, -18.0F, -5.0F, 10.0F, 18.0F, 10.0F)
                        .texOffs(0, 28).addBox(-4.0F, -20.0F, -4.0F, 8.0F, 2.0F, 8.0F),
                PartPose.offset(0.0F, 20.0F, 0.0F));
        body.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 0).addBox(-4.0F, -1.0F, -1.5F, 4.0F, 2.0F, 3.0F),
                PartPose.offset(-5.0F, -11.0F, 0.0F));
        body.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(40, 5).addBox(0.0F, -1.0F, -1.5F, 4.0F, 2.0F, 3.0F),
                PartPose.offset(5.0F, -11.0F, 0.0F));
        float[][] tailPivots = {{2.5F, -2.5F}, {-2.5F, -2.5F}, {2.5F, 2.5F}, {-2.5F, 2.5F}};
        for (int i = 0; i < TAILS.length; i++) {
            body.addOrReplaceChild(TAILS[i], CubeListBuilder.create().texOffs(32, 28).addBox(-2.5F, 0.0F, -2.5F, 5.0F, 3.0F, 5.0F),
                    PartPose.offset(tailPivots[i][0], 0.0F, tailPivots[i][1]));
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(GhostRenderState state) {
        super.setupAnim(state);
        float t = state.ageInTicks;
        this.body.y += Mth.sin(t * 0.1F) * 1.5F;
        this.body.yRot = state.yRot * Mth.DEG_TO_RAD;
        this.body.xRot = state.swooping ? 0.35F : 0.05F;

        float wave = Mth.sin(t * 0.15F) * 0.25F;
        if (state.swooping) {
            // Arms thrown forward: "boo!"
            this.rightArm.yRot = 1.2F;
            this.leftArm.yRot = -1.2F;
            this.rightArm.zRot = 0.2F + wave * 0.5F;
            this.leftArm.zRot = -0.2F - wave * 0.5F;
        } else {
            this.rightArm.zRot = 0.6F + wave;
            this.leftArm.zRot = -0.6F - wave;
        }

        for (int i = 0; i < this.tails.length; i++) {
            this.tails[i].xRot = Mth.sin(t * 0.2F + i * 1.7F) * 0.3F;
            this.tails[i].zRot = Mth.cos(t * 0.17F + i * 1.3F) * 0.2F;
        }
    }
}

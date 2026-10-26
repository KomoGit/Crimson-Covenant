package com.zestudio.crimsoncovenant.client.model;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/// The Spirit of Halloween: a tall, gaunt, headless thing. A crooked spine with its neck bone jutting from a bloody
/// stump, a hunched open ribcage with two broken ribs sticking out through it, long bony arms that reach out ahead
/// with clawed fingers, a torn shroud hanging from its shoulders, and rags trailing below its hips where its legs
/// should be. Drawn translucent. It drifts, sways, twitches and reaches.
public class HalloweenSpiritModel extends EntityModel<LivingEntityRenderState> {
    private static final String[] RAGS = {"rag_front_left", "rag_front_right", "rag_back_left", "rag_back_right", "rag_left", "rag_right"};

    private final ModelPart body;
    private final ModelPart chest;
    private final ModelPart neck;
    private final ModelPart cape;
    private final ModelPart rightArm;
    private final ModelPart rightForearm;
    private final ModelPart leftArm;
    private final ModelPart leftForearm;
    private final ModelPart[] rags = new ModelPart[RAGS.length];

    public HalloweenSpiritModel(ModelPart root) {
        super(root, RenderTypes::entityTranslucent);
        this.body = root.getChild("body");
        this.chest = this.body.getChild("chest");
        this.neck = this.chest.getChild("neck");
        this.cape = this.chest.getChild("cape");
        this.rightArm = this.chest.getChild("right_arm");
        this.rightForearm = this.rightArm.getChild("forearm");
        this.leftArm = this.chest.getChild("left_arm");
        this.leftForearm = this.leftArm.getChild("forearm");
        for (int i = 0; i < RAGS.length; i++) {
            this.rags[i] = this.body.getChild(RAGS[i]);
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // Pivots at the waist; everything below is bone (texture 0,0 to 32,32), stump (32,0), shroud (0,32), rags (24,32)
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                        // Hip bones, and the bottom of the spine
                        .texOffs(0, 16).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 3.0F, 4.0F)
                        .texOffs(0, 0).addBox(-1.0F, -6.0F, 0.5F, 2.0F, 6.0F, 2.0F),
                PartPose.offset(0.0F, 4.0F, 0.0F));

        CubeListBuilder ribs = CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, -12.0F, 1.5F, 2.0F, 12.0F, 2.0F)
                .texOffs(12, 0).addBox(-0.5F, -11.0F, -3.2F, 1.0F, 7.0F, 1.0F)
                .texOffs(0, 20).addBox(-6.0F, -13.0F, -1.0F, 12.0F, 1.0F, 2.0F);
        for (int i = 0; i < 5; i++) {
            float y = -11.0F + i * 2.0F;
            float in = i * 0.3F;
            ribs.texOffs(16, 4).addBox(-4.5F + in, y, -2.5F, 1.0F, 1.0F, 5.0F)
                    .texOffs(16, 4).addBox(3.5F - in, y, -2.5F, 1.0F, 1.0F, 5.0F)
                    .texOffs(16, 10).addBox(-4.0F + in, y, -3.0F, 3.0F, 1.0F, 1.0F)
                    .texOffs(16, 10).addBox(1.0F - in, y, -3.0F, 3.0F, 1.0F, 1.0F)
                    .texOffs(0, 24).addBox(-4.0F + in, y, 2.5F, 8.0F - 2 * in, 1.0F, 1.0F);
        }
        PartDefinition chest = body.addOrReplaceChild("chest", ribs, PartPose.offsetAndRotation(0.0F, -6.0F, 0.0F, 0.3F, 0.0F, -0.12F));
        // Two ribs snapped and sticking out through the shroud
        chest.addOrReplaceChild("broken_rib_left", CubeListBuilder.create().texOffs(16, 12).addBox(-4.0F, 0.0F, 0.0F, 4.0F, 1.0F, 1.0F),
                PartPose.offsetAndRotation(-4.3F, -7.0F, -1.5F, 0.0F, 0.5F, -0.45F));
        chest.addOrReplaceChild("broken_rib_right", CubeListBuilder.create().texOffs(16, 12).addBox(0.0F, 0.0F, 0.0F, 3.0F, 1.0F, 1.0F),
                PartPose.offsetAndRotation(3.6F, -5.0F, -2.0F, 0.0F, -0.7F, 0.6F));
        // The bloody stump, with the top of the spine jutting out of it
        chest.addOrReplaceChild("neck",
                CubeListBuilder.create().texOffs(32, 0).addBox(-1.5F, -2.0F, -1.5F, 3.0F, 2.0F, 3.0F)
                        .texOffs(12, 0).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 2.0F, 1.0F),
                PartPose.offsetAndRotation(0.0F, -13.0F, 1.0F, 0.2F, 0.0F, 0.15F));
        chest.addOrReplaceChild("cape", CubeListBuilder.create().texOffs(0, 32).addBox(-5.0F, 0.0F, 0.0F, 10.0F, 16.0F, 1.0F),
                PartPose.offsetAndRotation(0.0F, -13.0F, 3.0F, 0.15F, 0.0F, 0.0F));

        PartDefinition rightArm = chest.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 11.0F, 2.0F),
                PartPose.offset(-6.0F, -12.5F, 0.0F));
        PartDefinition rightForearm = rightArm.addOrReplaceChild("forearm",
                CubeListBuilder.create().texOffs(8, 0).addBox(-0.75F, 0.0F, -0.75F, 1.5F, 10.0F, 1.5F), PartPose.offset(0.0F, 11.0F, 0.0F));
        addClaw(rightForearm);
        PartDefinition leftArm = chest.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 11.0F, 2.0F),
                PartPose.offset(6.0F, -12.5F, 0.0F));
        PartDefinition leftForearm = leftArm.addOrReplaceChild("forearm",
                CubeListBuilder.create().texOffs(8, 0).addBox(-0.75F, 0.0F, -0.75F, 1.5F, 10.0F, 1.5F), PartPose.offset(0.0F, 11.0F, 0.0F));
        addClaw(leftForearm);

        // Rags hanging from the hips, front, back and sides
        float[][] rags = {{-2.0F, -2.2F, 0}, {2.0F, -2.2F, 0}, {-2.0F, 2.2F, 0}, {2.0F, 2.2F, 0}, {-4.2F, 0.0F, 1}, {4.2F, 0.0F, 1}};
        for (int i = 0; i < RAGS.length; i++) {
            CubeListBuilder rag = rags[i][2] == 0
                    ? CubeListBuilder.create().texOffs(24, 32).addBox(-1.5F, 0.0F, -0.5F, 3.0F, 13.0F, 1.0F)
                    : CubeListBuilder.create().texOffs(24, 32).addBox(-0.5F, 0.0F, -1.5F, 1.0F, 13.0F, 3.0F);
            body.addOrReplaceChild(RAGS[i], rag, PartPose.offset(rags[i][0], 2.5F, rags[i][1]));
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    // Three long, hooked fingers
    private static void addClaw(PartDefinition forearm) {
        forearm.addOrReplaceChild("claw",
                CubeListBuilder.create().texOffs(12, 0).addBox(-1.25F, 0.0F, -0.5F, 0.5F, 4.0F, 0.5F)
                        .texOffs(12, 0).addBox(-0.25F, 0.0F, -0.75F, 0.5F, 5.0F, 0.5F)
                        .texOffs(12, 0).addBox(0.75F, 0.0F, -0.5F, 0.5F, 4.0F, 0.5F),
                PartPose.offsetAndRotation(0.0F, 10.0F, 0.0F, -0.5F, 0.0F, 0.0F));
    }

    @Override
    public void setupAnim(LivingEntityRenderState state) {
        super.setupAnim(state);
        float t = state.ageInTicks;
        this.body.y += Mth.sin(t * 0.08F) * 1.2F;
        this.body.zRot = Mth.sin(t * 0.05F) * 0.05F;
        this.chest.zRot += Mth.sin(t * 0.04F) * 0.04F;
        // A twitch of the neck now and then
        this.neck.yRot = Mth.sin(t * 0.9F) * (Mth.sin(t * 0.07F) > 0.8F ? 0.25F : 0.02F);
        this.cape.xRot += Mth.sin(t * 0.1F) * 0.08F;

        // Both arms reach out ahead, the left more crooked than the right
        this.rightArm.xRot = -1.0F + Mth.sin(t * 0.06F) * 0.15F;
        this.rightArm.zRot = 0.15F;
        this.rightForearm.xRot = -0.45F + Mth.sin(t * 0.08F) * 0.1F;
        this.leftArm.xRot = -0.65F + Mth.cos(t * 0.05F) * 0.2F;
        this.leftArm.zRot = -0.3F;
        this.leftArm.yRot = -0.2F;
        this.leftForearm.xRot = -0.9F + Mth.cos(t * 0.07F) * 0.1F;

        for (int i = 0; i < this.rags.length; i++) {
            this.rags[i].xRot = 0.15F + Mth.sin(t * 0.2F + i * 1.7F) * 0.25F;
            this.rags[i].zRot = Mth.cos(t * 0.17F + i * 1.3F) * 0.15F;
        }
    }
}

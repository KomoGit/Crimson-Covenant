package com.zestudio.crimsoncovenant.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.zestudio.crimsoncovenant.client.ModModelLayers;
import com.zestudio.crimsoncovenant.client.model.BloodSpearModel;
import com.zestudio.crimsoncovenant.entity.BloodSpear;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ThrownTridentRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;

/// The Blood Spear in flight (see BloodSpearModel), glowing
public class BloodSpearRenderer extends EntityRenderer<BloodSpear, ThrownTridentRenderState> {
    private final BloodSpearModel model;

    public BloodSpearRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new BloodSpearModel(context.bakeLayer(ModModelLayers.BLOOD_SPEAR));
    }

    @Override
    public void submit(ThrownTridentRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.rotateDegrees(Axis.YP, state.yRot - 90.0F);
        poseStack.rotateDegrees(Axis.ZP, state.xRot + 90.0F);
        submitNodeCollector.submitModel(this.model, Unit.INSTANCE, poseStack, BloodSpearModel.TEXTURE, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
        super.submit(state, poseStack, submitNodeCollector, camera);
    }

    // It glows, like the blood skills' particles
    @Override
    protected int getBlockLightLevel(BloodSpear entity, BlockPos pos) {
        return 15;
    }

    @Override
    public ThrownTridentRenderState createRenderState() {
        return new ThrownTridentRenderState();
    }

    @Override
    public void extractRenderState(BloodSpear entity, ThrownTridentRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.yRot = entity.getYRot(partialTicks);
        state.xRot = entity.getXRot(partialTicks);
    }
}

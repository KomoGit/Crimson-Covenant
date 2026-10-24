package com.zestudio.crimsoncovenant.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.zestudio.crimsoncovenant.client.ModModelLayers;
import com.zestudio.crimsoncovenant.client.model.SpiritHandModel;
import com.zestudio.crimsoncovenant.entity.SpiritHand;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemDisplayContext;

/// Revenant's Grip: the ghostly hand (see SpiritHandModel) with the weapon in its grip, glowing, swinging the weapon down
/// when it's close enough to strike
public class SpiritHandRenderer extends EntityRenderer<SpiritHand, SpiritHandRenderer.State> {
    private final SpiritHandModel model;
    private final ItemModelResolver itemModelResolver;

    public static class State extends EntityRenderState {
        public final ItemStackRenderState weapon = new ItemStackRenderState();
        public float yRot;
        public boolean striking;
    }

    public SpiritHandRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new SpiritHandModel(context.bakeLayer(ModModelLayers.SPIRIT_HAND));
        this.itemModelResolver = context.getItemModelResolver();
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.rotateDegrees(Axis.YP, 180.0F - state.yRot);
        // Raised to strike, the blade comes down; otherwise it's held up and a little back, bobbing
        float swing = state.striking ? -60.0F + (float) Math.sin(state.ageInTicks * 0.6F) * 50.0F : 20.0F + (float) Math.sin(state.ageInTicks * 0.1F) * 6.0F;
        poseStack.rotateDegrees(Axis.XP, swing);
        poseStack.pushPose();
        poseStack.scale(1.0F / 16.0F * 1.5F, -1.0F / 16.0F * 1.5F, -1.0F / 16.0F * 1.5F);
        submitNodeCollector.submitModel(this.model, Unit.INSTANCE, poseStack, SpiritHandModel.TEXTURE, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
        // The weapon in the grip, blade up
        poseStack.translate(0.05, 0.25, 0.0);
        poseStack.rotateDegrees(Axis.YP, 90.0F);
        poseStack.rotateDegrees(Axis.ZP, -45.0F);
        poseStack.scale(1.1F, 1.1F, 1.1F);
        state.weapon.submit(poseStack, submitNodeCollector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
        super.submit(state, poseStack, submitNodeCollector, camera);
    }

    // It glows: it's a spirit
    @Override
    protected int getBlockLightLevel(SpiritHand entity, BlockPos pos) {
        return 15;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(SpiritHand entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.yRot = entity.getYRot(partialTicks);
        state.striking = entity.isStriking();
        this.itemModelResolver.updateForNonLiving(state.weapon, entity.getWeapon(), ItemDisplayContext.FIXED, entity);
    }
}

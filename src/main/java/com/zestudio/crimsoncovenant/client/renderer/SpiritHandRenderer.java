package com.zestudio.crimsoncovenant.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
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
import net.minecraft.world.item.ItemDisplayContext;

/// Revenant's Grip: the weapon floating on its own, blade up, swaying a little as it hangs in the air, and swinging
/// down when it's close enough to strike. It glows: it's held by a spirit.
public class SpiritHandRenderer extends EntityRenderer<SpiritHand, SpiritHandRenderer.State> {
    /// A little larger than life, so it's seen at a glance
    private static final float WEAPON_SIZE = 1.4F;
    private final ItemModelResolver itemModelResolver;

    public static class State extends EntityRenderState {
        public final ItemStackRenderState weapon = new ItemStackRenderState();
        public float yRot;
        public boolean striking;
    }

    public SpiritHandRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemModelResolver = context.getItemModelResolver();
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.rotateDegrees(Axis.YP, 180.0F - state.yRot);
        // Raised to strike, the blade comes down; otherwise it hangs upright, leaning a little back and swaying
        float swing = state.striking ? -60.0F + (float) Math.sin(state.ageInTicks * 0.6F) * 50.0F : 15.0F + (float) Math.sin(state.ageInTicks * 0.1F) * 6.0F;
        poseStack.rotateDegrees(Axis.XP, swing);
        poseStack.rotateDegrees(Axis.ZP, (float) Math.sin(state.ageInTicks * 0.07F) * 5.0F);
        // Blade up
        poseStack.rotateDegrees(Axis.YP, 90.0F);
        poseStack.rotateDegrees(Axis.ZP, -45.0F);
        poseStack.scale(WEAPON_SIZE, WEAPON_SIZE, WEAPON_SIZE);
        state.weapon.submit(poseStack, submitNodeCollector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
        super.submit(state, poseStack, submitNodeCollector, camera);
    }

    // It glows: it's a spirit's
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

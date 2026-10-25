package com.zestudio.crimsoncovenant.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.zestudio.crimsoncovenant.entity.HauntedObject;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.FallingBlockRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/// A haunted object: the thing the ghost's in, drawn as the block it is, turning as it floats and tipping a little
/// this way and that, the way a falling block is drawn
public class HauntedObjectRenderer extends EntityRenderer<HauntedObject, FallingBlockRenderState> {
    public HauntedObjectRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.4F;
    }

    @Override
    public void submit(FallingBlockRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        BlockState blockState = state.movingBlockRenderState.blockState;
        if (blockState.getRenderShape() != RenderShape.MODEL) {
            return;
        }
        poseStack.pushPose();
        // Turning about its middle, and rocking
        poseStack.translate(0.0, 0.5, 0.0);
        poseStack.rotateDegrees(Axis.YP, state.ageInTicks * 4.0F);
        poseStack.rotateDegrees(Axis.XP, (float) Math.sin(state.ageInTicks * 0.09F) * 12.0F);
        poseStack.rotateDegrees(Axis.ZP, (float) Math.cos(state.ageInTicks * 0.07F) * 12.0F);
        poseStack.translate(-0.5, -0.5, -0.5);
        submitNodeCollector.submitMovingBlock(poseStack, state.movingBlockRenderState, state.outlineColor);
        poseStack.popPose();
        super.submit(state, poseStack, submitNodeCollector, camera);
    }

    @Override
    public FallingBlockRenderState createRenderState() {
        return new FallingBlockRenderState();
    }

    @Override
    public void extractRenderState(HauntedObject entity, FallingBlockRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        BlockPos pos = BlockPos.containing(entity.getX(), entity.getBoundingBox().maxY, entity.getZ());
        state.movingBlockRenderState.randomSeedPos = entity.getOrigin();
        state.movingBlockRenderState.blockPos = pos;
        state.movingBlockRenderState.blockState = entity.getHeld();
        if (entity.level() instanceof ClientLevel clientLevel) {
            state.movingBlockRenderState.biome = clientLevel.getBiome(pos);
            state.movingBlockRenderState.cardinalLighting = clientLevel.cardinalLighting();
            state.movingBlockRenderState.lightEngine = clientLevel.getLightEngine();
        }
    }
}

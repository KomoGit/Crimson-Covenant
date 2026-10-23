package com.zestudio.crimsoncovenant.client.renderer;

import org.joml.Matrix4fc;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import com.zestudio.crimsoncovenant.entity.CrimsonLightning;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.RandomSource;

/// The Crimson Lightning upgrade's bolt: vanilla's lightning shape, in blood red and a fraction of the height
public class CrimsonLightningRenderer extends EntityRenderer<CrimsonLightning, CrimsonLightningRenderer.State> {
    private static final float SIZE = 0.3F;

    public static class State extends EntityRenderState {
        public long seed;
    }

    public CrimsonLightningRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        float[] xOffsets = new float[8];
        float[] zOffsets = new float[8];
        float xOffset = 0.0F;
        float zOffset = 0.0F;
        RandomSource random = RandomSource.createThreadLocalInstance(state.seed);
        poseStack.pushPose();
        poseStack.scale(SIZE, SIZE, SIZE);

        for (int heightSegmentIndex = 7; heightSegmentIndex >= 0; heightSegmentIndex--) {
            xOffsets[heightSegmentIndex] = xOffset;
            zOffsets[heightSegmentIndex] = zOffset;
            xOffset += random.nextInt(11) - 5;
            zOffset += random.nextInt(11) - 5;
        }

        float finalXOff = xOffset;
        float finalZOff = zOffset;
        submitNodeCollector.submitCustomGeometry(
            poseStack,
            RenderTypes.lightning(),
            (pose, buffer) -> {
                Matrix4fc poseMatrix = pose.pose();

                for (int layer = 0; layer < 4; layer++) {
                    RandomSource randomx = RandomSource.createThreadLocalInstance(state.seed);

                    for (int branchNumber = 0; branchNumber < 3; branchNumber++) {
                        boolean isTrunkBranch = branchNumber == 0;
                        int branchStartSegment = 7 - branchNumber;
                        int branchEndSegment = isTrunkBranch ? 0 : branchStartSegment - 3 + 1;
                        float segmentStartX = xOffsets[branchStartSegment] - finalXOff;
                        float segmentStartZ = zOffsets[branchStartSegment] - finalZOff;

                        for (int currentSegment = branchStartSegment; currentSegment >= branchEndSegment; currentSegment--) {
                            float segmentEndX = segmentStartX;
                            float segmentEndZ = segmentStartZ;
                            if (isTrunkBranch) {
                                segmentStartX += randomx.nextInt(11) - 5;
                                segmentStartZ += randomx.nextInt(11) - 5;
                            } else {
                                segmentStartX += randomx.nextInt(31) - 15;
                                segmentStartZ += randomx.nextInt(31) - 15;
                            }

                            float topRadius = 0.1F + layer * 0.2F;
                            float bottomRadius = topRadius;
                            if (isTrunkBranch) {
                                topRadius *= currentSegment * 0.1F + 1.0F;
                                bottomRadius *= (currentSegment - 1) * 0.1F + 1.0F;
                            }

                            quad(
                                poseMatrix,
                                buffer,
                                segmentStartX,
                                segmentStartZ,
                                segmentEndX,
                                segmentEndZ,
                                currentSegment,
                                topRadius,
                                bottomRadius,
                                false,
                                false,
                                true,
                                false
                            );
                            quad(
                                poseMatrix,
                                buffer,
                                segmentStartX,
                                segmentStartZ,
                                segmentEndX,
                                segmentEndZ,
                                currentSegment,
                                topRadius,
                                bottomRadius,
                                true,
                                false,
                                true,
                                true
                            );
                            quad(
                                poseMatrix,
                                buffer,
                                segmentStartX,
                                segmentStartZ,
                                segmentEndX,
                                segmentEndZ,
                                currentSegment,
                                topRadius,
                                bottomRadius,
                                true,
                                true,
                                false,
                                true
                            );
                            quad(
                                poseMatrix,
                                buffer,
                                segmentStartX,
                                segmentStartZ,
                                segmentEndX,
                                segmentEndZ,
                                currentSegment,
                                topRadius,
                                bottomRadius,
                                false,
                                true,
                                false,
                                false
                            );
                        }
                    }
                }
            }
        );
        poseStack.popPose();
    }

    private static void quad(
        Matrix4fc pose,
        VertexConsumer buffer,
        float segmentStartX,
        float segmentStartZ,
        float segmentEndX,
        float segmentEndZ,
        int currentSegment,
        float topRadius,
        float bottomRadius,
        boolean rightXPositive,
        boolean rightZPositive,
        boolean leftXPositive,
        boolean leftZPositive
    ) {
        buffer.addVertex(
                pose,
                segmentStartX + (rightXPositive ? bottomRadius : -bottomRadius),
                currentSegment * 16,
                segmentStartZ + (rightZPositive ? bottomRadius : -bottomRadius)
            )
            .setColor(0.75F, 0.05F, 0.1F, 0.35F);
        buffer.addVertex(
                pose,
                segmentEndX + (rightXPositive ? topRadius : -topRadius),
                (currentSegment + 1) * 16,
                segmentEndZ + (rightZPositive ? topRadius : -topRadius)
            )
            .setColor(0.75F, 0.05F, 0.1F, 0.35F);
        buffer.addVertex(
                pose, segmentEndX + (leftXPositive ? topRadius : -topRadius), (currentSegment + 1) * 16, segmentEndZ + (leftZPositive ? topRadius : -topRadius)
            )
            .setColor(0.75F, 0.05F, 0.1F, 0.35F);
        buffer.addVertex(
                pose,
                segmentStartX + (leftXPositive ? bottomRadius : -bottomRadius),
                currentSegment * 16,
                segmentStartZ + (leftZPositive ? bottomRadius : -bottomRadius)
            )
            .setColor(0.75F, 0.05F, 0.1F, 0.35F);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(CrimsonLightning entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.seed = entity.seed;
    }

    @Override
    protected boolean affectedByCulling(CrimsonLightning entity) {
        return false;
    }
}

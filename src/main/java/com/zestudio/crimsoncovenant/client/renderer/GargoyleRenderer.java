package com.zestudio.crimsoncovenant.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.client.ModModelLayers;
import com.zestudio.crimsoncovenant.client.model.GargoyleModel;
import com.zestudio.crimsoncovenant.entity.Gargoyle;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

// See GargoyleModel. Each side has its own stone, and eyes that glow in the dark while it's awake.
public class GargoyleRenderer extends HumanoidMobRenderer<Gargoyle, GargoyleRenderState, GargoyleModel> {
    private final Identifier texture;

    public GargoyleRenderer(EntityRendererProvider.Context context, Gargoyle.Side side) {
        super(context, new GargoyleModel(context.bakeLayer(ModModelLayers.GARGOYLE)), 0.5F);
        String name = side == Gargoyle.Side.HUNTER ? "cathedral_gargoyle" : "blood_gargoyle";
        this.texture = CrimsonCovenant.id("textures/entity/gargoyle/" + name + ".png");
        this.addLayer(new GlowingEyes(this, RenderTypes.eyes(CrimsonCovenant.id("textures/entity/gargoyle/" + name + "_eyes.png"))));
    }

    @Override
    public GargoyleRenderState createRenderState() {
        return new GargoyleRenderState();
    }

    @Override
    public void extractRenderState(Gargoyle gargoyle, GargoyleRenderState state, float partialTicks) {
        super.extractRenderState(gargoyle, state, partialTicks);
        state.stone = gargoyle.isStone();
        state.aggressive = gargoyle.isAggressive();
        state.airborne = !gargoyle.onGround() && !gargoyle.isInWater();
        if (state.stone) {
            state.isCrouching = true;
            state.walkAnimationSpeed = 0.0F;
        }
    }

    @Override
    public Identifier getTextureLocation(GargoyleRenderState state) {
        return this.texture;
    }

    @Override
    protected void scale(GargoyleRenderState state, PoseStack poseStack) {
        poseStack.scale(1.05F, 1.05F, 1.05F);
    }

    private static class GlowingEyes extends EyesLayer<GargoyleRenderState, GargoyleModel> {
        private final RenderType renderType;

        GlowingEyes(RenderLayerParent<GargoyleRenderState, GargoyleModel> renderer, RenderType renderType) {
            super(renderer);
            this.renderType = renderType;
        }

        // Stone eyes are dead eyes
        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, GargoyleRenderState state, float yRot, float xRot) {
            if (!state.stone) {
                super.submit(poseStack, submitNodeCollector, lightCoords, state, yRot, xRot);
            }
        }

        @Override
        public RenderType renderType() {
            return this.renderType;
        }
    }
}

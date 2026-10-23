package com.zestudio.crimsoncovenant.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zestudio.crimsoncovenant.CrimsonCovenant;

import net.minecraft.client.renderer.entity.BatRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.BatRenderState;
import net.minecraft.resources.Identifier;

// The vanilla bat model and animations with a blood-red texture, a little bigger than a normal bat
public class VampireBatRenderer extends BatRenderer {
    private static final Identifier TEXTURE = CrimsonCovenant.id("textures/entity/vampire_bat.png");
    private static final float SCALE = 1.2F;

    public VampireBatRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public Identifier getTextureLocation(BatRenderState state) {
        return TEXTURE;
    }

    @Override
    protected void scale(BatRenderState state, PoseStack poseStack) {
        poseStack.scale(SCALE, SCALE, SCALE);
    }
}

package com.zestudio.crimsoncovenant.client.renderer;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.client.ModModelLayers;
import com.zestudio.crimsoncovenant.entity.HeadlessHorseman;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

// Player-model rider whose skin has no head at all: the jack o'lantern in its head slot sits in its place
public class HeadlessHorsemanRenderer extends HumanoidMobRenderer<HeadlessHorseman, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
    private static final Identifier TEXTURE = CrimsonCovenant.id("textures/entity/headless_horseman.png");

    public HeadlessHorsemanRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModModelLayers.HUNTER)), 0.5F);
    }

    @Override
    public HumanoidRenderState createRenderState() {
        return new HumanoidRenderState();
    }

    @Override
    public Identifier getTextureLocation(HumanoidRenderState state) {
        return TEXTURE;
    }
}

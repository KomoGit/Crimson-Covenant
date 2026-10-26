package com.zestudio.crimsoncovenant.client.renderer;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.client.ModModelLayers;
import com.zestudio.crimsoncovenant.client.model.HalloweenSpiritModel;
import com.zestudio.crimsoncovenant.entity.HalloweenSpirit;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

public class HalloweenSpiritRenderer extends MobRenderer<HalloweenSpirit, LivingEntityRenderState, HalloweenSpiritModel> {
    private static final Identifier TEXTURE = CrimsonCovenant.id("textures/entity/halloween_spirit.png");

    public HalloweenSpiritRenderer(EntityRendererProvider.Context context) {
        super(context, new HalloweenSpiritModel(context.bakeLayer(ModModelLayers.HALLOWEEN_SPIRIT)), 0.0F);
    }

    // It glows faintly in the dark, as ghosts do
    @Override
    protected int getBlockLightLevel(HalloweenSpirit entity, BlockPos blockPos) {
        return 13;
    }

    @Override
    public LivingEntityRenderState createRenderState() {
        return new LivingEntityRenderState();
    }

    @Override
    public Identifier getTextureLocation(LivingEntityRenderState state) {
        return TEXTURE;
    }
}

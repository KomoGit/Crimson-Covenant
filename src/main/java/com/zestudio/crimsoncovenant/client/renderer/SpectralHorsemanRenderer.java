package com.zestudio.crimsoncovenant.client.renderer;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.client.ModModelLayers;
import com.zestudio.crimsoncovenant.entity.SpectralHorseman;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

/// The Headless Horseman's ghost: his body drawn pale and see-through, glowing in the dark. His jack o'lantern and spear
/// are as solid as ever.
public class SpectralHorsemanRenderer extends HumanoidMobRenderer<SpectralHorseman, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
    private static final Identifier TEXTURE = CrimsonCovenant.id("textures/entity/spectral_horseman.png");

    public SpectralHorsemanRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModModelLayers.HUNTER)), 0.0F);
    }

    @Override
    public HumanoidRenderState createRenderState() {
        return new HumanoidRenderState();
    }

    @Override
    public Identifier getTextureLocation(HumanoidRenderState state) {
        return TEXTURE;
    }

    @Override
    protected RenderType getRenderType(HumanoidRenderState state, boolean isBodyVisible, boolean forceTransparent, boolean appearGlowing) {
        return RenderTypes.entityTranslucent(this.getTextureLocation(state));
    }

    @Override
    protected int getBlockLightLevel(SpectralHorseman entity, BlockPos blockPos) {
        return 12;
    }
}

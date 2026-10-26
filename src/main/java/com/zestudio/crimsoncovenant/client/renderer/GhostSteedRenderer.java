package com.zestudio.crimsoncovenant.client.renderer;

import com.zestudio.crimsoncovenant.CrimsonCovenant;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.UndeadHorseRenderer;
import net.minecraft.client.renderer.entity.state.EquineRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.animal.equine.AbstractHorse;

/// A Ghost Steed: a zombie horse's shape, pale and see-through, glowing in the dark
public class GhostSteedRenderer extends UndeadHorseRenderer {
    private static final Identifier TEXTURE = CrimsonCovenant.id("textures/entity/ghost_steed.png");

    public GhostSteedRenderer(EntityRendererProvider.Context context) {
        super(context, EquipmentClientInfo.LayerType.ZOMBIE_HORSE_SADDLE, ModelLayers.ZOMBIE_HORSE_SADDLE, UndeadHorseRenderer.Type.ZOMBIE,
                UndeadHorseRenderer.Type.ZOMBIE_BABY);
    }

    @Override
    public Identifier getTextureLocation(EquineRenderState state) {
        return TEXTURE;
    }

    @Override
    protected RenderType getRenderType(EquineRenderState state, boolean isBodyVisible, boolean forceTransparent, boolean appearGlowing) {
        return RenderTypes.entityTranslucent(this.getTextureLocation(state));
    }

    @Override
    protected int getBlockLightLevel(AbstractHorse entity, BlockPos blockPos) {
        return 12;
    }
}

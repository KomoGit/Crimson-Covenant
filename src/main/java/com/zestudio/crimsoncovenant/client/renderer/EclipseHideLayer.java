package com.zestudio.crimsoncovenant.client.renderer;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.client.ModModelLayers;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EnergySwirlLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;

/// Eclipse Hide on a werewolf player: a swirling shell of moonlight, the way Armor of Ancient wraps a vampire in blood
public class EclipseHideLayer extends EnergySwirlLayer<HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
    /// Set on the beast's render state from the synced ModAttachments.ECLIPSE_HIDE_VISIBLE (see PossessionRenderer)
    public static final ContextKey<Boolean> ACTIVE = new ContextKey<>(CrimsonCovenant.id("eclipse_hide"));
    private static final Identifier TEXTURE = CrimsonCovenant.id("textures/entity/eclipse_hide.png");
    private final HumanoidModel<HumanoidRenderState> model;

    public EclipseHideLayer(RenderLayerParent<HumanoidRenderState, HumanoidModel<HumanoidRenderState>> renderer, EntityModelSet modelSet) {
        super(renderer);
        this.model = new HumanoidModel<>(modelSet.bakeLayer(ModModelLayers.WEREWOLF_ECLIPSE_HIDE));
    }

    @Override
    protected boolean isPowered(HumanoidRenderState state) {
        return state.getRenderDataOrDefault(ACTIVE, false);
    }

    @Override
    protected float xOffset(float t) {
        return Mth.cos(t * 0.02F) * 3.0F;
    }

    @Override
    protected Identifier getTextureLocation() {
        return TEXTURE;
    }

    @Override
    protected HumanoidModel<HumanoidRenderState> model() {
        return this.model;
    }
}

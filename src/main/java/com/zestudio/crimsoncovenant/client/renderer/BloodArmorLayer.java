package com.zestudio.crimsoncovenant.client.renderer;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.client.ModModelLayers;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EnergySwirlLayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/// The Master Vampire's low-health shell: a swirling crimson energy layer, drawn the same way as the wither's armor
public class BloodArmorLayer extends EnergySwirlLayer<VampireRenderState, HumanoidModel<VampireRenderState>> {
    private static final Identifier TEXTURE = CrimsonCovenant.id("textures/entity/vampire/blood_armor.png");
    private final HumanoidModel<VampireRenderState> model;

    public BloodArmorLayer(RenderLayerParent<VampireRenderState, HumanoidModel<VampireRenderState>> renderer, EntityModelSet modelSet) {
        super(renderer);
        this.model = new HumanoidModel<>(modelSet.bakeLayer(ModModelLayers.VAMPIRE_BLOOD_ARMOR));
    }

    @Override
    protected boolean isPowered(VampireRenderState state) {
        return state.bloodArmor;
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
    protected HumanoidModel<VampireRenderState> model() {
        return this.model;
    }
}

package com.zestudio.crimsoncovenant.client.renderer;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.client.ModModelLayers;

import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EnergySwirlLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;

/// The Armor of Ancient skill on a player: the same swirling blood shell the Master Vampire wears at low health
public class AncientArmorLayer extends EnergySwirlLayer<AvatarRenderState, PlayerModel> {
    /// Set on each player's render state from the synced ModAttachments.ANCIENT_ARMOR_VISIBLE
    public static final ContextKey<Boolean> ACTIVE = new ContextKey<>(CrimsonCovenant.id("ancient_armor"));
    private static final Identifier TEXTURE = CrimsonCovenant.id("textures/entity/vampire/blood_armor.png");
    private final PlayerModel model;

    public AncientArmorLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer, EntityModelSet modelSet) {
        super(renderer);
        this.model = new PlayerModel(modelSet.bakeLayer(ModModelLayers.VAMPIRE_BLOOD_ARMOR), false);
    }

    @Override
    protected boolean isPowered(AvatarRenderState state) {
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
    protected PlayerModel model() {
        return this.model;
    }
}

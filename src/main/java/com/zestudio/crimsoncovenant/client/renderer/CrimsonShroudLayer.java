package com.zestudio.crimsoncovenant.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.client.ModModelLayers;
import com.zestudio.crimsoncovenant.client.model.BatWingsModel;

import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;

/// The Crimson Shroud (see effect.CrimsonShroudEffect) on a player: a giant bat's wings growing from their back,
/// over whatever they wear. Players with a real elytra on show that instead.
public class CrimsonShroudLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    /// Set on each player's render state from the synced ModAttachments.CRIMSON_SHROUD_WORN
    public static final ContextKey<Boolean> ACTIVE = new ContextKey<>(CrimsonCovenant.id("crimson_shroud"));
    private static final Identifier TEXTURE = CrimsonCovenant.id("textures/entity/crimson_shroud_wings.png");
    private final BatWingsModel wings;

    public CrimsonShroudLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer, EntityModelSet modelSet) {
        super(renderer);
        this.wings = new BatWingsModel(modelSet.bakeLayer(ModModelLayers.BAT_WINGS));
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
        if (!state.getRenderDataOrDefault(ACTIVE, false) || state.chestEquipment.has(DataComponents.GLIDER)) {
            return;
        }
        poseStack.pushPose();
        // Rooted in the back, so they lean and turn with it
        this.getParentModel().body.translateAndRotate(poseStack);
        submitNodeCollector.submitModel(this.wings, state, poseStack, TEXTURE, lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
    }
}

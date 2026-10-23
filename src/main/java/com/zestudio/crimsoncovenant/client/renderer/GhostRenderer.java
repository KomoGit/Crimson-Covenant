package com.zestudio.crimsoncovenant.client.renderer;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.client.ModModelLayers;
import com.zestudio.crimsoncovenant.client.model.GhostModel;
import com.zestudio.crimsoncovenant.entity.Ghost;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

public class GhostRenderer extends MobRenderer<Ghost, GhostRenderState, GhostModel> {
    private static final Identifier TEXTURE = CrimsonCovenant.id("textures/entity/ghost.png");

    public GhostRenderer(EntityRendererProvider.Context context) {
        super(context, new GhostModel(context.bakeLayer(ModModelLayers.GHOST)), 0.3F);
    }

    // Ghosts glow faintly in the dark, like vexes
    @Override
    protected int getBlockLightLevel(Ghost entity, BlockPos blockPos) {
        return 12;
    }

    @Override
    public GhostRenderState createRenderState() {
        return new GhostRenderState();
    }

    @Override
    public void extractRenderState(Ghost ghost, GhostRenderState state, float partialTicks) {
        super.extractRenderState(ghost, state, partialTicks);
        state.swooping = ghost.isSwooping();
    }

    @Override
    public Identifier getTextureLocation(GhostRenderState state) {
        return TEXTURE;
    }
}

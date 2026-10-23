package com.zestudio.crimsoncovenant.client.renderer;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.entity.Hunter;
import com.zestudio.crimsoncovenant.registry.ModEffects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

/// Stalker's Gaze: while the effect is on, every hostile creature near the player (monsters, and hunters) is drawn
/// with a blood-red outline that shows through walls, like the glow from a spectral arrow. Only on that player's
/// own screen: nobody else sees the outlines.
@EventBusSubscriber(modid = CrimsonCovenant.MODID, value = Dist.CLIENT)
public final class StalkersGazeHighlight {
    private static final double RANGE = 48.0;
    private static final int OUTLINE_COLOR = ARGB.opaque(0xD01020);

    private StalkersGazeHighlight() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    @SubscribeEvent
    static void registerRenderStateModifiers(RegisterRenderStateModifiersEvent event) {
        // Every living creature's renderer is a LivingEntityRenderer, and the modifier carries over to subclasses
        event.registerEntityModifier((Class) LivingEntityRenderer.class, (entity, state) -> highlight((LivingEntity) entity, (LivingEntityRenderState) state));
    }

    private static void highlight(LivingEntity entity, LivingEntityRenderState state) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && entity != player && player.hasEffect(ModEffects.STALKERS_GAZE) && isPrey(entity)
                && entity.distanceToSqr(player) <= RANGE * RANGE) {
            state.outlineColor = OUTLINE_COLOR;
        }
    }

    private static boolean isPrey(LivingEntity entity) {
        return entity.isAlive() && (entity instanceof Enemy || entity instanceof Hunter);
    }
}

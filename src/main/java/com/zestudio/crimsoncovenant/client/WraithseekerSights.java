package com.zestudio.crimsoncovenant.client;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.curse.Curse;
import com.zestudio.crimsoncovenant.curse.Curses;
import com.zestudio.crimsoncovenant.registry.ModAttachments;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

/// Wraithseeker's sights: when the player's Wraithseeker arrows mark something (see Curses.mark), a soft chime, and its
/// name above the hotbar; and while they hold a bow bearing the haunting, the mark is outlined in the hauntings' cold
/// blue, through walls and all. Only on that player's own screen.
@EventBusSubscriber(modid = CrimsonCovenant.MODID, value = Dist.CLIENT)
public final class WraithseekerSights {
    private static final int OUTLINE_COLOR = ARGB.opaque(Curse.COLOUR);
    private static int lastMarkId = -1;
    private static int outlinedId = -1;

    private WraithseekerSights() {}

    @SubscribeEvent
    static void track(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            lastMarkId = -1;
            outlinedId = -1;
            return;
        }
        int markId = player.getData(ModAttachments.WRAITHSEEKER_MARK);
        LivingEntity mark = Curses.markOf(player);
        if (markId != lastMarkId && mark != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.6F, 0.8F));
            player.sendOverlayMessage(Component.translatable("message.crimsoncovenant.curse.wraithseeker_lock", mark.getDisplayName()).withColor(Curse.COLOUR));
        }
        lastMarkId = markId;
        outlinedId = mark != null && holdingWraithseeker(player) ? mark.getId() : -1;
    }

    private static boolean holdingWraithseeker(LocalPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            if (Curses.looseLevel(player, Curse.TRACING_SHOT, player.getItemInHand(hand)) > 0) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @SubscribeEvent
    static void registerRenderStateModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier((Class) LivingEntityRenderer.class, (entity, state) -> {
            if (((LivingEntity) entity).getId() == outlinedId) {
                ((LivingEntityRenderState) state).outlineColor = OUTLINE_COLOR;
            }
        });
    }
}

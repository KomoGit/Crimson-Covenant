package com.zestudio.crimsoncovenant.client;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.network.SkillPayloads;
import com.zestudio.crimsoncovenant.registry.ModAttachments;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/// While a player possesses a creature, the attack button is the creature's: instead of a normal swing (or
/// digging), pressing and releasing it is sent to the server, which bites, draws a bow, shoots... as the creature
/// would (see skill.CreatureAbilities).
@EventBusSubscriber(modid = CrimsonCovenant.MODID, value = Dist.CLIENT)
public final class PossessionInput {
    private static boolean attackWasDown = false;

    private PossessionInput() {}

    private static boolean isPossessing(LocalPlayer player) {
        return player.getData(ModAttachments.POSSESSED_FORM).isPresent();
    }

    // No ordinary swings or digging while possessing
    @SubscribeEvent
    static void blockNormalAttacks(InputEvent.InteractionKeyMappingTriggered event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (event.isAttack() && player != null && isPossessing(player)) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    @SubscribeEvent
    static void sendAttackButton(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        boolean down = player != null && isPossessing(player) && minecraft.gui.screen() == null && minecraft.options.keyAttack.isDown();
        if (down && !attackWasDown) {
            int target = minecraft.hitResult instanceof EntityHitResult hit ? hit.getEntity().getId() : -1;
            ClientPacketDistributor.sendToServer(new SkillPayloads.PossessedAttack(true, target));
        } else if (!down && attackWasDown && player != null) {
            ClientPacketDistributor.sendToServer(new SkillPayloads.PossessedAttack(false, -1));
        }
        attackWasDown = down;
    }
}

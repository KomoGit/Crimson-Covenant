package com.zestudio.crimsoncovenant.client;

import java.util.Optional;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.effect.FlightLift;
import com.zestudio.crimsoncovenant.registry.ModAttachments;
import com.zestudio.crimsoncovenant.registry.ModAttachments.Lift;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/// Holds each creature a flying vampire has snatched up (see effect.FlightLift) in their hands, once everything's
/// ticked: where the server last put it is a tick or more behind a vampire flying flat out, and it would trail behind.
@EventBusSubscriber(modid = CrimsonCovenant.MODID, value = Dist.CLIENT)
public final class LiftedHold {
    private LiftedHold() {}

    @SubscribeEvent
    static void hold(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || Minecraft.getInstance().isPaused()) {
            return;
        }
        for (Entity entity : level.entitiesForRendering()) {
            // A carried player is pulled along by the server instead
            if (!(entity instanceof LivingEntity held) || held instanceof Player) {
                continue;
            }
            Optional<Lift> lift = held.getData(ModAttachments.LIFTED);
            if (lift.isEmpty()) {
                continue;
            }
            for (AbstractClientPlayer carrier : level.players()) {
                if (carrier.getUUID().equals(lift.get().carrier())) {
                    FlightLift.hold(held, carrier, true);
                    break;
                }
            }
        }
    }
}

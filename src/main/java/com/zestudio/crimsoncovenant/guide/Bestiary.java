package com.zestudio.crimsoncovenant.guide;

import java.util.ArrayList;
import java.util.List;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.registry.ModAttachments;
import com.zestudio.crimsoncovenant.registry.ModTags;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/// The guide's bestiary: a creature of the night (those in the bestiary tag) gets its entry once a player has seen it,
/// near enough (16 blocks) and in plain sight
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public final class Bestiary {
    public static final double SIGHT_RANGE = 16.0;
    private static final int LOOK_INTERVAL = 20;

    private Bestiary() {}

    public static boolean hasSeen(Player player, EntityType<?> type) {
        return player.getData(ModAttachments.ENCOUNTERED).contains(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
    }

    @SubscribeEvent
    static void lookAroundEverySecond(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % LOOK_INTERVAL == 0 && !player.isSpectator()) {
            lookAround(player);
        }
    }

    /// Anything of the night the player can see, near enough, goes into their guide
    public static void lookAround(ServerPlayer player) {
        for (LivingEntity creature : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(SIGHT_RANGE),
                entity -> entity.is(ModTags.Entities.BESTIARY) && entity.isAlive() && !hasSeen(player, entity.getType()))) {
            if (creature.distanceToSqr(player) <= SIGHT_RANGE * SIGHT_RANGE && player.hasLineOfSight(creature)) {
                encounter(player, creature.getType());
            }
        }
    }

    /// The creature's entry goes into the player's guide
    public static void encounter(ServerPlayer player, EntityType<?> type) {
        if (hasSeen(player, type)) {
            return;
        }
        List<String> seen = new ArrayList<>(player.getData(ModAttachments.ENCOUNTERED));
        seen.add(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
        player.setData(ModAttachments.ENCOUNTERED, List.copyOf(seen));
        player.sendOverlayMessage(Component.translatable("message.crimsoncovenant.guide.new_entry", GuideBooks.bookFor(player).title(), type.getDescription())
                .withStyle(ChatFormatting.GOLD));
        player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 1.0F);
    }
}

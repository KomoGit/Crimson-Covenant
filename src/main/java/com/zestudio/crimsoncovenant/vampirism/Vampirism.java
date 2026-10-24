package com.zestudio.crimsoncovenant.vampirism;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.entity.Gargoyle;
import com.zestudio.crimsoncovenant.entity.MasterVampire;
import com.zestudio.crimsoncovenant.entity.Vampire;
import com.zestudio.crimsoncovenant.registry.ModAttachments;
import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.skill.BloodSkills;
import com.zestudio.crimsoncovenant.world.BloodMoon;
import com.zestudio.crimsoncovenant.hunter.HunterRanks;
import com.zestudio.crimsoncovenant.block.CoffinBlock;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/// Player vampirism: infection by vampires, the 3-day countdown, and life as a vampire.
/// Dying while infected skips the rest of the countdown. Vampire players have a blood bar (see BloodPool) to pay
/// for blood skills with.
///
/// The infection is tracked in a player attachment, and the Sanguine Vampiris effect just mirrors it as a
/// visible countdown. If the effect gets removed (milk, death) it is put back. Giving yourself the effect
/// with /effect also starts a real infection, which is handy for testing.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public final class Vampirism {
    /// 3 Minecraft days
    public static final int INFECTION_DURATION = 3 * 24000;
    private static final int PERK_REFRESH_INTERVAL = 20;
    /// How far away vampires will come to help a vampire player who's attacked
    private static final double AID_RANGE = 24.0;

    private Vampirism() {}

    public static boolean isVampire(Player player) {
        return player.getData(ModAttachments.IS_VAMPIRE);
    }

    /// Whether a vampire can drink from this: only the living. Not the undead (other vampires included), and not
    /// armor stands or stone gargoyles.
    /// Undead creatures, and vampire players with them
    public static boolean isUndead(LivingEntity entity) {
        return entity.is(EntityTypeTags.UNDEAD) || (entity instanceof Player player && isVampire(player));
    }

    public static boolean hasLivingBlood(LivingEntity entity) {
        return !entity.is(EntityTypeTags.UNDEAD) && !(entity instanceof ArmorStand) && !(entity instanceof Gargoyle) && !(entity instanceof Player player && isVampire(player));
    }

    public static boolean isInfected(Player player) {
        return player.getData(ModAttachments.INFECTION_TICKS) > 0;
    }

    /// Infected or fully turned. Hunters attack players like this on sight.
    public static boolean isTainted(Player player) {
        return isVampire(player) || isInfected(player) || player.hasEffect(ModEffects.SANGUINE_VAMPIRIS);
    }

    public static void infect(Player player, int ticks) {
        if (!isVampire(player)) {
            player.setData(ModAttachments.INFECTION_TICKS, ticks);
        }
    }

    public static void turn(ServerPlayer player) {
        turn(player, "message.crimsoncovenant.turned");
    }

    private static void turn(ServerPlayer player, String messageKey) {
        player.setData(ModAttachments.INFECTION_TICKS, 0);
        player.removeEffect(ModEffects.SANGUINE_VAMPIRIS);
        player.setData(ModAttachments.IS_VAMPIRE, true);
        player.sendSystemMessage(Component.translatable(messageKey).withStyle(ChatFormatting.DARK_RED));
        HunterRanks.castOut(player);
        player.level().playSound(null, player.blockPosition(), SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.PLAYERS, 1.0F, 0.6F);
    }

    public static void cure(Player player) {
        player.setData(ModAttachments.INFECTION_TICKS, 0);
        player.setData(ModAttachments.IS_VAMPIRE, false);
        player.setData(ModAttachments.BLOOD, 0);
        player.removeEffect(ModEffects.SANGUINE_VAMPIRIS);
    }

    /// Vampires look after their own: hurt a vampire player and every vampire nearby turns on you
    @SubscribeEvent
    static void onVampireHurt(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && isVampire(player) && event.getSource().getEntity() instanceof LivingEntity attacker) {
            callForAid(player, attacker);
        }
    }

    /// Sends every vampire within range after whoever attacked the vampire player (unless that's a vampire too)
    public static void callForAid(ServerPlayer player, LivingEntity attacker) {
        if (attacker == player || attacker instanceof Vampire || (attacker instanceof Player other && isVampire(other))) {
            return;
        }
        // Master vampires answer to no one, and don't come to anyone's aid
        for (Vampire vampire : player.level().getEntitiesOfClass(Vampire.class, player.getBoundingBox().inflate(AID_RANGE),
                vampire -> vampire.isAlive() && !(vampire instanceof MasterVampire))) {
            if (vampire.getTarget() != attacker) {
                vampire.setTarget(attacker);
            }
        }
    }

    @SubscribeEvent
    static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || isVampire(player)) {
            return;
        }
        // Dying with the curse in your blood cuts the wait short: you rise as a vampire.
        // Otherwise, players killed by a vampire (bites or arrows) wake up infected. The data survives death.
        if (isInfected(player) || player.hasEffect(ModEffects.SANGUINE_VAMPIRIS)) {
            turn(player, "message.crimsoncovenant.turned_in_death");
        } else if (event.getSource().getEntity() instanceof Vampire) {
            infect(player, INFECTION_DURATION);
        }
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!event.isEndConquered() && isInfected(event.getEntity())) {
            event.getEntity().sendSystemMessage(Component.translatable("message.crimsoncovenant.infected").withStyle(ChatFormatting.DARK_RED));
        }
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.isAlive()) {
            BloodSkills.tickDash(player);
            boolean armored = player.hasEffect(ModEffects.ANCIENT_ARMOR);
            if (player.getData(ModAttachments.ANCIENT_ARMOR_VISIBLE) != armored) {
                player.setData(ModAttachments.ANCIENT_ARMOR_VISIBLE, armored);
            }
            boolean shrouded = player.hasEffect(ModEffects.CRIMSON_SHROUD);
            if (player.getData(ModAttachments.CRIMSON_SHROUD_WORN) != shrouded) {
                player.setData(ModAttachments.CRIMSON_SHROUD_WORN, shrouded);
            }
            tickInfection(player);
            if (isVampire(player)) {
                tickVampire(player);
            }
        }
    }

    public static void tickInfection(ServerPlayer player) {
        MobEffectInstance effect = player.getEffect(ModEffects.SANGUINE_VAMPIRIS);
        int ticksLeft = player.getData(ModAttachments.INFECTION_TICKS);

        // Effect given some other way (e.g. /effect) without an infection: start one that matches it
        if (ticksLeft <= 0) {
            if (effect != null && !effect.isInfiniteDuration() && !isVampire(player)) {
                ticksLeft = effect.getDuration();
            } else {
                return;
            }
        }

        ticksLeft--;
        if (ticksLeft <= 0) {
            turn(player);
            return;
        }
        player.setData(ModAttachments.INFECTION_TICKS, ticksLeft);
        if (effect == null) {
            player.addEffect(new MobEffectInstance(ModEffects.SANGUINE_VAMPIRIS, ticksLeft));
        }
    }

    public static void tickVampire(ServerPlayer player) {
        BloodPool.tick(player);
        BloodSkills.showAnchor(player);
        GarlicWard.tickVampirePlayer(player);
        // Vampires don't hunger: blood is all they need. Food stays full (so they can sprint and heal like anyone
        // with a full belly) but without saturation, which would heal them much faster.
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(0.0F);
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        if (!player.hasEffect(ModEffects.DAYWALKER) && isInSunlight(player)) {
            burnInSunlight(player);
        }
        if (player.tickCount % PERK_REFRESH_INTERVAL == 0) {
            BloodMoon.tickBloodlust(player);
            // Night vision lasts over 10 seconds so it never starts flickering, the night buffs just get topped up
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, true, false, true));
            if (player.level().isDarkOutside()) {
                player.addEffect(new MobEffectInstance(MobEffects.SPEED, PERK_REFRESH_INTERVAL * 2, 0, true, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, PERK_REFRESH_INTERVAL * 2, 0, true, false, true));
            }
        }
    }

    /// Same rules vanilla uses for zombies and skeletons, randomized per tick so it doesn't ignite instantly.
    public static boolean isInSunlight(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level) || !level.environmentAttributes().getValue(EnvironmentAttributes.MONSTERS_BURN, entity.position())) {
            return false;
        }
        // Asleep in a coffin, the sun can't reach
        if (CoffinBlock.isInCoffin(entity)) {
            return false;
        }
        float brightness = entity.getLightLevelDependentMagicValue();
        BlockPos eyePos = BlockPos.containing(entity.getX(), entity.getEyeY(), entity.getZ());
        boolean protectedByWeather = entity.isInWaterOrRain() || entity.isInPowderSnow || entity.wasInPowderSnow;
        return brightness > 0.5F
                && entity.getRandom().nextFloat() * 30.0F < (brightness - 0.4F) * 2.0F
                && !protectedByWeather
                && level.canSeeSky(eyePos);
    }

    // Like undead mobs, a helmet takes the sun damage instead
    private static void burnInSunlight(ServerPlayer player) {
        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!helmet.isEmpty()) {
            if (helmet.isDamageableItem()) {
                helmet.hurtAndBreak(player.getRandom().nextInt(2), player, EquipmentSlot.HEAD);
            }
        } else {
            player.igniteForSeconds(8.0F);
        }
    }
}

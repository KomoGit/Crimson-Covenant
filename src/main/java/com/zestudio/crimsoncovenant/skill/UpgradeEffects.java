package com.zestudio.crimsoncovenant.skill;

import java.util.Optional;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.entity.BloodOrb;
import com.zestudio.crimsoncovenant.entity.Hunter;
import com.zestudio.crimsoncovenant.entity.Vampire;
import com.zestudio.crimsoncovenant.registry.ModAttachments;
import com.zestudio.crimsoncovenant.registry.ModDamageTypes;
import com.zestudio.crimsoncovenant.registry.ModDataComponents;
import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.registry.ModItems;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/// The Sanguine Table upgrades that work through what happens in the world rather than at the moment of casting:
/// Blood Wings' landing, Final Feast, Thirsting Blade, Grave March and Marked Prey. (See SkillUpgrade for the full
/// list; the rest live with their skills.)
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public final class UpgradeEffects {
    // Blood Wings: the slam at the end of the dive, stronger the further the fall
    private static final double DIVE_RADIUS = 3.5;
    private static final float MIN_DIVE_DAMAGE = 2.0F;
    private static final float MAX_DIVE_DAMAGE = 12.0F;
    /// Final Feast: how long after a defied death the healing blow can come
    public static final int FINAL_FEAST_WINDOW = 100;
    /// Thirsting Blade: what each kill adds to the Ephemeral Sword's time
    public static final int THIRSTING_BLADE_TICKS = 200;
    /// Grave March: how long the risen serve
    public static final int RISEN_SERVICE = 600;
    /// Marked Prey: extra damage to outlined creatures
    public static final float MARKED_PREY_BONUS = 1.2F;
    private static final double MARKED_PREY_RANGE = 48.0;

    private UpgradeEffects() {}

    // ---- Blood Wings ----

    /// Landing from a Blood Wings flight: no fall damage for the vampire, a slam for everything hostile around them
    @SubscribeEvent
    static void bloodWingsLanding(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.getData(ModAttachments.BLOOD_WINGS_DIVE)) {
            return;
        }
        player.removeData(ModAttachments.BLOOD_WINGS_DIVE);
        event.setDamageMultiplier(0.0F);
        ServerLevel level = player.level();
        float damage = Mth.clamp((float) event.getDistance(), MIN_DIVE_DAMAGE, MAX_DIVE_DAMAGE);
        DamageSource source = level.damageSources().source(ModDamageTypes.BLOOD_MAGIC, player);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(DIVE_RADIUS),
                entity -> BloodOrb.isHostile(entity, player) && entity.distanceToSqr(player) <= DIVE_RADIUS * DIVE_RADIUS)) {
            victim.hurtServer(level, source, damage);
        }
        BlockState ground = level.getBlockState(player.getOnPos());
        if (!ground.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), player.getX(), player.getY(), player.getZ(), 60, 1.2, 0.1, 1.2, 0.15);
        }
        level.sendParticles(new DustParticleOptions(Vampire.BLOOD_COLOR, 2.0F), player.getX(), player.getY() + 0.2, player.getZ(), 40, 1.5, 0.2, 1.5, 0.0);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.0F, 0.8F);
    }

    // ---- Final Feast ----

    /// Called when Defy Death saves a vampire: with the upgrade (amplifier 1), their next blow will heal them
    public static void defiedDeath(ServerPlayer player, MobEffectInstance defyDeath) {
        if (defyDeath.getAmplifier() >= 1) {
            player.setData(ModAttachments.FINAL_FEAST_UNTIL, player.level().getGameTime() + FINAL_FEAST_WINDOW);
        }
    }

    @SubscribeEvent
    static void finalFeast(LivingDamageEvent.Post event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || event.getSource().getDirectEntity() != player
                || player.getData(ModAttachments.FINAL_FEAST_UNTIL) < player.level().getGameTime()) {
            return;
        }
        player.removeData(ModAttachments.FINAL_FEAST_UNTIL);
        player.heal(event.getHealthDamage());
        Vampire.streamBlood(player.level(), event.getEntity(), player);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_DRINK.value(), SoundSource.PLAYERS, 1.0F, 0.6F);
    }

    // ---- Thirsting Blade, Grave March ----

    @SubscribeEvent
    static void onKill(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) {
            return;
        }
        // Thirsting Blade: a kill with the upgraded Ephemeral Sword keeps it 10 seconds longer
        if (event.getSource().getEntity() instanceof Player killer && event.getSource().getDirectEntity() == killer) {
            ItemStack sword = killer.getMainHandItem();
            Long expiresAt = sword.get(ModDataComponents.EXPIRES_AT.get());
            if (sword.is(ModItems.EPHEMERAL_SWORD.get()) && SkillUpgrade.isUpgraded(sword) && expiresAt != null) {
                sword.set(ModDataComponents.EXPIRES_AT.get(), expiresAt + THIRSTING_BLADE_TICKS);
                level.sendParticles(new DustParticleOptions(Vampire.BLOOD_COLOR, 1.2F), killer.getX(), killer.getY(0.6), killer.getZ(), 12, 0.3, 0.3, 0.3, 0.0);
            }
        }
        // Grave March: dying on the march, it rises to serve the vampire who sent it
        if (victim instanceof Mob mob && DeathMarch.isMarching(mob)) {
            Optional<ModAttachments.DeathMarch> march = mob.getData(ModAttachments.DEATH_MARCH);
            march.flatMap(ModAttachments.DeathMarch::raiser).map(level::getPlayerByUUID).ifPresent(raiser -> raise(level, mob, raiser));
        }
    }

    private static void raise(ServerLevel level, Mob dead, Player raiser) {
        Zombie risen = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (risen == null) {
            return;
        }
        risen.snapTo(dead.getX(), dead.getY(), dead.getZ(), dead.getYRot(), 0.0F);
        risen.finalizeSpawn(level, level.getCurrentDifficultyAt(dead.blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
        risen.addTag(Enthrall.RISEN_TAG);
        level.addFreshEntity(risen);
        Enthrall.enthrall(level, risen, raiser, RISEN_SERVICE);
        level.sendParticles(ParticleTypes.SOUL, risen.getX(), risen.getY(0.5), risen.getZ(), 20, 0.3, 0.6, 0.3, 0.02);
        level.playSound(null, risen.getX(), risen.getY(), risen.getZ(), SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.HOSTILE, 0.8F, 0.6F);
    }

    // ---- Marked Prey ----

    /// What Stalker's Gaze outlines (see StalkersGazeHighlight): monsters and hunters
    public static boolean isGazeTarget(LivingEntity entity) {
        return entity instanceof Enemy || entity instanceof Hunter;
    }

    @SubscribeEvent
    static void markedPrey(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof Player player && isGazeTarget(event.getEntity())
                && player.distanceTo(event.getEntity()) <= MARKED_PREY_RANGE) {
            MobEffectInstance gaze = player.getEffect(ModEffects.STALKERS_GAZE);
            if (gaze != null && gaze.getAmplifier() >= 1) {
                event.setAmount(event.getAmount() * MARKED_PREY_BONUS);
            }
        }
    }
}

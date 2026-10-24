package com.zestudio.crimsoncovenant.skill;

import java.util.Optional;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.entity.Hunter;
import com.zestudio.crimsoncovenant.entity.Vampire;
import com.zestudio.crimsoncovenant.registry.ModAttachments;
import com.zestudio.crimsoncovenant.registry.ModDataComponents;
import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.registry.ModItems;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/// The skill upgrades that work through what happens in the world rather than at the moment of casting:
/// Final Feast, Thirsting Blade, Grave March and Marked Prey. (See SkillUpgrade for the full
/// list; the rest live with their skills.)
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public final class UpgradeEffects {
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

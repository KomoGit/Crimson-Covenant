package com.zestudio.crimsoncovenant.skill;

import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.registry.ModAttachments;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/// The Enthrall upgrade to Possess: when a possession runs its course, the creature the vampire leaves behind is
/// still under their spell for a minute. It won't turn on its master, follows them about, and attacks whatever
/// hurts the master or whatever the master attacks. Once the minute is up (or the master is gone) it's itself again.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public final class Enthrall {
    public static final int DURATION = 1200;
    private static final int THINK_INTERVAL = 10;
    private static final double ENEMY_RANGE = 24.0;
    private static final double FOLLOW_DISTANCE = 6.0;
    private static final double FOLLOW_SPEED = 1.2;

    /// Who a thrall serves, and the game time it stops
    public record Thrall(UUID master, long until) {}

    private Enthrall() {}

    /// Put on the dead a Grave March raises: when their service ends, they crumble away
    public static final String RISEN_TAG = "crimsoncovenant.risen";

    public static void enthrall(ServerLevel level, Mob mob, Player master) {
        enthrall(level, mob, master, DURATION);
    }

    public static void enthrall(ServerLevel level, Mob mob, Player master, int ticks) {
        mob.setData(ModAttachments.THRALL, Optional.of(new Thrall(master.getUUID(), level.getGameTime() + ticks)));
        mob.setTarget(null);
        level.sendParticles(ParticleTypes.SCULK_SOUL, mob.getX(), mob.getY(1.0) + 0.3, mob.getZ(), 12, 0.3, 0.3, 0.3, 0.02);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0F, 0.6F);
    }

    public static Optional<Thrall> thrall(Entity entity) {
        return entity.hasData(ModAttachments.THRALL) ? entity.getData(ModAttachments.THRALL) : Optional.empty();
    }

    public static boolean isThrallOf(Entity entity, @Nullable Entity master) {
        return master != null && thrall(entity).map(thrall -> thrall.master().equals(master.getUUID())).orElse(false);
    }

    /// A thrall never takes its master, or a fellow thrall, for an enemy
    @SubscribeEvent
    static void spareTheMaster(LivingChangeTargetEvent event) {
        LivingEntity target = event.getNewAboutToBeSetTarget();
        Optional<Thrall> thrall = thrall(event.getEntity());
        if (target != null && thrall.isPresent()
                && (target.getUUID().equals(thrall.get().master()) || thrall(target).map(other -> other.master().equals(thrall.get().master())).orElse(false))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void serve(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level) || mob.tickCount % THINK_INTERVAL != 0) {
            return;
        }
        Optional<Thrall> thrall = thrall(mob);
        if (thrall.isEmpty()) {
            return;
        }
        Player master = level.getPlayerByUUID(thrall.get().master());
        if (master == null || !master.isAlive() || level.getGameTime() >= thrall.get().until() || !mob.isAlive()) {
            release(level, mob);
            return;
        }
        LivingEntity enemy = enemyOf(master, mob);
        if (enemy != null && mob.getTarget() != enemy) {
            mob.setTarget(enemy);
        } else if (enemy == null && mob.getTarget() == null && mob.distanceTo(master) > FOLLOW_DISTANCE) {
            mob.getNavigation().moveTo(master, FOLLOW_SPEED);
        }
        if (mob.tickCount % 40 == 0) {
            level.sendParticles(new DustParticleOptions(0x9B0A14, 1.0F), mob.getX(), mob.getY(1.0) + 0.4, mob.getZ(), 4, 0.2, 0.1, 0.2, 0.0);
        }
    }

    /// Whatever last hurt the master, or else whatever the master last hurt, if it's still about
    private static @Nullable LivingEntity enemyOf(Player master, Mob thrall) {
        for (LivingEntity candidate : new LivingEntity[] {master.getLastHurtByMob(), master.getLastHurtMob()}) {
            if (candidate != null && candidate != thrall && candidate.isAlive() && candidate.distanceTo(master) <= ENEMY_RANGE
                    && !isThrallOf(candidate, master)) {
                return candidate;
            }
        }
        return null;
    }

    private static void release(ServerLevel level, Mob mob) {
        mob.removeData(ModAttachments.THRALL);
        if (mob.entityTags().contains(RISEN_TAG)) {
            level.sendParticles(ParticleTypes.SOUL, mob.getX(), mob.getY(0.5), mob.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
            mob.discard();
            return;
        }
        mob.setTarget(null);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, mob.getX(), mob.getY(1.0), mob.getZ(), 8, 0.2, 0.2, 0.2, 0.01);
    }
}

package com.zestudio.crimsoncovenant.skill;

import java.util.EnumSet;
import java.util.Optional;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.registry.ModAttachments;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/// The Death March skill: the creature the vampire looks at walks straight ahead for 5 seconds and does nothing
/// else. It doesn't stop for walls, fights, or cliffs.
///
/// Like Night of Terror, the marching is a top-priority goal every mob carries, which only does anything while the
/// mob is under a Death March.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public final class DeathMarch {
    public static final int DURATION = 100;
    private static final double REACH = 24.0;

    private DeathMarch() {}

    static boolean tryCast(ServerPlayer player) {
        Mob target = Possession.lookedAt(player, Mob.class, REACH);
        if (target == null) {
            BloodSkills.refuse(player, Component.translatable("message.crimsoncovenant.skill.nothing_to_march"));
            return false;
        }
        if (target.is(Tags.EntityTypes.BOSSES)) {
            BloodSkills.refuse(player, Component.translatable("message.crimsoncovenant.skill.cannot_march", target.getDisplayName()));
            return false;
        }
        march(target);
        BloodSkills.playSound(player, SoundEvents.EVOKER_CAST_SPELL, 0.6F);
        return true;
    }

    /// Sets the mob marching the way it's facing
    public static void march(Mob mob) {
        Vec3 facing = Vec3.directionFromRotation(0.0F, mob.getYRot());
        mob.setData(ModAttachments.DEATH_MARCH, Optional.of(new ModAttachments.DeathMarch(facing, mob.level().getGameTime() + DURATION)));
        mob.setTarget(null);
        if (mob.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SOUL, mob.getX(), mob.getY(1.0) + 0.2, mob.getZ(), 10, 0.2, 0.2, 0.2, 0.02);
        }
    }

    public static boolean isMarching(Mob mob) {
        Optional<ModAttachments.DeathMarch> march = mob.getData(ModAttachments.DEATH_MARCH);
        if (march.isEmpty()) {
            return false;
        }
        if (mob.level().getGameTime() >= march.get().until()) {
            mob.setData(ModAttachments.DEATH_MARCH, Optional.empty());
            return false;
        }
        return true;
    }

    @SubscribeEvent
    static void addMarchGoal(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof PathfinderMob mob) {
            mob.goalSelector.addGoal(-2, new MarchGoal(mob));
        }
    }

    /// Walks straight on, a couple of blocks ahead at a time, with nothing to turn it aside
    static class MarchGoal extends Goal {
        private final PathfinderMob mob;

        MarchGoal(PathfinderMob mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return isMarching(this.mob);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            this.mob.setTarget(null);
            this.mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            this.mob.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            this.mob.getNavigation().stop();
            Vec3 direction = this.mob.getData(ModAttachments.DEATH_MARCH).map(ModAttachments.DeathMarch::direction).orElse(Vec3.ZERO);
            Vec3 ahead = this.mob.position().add(direction.scale(2.0));
            // Straight at a point ahead, not along a path: so nothing is walked around, and no edge stops it
            this.mob.getMoveControl().setWantedPosition(ahead.x, this.mob.getY(), ahead.z, 1.0);
            this.mob.getLookControl().setLookAt(ahead.x, this.mob.getEyeY(), ahead.z);
            if (this.mob.tickCount % 10 == 0 && this.mob.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.SOUL, this.mob.getX(), this.mob.getY(1.0) + 0.2, this.mob.getZ(), 1, 0.1, 0.1, 0.1, 0.0);
            }
        }
    }
}

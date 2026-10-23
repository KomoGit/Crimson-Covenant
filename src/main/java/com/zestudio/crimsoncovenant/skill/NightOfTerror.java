package com.zestudio.crimsoncovenant.skill;

import java.util.EnumSet;
import java.util.Optional;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.registry.ModAttachments;
import com.zestudio.crimsoncovenant.registry.ModAttachments.Terror;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/// The Night of Terror skill: every creature within 50 blocks (bosses aside, and the vampire's own pets) drops
/// what it was doing and runs from the vampire for 5 seconds.
///
/// The running away is a top-priority goal given to every mob, which only does anything while the mob is
/// terrified. Since it takes over movement, nothing can chase or attack while it runs.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public final class NightOfTerror {
    public static final double RADIUS = 50.0;
    public static final int DURATION = 100;

    private NightOfTerror() {}

    public static void unleash(ServerLevel level, ServerPlayer player) {
        Terror terror = new Terror(player.position(), level.getGameTime() + DURATION);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(RADIUS), mob -> canBeTerrified(mob, player))) {
            mob.setData(ModAttachments.TERROR, Optional.of(terror));
            mob.setTarget(null);
            level.sendParticles(ParticleTypes.SCULK_SOUL, mob.getX(), mob.getY(1.0) + 0.3, mob.getZ(), 3, 0.2, 0.2, 0.2, 0.02);
        }
        level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY(0.5), player.getZ(), 60, 1.5, 1.0, 1.5, 0.05);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 3.0F, 0.7F);
    }

    private static boolean canBeTerrified(Mob mob, ServerPlayer player) {
        return mob.isAlive() && mob.distanceTo(player) <= RADIUS && !mob.is(Tags.EntityTypes.BOSSES)
                && !(mob instanceof OwnableEntity pet && pet.getOwner() == player);
    }

    public static boolean isTerrified(Mob mob) {
        Optional<Terror> terror = mob.getData(ModAttachments.TERROR);
        if (terror.isEmpty()) {
            return false;
        }
        if (mob.level().getGameTime() >= terror.get().until()) {
            mob.setData(ModAttachments.TERROR, Optional.empty());
            return false;
        }
        return true;
    }

    @SubscribeEvent
    static void addFleeGoal(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof PathfinderMob mob) {
            mob.goalSelector.addGoal(-1, new FleeInTerrorGoal(mob));
        }
    }

    /// Runs from the vampire for as long as the terror lasts
    static class FleeInTerrorGoal extends Goal {
        private static final double SPEED = 1.5;
        private final PathfinderMob mob;

        FleeInTerrorGoal(PathfinderMob mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return isTerrified(this.mob);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            this.mob.setTarget(null);
            // Mobs run by a brain (villagers, piglins...) attack and walk from memories rather than goals
            this.mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            this.mob.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            if (this.mob.getNavigation().isDone() || this.mob.tickCount % 20 == 0) {
                Vec3 source = this.mob.getData(ModAttachments.TERROR).map(Terror::source).orElse(this.mob.position());
                Vec3 away = DefaultRandomPos.getPosAway(this.mob, 16, 7, source);
                if (away != null) {
                    this.mob.getNavigation().moveTo(away.x, away.y, away.z, SPEED);
                }
            }
            if (this.mob.tickCount % 10 == 0 && this.mob.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.SMOKE, this.mob.getX(), this.mob.getY(1.0) + 0.2, this.mob.getZ(), 2, 0.15, 0.1, 0.15, 0.0);
            }
        }

        @Override
        public void stop() {
            this.mob.getNavigation().stop();
        }
    }
}

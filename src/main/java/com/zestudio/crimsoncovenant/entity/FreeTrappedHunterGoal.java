package com.zestudio.crimsoncovenant.entity;

import java.util.Comparator;
import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.block.BearTrapBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/// A hunter caught in a bear trap is a call to arms: hunters within 24 blocks drop what they're doing, run to him,
/// and pry the jaws open (the trap drops, whole). Two hunters can't both pry the same trap, but they can both run.
class FreeTrappedHunterGoal extends Goal {
    private static final double SEARCH_RANGE = 24.0;
    private static final double SPEED = 1.3;
    /// Close enough to get hands on the trap
    private static final double REACH_SQR = 2.5 * 2.5;
    private static final int PRY_TICKS = 30;
    /// Gives up if it can't get there in this long
    private static final int GIVE_UP_TICKS = 400;
    private static final int CHECK_INTERVAL = 20;

    private final Hunter hunter;
    private @Nullable Hunter trapped;
    private @Nullable BlockPos trap;
    private int prying;
    private int ticks;
    private int untilNextLook = 0;

    FreeTrappedHunterGoal(Hunter hunter) {
        this.hunter = hunter;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // Goals are only looked at every other tick, so this counts checks rather than ticks
        if (--this.untilNextLook > 0 || BearTrapBlock.heldIn(this.hunter).isPresent()) {
            return false;
        }
        this.untilNextLook = reducedTickDelay(CHECK_INTERVAL);
        this.trapped = this.hunter.level().getEntitiesOfClass(Hunter.class, this.hunter.getBoundingBox().inflate(SEARCH_RANGE),
                        other -> other != this.hunter && other.isAlive() && BearTrapBlock.heldIn(other).isPresent())
                .stream().min(Comparator.comparingDouble(this.hunter::distanceToSqr)).orElse(null);
        if (this.trapped == null) {
            return false;
        }
        this.trap = BearTrapBlock.heldIn(this.trapped).orElse(null);
        return this.trap != null;
    }

    @Override
    public boolean canContinueToUse() {
        return this.trapped != null && this.trap != null && this.ticks < GIVE_UP_TICKS
                && BearTrapBlock.heldIn(this.trapped).filter(this.trap::equals).isPresent();
    }

    @Override
    public void start() {
        this.prying = 0;
        this.ticks = 0;
        // The fight waits: the goal outranks the attack goals, and the hunter goes back to its target once its friend is free
        this.runToTrap();
    }

    @Override
    public void stop() {
        this.trapped = null;
        this.trap = null;
        this.hunter.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (this.trap == null) {
            return;
        }
        this.ticks++;
        Vec3 trapMiddle = Vec3.atBottomCenterOf(this.trap);
        this.hunter.getLookControl().setLookAt(trapMiddle.x, trapMiddle.y, trapMiddle.z);
        if (this.hunter.distanceToSqr(trapMiddle) > REACH_SQR) {
            this.prying = 0;
            if (this.hunter.getNavigation().isDone() || this.ticks % 20 == 0) {
                this.runToTrap();
            }
            return;
        }
        this.hunter.getNavigation().stop();
        if (this.prying % 8 == 0) {
            this.hunter.swingForAttack(InteractionHand.MAIN_HAND);
            this.hunter.playSound(SoundEvents.CHAIN_HIT, 1.0F, 0.8F);
        }
        if (++this.prying >= PRY_TICKS && this.hunter.level() instanceof ServerLevel level) {
            BearTrapBlock.breakOpen(level, this.trap, this.hunter, true);
        }
    }

    private void runToTrap() {
        if (this.trap != null) {
            this.hunter.getNavigation().moveTo(this.trap.getX() + 0.5, this.trap.getY(), this.trap.getZ() + 0.5, SPEED);
        }
    }
}

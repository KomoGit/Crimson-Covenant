package com.zestudio.crimsoncovenant.entity;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.registry.ModTags;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/// A cartoonish ghost haunting old mineshafts and abandoned villages. Drifts through walls, swoops straight
/// through its victims, and anyone it passes through gets Nausea. Nothing hurts it except holy fire (Holy Water).
public class Ghost extends Monster {
    private static final EntityDataAccessor<Boolean> DATA_SWOOPING = SynchedEntityData.defineId(Ghost.class, EntityDataSerializers.BOOLEAN);
    private static final int HAUNT_COOLDOWN = 40;
    private static final int NAUSEA_DURATION = 200;
    /// How far past its victim a swoop carries the ghost
    private static final double SWOOP_OVERSHOOT = 4.0;

    private int hauntCooldown = 0;
    private @Nullable BlockPos haunt;

    public Ghost(EntityType<? extends Ghost> type, Level level) {
        super(type, level);
        this.moveControl = new GhostMoveControl(this);
        this.xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_SWOOPING, false);
    }

    public boolean isSwooping() {
        return this.entityData.get(DATA_SWOOPING);
    }

    private void setSwooping(boolean swooping) {
        this.entityData.set(DATA_SWOOPING, swooping);
    }

    /// The place it haunts: it drifts around here when it has no one to scare
    public void setHaunt(BlockPos haunt) {
        this.haunt = haunt;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(4, new SwoopThroughGoal());
        this.goalSelector.addGoal(8, new DriftGoal());
        this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        // Undead, so it hunts hunters like every other undead
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Hunter.class, true));
    }

    // Like the vex: no collision while moving, so it passes straight through blocks
    @Override
    public void tick() {
        this.noPhysics = true;
        super.tick();
        this.noPhysics = false;
        this.setNoGravity(true);
        if (this.level() instanceof ServerLevel) {
            this.hauntPlayersInside();
        }
    }

    @Override
    protected boolean isAffectedByBlocks() {
        return !this.isRemoved();
    }

    /// Anyone the ghost passes through feels sick for a while
    private void hauntPlayersInside() {
        if (this.hauntCooldown > 0) {
            this.hauntCooldown--;
            return;
        }
        boolean haunted = false;
        for (Player player : this.level().getEntitiesOfClass(Player.class, this.getBoundingBox(), player -> !player.isSpectator())) {
            player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, NAUSEA_DURATION), this);
            haunted = true;
        }
        if (haunted) {
            this.hauntCooldown = HAUNT_COOLDOWN;
            this.playSound(SoundEvents.SOUL_ESCAPE.value(), 2.0F, 0.6F);
        }
    }

    // ---- Only holy fire can harm a ghost ----

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        if (!source.is(ModTags.DamageTypes.HURTS_GHOSTS) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return true;
        }
        return super.isInvulnerableTo(level, source);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity entity) {
    }

    // ---- Sounds ----

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.VEX_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.VEX_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.VEX_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.5F + this.random.nextFloat() * 0.2F;
    }

    /// Picks a point on the far side of the target and flies straight there, through the target and any walls
    private class SwoopThroughGoal extends Goal {
        SwoopThroughGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = Ghost.this.getTarget();
            return target != null && target.isAlive() && !Ghost.this.getMoveControl().hasWanted() && Ghost.this.random.nextInt(reducedTickDelay(5)) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = Ghost.this.getTarget();
            return Ghost.this.getMoveControl().hasWanted() && target != null && target.isAlive();
        }

        @Override
        public void start() {
            LivingEntity target = Ghost.this.getTarget();
            if (target == null) {
                return;
            }
            Vec3 through = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
            Vec3 direction = through.subtract(Ghost.this.position());
            direction = direction.lengthSqr() < 1.0E-4 ? Ghost.this.getLookAngle() : direction.normalize();
            Vec3 beyond = through.add(direction.scale(SWOOP_OVERSHOOT));
            Ghost.this.getMoveControl().setWantedPosition(beyond.x, beyond.y - Ghost.this.getBbHeight() * 0.5, beyond.z, 0.6);
            Ghost.this.setSwooping(true);
            Ghost.this.playSound(SoundEvents.VEX_CHARGE, 1.0F, 0.5F);
        }

        @Override
        public void stop() {
            Ghost.this.setSwooping(false);
        }
    }

    /// Drifts slowly around its haunt
    private class DriftGoal extends Goal {
        DriftGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return !Ghost.this.getMoveControl().hasWanted() && Ghost.this.random.nextInt(reducedTickDelay(10)) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            BlockPos origin = Ghost.this.haunt != null ? Ghost.this.haunt : Ghost.this.blockPosition();
            for (int attempt = 0; attempt < 3; attempt++) {
                BlockPos pos = origin.offset(Ghost.this.random.nextInt(15) - 7, Ghost.this.random.nextInt(7) - 3, Ghost.this.random.nextInt(15) - 7);
                if (Ghost.this.level().isEmptyBlock(pos)) {
                    Ghost.this.getMoveControl().setWantedPosition(pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, 0.2);
                    return;
                }
            }
        }
    }

    /// Flies straight at the wanted position, the same way a vex does
    private static class GhostMoveControl extends MoveControl<Mob> {
        GhostMoveControl(Ghost ghost) {
            super(ghost);
        }

        @Override
        public void tick() {
            if (this.operation != MoveControl.Operation.MOVE_TO) {
                return;
            }
            Vec3 delta = new Vec3(this.wantedX - this.mob.getX(), this.wantedY - this.mob.getY(), this.wantedZ - this.mob.getZ());
            double distance = delta.length();
            if (distance < this.mob.getBoundingBox().getSize()) {
                this.operation = MoveControl.Operation.WAIT;
                this.mob.setDeltaMovement(this.mob.getDeltaMovement().scale(0.5));
            } else {
                this.mob.setDeltaMovement(this.mob.getDeltaMovement().add(delta.scale(this.speedModifier * 0.05 / distance)));
                LivingEntity target = this.mob.getTarget();
                Vec3 facing = target != null ? target.position().subtract(this.mob.position()) : this.mob.getDeltaMovement();
                this.mob.setYRot(-((float) Mth.atan2(facing.x, facing.z)) * (180.0F / (float) Math.PI));
                this.mob.yBodyRot = this.mob.getYRot();
            }
        }
    }
}

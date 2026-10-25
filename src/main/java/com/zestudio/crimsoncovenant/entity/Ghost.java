package com.zestudio.crimsoncovenant.entity;

import java.util.EnumSet;
import java.util.List;
import java.util.ArrayList;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.registry.ModTags;
import com.zestudio.crimsoncovenant.block.GravestoneBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/// A cartoonish ghost haunting old mineshafts, abandoned villages and graveyards. Drifts through walls, swoops straight
/// through its victims, and anyone it passes through gets Nausea. Nothing hurts it except holy fire (Holy Water).
///
/// A graveyard's ghosts are each bound to a gravestone (see GravestoneBlock): they rise from it at nightfall, keep near
/// it, and at dawn fly back into it to rest. And now and then a ghost with no one to chase slips into some ordinary
/// thing nearby, a crafting table, a bookshelf, a pumpkin, which then floats about the place (see HauntedObject).
public class Ghost extends Monster {
    private static final EntityDataAccessor<Boolean> DATA_SWOOPING = SynchedEntityData.defineId(Ghost.class, EntityDataSerializers.BOOLEAN);
    private static final int HAUNT_COOLDOWN = 40;
    private static final int NAUSEA_DURATION = 200;
    /// How far past its victim a swoop carries the ghost
    private static final double SWOOP_OVERSHOOT = 4.0;
    /// How far a ghost bound to a gravestone will stray from it
    private static final double GRAVE_LEASH = 24.0;
    /// How far it looks for something to slip into, how long it leaves between, and how long before it looks again when
    /// there's nothing about
    private static final int POSSESS_RANGE = 10;
    private static final int POSSESS_COOLDOWN = 300;
    private static final int POSSESS_RETRY = 100;

    private int hauntCooldown = 0;
    private int possessCooldown = POSSESS_RETRY;
    private @Nullable BlockPos haunt;
    /// The gravestone it's bound to, if it's a graveyard's ghost
    private @Nullable BlockPos grave;

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
    public void setHaunt(@Nullable BlockPos haunt) {
        this.haunt = haunt;
    }

    public @Nullable BlockPos getHaunt() {
        return this.haunt;
    }

    /// Binds it to the gravestone at `grave`: it haunts the graveyard round it by night, and rests in it by day. It
    /// stays in the world as long as the stone does (it never wanders off and fades as other monsters do).
    public void bindTo(BlockPos grave) {
        this.grave = grave.immutable();
        this.haunt = this.grave;
        this.setPersistenceRequired();
    }

    /// Free of its gravestone (it was broken)
    public void unbind() {
        this.grave = null;
    }

    public @Nullable BlockPos getGrave() {
        return this.grave;
    }

    /// Whether it should be back in its gravestone: it has one, and it's day
    private boolean shouldRest() {
        return this.grave != null && !this.level().isDarkOutside();
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new ReturnToGraveGoal());
        this.goalSelector.addGoal(4, new SwoopThroughGoal());
        this.goalSelector.addGoal(6, new PossessGoal());
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
            if (this.possessCooldown > 0) {
                this.possessCooldown--;
            }
            // A graveyard's ghost won't chase anyone far from its grave, nor anyone at all once it's day
            if (this.grave != null && this.getTarget() != null
                    && (this.shouldRest() || this.getTarget().distanceToSqr(Vec3.atCenterOf(this.grave)) > GRAVE_LEASH * GRAVE_LEASH)) {
                this.setTarget(null);
            }
        }
    }

    /// Back into its gravestone to rest, if it's still there (if it isn't, the ghost's bound to nothing now)
    public void enterGrave() {
        if (this.grave == null || !(this.level() instanceof ServerLevel level)) {
            return;
        }
        if (!GravestoneBlock.isGravestone(level.getBlockState(this.grave))) {
            this.unbind();
            return;
        }
        GravestoneBlock.setSpirit(level, this.grave, GravestoneBlock.Spirit.RESTING);
        level.playSound(null, this.grave, SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 0.8F, 0.4F);
        level.sendParticles(ParticleTypes.SOUL, this.grave.getX() + 0.5, this.grave.getY() + 0.8, this.grave.getZ() + 0.5, 8, 0.2, 0.3, 0.2, 0.01);
        this.discard();
    }

    /// Whether a ghost can slip into this: ordinary things (not ones holding anything) in the hauntable tag
    public static boolean isHauntable(BlockState state) {
        return state.is(ModTags.Blocks.HAUNTABLE) && !state.hasBlockEntity();
    }

    /// Slips into the thing at `pos`, which rises and floats about with the ghost in it. Returns it, if it could.
    public @Nullable HauntedObject possess(BlockPos pos) {
        if (!(this.level() instanceof ServerLevel level) || !isHauntable(level.getBlockState(pos))) {
            return null;
        }
        HauntedObject object = HauntedObject.possess(level, pos, this);
        this.discard();
        return object;
    }

    // Destroyed for good: its gravestone, if it had one, lies empty from now on
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (this.grave != null && this.level() instanceof ServerLevel level) {
            GravestoneBlock.setSpirit(level, this.grave, GravestoneBlock.Spirit.NONE);
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.storeNullable("haunt", BlockPos.CODEC, this.haunt);
        output.storeNullable("grave", BlockPos.CODEC, this.grave);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.haunt = input.read("haunt", BlockPos.CODEC).orElse(null);
        this.grave = input.read("grave", BlockPos.CODEC).orElse(null);
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
            return target != null && target.isAlive() && !Ghost.this.shouldRest() && !Ghost.this.getMoveControl().hasWanted()
                    && Ghost.this.random.nextInt(reducedTickDelay(5)) == 0;
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

    /// At dawn a graveyard's ghost flies back to its gravestone, and into it
    private class ReturnToGraveGoal extends Goal {
        ReturnToGraveGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return Ghost.this.shouldRest();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            BlockPos grave = Ghost.this.grave;
            if (grave == null) {
                return;
            }
            Vec3 stone = Vec3.atCenterOf(grave);
            if (Ghost.this.position().distanceTo(stone) < 1.0) {
                Ghost.this.enterGrave();
            } else {
                Ghost.this.getMoveControl().setWantedPosition(stone.x, stone.y - Ghost.this.getBbHeight() * 0.5, stone.z, 0.5);
            }
        }
    }

    /// With no one to chase, now and then it slips into some ordinary thing nearby, to set it floating
    private class PossessGoal extends Goal {
        private @Nullable BlockPos object;
        private int ticks;

        PossessGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (Ghost.this.getTarget() != null || Ghost.this.possessCooldown > 0 || Ghost.this.shouldRest() || Ghost.this.random.nextInt(reducedTickDelay(20)) != 0) {
                return false;
            }
            this.object = this.findSomething();
            if (this.object == null) {
                // Nothing to hand: try again in a while
                Ghost.this.possessCooldown = POSSESS_RETRY;
            }
            return this.object != null;
        }

        @Override
        public boolean canContinueToUse() {
            return this.object != null && this.ticks < 200 && Ghost.this.getTarget() == null && !Ghost.this.shouldRest()
                    && isHauntable(Ghost.this.level().getBlockState(this.object));
        }

        @Override
        public void start() {
            this.ticks = 0;
        }

        @Override
        public void stop() {
            this.object = null;
            Ghost.this.possessCooldown = POSSESS_COOLDOWN;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            this.ticks++;
            if (this.object == null) {
                return;
            }
            Vec3 centre = Vec3.atCenterOf(this.object);
            if (Ghost.this.position().add(0.0, Ghost.this.getBbHeight() * 0.5, 0.0).distanceTo(centre) < 1.2) {
                Ghost.this.possess(this.object);
            } else {
                Ghost.this.getMoveControl().setWantedPosition(centre.x, centre.y - Ghost.this.getBbHeight() * 0.5, centre.z, 0.4);
            }
        }

        // Anything hauntable nearby, picked at random
        private @Nullable BlockPos findSomething() {
            BlockPos here = Ghost.this.blockPosition();
            List<BlockPos> found = new ArrayList<>();
            for (BlockPos pos : BlockPos.betweenClosed(here.offset(-POSSESS_RANGE, -POSSESS_RANGE, -POSSESS_RANGE), here.offset(POSSESS_RANGE, POSSESS_RANGE, POSSESS_RANGE))) {
                if (isHauntable(Ghost.this.level().getBlockState(pos))) {
                    found.add(pos.immutable());
                }
            }
            return found.isEmpty() ? null : found.get(Ghost.this.random.nextInt(found.size()));
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

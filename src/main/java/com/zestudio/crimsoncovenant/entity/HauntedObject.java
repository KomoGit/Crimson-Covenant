package com.zestudio.crimsoncovenant.entity;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.block.GravestoneBlock;
import com.zestudio.crimsoncovenant.registry.ModEntities;
import com.zestudio.crimsoncovenant.registry.ModTags;
import com.zestudio.crimsoncovenant.vampirism.VampireAge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/// Something a ghost has slipped into (see Ghost.possess): a crafting table, a bookshelf, a pumpkin, lifted out of its
/// place to float about it, turning slowly, bobbing, now and then rushing at whoever's near and knocking into them.
/// After a while (or at dawn, for a graveyard's ghost) it drifts back down into its place and the ghost slips out of
/// it again. Like the ghost, nothing hurts it but holy fire, which drives the ghost out for good and drops the thing
/// where it is.
public class HauntedObject extends Mob {
    private static final EntityDataAccessor<BlockState> HELD = SynchedEntityData.defineId(HauntedObject.class, EntityDataSerializers.BLOCK_STATE);
    /// How long the ghost keeps it up, at least and at most
    private static final int MIN_HAUNT = 600;
    private static final int MAX_HAUNT = 1200;
    /// How far it floats from its place, and how fast
    private static final double ROAM = 2.5;
    private static final double SPEED = 0.12;
    private static final double RUSH_SPEED = 0.35;
    private static final double RUSH_RANGE = 8.0;
    private static final int RUSH_TICKS = 25;
    private static final int RUSH_COOLDOWN = 80;
    private static final float BUMP_DAMAGE = 2.0F;

    private BlockPos origin = BlockPos.ZERO;
    private int hauntLeft = MIN_HAUNT;
    private int rushTicks;
    private int rushCooldown = RUSH_COOLDOWN;
    private @Nullable Player rushingAt;
    // The ghost inside: where it haunts, and the gravestone it's bound to, if any
    private @Nullable BlockPos ghostHaunt;
    private @Nullable BlockPos ghostGrave;

    public HauntedObject(EntityType<? extends HauntedObject> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0);
    }

    /// `ghost` slips into the thing at `pos`, which lifts out of its place
    public static HauntedObject possess(ServerLevel level, BlockPos pos, Ghost ghost) {
        BlockState state = level.getBlockState(pos);
        HauntedObject object = new HauntedObject(ModEntities.HAUNTED_OBJECT.get(), level);
        object.entityData.set(HELD, state);
        object.origin = pos.immutable();
        object.hauntLeft = Mth.nextInt(level.getRandom(), MIN_HAUNT, MAX_HAUNT);
        object.ghostHaunt = ghost.getHaunt();
        object.ghostGrave = ghost.getGrave();
        object.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.addFreshEntity(object);
        level.playSound(null, pos, SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 1.0F, 0.7F);
        level.playSound(null, pos, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.8F, 0.6F);
        level.sendParticles(ParticleTypes.SOUL, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 10, 0.3, 0.3, 0.3, 0.02);
        return object;
    }

    public BlockState getHeld() {
        return this.entityData.get(HELD);
    }

    public BlockPos getOrigin() {
        return this.origin;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(HELD, Blocks.CRAFTING_TABLE.defaultBlockState());
    }

    @Override
    public void tick() {
        this.noPhysics = true;
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        this.setYRot(this.getYRot() + 4.0F);
        this.yBodyRot = this.getYRot();
        boolean dawn = this.ghostGrave != null && !level.isDarkOutside();
        if (--this.hauntLeft <= 0 || dawn) {
            this.settleBack(level);
            return;
        }
        if (this.rushCooldown > 0) {
            this.rushCooldown--;
        }
        Vec3 goal = this.wanderPoint();
        double speed = SPEED;
        if (this.rushTicks > 0 && this.rushingAt != null && this.rushingAt.isAlive()) {
            this.rushTicks--;
            goal = this.rushingAt.position().add(0.0, this.rushingAt.getBbHeight() * 0.5, 0.0);
            speed = RUSH_SPEED;
        } else if (this.rushCooldown == 0) {
            this.rushingAt = level.getNearestPlayer(this.getX(), this.getY(), this.getZ(), RUSH_RANGE, player -> !player.isSpectator() && !((Player) player).isCreative()
                    && !VampireAge.isAncient((Player) player));
            if (this.rushingAt != null) {
                this.rushTicks = RUSH_TICKS;
                this.rushCooldown = RUSH_COOLDOWN;
                this.playSound(SoundEvents.VEX_CHARGE, 1.0F, 0.6F);
            }
        }
        Vec3 toGoal = goal.subtract(this.position());
        this.setDeltaMovement(toGoal.lengthSqr() < 1.0E-4 ? Vec3.ZERO : toGoal.normalize().scale(Math.min(speed, toGoal.length())));
        this.bump(level);
        if (this.tickCount % 8 == 0) {
            level.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY() + 0.5, this.getZ(), 1, 0.3, 0.3, 0.3, 0.01);
        }
        if (this.random.nextInt(120) == 0) {
            level.playSound(null, this.blockPosition(), this.getHeld().getSoundType().getHitSound(), SoundSource.BLOCKS, 0.6F, 0.5F);
        }
    }

    // A loose, slow orbit of its place, rising and falling
    private Vec3 wanderPoint() {
        double t = this.tickCount;
        return Vec3.atBottomCenterOf(this.origin).add(Math.cos(t * 0.03) * ROAM, 1.2 + Math.sin(t * 0.07) * 0.8, Math.sin(t * 0.03) * ROAM);
    }

    // Knocks into whoever it's rushing at, and stops its rush there
    private void bump(ServerLevel level) {
        if (this.rushTicks <= 0 || this.rushingAt == null || !this.getBoundingBox().inflate(0.2).intersects(this.rushingAt.getBoundingBox())) {
            return;
        }
        Player player = this.rushingAt;
        DamageSource blow = level.damageSources().mobAttack(this);
        if (player.hurtServer(level, blow, BUMP_DAMAGE)) {
            Vec3 away = player.position().subtract(this.position()).normalize();
            player.knockback(0.6, -away.x, -away.z, blow, BUMP_DAMAGE);
            player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 100), this);
        }
        level.playSound(null, this.blockPosition(), this.getHeld().getSoundType().getHitSound(), SoundSource.HOSTILE, 1.0F, 0.6F);
        this.rushTicks = 0;
    }

    @Override
    public void travel(Vec3 input) {
        this.move(MoverType.SELF, this.getDeltaMovement());
    }

    /// The haunting's over: the thing drifts back into its place (or, if that's been filled meanwhile, drops there),
    /// and the ghost slips out of it
    public void settleBack(ServerLevel level) {
        BlockState held = this.getHeld();
        if (level.getBlockState(this.origin).canBeReplaced()) {
            level.setBlock(this.origin, held, Block.UPDATE_ALL);
        } else {
            this.spawnAtLocation(level, new ItemStack(held.getBlock()));
        }
        level.playSound(null, this.origin, held.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.6F);
        Ghost ghost = ModEntities.GHOST.get().create(level, EntitySpawnReason.EVENT);
        if (ghost != null) {
            ghost.snapTo(this.origin.getX() + 0.5, this.origin.getY() + 1.0, this.origin.getZ() + 0.5, this.getYRot(), 0.0F);
            if (this.ghostGrave != null) {
                ghost.bindTo(this.ghostGrave);
            } else {
                ghost.setHaunt(this.ghostHaunt != null ? this.ghostHaunt : this.origin);
            }
            level.addFreshEntity(ghost);
        }
        level.sendParticles(ParticleTypes.SOUL, this.origin.getX() + 0.5, this.origin.getY() + 0.8, this.origin.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.02);
        this.discard();
    }

    /// Holy fire drives the ghost out of it for good: the thing drops where it is, and the ghost's gravestone, if it had
    /// one, lies empty from now on
    public void exorcise(ServerLevel level) {
        BlockState held = this.getHeld();
        this.spawnAtLocation(level, new ItemStack(held.getBlock()));
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, held), this.getX(), this.getY() + 0.5, this.getZ(), 20, 0.3, 0.3, 0.3, 0.1);
        level.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY() + 0.5, this.getZ(), 12, 0.3, 0.4, 0.3, 0.03);
        level.playSound(null, this.blockPosition(), SoundEvents.VEX_DEATH, SoundSource.HOSTILE, 1.0F, 0.5F);
        if (this.ghostGrave != null) {
            GravestoneBlock.setSpirit(level, this.ghostGrave, GravestoneBlock.Spirit.NONE);
        }
        ExperienceOrb.award(level, this.position(), 10);
        this.discard();
    }

    // Only holy fire touches it: that drives the ghost out
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (source.is(ModTags.DamageTypes.HURTS_GHOSTS)) {
            this.exorcise(level);
            return true;
        }
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceSqr) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public void knockback(double power, double xd, double zd, @Nullable DamageSource source, float damage) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("held", BlockState.CODEC, this.getHeld());
        output.store("origin", BlockPos.CODEC, this.origin);
        output.putInt("haunt_left", this.hauntLeft);
        output.storeNullable("ghost_haunt", BlockPos.CODEC, this.ghostHaunt);
        output.storeNullable("ghost_grave", BlockPos.CODEC, this.ghostGrave);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        input.read("held", BlockState.CODEC).ifPresent(state -> this.entityData.set(HELD, state));
        this.origin = input.read("origin", BlockPos.CODEC).orElse(this.blockPosition());
        this.hauntLeft = input.getIntOr("haunt_left", MIN_HAUNT);
        this.ghostHaunt = input.read("ghost_haunt", BlockPos.CODEC).orElse(null);
        this.ghostGrave = input.read("ghost_grave", BlockPos.CODEC).orElse(null);
    }
}

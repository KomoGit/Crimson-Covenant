package com.zestudio.crimsoncovenant.entity;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.registry.ModAttachments;
import com.zestudio.crimsoncovenant.registry.ModAttachments.SpearDrive;
import com.zestudio.crimsoncovenant.registry.ModDamageTypes;
import com.zestudio.crimsoncovenant.registry.ModEntities;
import com.zestudio.crimsoncovenant.registry.ModItems;
import com.zestudio.crimsoncovenant.vampirism.BloodPool;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/// The Blood Spear skill, in the manner of Orisa's javelin: a spear of blood that hits hard and carries whatever it
/// strikes along with it for a moment. Drive something into a wall and it's slammed against it: more damage, a
/// stagger, and a crunch you can feel. If either blow is a kill, the thrower gets the spear's blood back.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public class BloodSpear extends AbstractArrow {
    public static final float DAMAGE = 8.0F;
    public static final float SLAM_DAMAGE = 8.0F;
    public static final int BLOOD_REFUND = 10;
    private static final int LIFETIME = 100;
    // The drive: carried at 1.1 blocks a tick for 6 ticks, about 6 blocks, unless a wall stops it first
    private static final double DRIVE_SPEED = 1.1;
    public static final int DRIVE_TICKS = 6;
    private static final int STAGGER_TICKS = 30;
    /// As much debris as a creature landing from about 12 blocks up
    private static final int SLAM_DEBRIS = 150;

    /// Thrown by an upgraded Blood Spear skill: red lightning strikes whatever it hits
    private boolean crimsonLightning = false;

    public BloodSpear(EntityType<? extends BloodSpear> type, Level level) {
        super(type, level);
    }

    public BloodSpear(Level level, LivingEntity owner) {
        super(ModEntities.BLOOD_SPEAR.get(), owner, level, new ItemStack(ModItems.BLOOD_SPEAR.get()), null);
        // Made of blood: it can't be picked up
        this.pickup = AbstractArrow.Pickup.DISALLOWED;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel level && this.isAlive()) {
            level.sendParticles(new DustParticleOptions(Vampire.BLOOD_COLOR, 1.0F), this.getX(), this.getY(), this.getZ(), 2, 0.05, 0.05, 0.05, 0.0);
            if (this.tickCount > LIFETIME) {
                this.discard();
            }
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        Entity target = hitResult.getEntity();
        Entity owner = this.getOwner();
        DamageSource source = this.damageSources().source(ModDamageTypes.BLOOD_MAGIC, this, owner == null ? this : owner);
        Vec3 heading = this.getDeltaMovement().horizontal();
        heading = heading.lengthSqr() < 1.0E-4 ? Vec3.ZERO : heading.normalize();

        boolean hurt = target.hurtServer(level, source, DAMAGE);
        if (hurt && target instanceof LivingEntity living) {
            if (!living.isAlive()) {
                refund(owner);
            } else if (heading != Vec3.ZERO) {
                double resistance = Mth.clamp(living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0, 1.0);
                if (resistance < 1.0) {
                    living.setData(ModAttachments.SPEAR_DRIVE, Optional.of(new SpearDrive(heading.scale(DRIVE_SPEED * (1.0 - resistance)), DRIVE_TICKS,
                            Optional.ofNullable(owner).map(Entity::getUUID))));
                }
            }
        }
        if (hurt && this.crimsonLightning && target instanceof LivingEntity living) {
            CrimsonLightning.strike(level, living, owner);
        }
        // A heavy, wet impact
        level.sendParticles(new DustParticleOptions(Vampire.BLOOD_COLOR, 2.0F), target.getX(), target.getY(0.6), target.getZ(), 40, 0.3, 0.4, 0.3, 0.0);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY(0.6), target.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        sound(level, target, SoundEvents.TRIDENT_HIT, 1.2F, 0.6F);
        sound(level, target, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.2F, 0.7F);
        this.discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult hitResult) {
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(new DustParticleOptions(Vampire.BLOOD_COLOR, 1.6F), this.getX(), this.getY(), this.getZ(), 20, 0.25, 0.25, 0.25, 0.0);
            this.playSound(SoundEvents.TRIDENT_HIT_GROUND, 1.0F, 0.6F);
            this.discard();
        }
    }

    public void setCrimsonLightning(boolean crimsonLightning) {
        this.crimsonLightning = crimsonLightning;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("CrimsonLightning", this.crimsonLightning);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.crimsonLightning = input.getBooleanOr("CrimsonLightning", false);
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(ModItems.BLOOD_SPEAR.get());
    }

    // ---- The drive ----

    /// Carries a speared creature along, a tick at a time. Each tick looks ahead first: if a wall is in the way,
    /// the creature is slammed into it and the drive ends there.
    @SubscribeEvent
    static void drive(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LivingEntity target) || !(target.level() instanceof ServerLevel level)) {
            return;
        }
        Optional<SpearDrive> found = target.getData(ModAttachments.SPEAR_DRIVE);
        if (found.isEmpty()) {
            return;
        }
        SpearDrive drive = found.get();
        if (!target.isAlive() || drive.ticksLeft() <= 0) {
            target.setData(ModAttachments.SPEAR_DRIVE, Optional.empty());
            return;
        }
        BlockHitResult wall = wallAhead(level, target, drive.velocity());
        if (wall != null) {
            target.setData(ModAttachments.SPEAR_DRIVE, Optional.empty());
            slam(level, target, wall, drive.thrower().map(level::getEntity).orElse(null));
            return;
        }
        int ticksLeft = drive.ticksLeft() - 1;
        // A little lift on the first tick, so the ground doesn't drag at it
        double lift = drive.ticksLeft() == DRIVE_TICKS ? 0.15 : target.getDeltaMovement().y;
        Vec3 velocity = ticksLeft == 0 ? drive.velocity().scale(0.3) : drive.velocity();
        target.setDeltaMovement(velocity.x, lift, velocity.z);
        target.syncVelocity = true;
        target.resetFallDistance();
        level.sendParticles(new DustParticleOptions(Vampire.BLOOD_COLOR, 1.2F), target.getX(), target.getY(0.5), target.getZ(), 4, 0.2, 0.3, 0.2, 0.0);
        target.setData(ModAttachments.SPEAR_DRIVE, ticksLeft == 0 ? Optional.empty() : Optional.of(new SpearDrive(drive.velocity(), ticksLeft, drive.thrower())));
    }

    /// The wall the creature would hit this tick, if any: checked at its middle and near its feet, so low walls count
    public static @Nullable BlockHitResult wallAhead(ServerLevel level, Entity target, Vec3 velocity) {
        Vec3 heading = velocity.normalize();
        double reach = velocity.length() + target.getBbWidth() * 0.5 + 0.15;
        for (double height : new double[] {0.5, 0.15}) {
            Vec3 from = target.position().add(0.0, target.getBbHeight() * height, 0.0);
            BlockHitResult hit = level.clip(new ClipContext(from, from.add(heading.scale(reach)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target));
            if (hit.getType() == HitResult.Type.BLOCK) {
                return hit;
            }
        }
        return null;
    }

    /// Pinned against the wall: extra damage, a stagger, and debris off the wall
    private static void slam(ServerLevel level, LivingEntity target, BlockHitResult wall, @Nullable Entity thrower) {
        target.setDeltaMovement(Vec3.ZERO);
        target.syncVelocity = true;
        // The spear's blow just landed, so the usual moment of invulnerability would swallow this one
        target.damageCooldownTime = 0;
        DamageSource source = thrower == null ? level.damageSources().source(ModDamageTypes.BLOOD_MAGIC)
                : level.damageSources().source(ModDamageTypes.BLOOD_MAGIC, thrower);
        if (target.hurtServer(level, source, SLAM_DAMAGE) && !target.isAlive()) {
            refund(thrower);
        }
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, STAGGER_TICKS, 6, false, false, true));
        target.addEffect(new MobEffectInstance(MobEffects.NAUSEA, STAGGER_TICKS * 2, 0, false, false, true));

        Vec3 at = wall.getLocation();
        BlockPos wallPos = wall.getBlockPos();
        BlockState wallState = level.getBlockState(wallPos);
        // The same burst of debris and thud as a creature landing from a great fall, only against the wall
        if (!wallState.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, wallState, wallPos), at.x, at.y, at.z, SLAM_DEBRIS, 0.0, 0.0, 0.0, 0.15);
            level.playSound(null, at.x, at.y, at.z, wallState.getSoundType().getFallSound(), SoundSource.BLOCKS, 1.0F, 0.75F);
        }
        sound(level, target, target.getFallSounds().big(), 1.0F, 1.0F);
        level.sendParticles(new DustParticleOptions(Vampire.BLOOD_COLOR, 2.2F), at.x, at.y, at.z, 40, 0.2, 0.5, 0.2, 0.0);
        level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.5), target.getZ(), 25, 0.3, 0.4, 0.3, 0.3);
        sound(level, target, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.3F, 0.8F);
        sound(level, target, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, 1.0F, 0.6F);
        sound(level, target, SoundEvents.ANVIL_LAND, 0.5F, 0.6F);
    }

    private static void refund(@Nullable Entity thrower) {
        if (thrower instanceof Player player) {
            BloodPool.add(player, BLOOD_REFUND);
        }
    }

    private static void sound(ServerLevel level, Entity at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.getX(), at.getY(), at.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }
}

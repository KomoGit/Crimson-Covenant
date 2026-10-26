package com.zestudio.crimsoncovenant.entity;

import java.util.Comparator;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.registry.ModDamageTypes;
import com.zestudio.crimsoncovenant.registry.ModEntities;
import com.zestudio.crimsoncovenant.registry.ModItems;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/// A bat of blood, set loose when a Blood Parade kills (see BloodOrb): it flies from the body at the nearest hostile
/// within 15 blocks, through anything in its way, leaving a trail of blood, and bites. If the bite kills, another bat
/// goes out from that body with 5 blocks less reach (10, then 5), and at 0 the chain ends. Never saved.
public class BloodBat extends Projectile implements ItemSupplier {
    public static final double FIRST_RANGE = 15.0;
    public static final double RANGE_STEP = 5.0;
    public static final float DAMAGE = 6.0F;
    private static final double SPEED = 0.8;
    /// Gives up after this long, if its target somehow keeps out of reach
    private static final int LIFETIME = 80;

    private @Nullable LivingEntity target;
    private double range;

    public BloodBat(EntityType<? extends BloodBat> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /// Sends a bat from where `victim` fell at the nearest hostile within `range` of it, if there's any range left
    /// and anything to bite. Returns the bat.
    public static @Nullable BloodBat chain(ServerLevel level, @Nullable LivingEntity owner, LivingEntity victim, double range) {
        if (range <= 0.0) {
            return null;
        }
        Vec3 from = victim.position().add(0.0, victim.getBbHeight() * 0.5, 0.0);
        LivingEntity next = level.getEntitiesOfClass(LivingEntity.class, victim.getBoundingBox().inflate(range),
                        entity -> entity != victim && BloodOrb.isHostile(entity, owner) && entity.distanceToSqr(from) <= range * range)
                .stream().min(Comparator.comparingDouble(entity -> entity.distanceToSqr(from))).orElse(null);
        if (next == null) {
            return null;
        }
        BloodBat bat = new BloodBat(ModEntities.BLOOD_BAT.get(), level);
        bat.setOwner(owner);
        bat.setPos(from);
        bat.target = next;
        bat.range = range;
        level.addFreshEntity(bat);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.BAT_TAKEOFF, SoundSource.PLAYERS, 0.8F, 0.7F);
        return bat;
    }

    public @Nullable LivingEntity target() {
        return this.target;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel level) {
            if (this.target == null || !this.target.isAlive() || this.tickCount > LIFETIME) {
                level.sendParticles(new DustParticleOptions(Vampire.BLOOD_COLOR, 1.2F), this.getX(), this.getY(), this.getZ(), 8, 0.2, 0.2, 0.2, 0.0);
                this.discard();
                return;
            }
            Vec3 toTarget = this.target.position().add(0.0, this.target.getBbHeight() * 0.5, 0.0).subtract(this.position());
            if (toTarget.length() <= SPEED) {
                this.bite(level, this.target);
                return;
            }
            this.setDeltaMovement(toTarget.normalize().scale(SPEED));
            this.needsSync = true;
            // The trail it leaves behind
            Vec3 step = this.getDeltaMovement();
            for (int i = 0; i < 3; i++) {
                Vec3 point = this.position().subtract(step.scale(i / 3.0));
                level.sendParticles(new DustParticleOptions(Vampire.BLOOD_COLOR, 0.9F), point.x, point.y, point.z, 1, 0.03, 0.03, 0.03, 0.0);
            }
        }
        this.setPos(this.position().add(this.getDeltaMovement()));
    }

    private void bite(ServerLevel level, LivingEntity victim) {
        LivingEntity owner = this.getOwner() instanceof LivingEntity living ? living : null;
        DamageSource source = this.damageSources().source(ModDamageTypes.BLOOD_MAGIC, this, owner == null ? this : owner);
        victim.damageCooldownTime = 0;
        if (victim.hurtServer(level, source, DAMAGE) && !victim.isAlive()) {
            chain(level, owner, victim, this.range - RANGE_STEP);
        }
        level.sendParticles(new DustParticleOptions(Vampire.BLOOD_COLOR, 1.6F), victim.getX(), victim.getY(0.5), victim.getZ(), 15, 0.3, 0.4, 0.3, 0.0);
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.BAT_HURT, SoundSource.PLAYERS, 0.8F, 0.6F);
        this.discard();
    }

    @Override
    public ItemStack getItem() {
        return new ItemStack(ModItems.BLOOD_BAT.get());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}

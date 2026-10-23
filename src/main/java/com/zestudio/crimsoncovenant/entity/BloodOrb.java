package com.zestudio.crimsoncovenant.entity;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.registry.ModDamageTypes;
import com.zestudio.crimsoncovenant.registry.ModEntities;
import com.zestudio.crimsoncovenant.registry.ModItems;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.hurtingprojectile.Fireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/// The Blood Parade skill: a huge ball of blood made from the caster's whole blood bar. It bursts on impact,
/// and anything the burst kills bursts in turn, which can set off a chain of bursts through a crowd.
public class BloodOrb extends Fireball {
    private static final float BASE_DAMAGE = 6.0F;
    /// Every point of blood poured into the orb adds this much damage: a full bar hits for 26
    private static final float DAMAGE_PER_BLOOD = 0.2F;
    private static final double BURST_RADIUS = 4.0;
    // Bursts from slain enemies: smaller and weaker, and the chain stops after a few links
    private static final double CHAIN_RADIUS = 3.0;
    private static final float CHAIN_DAMAGE_FACTOR = 0.6F;
    private static final int MAX_CHAIN_DEPTH = 3;
    private static final int LIFETIME = 100;

    private int bloodSpent;

    public BloodOrb(EntityType<? extends BloodOrb> type, Level level) {
        super(type, level);
    }

    public BloodOrb(Level level, LivingEntity owner, Vec3 direction, int bloodSpent) {
        super(ModEntities.BLOOD_ORB.get(), owner, direction, level);
        this.bloodSpent = bloodSpent;
        this.setItem(new ItemStack(ModItems.BLOOD_ORB.get()));
    }

    public float damage() {
        return BASE_DAMAGE + this.bloodSpent * DAMAGE_PER_BLOOD;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel level && this.isAlive()) {
            level.sendParticles(new DustParticleOptions(Vampire.BLOOD_COLOR, 2.5F), this.getX(), this.getY(0.5), this.getZ(), 4, 0.4, 0.4, 0.4, 0.0);
            if (this.tickCount > LIFETIME) {
                this.burst(level, null);
            }
        }
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (this.level() instanceof ServerLevel level && this.isAlive()) {
            this.burst(level, hitResult instanceof EntityHitResult entityHit ? entityHit.getEntity() : null);
        }
    }

    public void burst(ServerLevel level, @Nullable Entity directHit) {
        Entity owner = this.getOwner();
        DamageSource source = this.damageSources().source(ModDamageTypes.BLOOD_MAGIC, this, owner == null ? this : owner);
        if (directHit != null && directHit != owner) {
            hurtAndChain(level, source, owner, directHit, this.damage(), 0);
        }
        burstAt(level, source, owner, this.position(), BURST_RADIUS, this.damage(), 0);
        this.discard();
    }

    /// A burst of blood. Damage falls off to half at the edge.
    private static void burstAt(ServerLevel level, DamageSource source, @Nullable Entity owner, Vec3 centre, double radius, float damage, int depth) {
        level.sendParticles(new DustParticleOptions(Vampire.BLOOD_COLOR, 3.0F), centre.x, centre.y, centre.z, (int) (radius * 20), radius * 0.4, radius * 0.4, radius * 0.4, 0.0);
        level.sendParticles(ParticleTypes.EXPLOSION, centre.x, centre.y, centre.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2F, 0.6F + depth * 0.15F);

        List<LivingEntity> caught = level.getEntitiesOfClass(LivingEntity.class, new AABB(centre, centre).inflate(radius),
                entity -> entity != owner && entity.isAlive() && !(entity instanceof ArmorStand) && entity.distanceToSqr(centre) <= radius * radius
                        && (owner == null || !entity.isAlliedTo(owner)));
        for (LivingEntity entity : caught) {
            double falloff = 1.0 - Math.sqrt(entity.distanceToSqr(centre)) / radius * 0.5;
            hurtAndChain(level, source, owner, entity, (float) (damage * falloff), depth);
        }
    }

    /// Anything the blood kills bursts too
    private static void hurtAndChain(ServerLevel level, DamageSource source, @Nullable Entity owner, Entity target, float damage, int depth) {
        if (!target.isAlive() || !target.hurtServer(level, source, damage) || target.isAlive() || depth >= MAX_CHAIN_DEPTH) {
            return;
        }
        burstAt(level, source, owner, target.position().add(0.0, target.getBbHeight() * 0.5, 0.0), CHAIN_RADIUS, damage * CHAIN_DAMAGE_FACTOR, depth + 1);
    }

    // Blood doesn't set things alight, and can't be batted back like a ghast's fireball
    @Override
    protected boolean shouldBurn() {
        return false;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    protected @Nullable ParticleOptions getTrailParticle() {
        return null;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("BloodSpent", this.bloodSpent);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.bloodSpent = input.getIntOr("BloodSpent", 0);
    }
}

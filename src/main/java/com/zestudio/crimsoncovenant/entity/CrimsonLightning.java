package com.zestudio.crimsoncovenant.entity;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.registry.ModDamageTypes;
import com.zestudio.crimsoncovenant.registry.ModEntities;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/// The bolt of red lightning an upgraded Blood Spear calls down on what it hits (the Crimson Lightning upgrade). It
/// strikes the target for extra damage and arcs into hostiles standing close by. Unlike real lightning it starts no
/// fires and its thunder doesn't carry across the world. It only lives a few ticks: long enough to be seen.
public class CrimsonLightning extends Entity {
    public static final float STRIKE_DAMAGE = 6.0F;
    private static final float ARC_DAMAGE = 3.0F;
    private static final double ARC_RADIUS = 2.5;
    private static final int LIFETIME = 8;

    /// For the renderer: a fresh shape every bolt
    public final long seed;

    public CrimsonLightning(EntityType<? extends CrimsonLightning> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.seed = this.random.nextLong();
    }

    public static void strike(ServerLevel level, LivingEntity target, @Nullable Entity caster) {
        CrimsonLightning bolt = new CrimsonLightning(ModEntities.CRIMSON_LIGHTNING.get(), level);
        bolt.setPos(target.getX(), target.getY(), target.getZ());
        level.addFreshEntity(bolt);

        DamageSource source = level.damageSources().source(ModDamageTypes.BLOOD_MAGIC, bolt, caster);
        if (target.isAlive()) {
            // Lands in full right after the spear itself
            target.damageCooldownTime = 0;
            target.hurtServer(level, source, STRIKE_DAMAGE);
        }
        LivingEntity casterLiving = caster instanceof LivingEntity living ? living : null;
        for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(ARC_RADIUS),
                entity -> entity != target && BloodOrb.isHostile(entity, casterLiving))) {
            nearby.hurtServer(level, source, ARC_DAMAGE);
        }
        level.sendParticles(new DustParticleOptions(0xE01020, 2.0F), target.getX(), target.getY(0.5), target.getZ(), 40, 0.4, 0.8, 0.4, 0.0);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 2.0F, 1.3F);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.tickCount >= LIFETIME) {
            this.discard();
        }
    }

    /// Seen from as far off as real lightning. (By default the distance an entity is drawn from scales with its size,
    /// and a bolt has none, so it would never be drawn at all.)
    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double range = 64.0 * getViewScale();
        return distance < range * range;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }
}

package com.zestudio.crimsoncovenant.entity;

import com.zestudio.crimsoncovenant.registry.ModEntities;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/// A little jack o'lantern the Headless Horseman lobs (see HeadlessHorseman.throwBomb). It bursts in a gout of flame
/// where it lands: everyone close by is hurt a little and thrown clear, but it never harms the ground or anything built.
public class PumpkinBomb extends ThrowableItemProjectile {
    public static final double BLAST_RADIUS = 3.5;
    public static final float BLAST_DAMAGE = 4.0F;
    private static final double THROW_STRENGTH = 1.4;

    public PumpkinBomb(EntityType<? extends PumpkinBomb> type, Level level) {
        super(type, level);
    }

    public PumpkinBomb(Level level, LivingEntity thrower) {
        super(ModEntities.PUMPKIN_BOMB.get(), thrower, level, new ItemStack(Items.JACK_O_LANTERN));
    }

    @Override
    protected Item getDefaultItem() {
        return Items.JACK_O_LANTERN;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            this.level().addParticle(ParticleTypes.FLAME, this.getX(), this.getY() + 0.2, this.getZ(), 0.0, 0.0, 0.0);
            this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.2, this.getZ(), 0.0, 0.0, 0.0);
        }
    }

    // It bursts on whatever it touches, though never on the Horseman or his horse
    @Override
    protected boolean canHitEntity(Entity target) {
        Entity owner = this.getOwner();
        return super.canHitEntity(target) && (owner == null || (target != owner && !owner.isPassengerOfSameVehicle(target) && target != owner.getVehicle()));
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (this.level() instanceof ServerLevel level) {
            Vec3 at = hitResult instanceof EntityHitResult entityHit ? entityHit.getEntity().position() : this.position();
            burst(level, at, this);
            this.discard();
        }
    }

    /// A bomb bursting at `at`: hurts and throws clear every living thing within BLAST_RADIUS, bar whoever threw it and
    /// their horse, without touching a block
    public static void burst(ServerLevel level, Vec3 at, Entity bomb) {
        Entity thrower = bomb instanceof PumpkinBomb pumpkin ? pumpkin.getOwner() : null;
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 2, 0.3, 0.3, 0.3, 0.0);
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.5, at.z, 40, 0.8, 0.5, 0.8, 0.08);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.5, at.z, 15, 0.8, 0.5, 0.8, 0.03);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.5F, 1.3F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.WITCH_CELEBRATE, SoundSource.HOSTILE, 1.0F, 0.8F);
        for (LivingEntity caught : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(BLAST_RADIUS),
                caught -> caught.isAlive() && caught != thrower && (thrower == null || caught != thrower.getVehicle()))) {
            Vec3 away = caught.position().subtract(at).multiply(1.0, 0.0, 1.0);
            double distance = away.length();
            if (distance > BLAST_RADIUS) {
                continue;
            }
            if (distance < 1.0E-3) {
                away = new Vec3(level.getRandom().nextDouble() - 0.5, 0.0, level.getRandom().nextDouble() - 0.5);
            }
            caught.hurtServer(level, level.damageSources().explosion(bomb, thrower), BLAST_DAMAGE);
            double strength = THROW_STRENGTH * (1.0 - distance / (BLAST_RADIUS + 1.0)) * (1.0 - caught.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.EXPLOSION_KNOCKBACK_RESISTANCE));
            caught.setDeltaMovement(caught.getDeltaMovement().add(away.normalize().scale(strength)).add(0.0, 0.5 * strength + 0.2, 0.0));
            caught.syncVelocity = true;
        }
    }
}

package com.zestudio.crimsoncovenant.entity;

import com.zestudio.crimsoncovenant.registry.ModDamageTypes;
import com.zestudio.crimsoncovenant.registry.ModEntities;
import com.zestudio.crimsoncovenant.skill.BloodSkill;
import com.zestudio.crimsoncovenant.vampirism.BloodPool;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

/// The Blood Spear skill: a spear of blood thrown like a trident. It bursts on whatever it hits, and if the
/// hit is a kill, the thrower gets the spear's blood back.
public class BloodSpear extends AbstractArrow {
    private static final float DAMAGE = 8.0F;
    public static final int BLOOD_REFUND = 10;
    private static final int LIFETIME = 100;

    public BloodSpear(EntityType<? extends BloodSpear> type, Level level) {
        super(type, level);
    }

    public BloodSpear(Level level, LivingEntity owner) {
        super(ModEntities.BLOOD_SPEAR.get(), owner, level, new ItemStack(BloodSkill.BLOOD_SPEAR.skillItem()), null);
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
        Entity target = hitResult.getEntity();
        Entity owner = this.getOwner();
        DamageSource source = this.damageSources().source(ModDamageTypes.BLOOD_MAGIC, this, owner == null ? this : owner);
        if (this.level() instanceof ServerLevel level) {
            if (target.hurtServer(level, source, DAMAGE) && target instanceof LivingEntity living && !living.isAlive()
                    && owner instanceof Player player) {
                BloodPool.add(player, BLOOD_REFUND);
            }
            this.splash(level);
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult hitResult) {
        if (this.level() instanceof ServerLevel level) {
            this.splash(level);
        }
    }

    private void splash(ServerLevel level) {
        level.sendParticles(new DustParticleOptions(Vampire.BLOOD_COLOR, 1.6F), this.getX(), this.getY(), this.getZ(), 20, 0.25, 0.25, 0.25, 0.0);
        this.playSound(SoundEvents.TRIDENT_HIT, 1.0F, 0.6F);
        this.discard();
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(BloodSkill.BLOOD_SPEAR.skillItem());
    }
}

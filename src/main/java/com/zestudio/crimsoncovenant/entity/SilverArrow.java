package com.zestudio.crimsoncovenant.entity;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.registry.ModEntities;
import com.zestudio.crimsoncovenant.registry.ModItems;
import com.zestudio.crimsoncovenant.registry.ModTags;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

/// A silver-tipped arrow. Against anything weak to silver (undead and werewolves, see the
/// crimsoncovenant:weak_to_silver tag) it hits harder and leaves the target slowed.
public class SilverArrow extends AbstractArrow {
    /// Added to the arrow's base damage (normal arrows have 2), so roughly +75% against silver-weak targets
    private static final double SILVER_BONUS_DAMAGE = 1.5;
    private static final int SLOW_DURATION = 100;

    // AbstractArrow has no getter for its damage, so keep track of it here
    private double baseDamage = 2.0;

    public SilverArrow(EntityType<? extends SilverArrow> type, Level level) {
        super(type, level);
    }

    public SilverArrow(Level level, LivingEntity owner, ItemStack pickupItemStack, @Nullable ItemStack firedFromWeapon) {
        super(ModEntities.SILVER_ARROW.get(), owner, level, pickupItemStack, firedFromWeapon);
    }

    public SilverArrow(Level level, double x, double y, double z, ItemStack pickupItemStack, @Nullable ItemStack firedFromWeapon) {
        super(ModEntities.SILVER_ARROW.get(), x, y, z, level, pickupItemStack, firedFromWeapon);
    }

    @Override
    public void setBaseDamage(double baseDamage) {
        super.setBaseDamage(baseDamage);
        this.baseDamage = baseDamage;
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        boolean silverWeak = hitResult.getEntity().is(ModTags.Entities.WEAK_TO_SILVER);
        if (silverWeak) {
            super.setBaseDamage(this.baseDamage + SILVER_BONUS_DAMAGE);
        }
        super.onHitEntity(hitResult);
        if (silverWeak) {
            // Back to normal, in case a piercing arrow goes on to hit something else
            super.setBaseDamage(this.baseDamage);
        }
    }

    @Override
    protected void doPostHurtEffects(LivingEntity target) {
        super.doPostHurtEffects(target);
        if (target.is(ModTags.Entities.WEAK_TO_SILVER)) {
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOW_DURATION, 1), this.getEffectSource());
        }
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(ModItems.SILVER_ARROW.get());
    }
}

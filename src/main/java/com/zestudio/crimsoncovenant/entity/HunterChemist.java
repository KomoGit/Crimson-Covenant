package com.zestudio.crimsoncovenant.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.effect.HolyWaterEffect;
import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.registry.ModPotions;
import com.zestudio.crimsoncovenant.vampirism.Vampirism;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownLingeringPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/// A hunter who fights with a satchel of potions instead of an axe or crossbow, keeping its distance like a witch.
///
/// Against the undead it throws whatever will hurt most right now: Holy Water (a lingering cloud of it into a crowd),
/// garlic, silver to scatter them, or healing, which burns them. Against living foes (werewolves, tainted players)
/// it throws slowness, weakness, poison and harming. Between throws it splashes healing on hurt hunters nearby,
/// itself included. Turns up in hunter parties, and drops a witch's ingredients and some of its own when killed.
public class HunterChemist extends Hunter {
    private static final int THROW_INTERVAL = 60;
    private static final float THROW_RANGE = 10.0F;
    private static final int LINGERING_GROUP_SIZE = 3;
    private static final double GROUP_RANGE = 3.0;
    // Patching up hurt hunters
    private static final double HEAL_RANGE = 10.0;
    private static final float HEAL_BELOW = 0.6F;
    private static final int HEAL_COOLDOWN = 120;
    private static final int HEAL_CHECK_INTERVAL = 20;

    private int healCooldown = 0;

    public HunterChemist(EntityType<? extends HunterChemist> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Hunter.createAttributes()
                .add(Attributes.MAX_HEALTH, 22.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        // Ahead of the melee goal, so it keeps its distance and throws
        this.goalSelector.addGoal(2, new RangedAttackGoal(this, 1.0, THROW_INTERVAL, THROW_RANGE));
    }

    /// A potion in hand rather than a weapon
    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        this.setItemSlot(EquipmentSlot.MAINHAND, PotionContents.createItemStack(Items.SPLASH_POTION, ModPotions.HOLY_WATER));
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    // It has its own potions
    @Override
    protected boolean throwsGarlic() {
        return false;
    }

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        boolean crowd = Vampirism.isUndead(target) && level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(GROUP_RANGE),
                HolyWaterEffect::isAffected).size() >= LINGERING_GROUP_SIZE;
        if (crowd) {
            this.throwPotion(level, target, PotionContents.createItemStack(Items.LINGERING_POTION, ModPotions.HOLY_WATER), true);
        } else {
            this.throwPotion(level, target, PotionContents.createItemStack(Items.SPLASH_POTION, this.choosePotion(target)), false);
        }
    }

    /// What will hurt this target most right now
    public Holder<Potion> choosePotion(LivingEntity target) {
        List<Holder<Potion>> choices = new ArrayList<>();
        if (Vampirism.isUndead(target)) {
            if (!target.hasEffect(ModEffects.HOLY_FLAME)) {
                choices.add(ModPotions.HOLY_WATER);
            }
            if (!target.hasEffect(ModEffects.GARLIC)) {
                choices.add(ModPotions.GARLIC);
            }
            if (!target.hasEffect(ModEffects.SILVER_DREAD)) {
                choices.add(ModPotions.SILVER);
            }
            // Healing burns the undead
            choices.add(Potions.STRONG_HEALING);
        } else {
            if (!target.hasEffect(MobEffects.SLOWNESS)) {
                return Potions.SLOWNESS;
            }
            if (!target.hasEffect(MobEffects.WEAKNESS)) {
                return Potions.WEAKNESS;
            }
            if (!target.hasEffect(MobEffects.POISON) && target.getHealth() >= 8.0F) {
                return Potions.POISON;
            }
            return Potions.HARMING;
        }
        return choices.get(this.random.nextInt(choices.size()));
    }

    // Same aim as a witch
    private void throwPotion(ServerLevel level, LivingEntity at, ItemStack potion, boolean lingering) {
        Vec3 movement = at.getDeltaMovement();
        double dx = at.getX() + movement.x - this.getX();
        double dy = at.getEyeY() - 1.1 - this.getY();
        double dz = at.getZ() + movement.z - this.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        if (lingering) {
            Projectile.spawnProjectileUsingShoot(ThrownLingeringPotion::new, level, potion, this, dx, dy + horizontalDistance * 0.2, dz, 0.75F, 8.0F);
        } else {
            Projectile.spawnProjectileUsingShoot(ThrownSplashPotion::new, level, potion, this, dx, dy + horizontalDistance * 0.2, dz, 0.75F, 8.0F);
        }
        this.swingForAttack(InteractionHand.MAIN_HAND);
        this.playSound(SoundEvents.WITCH_THROW, 1.0F, 0.8F + this.random.nextFloat() * 0.4F);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (this.healCooldown > 0) {
            this.healCooldown--;
        } else if (this.tickCount % HEAL_CHECK_INTERVAL == 0) {
            LivingEntity patient = this.findPatient(level);
            if (patient != null) {
                this.throwPotion(level, patient, PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HEALING), false);
                this.healCooldown = HEAL_COOLDOWN;
            }
        }
    }

    /// The most badly hurt hunter in reach (this one too) that the chemist can see
    private @Nullable LivingEntity findPatient(ServerLevel level) {
        return level.getEntitiesOfClass(Hunter.class, this.getBoundingBox().inflate(HEAL_RANGE),
                        hunter -> hunter.isAlive() && hunter.getHealth() < hunter.getMaxHealth() * HEAL_BELOW
                                && (hunter == this || this.getSensing().hasLineOfSight(hunter)))
                .stream().min(Comparator.comparingDouble(hunter -> hunter.getHealth() / hunter.getMaxHealth())).orElse(null);
    }
}

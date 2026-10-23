package com.zestudio.crimsoncovenant.entity;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.effect.HolyWaterEffect;
import com.zestudio.crimsoncovenant.registry.ModPotions;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownLingeringPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/// The hunters' champion, and the Master Vampire's counterpart. Always leads its party, carries a Smite axe
/// and a shield bearing the hunters' banner (which blocks some attacks from the front), and throws
/// splash potions of Holy Water at undead foes. Shows a boss bar to nearby players.
public class MasterHunter extends Hunter {
    private static final float SHIELD_BLOCK_CHANCE = 0.33F;

    // Holy Water throwing
    private static final int HOLY_WATER_MIN_COOLDOWN = 100;
    private static final int HOLY_WATER_EXTRA_COOLDOWN = 60;
    private static final double HOLY_WATER_MIN_RANGE = 3.0;
    private static final double HOLY_WATER_MAX_RANGE = 12.0;
    // Throw a lingering cloud when at least this many undead are within range of the target (target included)
    private static final int LINGERING_GROUP_SIZE = 2;
    private static final double LINGERING_GROUP_RANGE = 4.0;

    // Drinking a Potion of Regeneration once a fight is over
    private static final int CALM_TICKS_BEFORE_DRINKING = 100;
    private static final int DRINK_COOLDOWN = 600;
    private static final float DRINK_HEALTH_THRESHOLD = 0.75F;

    private int lastCombatTick = 0;
    private int drinkCooldown = 0;
    private ItemStack weaponWhileDrinking = ItemStack.EMPTY;

    private final ServerBossEvent bossEvent = new ServerBossEvent(Mth.createInsecureUUID(this.random), this.getDisplayName(),
            BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.PROGRESS);
    private int holyWaterCooldown = 20;

    public MasterHunter(EntityType<? extends MasterHunter> type, Level level) {
        super(type, level);
        this.setLeader(true);
        this.xpReward = 50;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 80.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.ARMOR, 8.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5)
                .add(Attributes.FOLLOW_RANGE, 40.0);
    }

    // ---- Equipment ----

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        // Gives the leader's banner; the weapons below replace the regular hunter gear
        super.populateDefaultEquipmentSlots(random, difficulty);
        ItemStack axe = new ItemStack(Items.IRON_AXE);
        this.enchantSmite(axe, 3);
        this.setItemSlot(EquipmentSlot.MAINHAND, axe);
        this.setItemSlot(EquipmentSlot.OFFHAND, createHunterShield());
    }

    /// A shield painted with the Hunter's Banner design
    private ItemStack createHunterShield() {
        ItemStack banner = createHunterBanner(this.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN));
        ItemStack shield = new ItemStack(Items.SHIELD);
        shield.set(DataComponents.BANNER_PATTERNS, banner.get(DataComponents.BANNER_PATTERNS));
        shield.set(DataComponents.BASE_COLOR, DyeColor.RED);
        return shield;
    }

    // ---- Combat ----

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (this.holyWaterCooldown > 0) {
            this.holyWaterCooldown--;
        } else if (this.canThrowHolyWaterAt(this.getTarget())) {
            this.throwHolyWater(level, this.getTarget());
            this.holyWaterCooldown = HOLY_WATER_MIN_COOLDOWN + this.random.nextInt(HOLY_WATER_EXTRA_COOLDOWN);
        }
        this.tickRecoveryDrink();
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
    }

    // ---- Recovering after a fight ----

    public boolean isDrinkingRegeneration() {
        return !this.weaponWhileDrinking.isEmpty();
    }

    private void tickRecoveryDrink() {
        if (this.getTarget() != null) {
            this.lastCombatTick = this.tickCount;
        }
        if (this.drinkCooldown > 0) {
            this.drinkCooldown--;
        }

        if (this.isDrinkingRegeneration()) {
            // Ambushed mid-drink: drop the potion and fight
            if (this.getTarget() != null && this.isUsingItem()) {
                this.stopUsingItem();
            }
            // Finished (the potion took effect and left a glass bottle) or interrupted: back to the axe
            if (!this.isUsingItem()) {
                this.setItemSlot(EquipmentSlot.MAINHAND, this.weaponWhileDrinking);
                this.weaponWhileDrinking = ItemStack.EMPTY;
            }
        } else if (this.shouldDrinkRegeneration()) {
            this.weaponWhileDrinking = this.getMainHandItem().copy();
            this.setItemSlot(EquipmentSlot.MAINHAND, PotionContents.createItemStack(Items.POTION, Potions.STRONG_REGENERATION));
            this.startUsingItem(InteractionHand.MAIN_HAND);
            this.drinkCooldown = DRINK_COOLDOWN;
        }
    }

    private boolean shouldDrinkRegeneration() {
        return this.getTarget() == null
                && this.drinkCooldown <= 0
                && this.getHealth() < this.getMaxHealth() * DRINK_HEALTH_THRESHOLD
                && !this.hasEffect(MobEffects.REGENERATION)
                && this.tickCount - this.lastCombatTick > CALM_TICKS_BEFORE_DRINKING
                && this.tickCount - this.getLastHurtByMobTimestamp() > CALM_TICKS_BEFORE_DRINKING;
    }

    private boolean canThrowHolyWaterAt(@Nullable LivingEntity target) {
        if (target == null || !target.isAlive() || !HolyWaterEffect.isAffected(target)) {
            return false;
        }
        double distance = this.distanceTo(target);
        return distance >= HOLY_WATER_MIN_RANGE && distance <= HOLY_WATER_MAX_RANGE && this.getSensing().hasLineOfSight(target);
    }

    // Same aiming as a witch throwing potions. Against a group of undead, throws a lingering cloud instead.
    private void throwHolyWater(ServerLevel level, LivingEntity target) {
        Vec3 targetMovement = target.getDeltaMovement();
        double dx = target.getX() + targetMovement.x - this.getX();
        double dy = target.getEyeY() - 1.1 - this.getY();
        double dz = target.getZ() + targetMovement.z - this.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        int undeadNearTarget = level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(LINGERING_GROUP_RANGE), HolyWaterEffect::isAffected).size();
        if (undeadNearTarget >= LINGERING_GROUP_SIZE) {
            ItemStack holyWater = PotionContents.createItemStack(Items.LINGERING_POTION, ModPotions.HOLY_WATER);
            Projectile.spawnProjectileUsingShoot(ThrownLingeringPotion::new, level, holyWater, this, dx, dy + horizontalDistance * 0.2, dz, 0.75F, 8.0F);
        } else {
            ItemStack holyWater = PotionContents.createItemStack(Items.SPLASH_POTION, ModPotions.HOLY_WATER);
            Projectile.spawnProjectileUsingShoot(ThrownSplashPotion::new, level, holyWater, this, dx, dy + horizontalDistance * 0.2, dz, 0.75F, 8.0F);
        }
        this.swingForAttack(InteractionHand.MAIN_HAND);
        this.playSound(SoundEvents.WITCH_THROW, 1.0F, 0.8F + this.random.nextFloat() * 0.4F);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (this.tryBlockWithShield(source, damage)) {
            return false;
        }
        return super.hurtServer(level, source, damage);
    }

    /// Some hits from the front are caught on the shield, which takes the damage instead.
    private boolean tryBlockWithShield(DamageSource source, float damage) {
        ItemStack shield = this.getItemBySlot(EquipmentSlot.OFFHAND);
        Vec3 sourcePos = source.getSourcePosition();
        if (!shield.is(Items.SHIELD) || sourcePos == null || source.is(DamageTypeTags.BYPASSES_SHIELD) || this.random.nextFloat() >= SHIELD_BLOCK_CHANCE) {
            return false;
        }
        Vec3 toSource = sourcePos.subtract(this.position()).horizontal().normalize();
        Vec3 facing = this.getViewVector(1.0F).horizontal().normalize();
        if (toSource.dot(facing) <= 0.0) {
            return false;
        }
        shield.hurtAndBreak(1 + Mth.floor(damage), this, EquipmentSlot.OFFHAND);
        this.playSound(SoundEvents.SHIELD_BLOCK.value(), 1.0F, 0.8F + this.random.nextFloat() * 0.4F);
        return true;
    }

    // ---- Boss bar ----

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        this.bossEvent.setName(this.getDisplayName());
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.storeNullable("WeaponWhileDrinking", ItemStack.CODEC, this.weaponWhileDrinking.isEmpty() ? null : this.weaponWhileDrinking);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.weaponWhileDrinking = input.read("WeaponWhileDrinking", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        this.setLeader(true);
        if (this.hasCustomName()) {
            this.bossEvent.setName(this.getDisplayName());
        }
    }
}

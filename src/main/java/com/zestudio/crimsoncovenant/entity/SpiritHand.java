package com.zestudio.crimsoncovenant.entity;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.curse.Curse;
import com.zestudio.crimsoncovenant.registry.ModEntities;
import com.zestudio.crimsoncovenant.skill.Enthrall;

import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/// Revenant's Grip (the Spirit Hand curse): a ghostly hand holding a copy of the cursed weapon. It hovers at its
/// owner's shoulder and goes after whatever they're fighting (or whatever's come for them, or failing either the
/// nearest monster) on its own, striking with the weapon, until the curse is spent and it fades.
public class SpiritHand extends Entity {
    private static final EntityDataAccessor<ItemStack> WEAPON = SynchedEntityData.defineId(SpiritHand.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Boolean> STRIKING = SynchedEntityData.defineId(SpiritHand.class, EntityDataSerializers.BOOLEAN);
    private static final double RANGE = 12.0;
    private static final double SPEED = 0.55;
    private static final int STRIKE_INTERVAL = 16;

    private @Nullable UUID owner;
    private int level = 1;
    private int life;
    private int strikeCooldown;
    private @Nullable LivingEntity target;

    public SpiritHand(EntityType<? extends SpiritHand> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static SpiritHand summon(ServerLevel level, Player owner, ItemStack weapon, int curseLevel, int ticks) {
        SpiritHand hand = new SpiritHand(ModEntities.SPIRIT_HAND.get(), level);
        hand.owner = owner.getUUID();
        hand.level = curseLevel;
        hand.life = ticks;
        hand.entityData.set(WEAPON, weapon.copyWithCount(1));
        Vec3 at = hand.restingPlace(owner);
        hand.snapTo(at.x, at.y, at.z, owner.getYRot(), 0.0F);
        level.addFreshEntity(hand);
        level.sendParticles(ParticleTypes.SOUL, at.x, at.y, at.z, 12, 0.2, 0.2, 0.2, 0.02);
        return hand;
    }

    public ItemStack getWeapon() {
        return this.entityData.get(WEAPON);
    }

    public boolean isStriking() {
        return this.entityData.get(STRIKING);
    }

    public @Nullable Player getOwner() {
        return this.owner == null ? null : this.level().getPlayerByUUID(this.owner);
    }

    // Just above and behind the owner's right shoulder
    private Vec3 restingPlace(Player owner) {
        float yaw = owner.getYRot() * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
        Vec3 back = new Vec3(Mth.sin(yaw), 0.0, -Mth.cos(yaw));
        return owner.position().add(right.scale(0.8)).add(back.scale(0.3)).add(0.0, owner.getBbHeight() + 0.15 + Mth.sin(this.tickCount * 0.15F) * 0.08, 0.0);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        Player owner = this.getOwner();
        if (owner == null || !owner.isAlive() || --this.life <= 0 || owner.distanceToSqr(this) > 48 * 48) {
            level.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY(), this.getZ(), 8, 0.2, 0.2, 0.2, 0.02);
            this.discard();
            return;
        }
        if (this.strikeCooldown > 0) {
            this.strikeCooldown--;
        }
        if (this.tickCount % 10 == 0 || this.target == null || !this.target.isAlive()) {
            this.target = this.chooseTarget(owner);
        }
        Vec3 goal = this.target != null ? this.target.position().add(0.0, this.target.getBbHeight() * 0.6, 0.0) : this.restingPlace(owner);
        Vec3 toGoal = goal.subtract(this.position());
        double distance = toGoal.length();
        Vec3 step = distance > SPEED ? toGoal.scale(SPEED / distance) : toGoal;
        this.setPos(this.position().add(step));
        Vec3 facing = this.target != null ? toGoal : owner.getLookAngle();
        this.setYRot((float) (Mth.atan2(-facing.x, facing.z) * Mth.RAD_TO_DEG));
        this.entityData.set(STRIKING, this.target != null && distance < 2.0);
        if (this.target != null && distance < 2.0 && this.strikeCooldown == 0) {
            this.strike(level, owner, this.target);
        }
        if (this.tickCount % 3 == 0) {
            level.sendParticles(new DustParticleOptions(Curse.COLOUR, 0.6F), this.getX(), this.getY(), this.getZ(), 1, 0.1, 0.1, 0.1, 0.0);
        }
    }

    // What the owner's fighting, or what's after them, or the nearest monster; never their own thralls
    private @Nullable LivingEntity chooseTarget(Player owner) {
        for (LivingEntity candidate : new LivingEntity[] {owner.getLastHurtMob(), owner.getLastHurtByMob()}) {
            if (candidate != null && candidate.isAlive() && candidate != owner && candidate.distanceToSqr(owner) < RANGE * RANGE && !Enthrall.isThrallOf(candidate, owner)) {
                return candidate;
            }
        }
        return this.level().getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(RANGE),
                        entity -> entity instanceof Enemy && entity.isAlive() && !Enthrall.isThrallOf(entity, owner))
                .stream().min((a, b) -> Double.compare(a.distanceToSqr(owner), b.distanceToSqr(owner))).orElse(null);
    }

    private void strike(ServerLevel level, Player owner, LivingEntity victim) {
        this.strikeCooldown = STRIKE_INTERVAL;
        float damage = (float) owner.getAttributeValue(Attributes.ATTACK_DAMAGE) * (0.5F + 0.25F * this.level);
        DamageSource blow = level.damageSources().indirectMagic(this, owner);
        if (victim.hurtServer(level, blow, damage)) {
            Vec3 away = victim.position().subtract(this.position()).normalize();
            victim.knockback(0.3, -away.x, -away.z, blow, damage);
        }
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.7F, 1.3F);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, victim.getX(), victim.getY(0.5), victim.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        entityData.define(WEAPON, ItemStack.EMPTY);
        entityData.define(STRIKING, false);
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
    protected void readAdditionalSaveData(ValueInput input) {
        this.owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
        this.level = input.getIntOr("level", 1);
        this.life = input.getIntOr("life", 0);
        this.entityData.set(WEAPON, input.read("weapon", ItemStack.CODEC).orElse(ItemStack.EMPTY));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.storeNullable("owner", UUIDUtil.CODEC, this.owner);
        output.putInt("level", this.level);
        output.putInt("life", this.life);
        if (!this.getWeapon().isEmpty()) {
            output.store("weapon", ItemStack.CODEC, this.getWeapon());
        }
    }
}

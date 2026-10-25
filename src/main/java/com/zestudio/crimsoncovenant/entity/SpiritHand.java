package com.zestudio.crimsoncovenant.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.curse.Curse;
import com.zestudio.crimsoncovenant.curse.Curses;
import com.zestudio.crimsoncovenant.hunter.HunterRanks;
import com.zestudio.crimsoncovenant.registry.ModEntities;
import com.zestudio.crimsoncovenant.skill.Enthrall;

import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/// Revenant's Grip (the Spirit Hand curse, always awake): right-click with the cursed weapon and it leaves its owner's
/// inventory to float beside them on its own, held by a spirit unseen, leaving their hands free for other things. It
/// drifts at their shoulder, and goes after whatever they're fighting (or whatever's come for them, or failing either
/// the nearest monster) at a glide, never far from them, striking as it would in a hand, and wearing down as it would
/// in a hand (if it breaks, the spirit lets it go). Right-click the floating weapon to take it back: into its old slot
/// if that's free, else wherever there's room. It comes back too when its owner dies (to be dropped or kept like the
/// rest of their things), leaves the world, goes to another dimension, or can no longer wield the curse.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public class SpiritHand extends Entity {
    private static final EntityDataAccessor<ItemStack> WEAPON = SynchedEntityData.defineId(SpiritHand.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Boolean> STRIKING = SynchedEntityData.defineId(SpiritHand.class, EntityDataSerializers.BOOLEAN);
    /// How far from its owner it will go after something
    private static final double LEASH = 8.0;
    /// Blocks a tick at full glide, and how quickly it gets up to it
    private static final double SPEED = 0.16;
    private static final double ACCELERATION = 0.04;
    /// How close it comes to what it strikes: just within a blade's reach, never inside it
    private static final double REACH = 1.8;
    private static final int STRIKE_INTERVAL = 24;
    /// Left farther behind than this (its owner riding or flying off), it catches up at once
    private static final double CATCH_UP = 16.0;
    /// Each owner's hand, if it's out. Server side only.
    private static final Map<UUID, SpiritHand> HANDS = new HashMap<>();

    private @Nullable UUID owner;
    private int level = 1;
    /// The inventory slot the weapon came from, and goes back to if it's free
    private int slot;
    private int strikeCooldown = STRIKE_INTERVAL;
    private Vec3 velocity = Vec3.ZERO;
    private @Nullable LivingEntity target;

    public SpiritHand(EntityType<? extends SpiritHand> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /// Sends the weapon in the owner's `hand` out, if it bears Revenant's Grip and they can wield the curse: any hand of
    /// theirs already out comes back first. Returns the hand, or null if the weapon can't go.
    public static @Nullable SpiritHand sendOut(ServerLevel level, Player owner, InteractionHand hand) {
        int curseLevel = Curses.looseLevel(owner, Curse.SPIRIT_HAND, owner.getItemInHand(hand));
        if (curseLevel == 0 || !owner.isAlive() || owner.isSpectator()) {
            return null;
        }
        recall(owner);
        return summon(level, owner, curseLevel, hand == InteractionHand.MAIN_HAND ? owner.getInventory().getSelectedSlot() : Inventory.SLOT_OFFHAND);
    }

    /// Takes the weapon out of the owner's inventory `slot` and into a spirit hand beside them
    public static SpiritHand summon(ServerLevel level, Player owner, int curseLevel, int slot) {
        SpiritHand hand = new SpiritHand(ModEntities.SPIRIT_HAND.get(), level);
        Inventory inventory = owner.getInventory();
        hand.owner = owner.getUUID();
        hand.level = curseLevel;
        hand.slot = slot;
        hand.entityData.set(WEAPON, inventory.getItem(hand.slot).copy());
        inventory.setItem(hand.slot, ItemStack.EMPTY);
        Vec3 at = hand.restingPlace(owner);
        hand.snapTo(at.x, at.y, at.z, owner.getYRot(), 0.0F);
        level.addFreshEntity(hand);
        HANDS.put(owner.getUUID(), hand);
        level.sendParticles(ParticleTypes.SOUL, at.x, at.y, at.z, 12, 0.2, 0.2, 0.2, 0.02);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 0.6F, 1.4F);
        return hand;
    }

    /// The owner's hand, if it's out
    public static @Nullable SpiritHand of(Player owner) {
        SpiritHand hand = HANDS.get(owner.getUUID());
        return hand == null || hand.isRemoved() ? null : hand;
    }

    /// Calls the hand back when its owner can no longer keep it out: dead, a spectator, gone to another dimension, or no
    /// longer of rank for the curse. Called every tick for each player.
    public static void tend(ServerLevel level, Player owner) {
        SpiritHand hand = of(owner);
        if (hand != null && (!owner.isAlive() || owner.isSpectator() || hand.level() != owner.level() || HunterRanks.get(owner) < Curse.rankFor(hand.level))) {
            hand.fade((ServerLevel) hand.level(), owner);
        }
    }

    // Right-click with a weapon bearing Revenant's Grip: it goes out, instead of whatever the weapon would do (both
    // sides decide alike, the hunter's rank being known to their own client)
    @SubscribeEvent
    static void sendOutOnRightClick(PlayerInteractEvent.RightClickItem event) {
        Player owner = event.getEntity();
        if (Curses.looseLevel(owner, Curse.SPIRIT_HAND, event.getItemStack()) == 0) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (owner.level() instanceof ServerLevel level) {
            sendOut(level, owner, event.getHand());
        }
    }

    // Its owner right-clicks it: they take the weapon back
    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (!(this.level() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        if (!player.getUUID().equals(this.owner)) {
            return InteractionResult.PASS;
        }
        this.fade(level, player);
        return InteractionResult.SUCCESS;
    }

    // Blows go through it, to whatever it's fighting
    @Override
    public boolean skipAttackInteraction(Entity source) {
        return true;
    }

    /// Calls the owner's hand back, if it's out, giving the weapon back
    public static void recall(Player owner) {
        SpiritHand hand = of(owner);
        if (hand != null) {
            hand.fade((ServerLevel) hand.level(), owner);
        }
    }

    @SubscribeEvent
    static void tendEveryTick(PlayerTickEvent.Post event) {
        if (event.getEntity().level() instanceof ServerLevel level) {
            tend(level, event.getEntity());
        }
    }

    // Back before the death drops, so the weapon's dropped (or kept) along with everything else
    @SubscribeEvent
    static void recallOnDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide()) {
            recall(player);
        }
    }

    @SubscribeEvent
    static void recallOnLeaving(PlayerEvent.PlayerLoggedOutEvent event) {
        recall(event.getEntity());
    }

    @SubscribeEvent
    static void recallOnTravel(PlayerEvent.PlayerChangedDimensionEvent event) {
        recall(event.getEntity());
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

    // Beside and a little behind the owner's right shoulder, bobbing a little
    private Vec3 restingPlace(Player owner) {
        float yaw = owner.getYRot() * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
        Vec3 back = new Vec3(Mth.sin(yaw), 0.0, -Mth.cos(yaw));
        return owner.position().add(right.scale(1.0)).add(back.scale(0.4)).add(0.0, owner.getBbHeight() * 0.75 + Mth.sin(this.tickCount * 0.15F) * 0.1, 0.0);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        Player owner = this.getOwner();
        if (owner == null) {
            // Its owner's away (it was saved with the world while out): it waits for them, weapon in hand, until they come
            // and take it back
            return;
        }
        if (of(owner) != this) {
            if (of(owner) != null) {
                // A stray (its owner has another hand out): give the weapon back and go
                this.fade(level, owner);
                return;
            }
            HANDS.put(owner.getUUID(), this);
        }
        if (!owner.isAlive()) {
            this.fade(level, owner);
            return;
        }
        if (this.distanceToSqr(owner) > CATCH_UP * CATCH_UP) {
            this.setPos(this.restingPlace(owner));
            this.velocity = Vec3.ZERO;
        }
        if (this.strikeCooldown > 0) {
            this.strikeCooldown--;
        }
        if (this.tickCount % 10 == 0 || this.target == null || !this.target.isAlive() || this.target.distanceToSqr(owner) > LEASH * LEASH) {
            this.target = this.chooseTarget(owner);
        }
        // It glides: toward a spot just short of its target, or back to its owner's shoulder
        Vec3 goal;
        if (this.target != null) {
            Vec3 targetCentre = this.target.position().add(0.0, this.target.getBbHeight() * 0.6, 0.0);
            Vec3 fromTarget = this.position().subtract(targetCentre);
            Vec3 approach = fromTarget.lengthSqr() < 1.0E-4 ? owner.position().subtract(targetCentre).normalize() : fromTarget.normalize();
            goal = targetCentre.add(approach.scale(REACH * 0.8));
        } else {
            goal = this.restingPlace(owner);
        }
        Vec3 toGoal = goal.subtract(this.position());
        double distance = toGoal.length();
        Vec3 wanted = distance < 0.05 ? Vec3.ZERO : toGoal.scale(Math.min(SPEED, distance * 0.5) / distance);
        Vec3 change = wanted.subtract(this.velocity);
        if (change.length() > ACCELERATION) {
            change = change.normalize().scale(ACCELERATION);
        }
        this.velocity = this.velocity.add(change);
        this.setPos(this.position().add(this.velocity));
        Vec3 facing = this.target != null ? this.target.position().subtract(this.position()) : owner.getLookAngle();
        this.setYRot((float) (Mth.atan2(-facing.x, facing.z) * Mth.RAD_TO_DEG));
        boolean inReach = this.target != null && this.distanceTo(this.target) < REACH + this.target.getBbWidth() * 0.5;
        this.entityData.set(STRIKING, inReach);
        if (inReach && this.strikeCooldown == 0) {
            this.strike(level, owner, this.target);
        }
        if (this.tickCount % 3 == 0) {
            level.sendParticles(new DustParticleOptions(Curse.COLOUR, 0.6F), this.getX(), this.getY(), this.getZ(), 1, 0.1, 0.1, 0.1, 0.0);
        }
    }

    // What the owner's fighting, or what's after them, or the nearest monster: never their own thralls, and never anything
    // farther from them than it will go
    private @Nullable LivingEntity chooseTarget(Player owner) {
        for (LivingEntity candidate : new LivingEntity[] {owner.getLastHurtMob(), owner.getLastHurtByMob()}) {
            if (candidate != null && candidate.isAlive() && candidate != owner && candidate.distanceToSqr(owner) < LEASH * LEASH && !Enthrall.isThrallOf(candidate, owner)) {
                return candidate;
            }
        }
        return this.level().getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(LEASH),
                        entity -> entity instanceof Enemy && entity.isAlive() && !Enthrall.isThrallOf(entity, owner))
                .stream().min((a, b) -> Double.compare(a.distanceToSqr(owner), b.distanceToSqr(owner))).orElse(null);
    }

    /// What a blow from the weapon is worth on its own (a fist's 1, plus the weapon's damage), and what this hand makes of
    /// it: a little less than its owner would, more at higher levels
    public static float blowDamage(ItemStack weapon, int curseLevel) {
        double weaponDamage = weapon.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
                .compute(Attributes.ATTACK_DAMAGE, 1.0, EquipmentSlot.MAINHAND);
        return (float) weaponDamage * (0.35F + 0.15F * curseLevel);
    }

    private void strike(ServerLevel level, Player owner, LivingEntity victim) {
        this.strikeCooldown = STRIKE_INTERVAL;
        float damage = blowDamage(this.getWeapon(), this.level);
        DamageSource blow = level.damageSources().indirectMagic(this, owner);
        if (victim.hurtServer(level, blow, damage)) {
            Vec3 away = victim.position().subtract(this.position()).normalize();
            victim.knockback(0.3, -away.x, -away.z, blow, damage);
        }
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.7F, 1.3F);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, victim.getX(), victim.getY(0.5), victim.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        this.wear(level, owner);
    }

    // Each blow wears the weapon as a blow in its owner's hand would (Unbreaking and all); worn through, it shatters
    private void wear(ServerLevel level, Player owner) {
        ItemStack weapon = this.getWeapon().copy();
        weapon.hurtAndBreak(1, level, owner, broken -> {});
        if (!weapon.isEmpty()) {
            this.entityData.set(WEAPON, weapon);
            return;
        }
        this.entityData.set(WEAPON, ItemStack.EMPTY);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ITEM_BREAK.value(), SoundSource.PLAYERS, 0.8F, 0.8F);
        level.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY(), this.getZ(), 12, 0.2, 0.2, 0.2, 0.03);
        this.discard();
    }

    /// The weapon goes back to its owner, into its own slot if that's free (onto the ground where they fell, if they're
    /// already dead), and the hand fades
    public void fade(ServerLevel level, @Nullable Player owner) {
        ItemStack weapon = this.getWeapon();
        if (!weapon.isEmpty()) {
            if (owner != null && owner.isAlive()) {
                Inventory inventory = owner.getInventory();
                if (inventory.getItem(this.slot).isEmpty()) {
                    inventory.setItem(this.slot, weapon.copy());
                } else {
                    inventory.placeItemBackInInventory(weapon.copy(), Prediction.SERVER_ONLY);
                }
                level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 0.8F);
            } else {
                Vec3 at = owner != null ? owner.position() : this.position();
                ItemEntity dropped = new ItemEntity(level, at.x, at.y + 0.5, at.z, weapon.copy());
                dropped.setDefaultPickUpDelay();
                level.addFreshEntity(dropped);
            }
            this.entityData.set(WEAPON, ItemStack.EMPTY);
        }
        level.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY(), this.getZ(), 8, 0.2, 0.2, 0.2, 0.02);
        this.discard();
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        super.remove(reason);
        if (this.owner != null) {
            HANDS.remove(this.owner, this);
        }
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

    // So its owner can take it back with a right-click
    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
        this.level = input.getIntOr("level", 1);
        this.slot = input.getIntOr("slot", 0);
        this.entityData.set(WEAPON, input.read("weapon", ItemStack.CODEC).orElse(ItemStack.EMPTY));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.storeNullable("owner", UUIDUtil.CODEC, this.owner);
        output.putInt("level", this.level);
        output.putInt("slot", this.slot);
        if (!this.getWeapon().isEmpty()) {
            output.store("weapon", ItemStack.CODEC, this.getWeapon());
        }
    }
}

package com.zestudio.crimsoncovenant.entity;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.registry.ModDamageTypes;
import com.zestudio.crimsoncovenant.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/// The Spirit of Halloween: what rises from the Headless Horseman when he falls. A gaunt, headless ghost, its ribs
/// broken out through its shroud. It rises up out of the Horseman's Crooked Tree and drifts through walls after his
/// horse (see HorsemansSteed): if it reaches the horse, the Horseman rides again. With the horse dead and buried, it
/// waits by the tree for it to dig its way back out.
///
/// Nothing harms it but Holy Water, and even that only drives it back: it takes HOLY_HITS splashes (each a second or
/// more apart) to banish it for good, and only then does it give up the Horseman's head. It fights under a boss bar
/// that shows how many it has left to take.
public class HalloweenSpirit extends Monster {
    public static final int HOLY_HITS = 3;
    /// How soon after one splash the next can count
    public static final int HOLY_HIT_GAP = 20;
    /// How long a splash leaves it reeling, unable to go on
    private static final int REEL_TICKS = 40;
    /// Blocks a tick it drifts at
    public static final double SPEED = 0.11;
    /// How close it has to come to its steed to ride again
    public static final double REMOUNT_RANGE = 1.8;
    /// How long it takes to rise up out of the tree, and how high it rises
    public static final int RISE_TICKS = 40;
    private static final double RISE_HEIGHT = 4.0;
    /// How far off it can sense its horse
    private static final double STEED_RANGE = 160.0;
    /// How long it waits with no horse about before calling one up out of the ground
    private static final int HORSELESS_TICKS = 40;
    private static final int CHILL_COOLDOWN = 60;

    private final ServerBossEvent bossEvent = new ServerBossEvent(Mth.createInsecureUUID(this.random), this.getDisplayName(),
            BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_6);
    private @Nullable UUID steed;
    private @Nullable BlockPos tree;
    private int rising;
    private int horseless;
    private int holyHitsLeft = HOLY_HITS;
    private long lastHolyHit = Long.MIN_VALUE / 2;
    private int reeling;
    private int chillCooldown;
    private boolean banished;

    public HalloweenSpirit(EntityType<? extends HalloweenSpirit> type, Level level) {
        super(type, level);
        this.xpReward = 60;
        this.setNoGravity(true);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 100.0)
                .add(Attributes.FOLLOW_RANGE, 64.0);
    }

    /// The Spirit of the Horseman of the tree at `tree` rising up out of it, or out of where he fell (`fell`) if he had
    /// none, to go after `steed` (or whichever of his horses it can find)
    public static @Nullable HalloweenSpirit riseFrom(ServerLevel level, @Nullable BlockPos tree, Vec3 fell, @Nullable HorsemansSteed steed) {
        HalloweenSpirit spirit = ModEntities.HALLOWEEN_SPIRIT.get().create(level, EntitySpawnReason.EVENT);
        if (spirit == null) {
            return null;
        }
        spirit.tree = tree != null ? tree : BlockPos.containing(fell);
        Vec3 from = tree != null ? Vec3.atBottomCenterOf(tree) : fell;
        spirit.snapTo(from.x, from.y, from.z, level.getRandom().nextFloat() * 360.0F, 0.0F);
        spirit.rising = RISE_TICKS;
        if (steed != null) {
            spirit.setSteed(steed);
        }
        level.addFreshEntity(spirit);
        level.sendParticles(ParticleTypes.SOUL, from.x, from.y + 1.5, from.z, 40, 0.4, 1.5, 0.4, 0.04);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 3.0F, 0.5F);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.WITCH_CELEBRATE, SoundSource.HOSTILE, 2.0F, 0.4F);
        return spirit;
    }

    public void setSteed(HorsemansSteed steed) {
        this.steed = steed.getUUID();
    }

    public @Nullable BlockPos getTree() {
        return this.tree;
    }

    public boolean isRising() {
        return this.rising > 0;
    }

    /// Its horse: the one it was after, or any horse bound to its tree (one still in the ground included)
    public @Nullable HorsemansSteed findSteed() {
        if (!(this.level() instanceof ServerLevel level)) {
            return null;
        }
        if (this.steed != null && level.getEntity(this.steed) instanceof HorsemansSteed found && found.isAlive()) {
            return found;
        }
        HorsemansSteed found = level.getEntitiesOfClass(HorsemansSteed.class, this.getBoundingBox().inflate(STEED_RANGE),
                horse -> horse.isAlive() && !horse.isVehicle() && this.tree != null && this.tree.equals(horse.getTree())).stream()
                .min(java.util.Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        if (found != null) {
            this.setSteed(found);
        }
        return found;
    }

    public int getHolyHitsLeft() {
        return this.holyHitsLeft;
    }

    // It drifts by itself (see tick), so no goals
    @Override
    protected void registerGoals() {
    }

    @Override
    public void tick() {
        this.noPhysics = true;
        super.tick();
        this.noPhysics = false;
        this.setNoGravity(true);
        if (this.level().isClientSide()) {
            if (this.random.nextInt(2) == 0) {
                this.level().addParticle(ParticleTypes.SOUL, this.getRandomX(0.6), this.getRandomY(), this.getRandomZ(0.6), 0.0, 0.03, 0.0);
            }
            return;
        }
        if (this.level() instanceof ServerLevel level) {
            this.seekSteed(level);
            this.chillThoseNear();
            this.bossEvent.setProgress((float) this.holyHitsLeft / HOLY_HITS);
        }
    }

    // Up out of the tree; then after the horse, through anything in the way. With no horse about at all, it calls one up
    // out of the ground by the tree.
    private void seekSteed(ServerLevel level) {
        if (this.rising > 0) {
            this.rising--;
            this.setDeltaMovement(0.0, RISE_HEIGHT / RISE_TICKS, 0.0);
            level.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY(), this.getZ(), 2, 0.3, 0.2, 0.3, 0.02);
            return;
        }
        if (this.reeling > 0) {
            this.reeling--;
            this.setDeltaMovement(this.getDeltaMovement().scale(0.85));
            return;
        }
        HorsemansSteed steed = this.findSteed();
        if (steed == null) {
            this.setDeltaMovement(this.getDeltaMovement().scale(0.8));
            if (++this.horseless >= HORSELESS_TICKS && this.tree != null) {
                this.setSteed(HorsemansSteed.buryAt(level, this.tree));
                this.horseless = 0;
            }
            return;
        }
        this.horseless = 0;
        // Still in the ground: it hovers over the spot, waiting
        Vec3 toSteed = steed.position().add(0.0, steed.isRising() ? 3.0 : 0.5, 0.0).subtract(this.position());
        if (!steed.isRising() && toSteed.length() <= REMOUNT_RANGE) {
            this.rideAgain(level, steed);
            return;
        }
        if (toSteed.length() < 0.5) {
            this.setDeltaMovement(Vec3.ZERO);
            return;
        }
        this.setDeltaMovement(toSteed.normalize().scale(SPEED));
        this.setYRot(-((float) Mth.atan2(toSteed.x, toSteed.z)) * Mth.RAD_TO_DEG);
        this.yBodyRot = this.getYRot();
        this.yHeadRot = this.getYRot();
    }

    /// Back on its horse: the Headless Horseman rides again, and goes for whoever's nearest
    public @Nullable HeadlessHorseman rideAgain(ServerLevel level, HorsemansSteed steed) {
        Vec3 at = steed.position();
        this.discard();
        return HeadlessHorseman.summon(level, at, level.getNearestPlayer(at.x, at.y, at.z, 48.0, true), this.tree, steed);
    }

    // Those it drifts close to feel the dark close in
    private void chillThoseNear() {
        if (this.chillCooldown > 0) {
            this.chillCooldown--;
            return;
        }
        boolean chilled = false;
        for (Player player : this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(2.0), player -> !player.isSpectator())) {
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80), this);
            chilled = true;
        }
        if (chilled) {
            this.chillCooldown = CHILL_COOLDOWN;
            this.playSound(SoundEvents.SOUL_ESCAPE.value(), 2.0F, 0.4F);
        }
    }

    /// A splash of Holy Water (see effect.HolyWaterEffect): drives it back and leaves it reeling, and the last of
    /// HOLY_HITS banishes it for good. Returns whether the splash counted (it doesn't, so soon after the last).
    public boolean sanctify(ServerLevel level, @Nullable Entity thrower) {
        if (this.banished || level.getGameTime() - this.lastHolyHit < HOLY_HIT_GAP) {
            return false;
        }
        this.lastHolyHit = level.getGameTime();
        this.holyHitsLeft--;
        this.reeling = REEL_TICKS;
        HorsemansSteed steed = this.findSteed();
        Vec3 back = steed != null ? this.position().subtract(steed.position()).normalize() : Vec3.ZERO;
        this.setDeltaMovement(back.scale(0.8).add(0.0, 0.2, 0.0));
        level.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY(0.6), this.getZ(), 25, 0.4, 0.6, 0.4, 0.08);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GHAST_SCREAM, SoundSource.HOSTILE, 2.0F, 0.6F);
        if (this.holyHitsLeft <= 0) {
            this.banished = true;
            if (steed != null) {
                steed.crumble();
            }
            this.hurtServer(level, this.damageSources().source(ModDamageTypes.HOLY_FIRE, thrower, thrower), Float.MAX_VALUE);
        }
        this.bossEvent.setProgress((float) Math.max(0, this.holyHitsLeft) / HOLY_HITS);
        return true;
    }

    // Only the last splash of Holy Water can end it (or /kill)
    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        if (!this.banished && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return true;
        }
        return super.isInvulnerableTo(level, source);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    @Override
    protected boolean isAffectedByBlocks() {
        return !this.isRemoved();
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
        output.storeNullable("Steed", UUIDUtil.CODEC, this.steed);
        output.storeNullable("Tree", BlockPos.CODEC, this.tree);
        output.putInt("Rising", this.rising);
        output.putInt("HolyHitsLeft", this.holyHitsLeft);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.steed = input.read("Steed", UUIDUtil.CODEC).orElse(null);
        this.tree = input.read("Tree", BlockPos.CODEC).orElse(null);
        this.rising = input.getIntOr("Rising", 0);
        this.holyHitsLeft = input.getIntOr("HolyHitsLeft", HOLY_HITS);
        if (this.hasCustomName()) {
            this.bossEvent.setName(this.getDisplayName());
        }
    }

    // ---- Sounds ----

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.WITCH_CELEBRATE;
    }

    @Override
    public float getVoicePitch() {
        return 0.4F;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.GHAST_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.GHAST_DEATH;
    }
}

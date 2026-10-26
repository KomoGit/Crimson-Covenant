package com.zestudio.crimsoncovenant.entity;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.registry.ModDamageTypes;

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
/// broken out through its shroud, drifting through walls after its Ghost Steed (see GhostSteed). If it reaches the
/// horse, the Horseman rides again.
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
    public static final double SPEED = 0.14;
    /// How close it has to come to its steed to ride again
    public static final double REMOUNT_RANGE = 1.8;
    private static final int CHILL_COOLDOWN = 60;

    private final ServerBossEvent bossEvent = new ServerBossEvent(Mth.createInsecureUUID(this.random), this.getDisplayName(),
            BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_6);
    private @Nullable UUID steed;
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

    public void setSteed(GhostSteed steed) {
        this.steed = steed.getUUID();
    }

    public @Nullable GhostSteed findSteed() {
        return this.steed != null && this.level() instanceof ServerLevel level && level.getEntity(this.steed) instanceof GhostSteed found && found.isAlive()
                ? found
                : null;
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

    // After its steed, through anything in the way; if it's lost, the steed comes back to it from down the road
    private void seekSteed(ServerLevel level) {
        if (this.reeling > 0) {
            this.reeling--;
            this.setDeltaMovement(this.getDeltaMovement().scale(0.85));
            return;
        }
        GhostSteed steed = this.findSteed();
        if (steed == null) {
            if (this.tickCount % 20 == 0) {
                float angle = this.random.nextFloat() * Mth.TWO_PI;
                this.setSteed(GhostSteed.gallopFrom(level, this.position(),
                        new Vec3(Mth.cos(angle), 0.0, Mth.sin(angle)).scale(HeadlessHorseman.STEED_FLIGHT)));
            }
            return;
        }
        Vec3 toSteed = steed.position().add(0.0, 0.5, 0.0).subtract(this.position());
        if (toSteed.length() <= REMOUNT_RANGE) {
            this.rideAgain(level, steed);
            return;
        }
        this.setDeltaMovement(toSteed.normalize().scale(SPEED));
        this.setYRot(-((float) Mth.atan2(toSteed.x, toSteed.z)) * Mth.RAD_TO_DEG);
        this.yBodyRot = this.getYRot();
        this.yHeadRot = this.getYRot();
    }

    /// Back on its steed: the Headless Horseman rides again, and goes for whoever's nearest
    public @Nullable HeadlessHorseman rideAgain(ServerLevel level, GhostSteed steed) {
        Vec3 at = steed.position();
        steed.fade();
        this.discard();
        return HeadlessHorseman.summon(level, at, level.getNearestPlayer(at.x, at.y, at.z, 48.0, true));
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
        GhostSteed steed = this.findSteed();
        Vec3 back = steed != null ? this.position().subtract(steed.position()).normalize() : Vec3.ZERO;
        this.setDeltaMovement(back.scale(0.8).add(0.0, 0.2, 0.0));
        level.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY(0.6), this.getZ(), 25, 0.4, 0.6, 0.4, 0.08);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GHAST_SCREAM, SoundSource.HOSTILE, 2.0F, 0.6F);
        if (this.holyHitsLeft <= 0) {
            this.banished = true;
            if (steed != null) {
                steed.fade();
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
        output.putInt("HolyHitsLeft", this.holyHitsLeft);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.steed = input.read("Steed", UUIDUtil.CODEC).orElse(null);
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

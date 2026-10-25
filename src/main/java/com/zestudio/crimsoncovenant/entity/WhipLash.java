package com.zestudio.crimsoncovenant.entity;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.item.WhipItem;
import com.zestudio.crimsoncovenant.registry.ModEntities;
import com.zestudio.crimsoncovenant.curse.Grapple;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.entity.ai.attributes.Attributes;
import java.util.Comparator;
import java.util.List;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/// A whip's lash: thrown out from its wielder's hand along their aim to the whip's full reach, cracking at the tip,
/// and drawn back, in a few ticks (see client.renderer.WhipLashRenderer for its shape). The crack is the blow, and it
/// cuts through everything along the lash (along the wielder's aim, within reach, not behind a wall): the nearest takes
/// the wielder's full attack, enchantments, silver, hauntings and all; those beyond it the same blow, less the
/// enchantments. While a lash is out, its wielder can't throw another.
///
/// Not every crack is a blow (see Kind): a right-click's lash catches the first creature it reaches and yanks it in,
/// Gravequake's comes down on the ground before its wielder, and Wraith's Tether's hooks round a block and stays taut
/// while its wielder is hauled in along it (see curse.Grapple). Those last two are anchored: their tip is a fixed point,
/// not the end of the wielder's aim.
public class WhipLash extends Entity {
    private static final EntityDataAccessor<Integer> WIELDER = SynchedEntityData.defineId(WhipLash.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFETIME = SynchedEntityData.defineId(WhipLash.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> ANCHORED = SynchedEntityData.defineId(WhipLash.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Vector3fc> ANCHOR = SynchedEntityData.defineId(WhipLash.class, EntityDataSerializers.VECTOR3);
    /// How long the lash is out, and when it cracks
    public static final int LASH_TICKS = 8;
    public static final int CRACK_TICK = 3;
    /// The whip's full reach, from the hand
    public static final double REACH = 3.0 + WhipItem.EXTRA_REACH;
    /// How near the line of the lash a creature must be for its tip to catch it
    private static final double CATCH_MARGIN = 0.3;
    /// A right-click's pull: how hard it can yank, and how far short of the wielder it means to land what it caught
    private static final double PULL_MAX_SPEED = 1.1;
    private static final double PULL_LANDS_SHORT = 1.8;
    private static final double PULL_LIFT = 0.35;

    /// What a crack does
    public enum Kind {
        /// The wielder's blow, on everything along the lash (the attack key)
        STRIKE,
        /// Yanks the first creature it reaches in towards the wielder (right-click)
        PULL,
        /// Only seen and heard: brought down on the ground (Gravequake), or hooked round a block (Wraith's Tether)
        SHOW
    }

    private Kind kind = Kind.STRIKE;

    public WhipLash(EntityType<? extends WhipLash> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /// The wielder's whip cracks out (unless their last lash is still out)
    public static @Nullable WhipLash crack(Player wielder) {
        return crack(wielder, Kind.STRIKE, null, LASH_TICKS);
    }

    /// The wielder's whip cracks out, doing `kind`, its tip at `anchor` if there is one (else at the end of their aim),
    /// out for `lifetime` ticks: unless their last lash is still out
    public static @Nullable WhipLash crack(Player wielder, Kind kind, @Nullable Vec3 anchor, int lifetime) {
        if (!(wielder.level() instanceof ServerLevel level) || isLashing(wielder)) {
            return null;
        }
        WhipLash lash = new WhipLash(ModEntities.WHIP_LASH.get(), level);
        lash.kind = kind;
        lash.entityData.set(WIELDER, wielder.getId());
        lash.entityData.set(LIFETIME, lifetime);
        if (anchor != null) {
            lash.entityData.set(ANCHORED, true);
            lash.entityData.set(ANCHOR, anchor.toVector3f());
        }
        Vec3 eyes = wielder.getEyePosition();
        lash.snapTo(eyes.x, eyes.y, eyes.z, wielder.getYRot(), wielder.getXRot());
        level.addFreshEntity(lash);
        level.playSound(null, wielder.getX(), wielder.getY(), wielder.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.5F, 1.8F);
        return lash;
    }

    /// Whether the wielder's lash is still out (on either side: the client holds back its swing the same way)
    public static boolean isLashing(LivingEntity wielder) {
        return !wielder.level().getEntitiesOfClass(WhipLash.class, wielder.getBoundingBox().inflate(4.0),
                lash -> lash.getWielderId() == wielder.getId() && lash.isAlive()).isEmpty();
    }

    public int getWielderId() {
        return this.entityData.get(WIELDER);
    }

    public @Nullable LivingEntity getWielder() {
        return this.level().getEntity(this.getWielderId()) instanceof LivingEntity wielder ? wielder : null;
    }

    /// How long the lash is out, in ticks
    public int getLifetime() {
        return this.entityData.get(LIFETIME);
    }

    /// Where the lash's tip cracks: the whip's full reach along the wielder's aim
    public static Vec3 tip(LivingEntity wielder, float partialTicks) {
        return wielder.getEyePosition(partialTicks).add(wielder.getViewVector(partialTicks).scale(REACH));
    }

    /// Where this lash's tip is: its anchor, if it has one, else the end of its wielder's aim
    public Vec3 tipOf(LivingEntity wielder, float partialTicks) {
        return this.entityData.get(ANCHORED) ? new Vec3(this.entityData.get(ANCHOR)) : tip(wielder, partialTicks);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity wielder = this.getWielder();
        // A tether's lash goes slack once its wielder's been hauled in
        boolean slack = this.kind == Kind.SHOW && this.getLifetime() > LASH_TICKS && this.tickCount > CRACK_TICK
                && !(wielder instanceof Player player && Grapple.isGrappling(player));
        if (wielder == null || this.tickCount > this.getLifetime() || slack) {
            this.discard();
            return;
        }
        // Kept with its wielder, so it's drawn while they're in view
        Vec3 eyes = wielder.getEyePosition();
        this.setPos(eyes.x, eyes.y, eyes.z);
        if (this.tickCount == CRACK_TICK) {
            Vec3 tip = this.tipOf(wielder, 1.0F);
            level.playSound(null, tip.x, tip.y, tip.z, SoundEvents.LEAD_BREAK, SoundSource.PLAYERS, 1.2F, 1.6F);
            level.playSound(null, tip.x, tip.y, tip.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.6F, 1.4F);
            level.sendParticles(ParticleTypes.CRIT, tip.x, tip.y, tip.z, 6, 0.1, 0.1, 0.1, 0.2);
            switch (this.kind) {
                case STRIKE -> this.strike(level, wielder);
                case PULL -> pull(level, wielder);
                case SHOW -> {}
            }
        }
    }

    /// The first creature the lash reaches is yanked in towards the wielder, landing a little short of them (as much
    /// as its knockback resistance lets it be). Returns what it caught.
    public static @Nullable LivingEntity pull(ServerLevel level, LivingEntity wielder) {
        List<LivingEntity> reached = reached(level, wielder);
        if (reached.isEmpty()) {
            return null;
        }
        LivingEntity caught = reached.getFirst();
        double resistance = Mth.clamp(caught.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0, 1.0);
        Vec3 toward = wielder.position().subtract(caught.position()).horizontal();
        double distance = toward.length();
        if (resistance >= 1.0 || distance <= PULL_LANDS_SHORT) {
            return caught;
        }
        // Thrown up and in, it travels about six and a half times its speed before the air stops it
        double speed = Math.min(PULL_MAX_SPEED, (distance - PULL_LANDS_SHORT) / 6.5) * (1.0 - resistance);
        Vec3 yank = toward.normalize().scale(speed);
        caught.setDeltaMovement(yank.x, PULL_LIFT * (1.0 - resistance), yank.z);
        caught.syncVelocity = true;
        caught.resetFallDistance();
        level.playSound(null, caught.getX(), caught.getY(), caught.getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 1.0F, 0.8F);
        level.playSound(null, caught.getX(), caught.getY(), caught.getZ(), SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 1.0F, 1.2F);
        return caught;
    }

    // The crack lands: whatever the tip reaches takes the wielder's blow; reaching nothing, the swing's still spent
    private void strike(ServerLevel level, LivingEntity wielder) {
        if (!(wielder instanceof Player player) || !WhipItem.isWhip(player.getMainHandItem())) {
            return;
        }
        List<LivingEntity> struck = reached(level, player);
        if (struck.isEmpty()) {
            player.resetAttackStrengthTicker();
            return;
        }
        // Worked out before the first blow spends the swing
        float strength = player.getAttackStrengthScale(0.5F);
        float blow = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * (0.2F + strength * strength * 0.8F);
        player.attack(struck.getFirst());
        for (LivingEntity beyond : struck.subList(1, struck.size())) {
            beyond.hurtServer(level, level.damageSources().playerAttack(player), blow);
        }
    }

    /// Every creature along the wielder's aim the lash reaches, nearest first: within reach, and not behind a wall
    public static List<LivingEntity> reached(ServerLevel level, LivingEntity wielder) {
        Vec3 eyes = wielder.getEyePosition();
        Vec3 end = tip(wielder, 1.0F);
        BlockHitResult wall = level.clip(new ClipContext(eyes, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, wielder));
        if (wall.getType() == HitResult.Type.BLOCK) {
            end = wall.getLocation();
        }
        Vec3 to = end;
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(eyes, to).inflate(1.0),
                        entity -> entity != wielder && entity.isAlive() && entity.isPickable() && !entity.isSpectator()
                                && entity.getBoundingBox().inflate(CATCH_MARGIN + entity.getPickRadius()).clip(eyes, to).isPresent())
                .stream().sorted(Comparator.comparingDouble(entity -> entity.distanceToSqr(eyes))).toList();
    }

    // Seen from as far off as its wielder is (its own little box would hide it beyond a few blocks)
    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 64.0 * 64.0;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        entityData.define(WIELDER, -1);
        entityData.define(LIFETIME, LASH_TICKS);
        entityData.define(ANCHORED, false);
        entityData.define(ANCHOR, new Vector3f());
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    // Only a moment's sight: never saved
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }
}

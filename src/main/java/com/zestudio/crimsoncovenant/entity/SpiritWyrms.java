package com.zestudio.crimsoncovenant.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.joml.Vector3f;
import org.joml.Vector3fc;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/// The Wrath of the Twin Wyrms (the Dragonstrike curse): two spirit dragons loosed from an arrow, coiling round each
/// other as they tear straight on, through walls and all, biting at everything for as long as they're passing through
/// it, from their heads to the tips of their tails. Drawn in glowing
/// light-blue spirit-light (see client.renderer.SpiritWyrmsRenderer), their heads at the front of the coil; the
/// coil's shape is `coil`, the same on both sides.
public class SpiritWyrms extends Entity {
    private static final EntityDataAccessor<Vector3fc> HEADING = SynchedEntityData.defineId(SpiritWyrms.class, EntityDataSerializers.VECTOR3);
    public static final double SPEED = 0.55;
    public static final int LIFETIME = 100;
    /// How far either side of their line the wyrms reach
    public static final double REACH = 2.5;
    /// How long the wyrms are, head to tail, once they're fully out of the bow
    public static final double BODY_LENGTH = 12.0;
    /// While they're passing through a creature, they bite it this often: about four or five times, head to tail
    private static final int STRIKE_INTERVAL = 5;
    private static final int DEEP_BLUE = 0x3F8CFF;
    /// How wide the two coil round their line of flight, and how fast they turn (radians a tick)
    public static final double COIL_RADIUS = 1.1;
    public static final double COIL_TURN = 0.4;

    private @Nullable UUID owner;
    private int level = 1;
    private Vec3 heading = new Vec3(0.0, 0.0, 1.0);
    private final Map<UUID, Integer> lastStruck = new HashMap<>();

    public SpiritWyrms(EntityType<? extends SpiritWyrms> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static SpiritWyrms loose(ServerLevel level, Player owner, Vec3 from, Vec3 direction, int curseLevel) {
        SpiritWyrms wyrms = new SpiritWyrms(ModEntities.SPIRIT_WYRMS.get(), level);
        wyrms.owner = owner.getUUID();
        wyrms.level = curseLevel;
        wyrms.setHeading(direction.lengthSqr() < 1.0E-6 ? owner.getLookAngle() : direction.normalize());
        wyrms.snapTo(from.x, from.y, from.z, owner.getYRot(), owner.getXRot());
        level.addFreshEntity(wyrms);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 1.2F, 1.6F);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.0F, 0.6F);
        return wyrms;
    }

    private void setHeading(Vec3 heading) {
        this.heading = heading;
        this.entityData.set(HEADING, heading.toVector3f());
    }

    public Vec3 getHeading() {
        return new Vec3(this.entityData.get(HEADING));
    }

    /// Where along their coil the wyrms are: wyrm 0 or 1, `back` blocks behind the front, at `age` ticks, as an offset
    /// from their line of flight (which runs along `heading`)
    public static Vec3 coil(Vec3 heading, int wyrm, double back, double age) {
        Vec3 side = heading.cross(new Vec3(0.0, 1.0, 0.0));
        side = side.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
        Vec3 up = side.cross(heading).normalize();
        double angle = (age - back / SPEED) * COIL_TURN + wyrm * Math.PI;
        return side.scale(Math.cos(angle) * COIL_RADIUS).add(up.scale(Math.sin(angle) * COIL_RADIUS));
    }

    /// What each bite does
    public static float damage(int curseLevel) {
        return 2.5F + curseLevel;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        if (this.tickCount > LIFETIME) {
            this.discard();
            return;
        }
        this.setPos(this.position().add(this.heading.scale(SPEED)));
        // From their heads back to their tails (as much of them as is out of the bow yet)
        double length = Math.min(BODY_LENGTH, this.tickCount * SPEED + 0.3);
        this.strikeAlong(level, this.position().subtract(this.heading.scale(length)));
        this.drawCoils(level);
        if (this.tickCount % 10 == 0) {
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.8F, 0.5F);
        }
    }

    // Everything within reach of the wyrms' bodies, from their tails (`from`) to their heads, bitten every few ticks for as
    // long as they're passing through it
    private void strikeAlong(ServerLevel level, Vec3 from) {
        Player owner = this.owner == null ? null : level.getPlayerByUUID(this.owner);
        AABB swept = new AABB(from, this.position()).inflate(REACH);
        DamageSource bite = owner != null ? level.damageSources().indirectMagic(this, owner) : level.damageSources().magic();
        for (LivingEntity creature : level.getEntitiesOfClass(LivingEntity.class, swept, entity -> entity.isAlive() && entity != owner
                && !Enthrall.isThrallOf(entity, owner))) {
            Vec3 centre = creature.position().add(0.0, creature.getBbHeight() * 0.5, 0.0);
            if (distanceToLine(centre, from, this.position()) > REACH) {
                continue;
            }
            Integer last = this.lastStruck.get(creature.getUUID());
            if (last != null && this.tickCount - last < STRIKE_INTERVAL) {
                continue;
            }
            this.lastStruck.put(creature.getUUID(), this.tickCount);
            // Spirits pass through: the bite doesn't shove what it strikes (taking back the knockback a blow brings), and
            // the moment's grace after a blow doesn't spare it the next bite
            Vec3 motion = creature.getDeltaMovement();
            creature.setInvulnerableTime(0);
            creature.hurtServer(level, bite, damage(this.level));
            creature.setDeltaMovement(motion);
            creature.syncVelocity = true;
        }
    }

    private static double distanceToLine(Vec3 point, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double length = ab.lengthSqr();
        double t = length < 1.0E-6 ? 0.0 : Mth.clamp(point.subtract(a).dot(ab) / length, 0.0, 1.0);
        return point.distanceTo(a.add(ab.scale(t)));
    }

    // Cold fire trailing from the dragons' heads, and sparks shed along their coils
    private void drawCoils(ServerLevel level) {
        for (int wyrm = 0; wyrm < 2; wyrm++) {
            Vec3 head = this.position().add(coil(this.heading, wyrm, 0.0, this.tickCount));
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, head.x, head.y, head.z, 2, 0.1, 0.1, 0.1, 0.01);
            Vec3 body = this.position().subtract(this.heading.scale(2.0)).add(coil(this.heading, wyrm, 2.0, this.tickCount));
            level.sendParticles(new DustParticleOptions(wyrm == 0 ? Curse.COLOUR : DEEP_BLUE, 1.2F), body.x, body.y, body.z, 2, 0.3, 0.3, 0.3, 0.0);
        }
        level.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY(), this.getZ(), 1, 0.4, 0.4, 0.4, 0.01);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        entityData.define(HEADING, new Vector3f(0.0F, 0.0F, 1.0F));
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
        this.level = input.getIntOr("level", 1);
        this.setHeading(input.read("heading", Vec3.CODEC).orElse(this.heading));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.storeNullable("owner", UUIDUtil.CODEC, this.owner);
        output.putInt("level", this.level);
        output.store("heading", Vec3.CODEC, this.heading);
    }
}

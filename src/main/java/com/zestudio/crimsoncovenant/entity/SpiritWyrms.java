package com.zestudio.crimsoncovenant.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.curse.Curse;
import com.zestudio.crimsoncovenant.registry.ModEntities;
import com.zestudio.crimsoncovenant.skill.Enthrall;

import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
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

/// The Wrath of the Twin Wyrms (the Dragonstrike curse): two spirit wyrms loosed from an arrow, coiling round each
/// other as they tear straight on, through walls and all, striking everything they pass through. Drawn in light,
/// with their heads at the front of the coil.
public class SpiritWyrms extends Entity {
    public static final double SPEED = 0.9;
    public static final int LIFETIME = 60;
    /// How far either side of their line the wyrms reach
    public static final double REACH = 2.5;
    // Each creature is struck no more often than this
    private static final int STRIKE_INTERVAL = 8;
    private static final int DEEP_BLUE = 0x3F8CFF;

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
        wyrms.heading = direction.lengthSqr() < 1.0E-6 ? owner.getLookAngle() : direction.normalize();
        wyrms.snapTo(from.x, from.y, from.z, owner.getYRot(), owner.getXRot());
        level.addFreshEntity(wyrms);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 1.2F, 1.6F);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.0F, 0.6F);
        return wyrms;
    }

    public static float damage(int curseLevel) {
        return 6.0F + 3.0F * curseLevel;
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
        Vec3 from = this.position();
        this.setPos(from.add(this.heading.scale(SPEED)));
        this.strikeAlong(level, from);
        this.drawCoils(level);
        if (this.tickCount % 10 == 0) {
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.8F, 0.5F);
        }
    }

    // Everything within reach of the stretch the wyrms just tore along
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
            creature.hurtServer(level, bite, damage(this.level));
            creature.knockback(0.4, -this.heading.x, -this.heading.z, bite, damage(this.level));
        }
    }

    private static double distanceToLine(Vec3 point, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double length = ab.lengthSqr();
        double t = length < 1.0E-6 ? 0.0 : Mth.clamp(point.subtract(a).dot(ab) / length, 0.0, 1.0);
        return point.distanceTo(a.add(ab.scale(t)));
    }

    // Two coils wound round the line of flight, a head of cold fire at the front of each
    private void drawCoils(ServerLevel level) {
        Vec3 side = this.heading.cross(new Vec3(0.0, 1.0, 0.0));
        if (side.lengthSqr() < 1.0E-4) {
            side = new Vec3(1.0, 0.0, 0.0);
        }
        side = side.normalize();
        Vec3 up = side.cross(this.heading).normalize();
        for (int wyrm = 0; wyrm < 2; wyrm++) {
            for (int segment = 0; segment < 6; segment++) {
                double back = segment * 0.45;
                double angle = (this.tickCount - back / SPEED) * 0.55 + wyrm * Math.PI;
                double radius = 1.1 - segment * 0.05;
                Vec3 at = this.position().subtract(this.heading.scale(back))
                        .add(side.scale(Math.cos(angle) * radius)).add(up.scale(Math.sin(angle) * radius));
                int colour = wyrm == 0 ? Curse.COLOUR : DEEP_BLUE;
                level.sendParticles(new DustParticleOptions(colour, segment == 0 ? 3.0F : 2.2F - segment * 0.2F), at.x, at.y, at.z, 2, 0.05, 0.05, 0.05, 0.0);
                if (segment == 0) {
                    level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y, at.z, 2, 0.1, 0.1, 0.1, 0.01);
                }
            }
        }
        level.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY(), this.getZ(), 1, 0.4, 0.4, 0.4, 0.01);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
        this.level = input.getIntOr("level", 1);
        this.heading = input.read("heading", Vec3.CODEC).orElse(this.heading);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.storeNullable("owner", UUIDUtil.CODEC, this.owner);
        output.putInt("level", this.level);
        output.store("heading", Vec3.CODEC, this.heading);
    }
}

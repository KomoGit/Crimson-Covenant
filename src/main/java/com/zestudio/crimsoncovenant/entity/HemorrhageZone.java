package com.zestudio.crimsoncovenant.entity;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.registry.ModDamageTypes;
import com.zestudio.crimsoncovenant.registry.ModEntities;
import com.zestudio.crimsoncovenant.vampirism.BloodPool;
import com.zestudio.crimsoncovenant.vampirism.Vampirism;

import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/// The Boiling Hemorrhage skill: a patch of ground that boils with blood for 8 seconds. Anything that stands in it
/// (other than the vampire who made it) is scalded every half second, and the blood of anything living that dies
/// in it flows to that vampire, however it died. Master Vampires boil it up too: theirs spares their own kind, and
/// what dies in it heals them.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public class HemorrhageZone extends Entity {
    public static final int LIFETIME = 160;
    public static final double RADIUS = 3.5;
    /// With the Spreading Boil upgrade, every death in the patch widens it by a block, up to this
    public static final double MAX_RADIUS = 6.5;
    private static final double GROWTH = 1.0;
    private static final int DAMAGE_INTERVAL = 10;
    private static final float DAMAGE = 3.0F;

    private @Nullable UUID owner;
    private double radius = RADIUS;
    private boolean spreading = false;
    private int life = 0;

    public HemorrhageZone(EntityType<? extends HemorrhageZone> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static HemorrhageZone create(ServerLevel level, LivingEntity owner, double x, double y, double z) {
        HemorrhageZone zone = new HemorrhageZone(ModEntities.HEMORRHAGE_ZONE.get(), level);
        zone.owner = owner.getUUID();
        zone.setPos(x, y, z);
        return zone;
    }

    public @Nullable LivingEntity getOwner() {
        return this.owner != null && this.level() instanceof ServerLevel level && level.getEntity(this.owner) instanceof LivingEntity living
                ? living : null;
    }

    /// Who the boiling blood hurts: everyone but its maker, and for a Master Vampire, only its enemies
    private boolean scalds(LivingEntity entity, @Nullable LivingEntity owner) {
        if (entity == owner || !entity.isAlive() || entity instanceof ArmorStand || !this.contains(entity)) {
            return false;
        }
        return !(owner instanceof MasterVampire master) || master.isEnemy(entity);
    }

    public AABB area() {
        return new AABB(this.getX() - this.radius, this.getY() - 0.5, this.getZ() - this.radius, this.getX() + this.radius, this.getY() + 2.5, this.getZ() + this.radius);
    }

    public boolean contains(Entity entity) {
        double dx = entity.getX() - this.getX();
        double dz = entity.getZ() - this.getZ();
        return dx * dx + dz * dz <= this.radius * this.radius && this.area().intersects(entity.getBoundingBox());
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        if (++this.life > LIFETIME) {
            this.discard();
            return;
        }
        this.boil(level);
        if (this.life % DAMAGE_INTERVAL == 0) {
            this.scald(level);
        }
    }

    // Blood bubbling up across the whole patch
    private void boil(ServerLevel level) {
        for (int i = 0; i < 6; i++) {
            double angle = this.random.nextDouble() * Math.PI * 2.0;
            double distance = Math.sqrt(this.random.nextDouble()) * this.radius;
            double x = this.getX() + Math.cos(angle) * distance;
            double z = this.getZ() + Math.sin(angle) * distance;
            level.sendParticles(new DustParticleOptions(Vampire.BLOOD_COLOR, 1.4F), x, this.getY() + 0.1, z, 1, 0.0, 0.05, 0.0, 0.0);
            if (i == 0) {
                level.sendParticles(ParticleTypes.BUBBLE_POP, x, this.getY() + 0.2, z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        if (this.life % 20 == 0) {
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.LAVA_POP, SoundSource.PLAYERS, 0.8F, 0.6F);
        }
    }

    private void scald(ServerLevel level) {
        LivingEntity owner = this.getOwner();
        DamageSource source = this.damageSources().source(ModDamageTypes.BLOOD_MAGIC, this, owner);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, this.area(), entity -> this.scalds(entity, owner))) {
            victim.hurtServer(level, source, DAMAGE);
        }
    }

    public double getRadius() {
        return this.radius;
    }

    /// The Spreading Boil upgrade
    public void setSpreading(boolean spreading) {
        this.spreading = spreading;
    }

    // A wider patch, with a gout of blood all round its new edge
    private void spread(ServerLevel level) {
        if (this.radius >= MAX_RADIUS) {
            return;
        }
        this.radius = Math.min(MAX_RADIUS, this.radius + GROWTH);
        for (int i = 0; i < 32; i++) {
            double angle = Math.PI * 2.0 * i / 32;
            level.sendParticles(new DustParticleOptions(Vampire.BLOOD_COLOR, 1.8F), this.getX() + Math.cos(angle) * this.radius, this.getY() + 0.2,
                    this.getZ() + Math.sin(angle) * this.radius, 2, 0.1, 0.2, 0.1, 0.0);
        }
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.LAVA_EXTINGUISH, SoundSource.PLAYERS, 1.0F, 0.5F);
    }

    /// Whatever dies in the zone, however it died, gives up its blood to the zone's maker. (A kill by the
    /// vampire's own hand is already paid for by BloodPool.) A spreading zone widens with every death.
    @SubscribeEvent
    static void bloodFlowsToTheOwner(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) {
            return;
        }
        List<HemorrhageZone> zones = level.getEntitiesOfClass(HemorrhageZone.class, victim.getBoundingBox().inflate(MAX_RADIUS + 1.0), zone -> zone.contains(victim));
        for (HemorrhageZone zone : zones) {
            if (zone.spreading && victim != zone.getOwner()) {
                zone.spread(level);
            }
        }
        if (!Vampirism.hasLivingBlood(victim)) {
            return;
        }
        for (HemorrhageZone zone : zones) {
            LivingEntity owner = zone.getOwner();
            if (owner == null || owner == victim || event.getSource().getEntity() == owner) {
                continue;
            }
            if (owner instanceof ServerPlayer player && Vampirism.isVampire(player)) {
                BloodPool.add(player, BloodPool.bloodFrom(victim));
                Vampire.streamBlood(level, victim, player);
                return;
            }
            if (owner instanceof MasterVampire master) {
                master.heal(BloodPool.bloodFrom(victim));
                Vampire.streamBlood(level, victim, master);
                return;
            }
        }
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
        this.owner = input.read("Owner", UUIDUtil.CODEC).orElse(null);
        this.radius = input.getDoubleOr("Radius", RADIUS);
        this.spreading = input.getBooleanOr("Spreading", false);
        this.life = input.getIntOr("Life", 0);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.storeNullable("Owner", UUIDUtil.CODEC, this.owner);
        output.putDouble("Radius", this.radius);
        output.putBoolean("Spreading", this.spreading);
        output.putInt("Life", this.life);
    }
}

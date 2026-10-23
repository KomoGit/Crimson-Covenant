package com.zestudio.crimsoncovenant.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.registry.ModDamageTypes;
import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.registry.ModEntities;
import com.zestudio.crimsoncovenant.vampirism.BloodPool;
import com.zestudio.crimsoncovenant.vampirism.Vampirism;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/// One bat of a bat swarm, sent by a Master Vampire or by a vampire player's Bat Swarm skill. Flies in a straight
/// line through blocks and creatures, hurting each enemy it passes through once and leaving them with Blood Loss,
/// then vanishes when it reaches the end of its path (or after 5 seconds at most). Can't be hurt, and isn't saved.
public class SwarmBat extends Bat {
    public static final int LIFETIME = 100;
    private static final float DAMAGE = 4.0F;
    private static final float OWNER_HEAL_PER_HIT = 1.0F;
    private static final int BLOOD_LOSS_DURATION = 160;
    private static final double PUSH_STRENGTH = 1.2;
    private static final double PUSH_LIFT = 0.3;

    private Vec3 flightVelocity = Vec3.ZERO;
    private static final int SWARM_SIZE = 16;
    private static final double SWARM_SPEED = 0.8;

    private @Nullable LivingEntity owner;
    private boolean leeching = false;
    /// Blood each bite of a Leeching Swarm brings its vampire
    public static final int LEECHED_BLOOD = 3;
    // Shared by every bat in one swarm, so each enemy is only hurt once per swarm
    private Set<UUID> struck = new HashSet<>();
    private int life = 0;
    private double rangeLeft = 0.0;

    public SwarmBat(EntityType<? extends SwarmBat> type, Level level) {
        super(type, level);
        this.setResting(false);
        this.setNoGravity(true);
        // The flight path is fixed: no bat AI steering it
        this.setNoAi(true);
    }

    /// Sends a whole swarm down a path, spread out a little around it
    public static void launchSwarm(ServerLevel level, LivingEntity owner, Vec3 origin, Vec3 direction, double range) {
        launchSwarm(level, owner, origin, direction, range, false);
    }

    /// `leeching`: the Leeching Swarm upgrade, every bite brings a vampire player blood
    public static void launchSwarm(ServerLevel level, LivingEntity owner, Vec3 origin, Vec3 direction, double range, boolean leeching) {
        direction = direction.normalize();
        Vec3 right = direction.cross(new Vec3(0.0, 1.0, 0.0));
        right = right.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
        Vec3 up = right.cross(direction).normalize();
        Set<UUID> struck = new HashSet<>();
        for (int i = 0; i < SWARM_SIZE; i++) {
            Vec3 offset = right.scale((owner.getRandom().nextDouble() - 0.5) * 3.0)
                    .add(up.scale((owner.getRandom().nextDouble() - 0.5) * 2.0))
                    .add(direction.scale(-owner.getRandom().nextDouble() * 1.5));
            SwarmBat bat = launch(level, owner, origin.add(offset), direction.scale(SWARM_SPEED), range, struck);
            if (bat != null) {
                bat.leeching = leeching;
            }
        }
    }

    /// Sends a bat flying along the path for `range` blocks, after which it vanishes
    public static @Nullable SwarmBat launch(ServerLevel level, LivingEntity owner, Vec3 position, Vec3 velocity, double range, Set<UUID> sharedStruck) {
        SwarmBat bat = ModEntities.SWARM_BAT.get().create(level, EntitySpawnReason.MOB_SUMMONED);
        if (bat == null) {
            return null;
        }
        bat.owner = owner;
        bat.flightVelocity = velocity;
        bat.rangeLeft = range;
        bat.struck = sharedStruck;
        bat.snapTo(position.x, position.y, position.z, (float) Math.toDegrees(Math.atan2(-velocity.x, velocity.z)), 0.0F);
        bat.setDeltaMovement(velocity);
        level.addFreshEntity(bat);
        return bat;
    }

    public @Nullable LivingEntity getOwner() {
        return this.owner;
    }

    @Override
    public void tick() {
        this.noPhysics = true;
        this.setDeltaMovement(this.flightVelocity);
        super.tick();
        this.noPhysics = false;
        if (this.level() instanceof ServerLevel level) {
            // With no AI the game doesn't move the bat itself, so it's flown along the path here (through blocks)
            if (this.rangeLeft > 0.0) {
                this.setPos(this.position().add(this.flightVelocity));
                this.rangeLeft -= this.flightVelocity.length();
                if (this.rangeLeft <= 0.0) {
                    this.vanish(level);
                    return;
                }
            }
            boolean expired = ++this.life >= LIFETIME || (this.owner != null && this.owner.isRemoved() && this.life > 20);
            if (expired || !level.isPositionEntityTicking(this.blockPosition())) {
                this.vanish(level);
                return;
            }
            this.strikeEnemies(level);
        }
    }

    private void vanish(ServerLevel level) {
        level.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY(0.5), this.getZ(), 4, 0.15, 0.15, 0.15, 0.01);
        this.discard();
    }

    private void strikeEnemies(ServerLevel level) {
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(0.4), this::isEnemy)) {
            if (this.struck.add(victim.getUUID())) {
                DamageSource source = level.damageSources().source(ModDamageTypes.LIFE_DRAIN, this, this.owner);
                if (victim.hurtServer(level, source, DAMAGE)) {
                    victim.addEffect(new MobEffectInstance(ModEffects.BLOOD_LOSS, BLOOD_LOSS_DURATION), this.owner);
                    // The swarm carries its victims along with it, the way it's flying
                    Vec3 push = this.flightVelocity.normalize().scale(PUSH_STRENGTH);
                    victim.setDeltaMovement(victim.getDeltaMovement().add(push.x, PUSH_LIFT, push.z));
                    victim.syncVelocity = true;
                    level.sendParticles(new DustParticleOptions(0x9B0A14, 1.0F), victim.getX(), victim.getY(0.6), victim.getZ(), 8, 0.2, 0.3, 0.2, 0.0);
                    // Vampires only drink from the living
                    if (this.owner != null && this.owner.isAlive() && Vampirism.hasLivingBlood(victim)) {
                        this.owner.heal(OWNER_HEAL_PER_HIT);
                        if (this.leeching && this.owner instanceof ServerPlayer player) {
                            BloodPool.add(player, LEECHED_BLOOD);
                        }
                    }
                }
            }
        }
    }

    private boolean isEnemy(LivingEntity entity) {
        if (entity instanceof SwarmBat || entity == this.owner) {
            return false;
        }
        if (this.owner instanceof MasterVampire master) {
            return master.isEnemy(entity);
        }
        if (this.owner instanceof Player player) {
            // A player's swarm hits everything in its way, apart from the player's own pets and creative players
            return !(entity instanceof OwnableEntity pet && pet.getOwner() == player)
                    && !(entity instanceof Player other && (other.isCreative() || other.isSpectator()));
        }
        return MasterVampire.isEnemyOfVampires(entity, null);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}

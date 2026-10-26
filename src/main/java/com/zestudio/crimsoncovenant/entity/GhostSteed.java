package com.zestudio.crimsoncovenant.entity;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.registry.ModEntities;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.equine.ZombieHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/// A pale, see-through horse that nothing can hurt or ride. Two kinds:
/// - The Spectral Horseman's mount (see SpectralHorseman), which fades the moment it has no rider.
/// - The Headless Horseman's horse, risen when he falls: it gallops off down the road to a rally point and waits there
///   for the Spirit of Halloween (see HalloweenSpirit), fading if the spirit's banished.
public class GhostSteed extends ZombieHorse {
    private static final double GALLOP_SPEED = 1.8;

    private @Nullable Vec3 rally;
    private boolean spectral;

    public GhostSteed(EntityType<? extends GhostSteed> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    /// The Headless Horseman's horse risen from `from`, galloping off by `away` to wait for his spirit
    public static GhostSteed gallopFrom(ServerLevel level, Vec3 from, Vec3 away) {
        GhostSteed steed = ModEntities.GHOST_STEED.get().create(level, EntitySpawnReason.EVENT);
        Vec3 rally = HeadlessHorseman.groundAt(level, from.x + away.x, from.z + away.z);
        steed.snapTo(from.x, from.y, from.z, 0.0F, 0.0F);
        steed.rally = rally;
        level.addFreshEntity(steed);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, from.x, from.y + 1.0, from.z, 20, 0.6, 0.6, 0.6, 0.02);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.ZOMBIE_HORSE_DEATH, SoundSource.HOSTILE, 2.0F, 0.5F);
        return steed;
    }

    /// A Spectral Horseman's mount, which fades once it's riderless
    public static GhostSteed spectral(Level level) {
        GhostSteed steed = ModEntities.GHOST_STEED.get().create(level, EntitySpawnReason.JOCKEY);
        steed.spectral = true;
        return steed;
    }

    public @Nullable Vec3 getRally() {
        return this.rally;
    }

    public boolean isSpectral() {
        return this.spectral;
    }

    @Override
    protected void addBehaviourGoals() {
        this.goalSelector.addGoal(1, new GallopToRallyGoal());
    }

    /// Fades away, in a wisp of soul fire
    public void fade() {
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY(0.6), this.getZ(), 20, 0.5, 0.5, 0.5, 0.03);
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 1.5F, 0.6F);
        }
        this.ejectPassengers();
        this.discard();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            if (this.random.nextInt(3) == 0) {
                this.level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, this.getRandomX(0.6), this.getRandomY(), this.getRandomZ(0.6), 0.0, 0.02, 0.0);
            }
            return;
        }
        if (this.spectral && !this.isVehicle() && this.tickCount > 20) {
            this.fade();
        }
    }

    // ---- Nothing touches it ----

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || super.isInvulnerableTo(level, source);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        return InteractionResult.PASS;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("Spectral", this.spectral);
        output.storeNullable("Rally", Vec3.CODEC, this.rally);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.spectral = input.getBooleanOr("Spectral", false);
        this.rally = input.read("Rally", Vec3.CODEC).orElse(null);
    }

    /// Riderless, it gallops to its rally point and stands there
    class GallopToRallyGoal extends Goal {
        GallopToRallyGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return GhostSteed.this.rally != null && !GhostSteed.this.isVehicle() && GhostSteed.this.distanceToSqr(GhostSteed.this.rally) > 2.0;
        }

        @Override
        public void tick() {
            Vec3 rally = GhostSteed.this.rally;
            if (rally != null && (GhostSteed.this.getNavigation().isDone() || GhostSteed.this.tickCount % 20 == 0)) {
                GhostSteed.this.getNavigation().moveTo(rally.x, rally.y, rally.z, GALLOP_SPEED);
            }
        }
    }
}

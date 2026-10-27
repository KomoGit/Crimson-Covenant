package com.zestudio.crimsoncovenant.entity;



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
import net.minecraft.world.entity.animal.equine.ZombieHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/// A pale, see-through horse that nothing can hurt or ride: the Spectral Horseman's mount (see SpectralHorseman), which
/// fades the moment it has no rider.
public class GhostSteed extends ZombieHorse {
    private boolean spectral;

    public GhostSteed(EntityType<? extends GhostSteed> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    /// A Spectral Horseman's mount, which fades once it's riderless
    public static GhostSteed spectral(Level level) {
        GhostSteed steed = ModEntities.GHOST_STEED.get().create(level, EntitySpawnReason.JOCKEY);
        steed.spectral = true;
        return steed;
    }

    public boolean isSpectral() {
        return this.spectral;
    }

    // Its rider steers it
    @Override
    protected void addBehaviourGoals() {
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
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.spectral = input.getBooleanOr("Spectral", false);
    }
}

package com.zestudio.crimsoncovenant.entity;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.block.HeadOnAPikeBlock;
import com.zestudio.crimsoncovenant.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/// The ghost of the Headless Horseman, called up each night by his head set on a pike (see block.HeadOnAPikeBlock), to
/// ride the land round it. He hunts down the monsters there, hit and run as the Horseman fights, and tramples any
/// creature that strays into his path (though never a player, their pets, villagers or golems). Nothing can harm him,
/// and he and his Ghost Steed fade at dawn.
public class SpectralHorseman extends HeadlessHorseman {
    /// How far from the pike he rides
    public static final double PATROL_RANGE = 50.0;
    public static final float TRAMPLE_DAMAGE = 5.0F;

    private @Nullable BlockPos pike;

    public SpectralHorseman(EntityType<? extends SpectralHorseman> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    /// Calls him up by the pike at `pike`, mounted on a Ghost Steed, at `at`
    public static @Nullable SpectralHorseman rise(ServerLevel level, BlockPos pike, Vec3 at) {
        SpectralHorseman horseman = ModEntities.SPECTRAL_HORSEMAN.get().create(level, EntitySpawnReason.EVENT);
        if (horseman == null) {
            return null;
        }
        horseman.pike = pike.immutable();
        horseman.snapTo(at.x, at.y, at.z, level.getRandom().nextFloat() * 360.0F, 0.0F);
        horseman.finalizeSpawn(level, level.getCurrentDifficultyAt(pike), EntitySpawnReason.EVENT, null);
        horseman.setPersistenceRequired();
        level.addFreshEntity(horseman);
        level.sendParticles(ParticleTypes.SOUL, at.x, at.y + 1.0, at.z, 30, 0.5, 1.0, 0.5, 0.03);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ZOMBIE_HORSE_AMBIENT, SoundSource.NEUTRAL, 1.5F, 0.5F);
        return horseman;
    }

    public @Nullable BlockPos getPike() {
        return this.pike;
    }

    /// Whether he'd ride down `mob`: a monster near his pike, bar bosses and his own kind
    public boolean isPrey(LivingEntity mob) {
        return mob instanceof Enemy && mob.isAlive() && !mob.is(Tags.EntityTypes.BOSSES) && !(mob instanceof HeadlessHorseman)
                && !(mob instanceof HalloweenSpirit) && (this.pike == null || mob.distanceToSqr(Vec3.atCenterOf(this.pike)) <= PATROL_RANGE * PATROL_RANGE);
    }

    @Override
    protected void addBehaviourGoals() {
        this.goalSelector.addGoal(2, new HitAndRunGoal(this));
        this.goalSelector.addGoal(7, new PatrolGoal());
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Mob.class, 10, false, false, (target, level) -> this.isPrey(target)));
    }

    @Override
    protected AbstractHorse createSteed() {
        return GhostSteed.spectral(this.level());
    }

    @Override
    protected boolean dropsGear() {
        return false;
    }

    @Override
    protected boolean throwsBombs() {
        return false;
    }

    @Override
    public boolean hasBossBar() {
        return false;
    }

    // A ghost already: nothing rises from him
    @Override
    protected void riseAsSpirit(ServerLevel level, @Nullable AbstractHorse steed) {
    }

    /// Fades away with his steed
    public void fade() {
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY(0.6), this.getZ(), 20, 0.4, 0.6, 0.4, 0.03);
        }
        if (this.getVehicle() instanceof GhostSteed steed) {
            steed.fade();
        }
        this.discard();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (!level.isDarkOutside() || (this.pike != null && !HeadOnAPikeBlock.isPike(level.getBlockState(this.pike)) && level.isLoaded(this.pike))) {
            this.fade();
            return;
        }
        if (this.getTarget() != null && !this.isPrey(this.getTarget())) {
            this.setTarget(null);
        }
        this.trample(level);
    }

    /// Hurts and throws aside every creature in the steed's way, while it's moving
    void trample(ServerLevel level) {
        Entity steed = this.getVehicle();
        if (steed == null || steed.getDeltaMovement().horizontalDistanceSqr() < 0.01) {
            return;
        }
        for (LivingEntity trampled : level.getEntitiesOfClass(LivingEntity.class, steed.getBoundingBox().inflate(0.3), this::canTrample)) {
            if (trampled.hurtServer(level, this.damageSources().mobAttack(this), TRAMPLE_DAMAGE)) {
                Vec3 aside = trampled.position().subtract(steed.position()).multiply(1.0, 0.0, 1.0);
                trampled.knockback(0.8, -aside.x, -aside.z, this.damageSources().mobAttack(this), TRAMPLE_DAMAGE);
            }
        }
    }

    /// What he rides down: any creature but players, their pets, villagers, golems and his own kind
    public boolean canTrample(LivingEntity creature) {
        return creature != this && creature != this.getVehicle() && creature.isAlive() && !(creature instanceof Player)
                && !(creature instanceof OwnableEntity pet && pet.getOwnerReference() != null) && !(creature instanceof AbstractVillager)
                && !(creature instanceof AbstractGolem) && !(creature instanceof HeadlessHorseman) && !(creature instanceof GhostSteed)
                && !(creature instanceof HalloweenSpirit);
    }

    // ---- Nothing touches him ----

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || super.isInvulnerableTo(level, source);
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    // Players are no quarry of his: he never even looks their way
    @Override
    public boolean canAttack(LivingEntity target) {
        return !(target instanceof Player) && super.canAttack(target);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.storeNullable("Pike", BlockPos.CODEC, this.pike);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.pike = input.read("Pike", BlockPos.CODEC).orElse(null);
    }

    /// With no monster to ride down, he rides round his pike
    class PatrolGoal extends Goal {
        private static final double SPEED = 1.2;

        PatrolGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return SpectralHorseman.this.getTarget() == null && SpectralHorseman.this.pike != null
                    && (SpectralHorseman.this.getNavigation().isDone() || SpectralHorseman.this.random.nextInt(100) == 0);
        }

        @Override
        public boolean canContinueToUse() {
            return SpectralHorseman.this.getTarget() == null && !SpectralHorseman.this.getNavigation().isDone();
        }

        @Override
        public void start() {
            BlockPos pike = SpectralHorseman.this.pike;
            if (pike == null) {
                return;
            }
            double angle = SpectralHorseman.this.random.nextDouble() * Math.PI * 2.0;
            double radius = 8.0 + SpectralHorseman.this.random.nextDouble() * 24.0;
            Vec3 to = HeadlessHorseman.groundAt((ServerLevel) SpectralHorseman.this.level(), pike.getX() + 0.5 + Math.cos(angle) * radius,
                    pike.getZ() + 0.5 + Math.sin(angle) * radius);
            SpectralHorseman.this.getNavigation().moveTo(to.x, to.y, to.z, SPEED);
        }
    }
}

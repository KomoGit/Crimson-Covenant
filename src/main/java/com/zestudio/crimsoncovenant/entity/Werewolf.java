package com.zestudio.crimsoncovenant.entity;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.item.WerewolfBelt;
import com.zestudio.crimsoncovenant.skill.MoonPowers;
import com.zestudio.crimsoncovenant.world.WerewolfSpawner;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariant;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariants;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/// A werewolf: only abroad on full-moon and blood-moon nights, and gone with the moon. It hunts almost anything
/// that lives (players, villagers, animals, hunters, even vampires) but flees from Master Hunters.
/// Silver hurts it (see the weak_to_silver tag); Holy Water doesn't, since it isn't undead.
public class Werewolf extends Monster {
    /// Werewolves from the moon spawner vanish when the moon does. Spawn-egg ones stay, for testing.
    private boolean moonBound = false;

    public Werewolf(EntityType<? extends Werewolf> type, Level level) {
        super(type, level);
        this.xpReward = 15;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.34)
                .add(Attributes.ATTACK_DAMAGE, 7.0)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3)
                .add(Attributes.FOLLOW_RANGE, 40.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new AvoidEntityGoal<>(this, MasterHunter.class, 12.0F, 1.2, 1.5));
        this.goalSelector.addGoal(2, new LeapAtTargetGoal(this, 0.45F));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.25, true));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.9));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, Werewolf.class));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false, (target, level) -> isPrey(target)));
    }

    /// Almost everything: not other werewolves (players turned by a belt and their packs among them, see
    /// item.WerewolfBelt), not the Master Hunters it fears, and not what it can't catch or shouldn't bite (creepers,
    /// ghosts, bats, fish, armor stands)
    public static boolean isPrey(LivingEntity target) {
        return !(target instanceof Werewolf)
                && !(target instanceof Player player && WerewolfBelt.isTurned(player))
                && !MoonPowers.isPackWolf(target)
                && !(target instanceof MasterHunter)
                && !(target instanceof Creeper)
                && !(target instanceof Ghost)
                && !(target instanceof AmbientCreature)
                && !(target instanceof WaterAnimal)
                && !(target instanceof ArmorStand)
                && !Vampire.isFamiliar(target);
    }

    public void setMoonBound(boolean moonBound) {
        this.moonBound = moonBound;
    }

    public boolean isMoonBound() {
        return this.moonBound;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        // Gone when the full moon sets
        if (this.moonBound && this.tickCount % 20 == 0 && this.level() instanceof ServerLevel level && !WerewolfSpawner.isWerewolfNight(level)) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY(0.5), this.getZ(), 20, 0.3, 0.6, 0.3, 0.02);
            this.playSound(sounds().whineSound().value(), 1.0F, 0.6F);
            this.discard();
        }
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        if (spawnReason == EntitySpawnReason.NATURAL || spawnReason == EntitySpawnReason.EVENT) {
            this.moonBound = true;
        }
        return super.finalizeSpawn(level, difficulty, spawnReason, groupData);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("MoonBound", this.moonBound);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.moonBound = input.getBooleanOr("MoonBound", false);
    }

    // ---- Sounds: the deep-voiced "big" wolf, pitched down further ----

    private static WolfSoundVariant.WolfSoundSet sounds() {
        return SoundEvents.WOLF_SOUNDS.get(WolfSoundVariants.SoundSet.BIG).adultSounds();
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return sounds().growlSound().value();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return sounds().hurtSound().value();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return sounds().deathSound().value();
    }

    @Override
    public float getVoicePitch() {
        return 0.6F + this.random.nextFloat() * 0.1F;
    }
}

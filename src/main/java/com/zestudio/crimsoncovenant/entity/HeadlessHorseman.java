package com.zestudio.crimsoncovenant.entity;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.CrimsonCovenant;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.SpearUseGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.ZombieAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.equine.ZombieHorse;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/// A headless rider with a jack o'lantern for a head and an enchanted golden spear, mounted on a zombie horse.
/// Haunts forests at night and hunts players and hunters, and dies along with its horse.
/// Builds on vanilla's zombie spear-rider behaviour.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public class HeadlessHorseman extends Zombie {
    public HeadlessHorseman(EntityType<? extends HeadlessHorseman> type, Level level) {
        super(type, level);
        this.xpReward = 20;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0);
    }

    @Override
    protected void addBehaviourGoals() {
        this.goalSelector.addGoal(2, new SpearUseGoal<>(this, 1.0, 1.0, 10.0F, 2.0F));
        this.goalSelector.addGoal(3, new ZombieAttackGoal(this, 1.0, false));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Hunter.class, true));
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        // Never a baby, never a chicken jockey
        groupData = super.finalizeSpawn(level, difficulty, spawnReason, new Zombie.ZombieGroupData(false, false));
        this.setBaby(false);
        this.setCanBreakDoors(false);
        this.populateHorsemanEquipment();
        if (!this.isPassenger()) {
            this.mountSteed(level, difficulty);
        }
        return groupData;
    }

    private void populateHorsemanEquipment() {
        this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.JACK_O_LANTERN));
        this.setDropChance(EquipmentSlot.HEAD, 0.5F);

        ItemStack spear = new ItemStack(Items.GOLDEN_SPEAR);
        var enchantments = this.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        spear.enchant(enchantments.getOrThrow(Enchantments.SHARPNESS), 3);
        spear.enchant(enchantments.getOrThrow(Enchantments.LUNGE), 2);
        this.setItemSlot(EquipmentSlot.MAINHAND, spear);
        this.setDropChance(EquipmentSlot.MAINHAND, 0.25F);
    }

    // A zombie horse in golden barding, which also keeps it from burning at sunrise
    private void mountSteed(ServerLevelAccessor level, DifficultyInstance difficulty) {
        ZombieHorse steed = EntityTypes.ZOMBIE_HORSE.create(this.level(), EntitySpawnReason.JOCKEY);
        if (steed == null) {
            return;
        }
        steed.snapTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), 0.0F);
        steed.finalizeSpawn(level, difficulty, EntitySpawnReason.JOCKEY, null);
        steed.setItemSlot(EquipmentSlot.BODY, new ItemStack(Items.GOLDEN_HORSE_ARMOR));
        steed.setDropChance(EquipmentSlot.BODY, 0.0F);
        level.addFreshEntity(steed);
        this.startRiding(steed, false, false);
    }

    /// Horse and rider are one: when the steed falls, so does the horseman, in a puff of flame and smoke
    @SubscribeEvent
    static void onSteedDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        for (Entity passenger : event.getEntity().getPassengers()) {
            if (passenger instanceof HeadlessHorseman horseman && horseman.isAlive()) {
                level.sendParticles(ParticleTypes.FLAME, horseman.getX(), horseman.getY(1.0), horseman.getZ(), 30, 0.3, 0.5, 0.3, 0.02);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, horseman.getX(), horseman.getY(1.0), horseman.getZ(), 20, 0.3, 0.5, 0.3, 0.02);
                // Credit the kill to whoever brought the horse down
                horseman.hurtServer(level, event.getSource(), Float.MAX_VALUE);
                if (horseman.isAlive()) {
                    horseman.kill(level);
                }
            }
        }
    }

    // Not a regular zombie: never a baby, and doesn't turn into a drowned underwater
    @Override
    protected boolean convertsInWater() {
        return false;
    }

    @Override
    public boolean isBaby() {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.WITCH_CELEBRATE;
    }

    @Override
    public float getVoicePitch() {
        return 0.6F;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.HOSTILE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.HOSTILE_DEATH;
    }

    // The horseman's gear is set in finalizeSpawn, not rolled like a zombie's
    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
    }
}

package com.zestudio.crimsoncovenant.entity;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.registry.ModEntities;
import com.zestudio.crimsoncovenant.registry.ModTags;
import com.zestudio.crimsoncovenant.vampirism.Vampirism;
import com.zestudio.crimsoncovenant.world.BloodMoon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.TrailParticleOption;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import net.minecraft.world.entity.ai.goal.FleeSunGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveThroughVillageGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.goal.RestrictSunGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.Vec3;

/// A fast, hard-hitting undead that burns in the sun. Vampires heal by killing, go after animals when
/// badly hurt, break down doors to reach villagers, and may carry a sword, an axe or a bow.
public class Vampire extends Monster implements RangedAttackMob {
    /// Below this fraction of max health, vampires also hunt animals
    private static final float THIRST_THRESHOLD = 0.5F;
    public static final int BLOOD_COLOR = 0x9B0A14;
    private static final int PREY_MEMORY_TICKS = 300;
    /// Resistance II: takes 40% less damage
    private static final int RESISTANCE_AMPLIFIER = 1;

    private final RangedBowAttackGoal<Vampire> bowGoal = new RangedBowAttackGoal<>(this, 1.0, 30, 15.0F);
    private final MeleeAttackGoal meleeGoal = new MeleeAttackGoal(this, 1.2, false) {
        // Bloodlust makes vampires strike faster (mobs don't use the attack speed attribute)
        @Override
        protected int getAttackInterval() {
            return Vampire.this.hasEffect(ModEffects.BLOODLUST) ? this.adjustedTickDelay(12) : super.getAttackInterval();
        }
    };
    private boolean ascensionPending = false;

    public Vampire(EntityType<? extends Vampire> type, Level level) {
        super(type, level);
        this.getNavigation().setCanOpenDoors(true);
        this.reassessWeaponGoal();
    }

    public static AttributeSupplier.Builder createAttributes() {
        // For comparison, a zombie has 20 health, 0.23 speed and 3 attack damage
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 24.0)
                .add(Attributes.MOVEMENT_SPEED, 0.33)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.ARMOR, 2.0)
                .add(Attributes.FOLLOW_RANGE, 35.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new VampireBreakDoorGoal(this));
        this.goalSelector.addGoal(2, new RestrictSunGoal(this));
        this.goalSelector.addGoal(2, new SeekShelterGoal(this));
        this.goalSelector.addGoal(3, new FleeSunGoal(this, 1.2));
        // Priority 4 is the weapon goal (melee or bow), see reassessWeaponGoal()
        this.goalSelector.addGoal(6, new MoveThroughVillageGoal(this, 1.0, true, 4, () -> true));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        // Prey that hides behind a door is remembered for a while, instead of forgotten after the default 3 seconds.
        // Fellow vampires (turned players) are left alone.
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true,
                (target, level) -> !(target instanceof Player player && Vampirism.isVampire(player))).setUnseenMemoryTicks(PREY_MEMORY_TICKS));
        // Villagers, illagers, hunters, golems... see data/crimsoncovenant/tags/entity_type/vampire_prey.json
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, LivingEntity.class, true,
                (target, level) -> target.is(ModTags.Entities.VAMPIRE_PREY)).setUnseenMemoryTicks(PREY_MEMORY_TICKS));
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Animal.class, 10, true, false,
                (target, level) -> this.isThirsty() && !isFamiliar(target)));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        // Vampires are permanently resistant. Re-applied if anything removes it, except while Holy Flame burns.
        if (!this.level().isClientSide() && this.tickCount % 20 == 0) {
            BloodMoon.tickBloodlust(this);
        }
        if (!this.level().isClientSide() && !this.hasEffect(MobEffects.RESISTANCE) && !this.hasEffect(ModEffects.HOLY_FLAME)) {
            this.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, MobEffectInstance.INFINITE_DURATION, RESISTANCE_AMPLIFIER, false, false));
        }
    }

    /// Creatures summoned by a Master Vampire. Vampires never hunt or drain them, even when thirsty.
    /// Stored as an entity tag so it survives saving and reloading.
    public static boolean isFamiliar(Entity entity) {
        return entity.entityTags().contains(MasterVampire.FAMILIAR_TAG);
    }

    public boolean isThirsty() {
        return this.getHealth() < this.getMaxHealth() * THIRST_THRESHOLD;
    }

    // ---- Feeding ----

    @Override
    public boolean killedEntity(ServerLevel level, LivingEntity victim, DamageSource source) {
        boolean result = super.killedEntity(level, victim, source);
        // Only the living have blood worth drinking
        if (Vampirism.hasLivingBlood(victim)) {
            this.feedOn(level, victim);
        }
        // A regular vampire that slays a Master Hunter rises as a Master Vampire (on its next tick, not mid-attack)
        if (victim instanceof MasterHunter && this.getType() == ModEntities.VAMPIRE.get()) {
            this.ascensionPending = true;
        }
        return result;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (this.ascensionPending) {
            this.ascensionPending = false;
            MasterVampire master = this.convertTo(ModEntities.MASTER_VAMPIRE.get(), ConversionParams.single(this, false, false),
                    converted -> converted.setHealth(converted.getMaxHealth()));
            if (master != null) {
                level.sendParticles(new DustParticleOptions(BLOOD_COLOR, 2.0F), master.getX(), master.getY(1.0), master.getZ(), 60, 0.5, 0.8, 0.5, 0.0);
                level.playSound(null, master.getX(), master.getY(), master.getZ(), SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.HOSTILE, 1.5F, 0.5F);
            }
        }
    }

    /// Heals from a kill, with a burst of blood that streams into the vampire like experience orbs.
    private void feedOn(ServerLevel level, LivingEntity victim) {
        this.heal(Mth.clamp(victim.getMaxHealth() * 0.5F, 4.0F, 12.0F));
        streamBlood(level, victim, this);
    }

    /// A burst of blood from the victim that streams into the feeder like experience orbs. Vampire players
    /// get the same effect when they refill their blood bar with a kill.
    public static void streamBlood(ServerLevel level, LivingEntity victim, LivingEntity feeder) {
        RandomSource random = feeder.getRandom();
        Vec3 from = victim.position().add(0.0, victim.getBbHeight() * 0.5, 0.0);
        Vec3 to = feeder.position().add(0.0, feeder.getBbHeight() * 0.6, 0.0);
        level.sendParticles(new DustParticleOptions(BLOOD_COLOR, 1.4F), from.x, from.y, from.z, 24, 0.25, 0.25, 0.25, 0.0);
        for (int i = 0; i < 14; i++) {
            level.sendParticles(new TrailParticleOption(to, BLOOD_COLOR, 12 + random.nextInt(14)),
                    from.x + (random.nextDouble() - 0.5) * 0.8,
                    from.y + (random.nextDouble() - 0.5) * 0.8,
                    from.z + (random.nextDouble() - 0.5) * 0.8,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
        level.playSound(null, feeder.getX(), feeder.getY(), feeder.getZ(), SoundEvents.GENERIC_DRINK, feeder.getSoundSource(), 1.0F, 0.6F);
    }

    // ---- Equipment ----

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        groupData = super.finalizeSpawn(level, difficulty, spawnReason, groupData);
        RandomSource random = level.getRandom();
        this.populateDefaultEquipmentSlots(random, difficulty);
        this.populateDefaultEquipmentEnchantments(level, random, difficulty);
        this.reassessWeaponGoal();
        return groupData;
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        // Most vampires fight with their claws
        float roll = random.nextFloat();
        if (roll < 0.15F) {
            this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        } else if (roll < 0.30F) {
            this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(random.nextBoolean() ? Items.IRON_SWORD : Items.STONE_SWORD));
        } else if (roll < 0.45F) {
            this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(random.nextBoolean() ? Items.IRON_AXE : Items.STONE_AXE));
        }
    }

    /// Switches between the bow and melee goals depending on what the vampire is holding (same as skeletons).
    public void reassessWeaponGoal() {
        if (this.level() == null || this.level().isClientSide()) {
            return;
        }
        this.goalSelector.removeGoal(this.meleeGoal);
        this.goalSelector.removeGoal(this.bowGoal);
        if (this.isHoldingBow()) {
            this.goalSelector.addGoal(4, this.bowGoal);
        } else {
            this.goalSelector.addGoal(4, this.meleeGoal);
        }
    }

    public boolean isHoldingBow() {
        return this.getItemInHand(ProjectileUtil.getWeaponHoldingHand(this, item -> item instanceof BowItem)).getItem() instanceof BowItem;
    }

    @Override
    public void onEquipItem(EquipmentSlot slot, ItemStack oldStack, ItemStack stack) {
        super.onEquipItem(slot, oldStack, stack);
        if (!this.level().isClientSide()) {
            this.reassessWeaponGoal();
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.reassessWeaponGoal();
    }

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        ItemStack bow = this.getItemInHand(ProjectileUtil.getWeaponHoldingHand(this, item -> item instanceof BowItem));
        ItemStack projectile = this.getProjectile(bow);
        AbstractArrow arrow = ProjectileUtil.getMobArrow(this, projectile, power, bow);
        if (bow.getItem() instanceof ProjectileWeaponItem weapon) {
            arrow = weapon.customArrow(arrow, projectile, bow);
        }
        double dx = target.getX() - this.getX();
        double dy = target.getY(1.0 / 3.0) - arrow.getY();
        double dz = target.getZ() - this.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        if (this.level() instanceof ServerLevel serverLevel) {
            Projectile.spawnProjectileUsingShoot(arrow, serverLevel, projectile, dx, dy + horizontalDistance * 0.2, dz, 1.6F, this.rangedAttackUncertainty(serverLevel));
        }
        this.playSound(SoundEvents.ARROW_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
    }

    /// Vanilla's door breaking doesn't stop the mob from wandering off mid-way (more than 2 blocks away
    /// cancels it), so vampires bumping into doors while roaming villages would knock and leave. This version
    /// holds the vampire at the door until it's down, unless prey comes into view, and is faster than zombies.
    /// Whether the sun is beating down on this vampire right now (and it has no helmet to hide under)
    public boolean isExposedToSun() {
        Level level = this.level();
        BlockPos eyes = BlockPos.containing(this.getX(), this.getEyeY(), this.getZ());
        return level.isBrightOutside()
                && level.environmentAttributes().getValue(EnvironmentAttributes.MONSTERS_BURN, this.position())
                && level.canSeeSky(eyes)
                && this.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
    }

    /// When the sun comes up, a vampire caught in the open drops everything and makes for cover: under a roof,
    /// into a house (breaking the door down on the way, see VampireBreakDoorGoal) or down into a cave.
    /// Once it's in the shade, RestrictSunGoal keeps it from wandering back out.
    static class SeekShelterGoal extends Goal {
        private static final int SEARCH_RADIUS = 16;
        private static final int SEARCH_COLUMNS = 96;
        private static final int SEARCH_UP = 4;
        private static final int SEARCH_DOWN = 6;
        private static final int CHECK_INTERVAL = 20;

        private final Vampire vampire;
        private @Nullable BlockPos shelter;
        private int nextCheck = 0;

        SeekShelterGoal(Vampire vampire) {
            this.vampire = vampire;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (--this.nextCheck > 0 || !this.vampire.isExposedToSun()) {
                return false;
            }
            this.nextCheck = reducedTickDelay(CHECK_INTERVAL);
            this.shelter = this.findShelter();
            return this.shelter != null;
        }

        @Override
        public boolean canContinueToUse() {
            return !this.vampire.getNavigation().isDone();
        }

        @Override
        public void start() {
            BlockPos target = this.shelter;
            if (target != null) {
                this.vampire.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.3);
            }
        }

        // The closest spot nearby where a vampire can stand with a roof (or a cave ceiling) over its head.
        // Samples columns around it and scans each one top to bottom, so roofs and cave mouths are hard to miss.
        private @Nullable BlockPos findShelter() {
            Level level = this.vampire.level();
            RandomSource random = this.vampire.getRandom();
            BlockPos origin = this.vampire.blockPosition();
            BlockPos best = null;
            double bestDistance = Double.MAX_VALUE;
            for (int i = 0; i < SEARCH_COLUMNS; i++) {
                int dx = random.nextInt(SEARCH_RADIUS * 2 + 1) - SEARCH_RADIUS;
                int dz = random.nextInt(SEARCH_RADIUS * 2 + 1) - SEARCH_RADIUS;
                for (int dy = SEARCH_UP; dy >= -SEARCH_DOWN; dy--) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (level.isEmptyBlock(pos) && level.isEmptyBlock(pos.above())
                            && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
                            && !level.canSeeSky(pos.above())) {
                        double distance = pos.distSqr(origin);
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            best = pos;
                        }
                        break;
                    }
                }
            }
            return best;
        }
    }

    static class VampireBreakDoorGoal extends BreakDoorGoal {
        private static final int BREAK_TIME = 160;

        VampireBreakDoorGoal(Vampire vampire) {
            super(vampire, difficulty -> difficulty != Difficulty.PEACEFUL);
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        protected int getDoorBreakTime() {
            return BREAK_TIME;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = this.mob.getTarget();
            boolean preyInSight = target != null && this.mob.getSensing().hasLineOfSight(target);
            return super.canContinueToUse() && !preyInSight;
        }

        @Override
        public void tick() {
            this.mob.getNavigation().stop();
            this.mob.getLookControl().setLookAt(this.doorPos.getX() + 0.5, this.doorPos.getY() + 1.0, this.doorPos.getZ() + 0.5);
            super.tick();
        }
    }

    // ---- Sounds ----

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.HOSTILE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.HOSTILE_DEATH;
    }
}

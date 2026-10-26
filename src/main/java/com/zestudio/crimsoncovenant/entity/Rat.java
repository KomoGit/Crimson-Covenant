package com.zestudio.crimsoncovenant.entity;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.block.RatBurrowBlock;
import com.zestudio.crimsoncovenant.block.RatBurrowBlockEntity;
import com.zestudio.crimsoncovenant.block.RatTrapBlock;
import com.zestudio.crimsoncovenant.effect.PlagueEffect;
import com.zestudio.crimsoncovenant.effect.Rabies;
import com.zestudio.crimsoncovenant.registry.ModBlocks;
import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.registry.ModEntities;
import com.zestudio.crimsoncovenant.registry.ModTags;

import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.stats.Stats;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/// A rat: a vermin that lives in burrows dug into the earth (see RatBurrowBlock), in forests and around villages.
///
/// By day (and while it rains) rats keep to their burrows. At night they come out and raid the fields and stores, eating crops, hay,
/// pumpkins and melons down to nothing (see the crimsoncovenant:rats_eat tag), and scavenge any food lying about:
/// anything edible that's been dropped, cake included (see isScavenge). A rat that has eaten is ready to breed, and two that meet have a litter of two, who grow up in a day. Left alone, a village's rats multiply until, past ten, a Miasma settles over
/// it (see MiasmaCloud). Their bite can pass on the Plague.
///
/// Rats bolt the moment they see people (players, villagers, Plague Doctors), cats or iron golems, home to their
/// burrow if they have one, but a rat that's hurt bites back, and its neighbours join in. Break
/// open a burrow and everyone who lives in it comes boiling out for you. Iron golems and cats hunt them, and a
/// baited Rat Trap lures them in.
///
/// Ten or more together are a swarm, bold enough to hunt: now and then a hungry swarm picks out a farm animal and
/// every rat in it goes for it at once, then devours it, meat and all.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public class Rat extends Animal {
    /// No more breeding once this many rats crowd round
    public static final int CROWD_LIMIT = 20;
    private static final double CROWD_RANGE = 32.0;
    /// More rats than this around a village and the Miasma comes
    public static final int MIASMA_THRESHOLD = 10;
    public static final double VILLAGE_RANGE = 48.0;
    private static final int MIASMA_CHECK_INTERVAL = 200;
    /// How long a rat whose home was broken open stays out for blood
    public static final int ANGER_TICKS = 600;
    /// Once out of the burrow, a while before it goes back in
    public static final int STAY_OUT_TICKS = 400;
    /// How long a rat chews at a meal before it's gone
    public static final int CHEW_TICKS = 50;
    private static final int BITE_INTERVAL = 8;
    /// A meal brings a youngster this much (of the time it has left) closer to grown
    private static final float GROWTH_PER_MEAL = 0.25F;
    // Farm animals that catch sight of a rat raise the alarm, no more than this often each
    private static final double ALARM_RANGE = 8.0;
    private static final int ALARM_INTERVAL = 40;
    private static final int ALARM_COOLDOWN = 200;
    private static final Map<Animal, Long> LAST_ALARM = new WeakHashMap<>();
    /// How far off a rat notices something it's afraid of
    private static final double DANGER_RANGE = 12.0;
    /// This many rats together are a swarm, and hunt livestock
    public static final int SWARM_SIZE = 10;
    private static final double SWARM_RANGE = 16.0;
    /// How often (on average, in ticks) each hungry rat in a swarm thinks to start a hunt
    private static final int HUNT_CHANCE = 1200;
    /// After a kill, the swarm is full for a while (two minutes) before it hunts again
    public static final int SATED_TICKS = 2400;
    private static final double FEAST_RANGE = 8.0;
    private static final float PLAGUE_CHANCE = 0.3F;
    private static final int PLAGUE_DURATION = 600;

    private @Nullable BlockPos homePos;
    private int angerTicks;
    private int stayOutTicks;
    private int satedTicks;

    public Rat(EntityType<? extends Rat> type, Level level) {
        super(type, level);
        // Rats keep out of the water, and away from its edge where they can help it
        this.setPathfindingMalus(PathType.WATER, -1.0F);
        this.setPathfindingMalus(PathType.WATER_BORDER, 8.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes()
                // Half a heart: any blow kills one, even a bare fist
                .add(Attributes.MAX_HEALTH, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 2.0)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.4, true));
        // In danger, a rat with a burrow makes for it; one without just runs (below)
        this.goalSelector.addGoal(1, new HideAtHomeGoal(this));
        // Skittish: they bolt as soon as they see people (players, villagers, plague doctors and traders), unless
        // they've picked a fight, and always from cats and golems
        this.goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Player.class, 12.0F, 1.3, 1.7,
                entity -> this.getTarget() == null && !entity.isSpectator() && !(entity instanceof Player player && player.isCreative())));
        this.goalSelector.addGoal(2, new AvoidEntityGoal<>(this, AbstractVillager.class, 10.0F, 1.3, 1.7, entity -> this.getTarget() != entity));
        this.goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Cat.class, 12.0F, 1.4, 1.8));
        this.goalSelector.addGoal(2, new AvoidEntityGoal<>(this, IronGolem.class, 14.0F, 1.4, 1.8));
        this.goalSelector.addGoal(3, new BreedGoal(this, 1.0));
        this.goalSelector.addGoal(4, new TakeBaitGoal(this));
        this.goalSelector.addGoal(5, new RaidCropsGoal(this));
        this.goalSelector.addGoal(5, new ScavengeFoodGoal(this));
        this.goalSelector.addGoal(6, new GoHomeGoal(this));
        this.goalSelector.addGoal(6, new DigBurrowGoal(this));
        this.goalSelector.addGoal(7, new FollowParentGoal(this, 1.1));
        this.goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 1.0));
        this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));
        // Hurt one and the rest of the pack joins in
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        this.targetSelector.addGoal(2, new SwarmHuntGoal(this));
    }

    // ---- Breeding ----

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(Items.ROTTEN_FLESH);
    }

    @Override
    public boolean canFallInLove() {
        return super.canFallInLove() && !this.isCrowded();
    }

    public boolean isCrowded() {
        return this.level().getEntitiesOfClass(Rat.class, this.getBoundingBox().inflate(CROWD_RANGE)).size() >= CROWD_LIMIT;
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        Rat young = ModEntities.RAT.get().create(level, EntitySpawnReason.BREEDING);
        if (young != null) {
            young.homePos = this.familyHome(partner);
        }
        return young;
    }

    // The young move into their parents' burrow
    private @Nullable BlockPos familyHome(AgeableMob partner) {
        return this.homePos != null ? this.homePos : partner instanceof Rat other ? other.homePos : null;
    }

    /// Rats have litters: two young at a time, who grow up in a day
    @Override
    public void spawnChildFromBreeding(ServerLevel level, Animal partner) {
        super.spawnChildFromBreeding(level, partner);
        Rat second = (Rat) this.getBreedOffspring(level, partner);
        if (second != null) {
            second.setBaby(true);
            second.snapTo(this.getX(), this.getY(), this.getZ(), 0.0F, 0.0F);
            level.addFreshEntityWithPassengers(second);
        }
    }

    /// As for any animal, but rats breeding is vermin multiplying, not husbandry: no experience comes of it
    @Override
    public void finalizeSpawnChildFromBreeding(ServerLevel level, Animal partner, @Nullable AgeableMob offspring) {
        Optional.ofNullable(this.getLoveCause()).or(() -> Optional.ofNullable(partner.getLoveCause())).ifPresent(cause -> {
            cause.awardStat(Stats.ANIMALS_BRED);
            CriteriaTriggers.BRED_ANIMALS.trigger(cause, this, partner, offspring);
        });
        this.setAge(6000);
        partner.setAge(6000);
        this.resetLove();
        partner.resetLove();
        level.broadcastEntityEvent(this, (byte) 18);
    }

    /// Called whenever the rat eats, crops or dropped food: a fed rat is ready to breed, and a young one grows
    public void feed() {
        this.heal(1.0F);
        if (this.isBaby()) {
            this.ageUp((int) (-this.getAge() * GROWTH_PER_MEAL / 20), true);
        } else if (this.canFallInLove()) {
            this.setInLove(null);
        }
        this.playSound(SoundEvents.GENERIC_EAT.value(), 0.4F, 1.6F);
    }

    // ---- Home ----

    public @Nullable BlockPos getHomePos() {
        return this.homePos;
    }

    public void setHomePos(@Nullable BlockPos homePos) {
        this.homePos = homePos;
    }

    public void leftBurrow(BlockPos burrow) {
        this.homePos = burrow;
        this.stayOutTicks = STAY_OUT_TICKS;
    }

    /// Its home broken open: out for the culprit's blood
    public void becomeAngryAt(LivingEntity culprit) {
        this.setTarget(culprit);
        this.angerTicks = ANGER_TICKS;
        this.stayOutTicks = ANGER_TICKS;
    }

    public boolean isAngry() {
        return this.angerTicks > 0;
    }

    private @Nullable RatBurrowBlockEntity home() {
        return this.homePos != null && this.level().getBlockEntity(this.homePos) instanceof RatBurrowBlockEntity burrow ? burrow : null;
    }

    // ---- Living ----

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        if (this.stayOutTicks > 0) {
            this.stayOutTicks--;
        }
        if (this.angerTicks > 0 && --this.angerTicks == 0 && this.getTarget() instanceof Player) {
            this.setTarget(null);
        }
        if (this.satedTicks > 0) {
            this.satedTicks--;
        }
        // The swarm sets its targets all at once, so each rat lets go of the prey itself once it's dead
        if (this.getTarget() != null && !this.getTarget().isAlive()) {
            this.setTarget(null);
        }
        if ((this.tickCount + this.getId()) % MIASMA_CHECK_INTERVAL == 0) {
            this.checkForMiasma(level);
        }
        if ((this.tickCount + this.getId()) % ALARM_INTERVAL == 0) {
            this.alarmFarmAnimals(level);
        }
    }

    /// Farm animals that can see a rat cry out: horses rear and whinny, cows low, sheep bleat, pigs squeal, chickens
    /// cluck. Returns how many did.
    public int alarmFarmAnimals(ServerLevel level) {
        long now = level.getGameTime();
        int alarmed = 0;
        for (Animal animal : level.getEntitiesOfClass(Animal.class, this.getBoundingBox().inflate(ALARM_RANGE),
                other -> isFarmAnimal(other) && other.isAlive() && other.hasLineOfSight(this))) {
            Long last = LAST_ALARM.get(animal);
            if (last != null && now - last < ALARM_COOLDOWN) {
                continue;
            }
            LAST_ALARM.put(animal, now);
            animal.getLookControl().setLookAt(this);
            if (animal instanceof AbstractHorse horse) {
                horse.makeMad();
            } else {
                animal.playAmbientSound();
            }
            alarmed++;
        }
        return alarmed;
    }

    /// Livestock: animals other than rats, pets and the wild ones that hunt them
    public static boolean isFarmAnimal(Animal animal) {
        return !(animal instanceof Rat) && !(animal instanceof TamableAnimal) && !(animal instanceof Fox);
    }

    /// Too many rats around a village bring the Miasma. It rises from their burrows: this rat's own, or the nearest,
    /// so every burrow in an infested village breeds its own cloud.
    public void checkForMiasma(ServerLevel level) {
        if (level.isVillage(this.blockPosition()) && countRatsNear(level, this.position(), VILLAGE_RANGE) > MIASMA_THRESHOLD) {
            MiasmaCloud.gatherOver(level, this.miasmaSource(level));
        }
    }

    /// The doorstep of the burrow the Miasma rises from: this rat's home, the nearest burrow, or failing any, the rat
    public BlockPos miasmaSource(ServerLevel level) {
        if (this.homePos != null && RatBurrowBlock.isBurrow(level.getBlockState(this.homePos))) {
            return RatBurrowBlock.doorstep(level.getBlockState(this.homePos), this.homePos);
        }
        BlockPos nearest = null;
        for (BlockPos pos : BlockPos.betweenClosed(this.blockPosition().offset(-16, -4, -16), this.blockPosition().offset(16, 4, 16))) {
            if (RatBurrowBlock.isBurrow(level.getBlockState(pos)) && (nearest == null || pos.distSqr(this.blockPosition()) < nearest.distSqr(this.blockPosition()))) {
                nearest = pos.immutable();
            }
        }
        return nearest != null ? RatBurrowBlock.doorstep(level.getBlockState(nearest), nearest) : this.blockPosition();
    }

    public static int countRatsNear(ServerLevel level, net.minecraft.world.phys.Vec3 centre, double range) {
        return level.getEntitiesOfClass(Rat.class, AABB.ofSize(centre, range * 2, range, range * 2)).size();
    }

    // ---- Swarms ----

    /// The rats near this one, itself included
    public List<Rat> swarm() {
        return this.level().getEntitiesOfClass(Rat.class, this.getBoundingBox().inflate(SWARM_RANGE), Rat::isAlive);
    }

    public boolean isSated() {
        return this.satedTicks > 0;
    }

    private boolean canHunt() {
        return !this.isBaby() && !this.isSated() && this.getTarget() == null && !this.isAngry();
    }

    /// If this rat is one of a swarm, the swarm picks out the nearest farm animal it can see and every hungry rat in
    /// it goes for it at once. Returns the prey, or null if there's no swarm or nothing to hunt.
    public @Nullable Animal raiseSwarm() {
        if (!this.canHunt()) {
            return null;
        }
        List<Rat> swarm = this.swarm();
        if (swarm.size() < SWARM_SIZE) {
            return null;
        }
        Animal prey = this.level().getEntitiesOfClass(Animal.class, this.getBoundingBox().inflate(SWARM_RANGE),
                        animal -> isFarmAnimal(animal) && animal.isAlive() && this.hasLineOfSight(animal))
                .stream().min((a, b) -> Double.compare(a.distanceToSqr(this), b.distanceToSqr(this))).orElse(null);
        if (prey == null) {
            return null;
        }
        for (Rat rat : swarm) {
            if (rat.canHunt()) {
                rat.setTarget(prey);
            }
        }
        this.playSound(SoundEvents.SILVERFISH_AMBIENT, 1.0F, 0.8F);
        return prey;
    }

    /// Now and then, a hungry rat in a swarm starts a hunt
    static class SwarmHuntGoal extends Goal {
        private final Rat rat;

        SwarmHuntGoal(Rat rat) {
            this.rat = rat;
        }

        @Override
        public boolean canUse() {
            return this.rat.canHunt() && this.rat.getRandom().nextInt(reducedTickDelay(HUNT_CHANCE)) == 0 && this.rat.raiseSwarm() != null;
        }

        // The hunt itself is the attack goal's: this only sets the swarm on its prey
        @Override
        public boolean canContinueToUse() {
            return false;
        }
    }

    /// The swarm that brought down a farm animal feasts on it: every rat in at the kill eats its fill
    private void feastOn(ServerLevel level, Animal prey) {
        for (Rat rat : level.getEntitiesOfClass(Rat.class, prey.getBoundingBox().inflate(FEAST_RANGE), Rat::isAlive)) {
            if (rat == this || rat.getTarget() == prey) {
                rat.feed();
                rat.satedTicks = SATED_TICKS;
                rat.setTarget(null);
            }
        }
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState()),
                prey.getX(), prey.getY(0.5), prey.getZ(), 24, 0.4, 0.3, 0.4, 0.1);
    }

    /// Nothing edible is left of an animal a swarm has eaten: its meat goes, its hide and wool stay
    @SubscribeEvent
    static void devourTheKill(LivingDropsEvent event) {
        if (event.getEntity() instanceof Animal animal && isFarmAnimal(animal) && event.getSource().getEntity() instanceof Rat) {
            event.getDrops().removeIf(item -> item.getItem().has(DataComponents.FOOD));
        }
    }

    /// A villager killed by a rat rises again as a zombie villager, as one killed by a zombie does (always, though,
    /// whatever the difficulty): same trade, same level, same offers
    @Override
    public boolean killedEntity(ServerLevel level, LivingEntity entity, DamageSource source) {
        boolean perished = super.killedEntity(level, entity, source);
        if (entity instanceof Animal animal && isFarmAnimal(animal)) {
            this.feastOn(level, animal);
        }
        if (entity instanceof Villager villager && EventHooks.canLivingConvert(villager, EntityTypes.ZOMBIE_VILLAGER, timer -> {})
                && turnToZombie(level, villager) != null) {
            return false;
        }
        return perished;
    }

    /// Turns the villager into a zombie villager, keeping their trade and standing. Returns the zombie villager, or null
    /// if it couldn't be done.
    public static @Nullable ZombieVillager turnToZombie(ServerLevel level, Villager villager) {
        ZombieVillager zombie = villager.convertTo(EntityTypes.ZOMBIE_VILLAGER, ConversionParams.single(villager, true, true), converted -> {
            converted.setVillagerDataFinalized(villager.getVillagerDataFinalized());
            converted.finalizeSpawn(level, level.getCurrentDifficultyAt(converted.blockPosition()), EntitySpawnReason.CONVERSION, null);
            converted.setVillagerData(villager.getVillagerData());
            converted.setGossips(villager.getGossips().copy());
            converted.setTradeOffers(villager.getOffers().copy());
            converted.setVillagerXp(villager.getVillagerXp());
            EventHooks.onLivingConvert(villager, converted);
            level.levelEvent(null, 1026, converted.blockPosition(), 0);
        });
        return zombie;
    }

    // Their bite can carry the Plague
    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean bit = super.doHurtTarget(level, target);
        if (bit && target instanceof LivingEntity living && this.random.nextFloat() < PLAGUE_CHANCE && !PlagueEffect.isImmune(living)) {
            living.addEffect(new MobEffectInstance(ModEffects.PLAGUE, PLAGUE_DURATION), this);
        }
        return bit;
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        // Like rabbits and foxes, a spawned group comes with the odd youngster
        if (spawnReason == EntitySpawnReason.NATURAL && level.getRandom().nextFloat() < 0.2F) {
            this.setBaby(true);
        }
        // One in five comes with rabies
        Rabies.mayCarry(this, spawnReason, level.getRandom());
        return super.finalizeSpawn(level, difficulty, spawnReason, groupData);
    }

    /// A little experience for killing one: one to three for a grown rat, one for a youngster (most animals' young
    /// give nothing, but these are vermin, not livestock)
    @Override
    protected int getBaseExperienceReward(ServerLevel level) {
        return this.isBaby() ? 1 : 1 + this.random.nextInt(3);
    }

    @Override
    public boolean shouldDropExperience() {
        return true;
    }

    // Vermin don't despawn once they've made a home
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return this.homePos == null && super.removeWhenFarAway(distanceToClosestPlayer);
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return SoundEvents.SILVERFISH_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SILVERFISH_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SILVERFISH_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.SILVERFISH_STEP, 0.1F, 1.4F);
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 1.5F;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.storeNullable("home_pos", BlockPos.CODEC, this.homePos);
        output.putInt("anger", this.angerTicks);
        output.putInt("sated", this.satedTicks);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.homePos = input.read("home_pos", BlockPos.CODEC).orElse(null);
        this.angerTicks = input.getIntOr("anger", 0);
        this.satedTicks = input.getIntOr("sated", 0);
    }

    // ---- Who hunts rats ----

    /// Iron golems and cats hunt rats
    @SubscribeEvent
    static void huntRats(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof IronGolem golem) {
            golem.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(golem, Rat.class, true));
        } else if (event.getEntity() instanceof Cat cat) {
            cat.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(cat, Rat.class, false, (target, level) -> !cat.isInSittingPose()));
            cat.goalSelector.addGoal(4, new CatChaseRatGoal(cat));
        }
    }

    /// A cat after a rat doesn't stalk it the way it stalks a rabbit: it pounces after it flat out, faster than a
    /// rat can run
    static class CatChaseRatGoal extends MeleeAttackGoal {
        public static final double SPEED = 1.8;
        private final Cat cat;

        CatChaseRatGoal(Cat cat) {
            super(cat, SPEED, true);
            this.cat = cat;
        }

        @Override
        public boolean canUse() {
            return this.cat.getTarget() instanceof Rat && !this.cat.isInSittingPose() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return this.cat.getTarget() instanceof Rat && !this.cat.isInSittingPose() && super.canContinueToUse();
        }
    }

    // ---- Goals ----

    /// By night, off to the fields and stores: eat a crop, root and all, or a hay bale, pumpkin or melon
    static class RaidCropsGoal extends MoveToBlockGoal {
        private final Rat rat;

        RaidCropsGoal(Rat rat) {
            super(rat, 1.1, 24, 2);
            this.rat = rat;
        }

        @Override
        public boolean canUse() {
            return this.rat.level().isDarkOutside() && !this.rat.isAngry() && !Rabies.refusesFood(this.rat)
                    && EventHooks.canEntityGrief(getServerLevel(this.rat), this.rat) && super.canUse();
        }

        @Override
        protected boolean isValidTarget(LevelReader level, BlockPos pos) {
            BlockState crop = level.getBlockState(pos);
            return isRatFood(level.getBlockState(pos));
        }

        @Override
        protected BlockPos getMoveToTarget() {
            return this.blockPos;
        }

        // Always hungry, always breeding: they're soon looking for the next meal
        @Override
        protected int nextStartTick(net.minecraft.world.entity.PathfinderMob mob) {
            return reducedTickDelay(40 + mob.getRandom().nextInt(60));
        }

        @Override
        public double acceptedDistance() {
            return 1.5;
        }

        private int chewing;

        @Override
        public void start() {
            super.start();
            this.chewing = 0;
        }

        // At the crop, it gnaws a while before it's gone
        @Override
        public void tick() {
            super.tick();
            if (!this.isReachedTarget()) {
                return;
            }
            this.rat.getNavigation().stop();
            this.rat.getLookControl().setLookAt(this.blockPos.getX() + 0.5, this.blockPos.getY() + 0.5, this.blockPos.getZ() + 0.5);
            if (++this.chewing % BITE_INTERVAL == 0) {
                chewOn(this.rat, new BlockParticleOption(ParticleTypes.BLOCK, this.rat.level().getBlockState(this.blockPos)), Vec3.atCenterOf(this.blockPos));
            }
            if (this.chewing >= CHEW_TICKS) {
                eatCrop(this.rat, this.blockPos);
                this.nextStartTick = 60 + this.rat.getRandom().nextInt(60);
                this.stop();
            }
        }
    }

    /// A bite of a meal: crumbs and a crunch
    static void chewOn(Rat rat, ParticleOptions crumbs, Vec3 at) {
        if (rat.level() instanceof ServerLevel level) {
            level.sendParticles(crumbs, at.x, at.y, at.z, 4, 0.15, 0.15, 0.15, 0.05);
        }
        rat.playSound(SoundEvents.GENERIC_EAT.value(), 0.35F, 1.7F + rat.getRandom().nextFloat() * 0.2F);
    }

    public static boolean isRatFood(BlockState state) {
        return state.getBlock() instanceof CropBlock || state.is(ModTags.Blocks.RATS_EAT);
    }

    /// Eats the crop (or hay bale, pumpkin, melon...) at `pos`, leaving nothing behind: no seeds, no harvest
    public static boolean eatCrop(Rat rat, BlockPos pos) {
        Level level = rat.level();
        BlockState crop = level.getBlockState(pos);
        if (!isRatFood(crop)) {
            return false;
        }
        level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        level.levelEvent(2001, pos, net.minecraft.world.level.block.Block.getId(crop));
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(rat));
        rat.feed();
        return true;
    }

    /// Whether a rat will eat this, dropped on the ground: anything edible, and the odd thing that isn't to us but is
    /// to a rat, like a cake or a sack of grain (the crimsoncovenant:rats_scavenge tag)
    public static boolean isScavenge(ItemStack stack) {
        return stack.has(DataComponents.FOOD) || stack.is(ModTags.Items.RATS_SCAVENGE);
    }

    /// Food lying about draws them in, and feeds them just like a crop
    static class ScavengeFoodGoal extends Goal {
        private static final double RANGE = 10.0;
        private final Rat rat;
        private @Nullable ItemEntity flesh;

        ScavengeFoodGoal(Rat rat) {
            this.rat = rat;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (this.rat.isAngry() || Rabies.refusesFood(this.rat) || this.rat.getRandom().nextInt(reducedTickDelay(4)) != 0) {
                return false;
            }
            List<ItemEntity> found = this.rat.level().getEntitiesOfClass(ItemEntity.class, this.rat.getBoundingBox().inflate(RANGE),
                    item -> isScavenge(item.getItem()) && item.isAlive());
            this.flesh = found.stream().min((a, b) -> Double.compare(a.distanceToSqr(this.rat), b.distanceToSqr(this.rat))).orElse(null);
            return this.flesh != null;
        }

        @Override
        public boolean canContinueToUse() {
            return this.flesh != null && this.flesh.isAlive() && !this.rat.isAngry();
        }

        private int chewing;

        @Override
        public void start() {
            this.chewing = 0;
        }

        // At the flesh, it gnaws a while before a piece is gone
        @Override
        public void tick() {
            if (this.flesh == null) {
                return;
            }
            double distance = this.rat.distanceToSqr(this.flesh);
            if (distance >= 2.0) {
                this.chewing = 0;
                if (distance < 9.0) {
                    // A path ends a little short: for the last stretch it makes straight for the meal
                    this.rat.getMoveControl().setWantedPosition(this.flesh.getX(), this.flesh.getY(), this.flesh.getZ(), 1.1);
                } else {
                    this.rat.getNavigation().moveTo(this.flesh, 1.1);
                }
                return;
            }
            this.rat.getNavigation().stop();
            this.rat.getLookControl().setLookAt(this.flesh);
            if (++this.chewing % BITE_INTERVAL == 0) {
                chewOn(this.rat, new ItemParticleOption(ParticleTypes.ITEM, ItemStackTemplate.fromNonEmptyStack(this.flesh.getItem())), this.flesh.position());
            }
            if (this.chewing >= CHEW_TICKS) {
                eatDropped(this.rat, this.flesh);
                this.flesh = null;
            }
        }

        @Override
        public void stop() {
            this.flesh = null;
            this.chewing = 0;
            this.rat.getNavigation().stop();
        }
    }

    /// Eats one from the stack of food lying on the ground
    public static void eatDropped(Rat rat, ItemEntity flesh) {
        flesh.getItem().shrink(1);
        if (flesh.getItem().isEmpty()) {
            flesh.discard();
        }
        rat.feed();
    }

    /// A baited Rat Trap is too tempting to pass up, day or night
    static class TakeBaitGoal extends MoveToBlockGoal {
        private final Rat rat;

        TakeBaitGoal(Rat rat) {
            super(rat, 1.1, 12, 2);
            this.rat = rat;
        }

        @Override
        public boolean canUse() {
            return !this.rat.isAngry() && !Rabies.refusesFood(this.rat) && super.canUse();
        }

        @Override
        protected boolean isValidTarget(LevelReader level, BlockPos pos) {
            BlockState state = level.getBlockState(pos);
            return state.is(ModBlocks.RAT_TRAP.get()) && state.getValue(RatTrapBlock.BAITED) && !state.getValue(RatTrapBlock.SPRUNG);
        }

        // A path ends a little short of where it's going, and a trap only snaps when stepped right onto: for the last
        // stretch the rat makes straight for the bait
        @Override
        public void tick() {
            super.tick();
            double dx = this.blockPos.getX() + 0.5 - this.rat.getX();
            double dz = this.blockPos.getZ() + 0.5 - this.rat.getZ();
            if (dx * dx + dz * dz < 4.0) {
                this.rat.getMoveControl().setWantedPosition(this.blockPos.getX() + 0.5, this.blockPos.getY(), this.blockPos.getZ() + 0.5, this.speedModifier);
            }
        }

        @Override
        protected BlockPos getMoveToTarget() {
            return this.blockPos;
        }

        @Override
        public double acceptedDistance() {
            return 0.5;
        }
    }

    /// Anything a rat is afraid of, in sight: people (unless they've picked a fight with it), cats and iron golems
    public @Nullable LivingEntity seenDanger() {
        return this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(DANGER_RANGE), entity -> entity.isAlive() && entity != this.getTarget()
                        && isFrightening(entity) && this.hasLineOfSight(entity))
                .stream().min((a, b) -> Double.compare(a.distanceToSqr(this), b.distanceToSqr(this))).orElse(null);
    }

    public static boolean isFrightening(LivingEntity entity) {
        if (entity instanceof Player player) {
            return !player.isCreative() && !player.isSpectator();
        }
        return entity instanceof AbstractVillager || entity instanceof Cat || entity instanceof IronGolem;
    }

    /// In danger (and not fighting), a rat with a burrow bolts for it and hides
    static class HideAtHomeGoal extends Goal {
        private final Rat rat;

        HideAtHomeGoal(Rat rat) {
            this.rat = rat;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (this.rat.homePos == null || this.rat.getTarget() != null || this.rat.isAngry()) {
                return false;
            }
            RatBurrowBlockEntity burrow = this.rat.home();
            return burrow != null && !burrow.isFull() && this.rat.seenDanger() != null;
        }

        @Override
        public boolean canContinueToUse() {
            return this.rat.homePos != null && this.rat.getTarget() == null && this.rat.home() != null;
        }

        @Override
        public void tick() {
            BlockPos home = this.rat.homePos;
            if (home == null) {
                return;
            }
            BlockPos door = RatBurrowBlock.doorstep(this.rat.level().getBlockState(home), home);
            double distance = this.rat.distanceToSqr(door.getX() + 0.5, door.getY(), door.getZ() + 0.5);
            if (distance < 2.5) {
                RatBurrowBlockEntity burrow = this.rat.home();
                if (burrow != null) {
                    burrow.tryEnter(this.rat);
                }
            } else if (distance < 9.0) {
                this.rat.getMoveControl().setWantedPosition(door.getX() + 0.5, door.getY(), door.getZ() + 0.5, 1.8);
            } else if (this.rat.getNavigation().isDone()) {
                this.rat.getNavigation().moveTo(door.getX() + 0.5, door.getY(), door.getZ() + 0.5, 1.8);
            }
        }
    }

    /// By day, or when hurt, back to the burrow
    static class GoHomeGoal extends Goal {
        private final Rat rat;

        GoHomeGoal(Rat rat) {
            this.rat = rat;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        private boolean wantsHome() {
            return !this.rat.isAngry() && this.rat.stayOutTicks <= 0 && !this.rat.isInLove()
                    && (keepsIndoors(this.rat.level()) || this.rat.getHealth() < this.rat.getMaxHealth() * 0.5F);
        }

        @Override
        public boolean canUse() {
            if (this.rat.homePos == null || !this.wantsHome()) {
                return false;
            }
            RatBurrowBlockEntity burrow = this.rat.home();
            if (burrow == null) {
                // The burrow's gone: find or dig another
                this.rat.homePos = null;
                return false;
            }
            return !burrow.isFull();
        }

        @Override
        public boolean canContinueToUse() {
            return this.rat.homePos != null && this.wantsHome();
        }

        @Override
        public void tick() {
            BlockPos home = this.rat.homePos;
            if (home == null) {
                return;
            }
            BlockPos door = RatBurrowBlock.doorstep(this.rat.level().getBlockState(home), home);
            if (this.rat.distanceToSqr(door.getX() + 0.5, door.getY(), door.getZ() + 0.5) < 2.5) {
                RatBurrowBlockEntity burrow = this.rat.home();
                if (burrow != null) {
                    burrow.tryEnter(this.rat);
                }
            } else if (this.rat.getNavigation().isDone()) {
                this.rat.getNavigation().moveTo(door.getX() + 0.5, door.getY(), door.getZ() + 0.5, 1.1);
            }
        }
    }

    /// A rat with no home, come the day, finds a burrow with room or digs one into the earth
    static class DigBurrowGoal extends Goal {
        private static final int RANGE = 10;
        private final Rat rat;
        private @Nullable BlockPos target;
        private @Nullable Direction face;
        private int timeout;

        DigBurrowGoal(Rat rat) {
            this.rat = rat;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (this.rat.homePos != null || this.rat.isBaby() || this.rat.isAngry() || this.rat.isInLove() || !keepsIndoors(this.rat.level())
                    || this.rat.getRandom().nextInt(reducedTickDelay(20)) != 0) {
                return false;
            }
            Optional<BurrowSpot> spot = findBurrowSpot(this.rat.level(), this.rat.blockPosition(), RANGE);
            if (spot.isEmpty()) {
                return false;
            }
            this.target = spot.get().pos();
            this.face = spot.get().face();
            this.timeout = 400;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return this.target != null && this.timeout > 0 && this.rat.homePos == null;
        }

        @Override
        public void tick() {
            this.timeout--;
            if (this.target == null || this.face == null) {
                return;
            }
            BlockPos door = this.target.relative(this.face);
            if (this.rat.distanceToSqr(door.getX() + 0.5, door.getY(), door.getZ() + 0.5) < 2.5) {
                digInto(this.rat, this.target, this.face);
                this.target = null;
            } else if (this.rat.getNavigation().isDone()) {
                this.rat.getNavigation().moveTo(door.getX() + 0.5, door.getY(), door.getZ() + 0.5, 1.0);
            }
        }

        @Override
        public void stop() {
            this.target = null;
        }
    }

    /// A block a rat could make its home: a burrow with room, earth with open air to dig in from, or wood with open air
    /// at its side to gnaw in from
    public record BurrowSpot(BlockPos pos, Direction face) {}

    public static Optional<BurrowSpot> findBurrowSpot(Level level, BlockPos around, int range) {
        BurrowSpot best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(around.offset(-range, -3, -range), around.offset(range, 3, range))) {
            BlockState state = level.getBlockState(pos);
            Direction face = null;
            if (RatBurrowBlock.isBurrow(state)) {
                if (level.getBlockEntity(pos) instanceof RatBurrowBlockEntity burrow && !burrow.isFull()) {
                    face = state.getValue(RatBurrowBlock.FACING);
                }
            } else if (state.is(ModTags.Blocks.RATS_BURROW_IN)) {
                face = diggableFace(level, pos, true);
            } else if (state.is(ModTags.Blocks.RATS_GNAW_INTO)) {
                face = diggableFace(level, pos, false);
            }
            if (face != null) {
                double distance = pos.distSqr(around);
                if (distance < bestDistance) {
                    best = new BurrowSpot(pos.immutable(), face);
                    bestDistance = distance;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    // A side open to the air with ground to stand on in front, or failing that (for earth) the top
    private static @Nullable Direction diggableFace(Level level, BlockPos pos, boolean fromAbove) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos front = pos.relative(side);
            if (level.getBlockState(front).isAir() && level.getBlockState(front.above()).isAir()
                    && level.getBlockState(front.below()).isFaceSturdy(level, front.below(), Direction.UP)) {
                return side;
            }
        }
        return fromAbove && level.getBlockState(pos.above()).isAir() && level.getBlockState(pos.above(2)).isAir() ? Direction.UP : null;
    }

    /// Rats keep to their burrows by day, and while it rains
    public static boolean keepsIndoors(Level level) {
        return !level.isDarkOutside() || level.isRaining();
    }

    /// Digs a burrow at `spot` (if there isn't one already) and moves a family of `rats` new rats straight in, as a new
    /// village's rats are. Returns how many went in.
    public static int settleIn(ServerLevel level, BurrowSpot spot, int rats) {
        makeBurrow(level, spot.pos(), spot.face());
        if (!(level.getBlockEntity(spot.pos()) instanceof RatBurrowBlockEntity burrow)) {
            return 0;
        }
        int settled = 0;
        BlockPos door = spot.pos().relative(spot.face());
        for (int i = 0; i < rats; i++) {
            Rat rat = ModEntities.RAT.get().create(level, EntitySpawnReason.STRUCTURE);
            if (rat == null) {
                continue;
            }
            rat.snapTo(door.getX() + 0.5, door.getY(), door.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
            Rabies.mayCarry(rat, EntitySpawnReason.STRUCTURE, level.getRandom());
            if (burrow.tryEnter(rat)) {
                settled++;
            }
        }
        return settled;
    }

    // Turns the block into a burrow, if it isn't one already: a Rat Hole if it's wood
    private static void makeBurrow(Level level, BlockPos pos, Direction face) {
        BlockState state = level.getBlockState(pos);
        if (!RatBurrowBlock.isBurrow(state)) {
            boolean wood = state.is(ModTags.Blocks.RATS_GNAW_INTO);
            level.setBlock(pos, (wood ? ModBlocks.RAT_HOLE : ModBlocks.RAT_BURROW).get().defaultBlockState().setValue(RatBurrowBlock.FACING, face), 3);
        }
    }

    /// Makes the block at `pos` a burrow (if it isn't one already: a Rat Hole if it's wood) and moves the rat in
    public static void digInto(Rat rat, BlockPos pos, Direction face) {
        Level level = rat.level();
        BlockState state = level.getBlockState(pos);
        if (!RatBurrowBlock.isBurrow(state)) {
            boolean wood = state.is(ModTags.Blocks.RATS_GNAW_INTO);
            level.setBlock(pos, (wood ? ModBlocks.RAT_HOLE : ModBlocks.RAT_BURROW).get().defaultBlockState().setValue(RatBurrowBlock.FACING, face), 3);
            level.playSound(null, pos, wood ? SoundEvents.WOOD_BREAK : SoundEvents.ROOTED_DIRT_BREAK, rat.getSoundSource(), 1.0F, 1.4F);
        }
        rat.homePos = pos.immutable();
        if (level.getBlockEntity(pos) instanceof RatBurrowBlockEntity burrow) {
            burrow.tryEnter(rat);
        }
    }

}

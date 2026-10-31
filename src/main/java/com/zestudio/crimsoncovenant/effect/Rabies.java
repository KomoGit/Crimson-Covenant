package com.zestudio.crimsoncovenant.effect;

import java.util.EnumSet;
import java.util.Map;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.entity.Rat;
import com.zestudio.crimsoncovenant.entity.VampireBat;
import com.zestudio.crimsoncovenant.registry.ModDamageTypes;
import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.vampirism.Vampirism;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingUseTotemEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/// Rabies, carried by wild animals: some wild wolves, foxes, vampire bats and rats are born with it.
///
/// It comes in two stages. First Hydrophobia (HydrophobiaEffect): the sick animal won't eat and runs from water. It
/// lasts about a day, and a Potion of Cleanse cures it (Plague Doctors sell it, but won't treat it themselves). Left
/// alone it becomes Rabies
/// (RabiesEffect), which nothing cures: the rabid animal foams at the mouth, attacks people on sight and snaps at any
/// creature that comes near, even those that can't catch it (never biting to kill), and two days later it dies. It
/// fears water as much as ever, so it won't go for anyone standing by it. Healthy animals keep well clear of it. Every bite has a one in ten chance of
/// passing Hydrophobia on. A rabid animal is worth more experience to whoever puts it down.
///
/// Both stages are mob effects, so a player sees them with their time left. Milk doesn't wash either away: only a
/// Cleanse takes Hydrophobia off (see cleanse), and no potion takes Rabies off. Death can still be cheated, though: a
/// Totem of Undying, whenever it saves its holder, burns the sickness out with everything else, and a vampire's Defy
/// Death that turns aside the death Rabies brings ends it too.
///
/// Who catches it: anything with living blood that can bite or strike, players included, and cats. Not the undead
/// (vampire players with them, though vampire bats carry it), bosses, iron golems, or creatures that can't attack.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public final class Rabies {
    /// How long Hydrophobia takes to turn into Rabies, after a bite: one day
    public static final int HYDROPHOBIA_TICKS = 24000;
    /// An animal born with it has had it a while already: half a day to a day and a half left
    public static final int CARRIER_MIN_TICKS = 12000;
    public static final int CARRIER_SPREAD_TICKS = 24000;
    /// How long a rabid animal has to live: two days
    public static final int RABIES_TICKS = 48000;
    public static final float BITE_CHANCE = 0.1F;
    /// How close another animal has to come before a rabid one goes for it
    public static final double NIP_RANGE = 4.0;
    /// After snapping at an animal, a rabid one leaves animals be for a few seconds
    private static final int NIP_REST = 100;
    private static final Map<LivingEntity, Long> LAST_NIP = new WeakHashMap<>();
    /// How near water has to be before a sick animal runs from it
    public static final int WATER_FEAR_RANGE = 5;
    /// Anyone this near water is safe from a rabid animal
    public static final int WATER_REFUGE_RANGE = 3;
    /// How far off healthy animals keep from a rabid one
    public static final float AVOID_RANGE = 8.0F;
    public static final int BONUS_EXPERIENCE = 5;
    private static final int FOAM_INTERVAL = 4;
    private static final int FOAM_COLOUR = 0xF4F4EA;

    /// Set while the sickness is being cured here, so the removal goes through (every other removal, milk's, is refused)
    private static boolean curing;

    private Rabies() {}

    public static boolean isHydrophobic(LivingEntity entity) {
        return entity.hasEffect(ModEffects.HYDROPHOBIA);
    }

    public static boolean isRabid(LivingEntity entity) {
        return entity.hasEffect(ModEffects.RABIES);
    }

    /// Either stage of the sickness
    public static boolean isSick(LivingEntity entity) {
        return isHydrophobic(entity) || isRabid(entity);
    }

    /// Sick animals won't eat
    public static boolean refusesFood(LivingEntity entity) {
        return isSick(entity);
    }

    public static boolean canCatch(LivingEntity entity) {
        if (entity instanceof VampireBat) {
            return true;
        }
        if (Vampirism.isUndead(entity) || !Vampirism.hasLivingBlood(entity) || entity.is(Tags.EntityTypes.BOSSES) || entity instanceof IronGolem) {
            return false;
        }
        return entity instanceof Player || entity instanceof Cat || entity.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE);
    }

    /// The odds (one in so many) this creature is born with Hydrophobia, or 0 if it never is
    public static int carrierOdds(LivingEntity entity) {
        if (entity instanceof Wolf wolf) {
            return wolf.isTame() ? 0 : 10;
        }
        if (entity instanceof Fox) {
            return 8;
        }
        if (entity instanceof VampireBat) {
            return 6;
        }
        if (entity instanceof Rat) {
            return 5;
        }
        return 0;
    }

    /// A wild-born carrier may come into the world with Hydrophobia already: those spawned naturally, with the world, or
    /// with a village (not bred, summoned or hatched from an egg). Returns whether it did.
    public static boolean mayCarry(Mob mob, EntitySpawnReason reason, RandomSource random) {
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION && reason != EntitySpawnReason.STRUCTURE) {
            return false;
        }
        int odds = carrierOdds(mob);
        if (odds == 0 || random.nextInt(odds) != 0) {
            return false;
        }
        return infect(mob, CARRIER_MIN_TICKS + random.nextInt(CARRIER_SPREAD_TICKS));
    }

    /// Gives it Hydrophobia, for `ticks` before it turns into Rabies, unless it's already sick or can't catch it
    public static boolean infect(LivingEntity entity, int ticks) {
        return !isSick(entity) && entity.addEffect(new MobEffectInstance(ModEffects.HYDROPHOBIA, ticks));
    }

    /// Hydrophobia runs its course into Rabies: no cure now. A pet forgets its orders.
    public static void becomeRabid(LivingEntity entity) {
        removeHydrophobia(entity);
        entity.addEffect(new MobEffectInstance(ModEffects.RABIES, RABIES_TICKS));
        if (entity instanceof TamableAnimal pet) {
            pet.setOrderedToSit(false);
            pet.setInSittingPose(false);
        }
    }

    /// Cures Hydrophobia (the Potion of Cleanse). Rabies stays.
    public static boolean cleanse(LivingEntity entity) {
        return removeHydrophobia(entity);
    }

    /// Cures both stages: for when death has been cheated (a Totem of Undying, Defy Death)
    public static boolean cureEntirely(LivingEntity entity) {
        boolean hydrophobia = removeHydrophobia(entity);
        curing = true;
        try {
            return entity.removeEffect(ModEffects.RABIES) || hydrophobia;
        } finally {
            curing = false;
        }
    }

    private static boolean removeHydrophobia(LivingEntity entity) {
        curing = true;
        try {
            return entity.removeEffect(ModEffects.HYDROPHOBIA);
        } finally {
            curing = false;
        }
    }

    /// Whether this rabid creature goes for `target`: people on sight, and any other creature that comes close (whether
    /// or not it could catch rabies), but not straight after it has just snapped at one. Never anyone standing by water.
    public static boolean wantsToBite(LivingEntity rabid, LivingEntity target) {
        if (target == rabid || !target.isAlive() || byWater(target)) {
            return false;
        }
        if (target instanceof Player) {
            return true;
        }
        Long nipped = LAST_NIP.get(rabid);
        return target instanceof Mob && target.distanceToSqr(rabid) <= NIP_RANGE * NIP_RANGE
                && (nipped == null || rabid.level().getGameTime() - nipped >= NIP_REST);
    }

    /// Whether this animal keeps clear of `other`: the rabid, unless it's sick itself
    public static boolean shuns(LivingEntity animal, LivingEntity other) {
        return isRabid(other) && !isSick(animal);
    }

    /// A bite from a rabid creature: `roll` (0 to 1) under the bite chance passes Hydrophobia on. Returns whether it did.
    public static boolean bite(LivingEntity rabid, LivingEntity victim, float roll) {
        return isRabid(rabid) && roll < BITE_CHANCE && canCatch(victim) && infect(victim, HYDROPHOBIA_TICKS);
    }

    /// What a rabid creature is worth, for `experience` it would be worth well
    public static int rabidExperience(int experience) {
        return experience * 2 + BONUS_EXPERIENCE;
    }

    /// Whether it turns down this food (from a player's hand)
    public static boolean refusesToEat(Animal animal, ItemStack food) {
        return refusesFood(animal) && animal.isFood(food);
    }

    /// The nearest water within reach of its fear, or null
    public static @Nullable BlockPos nearestWater(LivingEntity entity) {
        return nearestWater(entity, WATER_FEAR_RANGE);
    }

    /// Whether it's standing close enough to water that the rabid leave it be
    public static boolean byWater(LivingEntity entity) {
        return nearestWater(entity, WATER_REFUGE_RANGE) != null;
    }

    private static @Nullable BlockPos nearestWater(LivingEntity entity, int range) {
        Level level = entity.level();
        BlockPos at = entity.blockPosition();
        BlockPos nearest = null;
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-range, -2, -range), at.offset(range, 2, range))) {
            if (level.getFluidState(pos).is(FluidTags.WATER) && (nearest == null || pos.distSqr(at) < nearest.distSqr(at))) {
                nearest = pos.immutable();
            }
        }
        return nearest;
    }

    /// Where a creature's mouth is: the front of its head
    public static Vec3 mouth(LivingEntity entity) {
        Vec3 facing = Vec3.directionFromRotation(0.0F, entity.getYHeadRot());
        return entity.getEyePosition().add(facing.scale(entity.getBbWidth() * 0.5 + 0.1)).add(0.0, -entity.getBbHeight() * 0.1, 0.0);
    }

    /// Foaming at the mouth: white froth gathering at its jaws, dripping down
    public static void foam(ServerLevel level, LivingEntity entity) {
        Vec3 mouth = mouth(entity);
        level.sendParticles(new DustParticleOptions(FOAM_COLOUR, 0.6F), mouth.x, mouth.y, mouth.z, 2, 0.05, 0.03, 0.05, 0.0);
        if (entity.getRandom().nextInt(3) == 0) {
            level.sendParticles(new DustParticleOptions(FOAM_COLOUR, 0.4F), mouth.x, mouth.y - 0.15, mouth.z, 1, 0.03, 0.06, 0.03, 0.0);
        }
    }

    // ---- The sickness running its course ----

    // Turning rabid, and dying of it, are done before the creature's effects tick: it's not safe to add or remove
    // effects while they do
    @SubscribeEvent
    static void runCourse(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        MobEffectInstance hydrophobia = entity.getEffect(ModEffects.HYDROPHOBIA);
        if (hydrophobia != null && !hydrophobia.isInfiniteDuration() && hydrophobia.getDuration() <= 1) {
            becomeRabid(entity);
        }
        MobEffectInstance rabies = entity.getEffect(ModEffects.RABIES);
        if (rabies != null) {
            if (!rabies.isInfiniteDuration() && rabies.getDuration() <= 1) {
                entity.hurtServer(level, level.damageSources().source(ModDamageTypes.RABIES), Float.MAX_VALUE);
            } else if ((entity.tickCount + entity.getId()) % FOAM_INTERVAL == 0) {
                foam(level, entity);
            }
        }
    }

    @SubscribeEvent
    static void immunity(MobEffectEvent.Applicable event) {
        MobEffectInstance effect = event.getEffectInstance();
        if ((effect.is(ModEffects.HYDROPHOBIA) || effect.is(ModEffects.RABIES)) && !canCatch(event.getEntity())) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    // Milk and the like don't wash it away
    @SubscribeEvent
    static void noEasyCure(MobEffectEvent.Remove event) {
        if (!curing && (event.getEffect().is(ModEffects.RABIES) || event.getEffect().is(ModEffects.HYDROPHOBIA))) {
            event.setCanceled(true);
        }
    }

    // A Totem of Undying saving its holder, from rabies or anything else, burns the sickness out
    @SubscribeEvent
    static void totemCures(LivingUseTotemEvent event) {
        cureEntirely(event.getEntity());
    }

    // Defy Death (or anything else) turning aside the death Rabies brings ends it. Last, so it sees whether anyone did.
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    static void deathDefied(LivingDeathEvent event) {
        if (event.isCanceled() && event.getSource().is(ModDamageTypes.RABIES)) {
            cureEntirely(event.getEntity());
        }
    }

    // ---- Biting ----

    private static @Nullable LivingEntity rabidBiter(DamageSource source) {
        return source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker && !(attacker instanceof Player)
                && isRabid(attacker) ? attacker : null;
    }

    // A rabid animal bites to hurt, not to kill: anything but a person is left with at least half a heart
    @SubscribeEvent
    static void biteNotToKill(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim instanceof Player || rabidBiter(event.getSource()) == null) {
            return;
        }
        float most = victim.getHealth() - 1.0F;
        if (most <= 0.0F) {
            event.setCanceled(true);
        } else if (event.getAmount() > most) {
            event.setAmount(most);
        }
    }

    // Each bite may pass it on. Having snapped at an animal, it lets it go.
    @SubscribeEvent
    static void afterBite(LivingDamageEvent.Post event) {
        LivingEntity rabid = rabidBiter(event.getSource());
        if (rabid == null) {
            return;
        }
        LivingEntity victim = event.getEntity();
        bite(rabid, victim, rabid.getRandom().nextFloat());
        if (!(victim instanceof Player) && rabid instanceof Mob mob && mob.getTarget() == victim) {
            LAST_NIP.put(rabid, rabid.level().getGameTime());
            mob.setTarget(null);
        }
    }

    @SubscribeEvent
    static void moreExperience(LivingExperienceDropEvent event) {
        if (isRabid(event.getEntity())) {
            event.setDroppedExperience(rabidExperience(event.getDroppedExperience()));
        }
    }

    // ---- Food and water ----

    @SubscribeEvent
    static void refuseFood(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof Animal animal && refusesToEat(animal, event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    /// A fox carries its food in its mouth until it eats it: a sick one spits it out
    public static void spitOutFood(Fox fox) {
        ItemStack held = fox.getMainHandItem();
        if (!held.isEmpty() && held.has(DataComponents.FOOD) && fox.level() instanceof ServerLevel level) {
            fox.spawnAtLocation(level, held.copy());
            fox.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
    }

    // ---- Spawning, and the goals that carry it out ----

    // Wolves, foxes and vampire bats from the world's own spawning. Rats come from their own spawners and burrows,
    // and see to it themselves (Rat.finalizeSpawn).
    @SubscribeEvent
    static void bornWithIt(FinalizeSpawnEvent event) {
        Mob mob = event.getEntity();
        if (!(mob instanceof Rat)) {
            mayCarry(mob, event.getSpawnType(), event.getLevel().getRandom());
        }
    }

    @SubscribeEvent
    static void addGoals(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob)) {
            return;
        }
        // Healthy animals keep clear of the rabid
        if (mob instanceof Animal animal) {
            animal.goalSelector.addGoal(1, new ShunRabidGoal(animal));
        }
        if (!canCatch(mob)) {
            return;
        }
        mob.targetSelector.addGoal(0, new RabidTargetGoal(mob));
        if (mob instanceof PathfinderMob walker) {
            // Fear of water comes before everything, the chase included
            walker.goalSelector.addGoal(0, new ShunWaterGoal(walker));
            walker.goalSelector.addGoal(1, new RabidAttackGoal(walker));
        }
    }

    /// A rabid creature picks out who to go for (see wantsToBite), and gives up on anyone who reaches water
    static final class RabidTargetGoal extends NearestAttackableTargetGoal<LivingEntity> {
        RabidTargetGoal(Mob mob) {
            super(mob, LivingEntity.class, true, (target, level) -> wantsToBite(mob, target));
        }

        @Override
        public boolean canUse() {
            return isRabid(this.mob) && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = this.mob.getTarget();
            return isRabid(this.mob) && (target == null || !byWater(target)) && super.canContinueToUse();
        }
    }

    /// ...and goes for it, faster than it would in its right mind
    static final class RabidAttackGoal extends MeleeAttackGoal {
        RabidAttackGoal(PathfinderMob mob) {
            super(mob, 1.3, true);
        }

        @Override
        public boolean canUse() {
            return isRabid(this.mob) && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return isRabid(this.mob) && super.canContinueToUse();
        }
    }

    /// A healthy animal keeps clear of the rabid. Every animal has this, so it only looks about now and then.
    static final class ShunRabidGoal extends AvoidEntityGoal<LivingEntity> {
        ShunRabidGoal(Animal animal) {
            super(animal, LivingEntity.class, AVOID_RANGE, 1.2, 1.5, other -> shuns(animal, other));
        }

        @Override
        public boolean canUse() {
            return this.mob.getRandom().nextInt(reducedTickDelay(5)) == 0 && super.canUse();
        }
    }

    /// A sick creature, hydrophobic or rabid, runs from water it comes near
    static final class ShunWaterGoal extends Goal {
        private final PathfinderMob mob;
        private @Nullable Vec3 away;

        ShunWaterGoal(PathfinderMob mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!isSick(this.mob) || (!this.mob.isInWater() && this.mob.getRandom().nextInt(reducedTickDelay(10)) != 0)) {
                return false;
            }
            BlockPos water = nearestWater(this.mob);
            if (water == null) {
                return false;
            }
            this.away = DefaultRandomPos.getPosAway(this.mob, 12, 4, Vec3.atCenterOf(water));
            return this.away != null;
        }

        @Override
        public void start() {
            if (this.away != null) {
                this.mob.getNavigation().moveTo(this.away.x, this.away.y, this.away.z, 1.4);
            }
        }

        @Override
        public boolean canContinueToUse() {
            return isSick(this.mob) && !this.mob.getNavigation().isDone();
        }

        @Override
        public void stop() {
            this.away = null;
        }
    }
}

package com.zestudio.crimsoncovenant.entity;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.registry.ModEntities;
import com.zestudio.crimsoncovenant.registry.ModPotions;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.ZombieHorse;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/// A headless rider with a jack o'lantern for a head and an enchanted golden spear, mounted on a zombie horse. Called up
/// at a Crooked Tree (see world.HorsemanSummoning), and fought under a boss bar.
///
/// He fights hit-and-run: charges down his quarry, strikes once, then wheels away down the road before charging again.
/// One blow in nine blinds and slows a player. Every 30 seconds he lobs a Pumpkin Bomb, which knocks people flying
/// without harming the ground. He won't charge through lingering Holy Water, nor at anyone standing in it.
///
/// Slain (or his horse slain from under him), he rises as the Spirit of Halloween, and his horse as a Ghost Steed that
/// gallops off down the road: if the spirit reaches it, he rides again (see HalloweenSpirit).
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public class HeadlessHorseman extends Zombie {
    /// One blow in this many blinds and slows a player
    public static final int AFFLICTION_ODDS = 9;
    public static final int AFFLICTION_TICKS = 100;
    public static final int BOMB_COOLDOWN = 600;
    private static final int FIRST_BOMB = 100;
    private static final double BOMB_MIN_RANGE = 5.0;
    private static final double BOMB_MAX_RANGE = 28.0;
    /// How far down the road his Ghost Steed gallops when he falls
    public static final double STEED_FLIGHT = 24.0;

    private final ServerBossEvent bossEvent = new ServerBossEvent(Mth.createInsecureUUID(this.random), this.getDisplayName(),
            BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);
    private int bombCooldown = FIRST_BOMB;

    public HeadlessHorseman(EntityType<? extends HeadlessHorseman> type, Level level) {
        super(type, level);
        this.xpReward = 20;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 80.0)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0);
    }

    /// Calls the Horseman up at `at`, mounted and set on `target` (if there's one), never to wander off and fade
    public static @Nullable HeadlessHorseman summon(ServerLevel level, Vec3 at, @Nullable LivingEntity target) {
        HeadlessHorseman horseman = ModEntities.HEADLESS_HORSEMAN.get().create(level, EntitySpawnReason.EVENT);
        if (horseman == null) {
            return null;
        }
        horseman.snapTo(at.x, at.y, at.z, level.getRandom().nextFloat() * 360.0F, 0.0F);
        horseman.finalizeSpawn(level, level.getCurrentDifficultyAt(horseman.blockPosition()), EntitySpawnReason.EVENT, null);
        horseman.setPersistenceRequired();
        if (horseman.getVehicle() instanceof AbstractHorse steed) {
            steed.setPersistenceRequired();
        }
        level.addFreshEntity(horseman);
        if (target != null) {
            horseman.setTarget(target);
        }
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 1.0, at.z, 40, 0.6, 1.0, 0.6, 0.03);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 1.0, at.z, 30, 0.6, 1.0, 0.6, 0.02);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.WITCH_CELEBRATE, SoundSource.HOSTILE, 3.0F, 0.5F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ZOMBIE_HORSE_ANGRY, SoundSource.HOSTILE, 3.0F, 0.6F);
        return horseman;
    }

    @Override
    protected void addBehaviourGoals() {
        this.goalSelector.addGoal(2, new HitAndRunGoal(this));
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
        this.setDropChance(EquipmentSlot.HEAD, 0.0F);

        ItemStack spear = new ItemStack(Items.GOLDEN_SPEAR);
        var enchantments = this.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        spear.enchant(enchantments.getOrThrow(Enchantments.SHARPNESS), 3);
        spear.enchant(enchantments.getOrThrow(Enchantments.LUNGE), 2);
        this.setItemSlot(EquipmentSlot.MAINHAND, spear);
        this.setDropChance(EquipmentSlot.MAINHAND, this.dropsGear() ? 0.25F : 0.0F);
    }

    /// Whether his spear can fall when he does
    protected boolean dropsGear() {
        return true;
    }

    /// The horse he rides: a zombie horse in golden barding, which also keeps it from burning at sunrise
    protected @Nullable AbstractHorse createSteed() {
        ZombieHorse steed = EntityTypes.ZOMBIE_HORSE.create(this.level(), EntitySpawnReason.JOCKEY);
        if (steed != null) {
            steed.setItemSlot(EquipmentSlot.BODY, new ItemStack(Items.GOLDEN_HORSE_ARMOR));
            steed.setDropChance(EquipmentSlot.BODY, 0.0F);
        }
        return steed;
    }

    private void mountSteed(ServerLevelAccessor level, DifficultyInstance difficulty) {
        AbstractHorse steed = this.createSteed();
        if (steed == null) {
            return;
        }
        steed.snapTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), 0.0F);
        steed.finalizeSpawn(level, difficulty, EntitySpawnReason.JOCKEY, null);
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

    // ---- The fight ----

    /// A blow that lands has a one in AFFLICTION_ODDS chance (a `roll` of 0) of blinding and slowing a player. Returns
    /// whether it did.
    public static boolean afflict(LivingEntity target, int roll) {
        if (roll != 0 || !(target instanceof Player)) {
            return false;
        }
        target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, AFFLICTION_TICKS));
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, AFFLICTION_TICKS, 1));
        return true;
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && target instanceof LivingEntity living) {
            afflict(living, this.random.nextInt(AFFLICTION_ODDS));
        }
        return hit;
    }

    /// Whether `at` lies in a lingering cloud of Holy Water
    public static boolean isHallowed(Level level, Vec3 at) {
        for (AreaEffectCloud cloud : level.getEntitiesOfClass(AreaEffectCloud.class, new net.minecraft.world.phys.AABB(at, at).inflate(8.0))) {
            PotionContents contents = cloud.get(DataComponents.POTION_CONTENTS);
            double radius = cloud.getRadius() + 1.0;
            if (contents != null && contents.is(ModPotions.HOLY_WATER)
                    && Mth.lengthSquared(at.x - cloud.getX(), at.z - cloud.getZ()) <= radius * radius && Math.abs(at.y - cloud.getY()) < 3.0) {
                return true;
            }
        }
        return false;
    }

    /// Whether a charge from `from` to `to` would cross lingering Holy Water (or end in it)
    public static boolean isPathHallowed(Level level, Vec3 from, Vec3 to) {
        double length = from.distanceTo(to);
        int steps = Math.max(1, Mth.ceil(length / 2.0));
        for (int i = 1; i <= steps; i++) {
            if (isHallowed(level, from.lerp(to, (double) i / steps))) {
                return true;
            }
        }
        return false;
    }

    /// Whether he lobs Pumpkin Bombs
    protected boolean throwsBombs() {
        return true;
    }

    /// Lobs a Pumpkin Bomb in an arc at `target`
    public void throwBomb(ServerLevel level, LivingEntity target) {
        PumpkinBomb bomb = new PumpkinBomb(level, this);
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        double dy = target.getY(0.3) - bomb.getY();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        bomb.shoot(dx, dy + horizontal * 0.2, dz, 1.1F + (float) horizontal * 0.02F, 4.0F);
        level.addFreshEntity(bomb);
        this.swingForAttack(net.minecraft.world.InteractionHand.MAIN_HAND);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WITCH_THROW, SoundSource.HOSTILE, 1.0F, 0.6F);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WITCH_CELEBRATE, SoundSource.HOSTILE, 1.5F, 0.7F);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        LivingEntity target = this.getTarget();
        if (this.throwsBombs() && target != null && target.isAlive()) {
            if (this.bombCooldown > 0) {
                this.bombCooldown--;
            } else {
                double distance = this.distanceTo(target);
                if (distance >= BOMB_MIN_RANGE && distance <= BOMB_MAX_RANGE && this.hasLineOfSight(target)) {
                    this.throwBomb(level, target);
                    this.bombCooldown = BOMB_COOLDOWN;
                }
            }
        }
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
    }

    // ---- Death, and the spirit that rises ----

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (this.level() instanceof ServerLevel level && this.isDeadOrDying()) {
            this.riseAsSpirit(level, source);
        }
    }

    /// The Spirit of Halloween rises from him, and his horse comes back as a Ghost Steed that gallops away down the road
    protected void riseAsSpirit(ServerLevel level, DamageSource source) {
        Vec3 from = this.position();
        LivingEntity chaser = source.getEntity() instanceof LivingEntity killer ? killer : level.getNearestPlayer(this, 32.0);
        Vec3 away = chaser != null ? from.subtract(chaser.position()).multiply(1.0, 0.0, 1.0) : Vec3.ZERO;
        if (away.lengthSqr() < 1.0E-4) {
            float angle = this.random.nextFloat() * Mth.TWO_PI;
            away = new Vec3(Mth.cos(angle), 0.0, Mth.sin(angle));
        }
        GhostSteed steed = GhostSteed.gallopFrom(level, from, away.normalize().scale(STEED_FLIGHT));
        HalloweenSpirit spirit = ModEntities.HALLOWEEN_SPIRIT.get().create(level, EntitySpawnReason.EVENT);
        if (spirit == null) {
            return;
        }
        spirit.snapTo(from.x, from.y + 0.5, from.z, this.getYRot(), 0.0F);
        spirit.setSteed(steed);
        level.addFreshEntity(spirit);
        level.sendParticles(ParticleTypes.SOUL, from.x, from.y + 1.0, from.z, 40, 0.5, 1.0, 0.5, 0.05);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 3.0F, 0.5F);
    }

    /// The highest ground at `x`, `z`
    static Vec3 groundAt(ServerLevel level, double x, double z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
        return new Vec3(x, y, z);
    }

    // ---- Boss bar ----

    /// Whether he fights under a boss bar
    public boolean hasBossBar() {
        return true;
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (this.hasBossBar()) {
            this.bossEvent.addPlayer(player);
        }
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        this.bossEvent.setName(this.getDisplayName());
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("BombCooldown", this.bombCooldown);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.bombCooldown = input.getIntOr("BombCooldown", FIRST_BOMB);
        if (this.hasCustomName()) {
            this.bossEvent.setName(this.getDisplayName());
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

    /// Hit and run: rides his quarry down at a gallop, strikes once, then wheels away down the road to come round again.
    /// He won't charge through lingering Holy Water, or at anyone standing in it: he keeps his distance instead.
    static class HitAndRunGoal extends Goal {
        private static final double CHARGE_SPEED = 1.6;
        private static final double RETREAT_SPEED = 1.4;
        private static final int RETREAT_TICKS = 70;
        /// How far off he rides before turning to charge again
        private static final double RETREAT_DISTANCE = 16.0;
        private final HeadlessHorseman horseman;
        private boolean charging = true;
        private int retreatTicks;
        private int attackCooldown;
        private @Nullable Vec3 retreatTo;

        HitAndRunGoal(HeadlessHorseman horseman) {
            this.horseman = horseman;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = this.horseman.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public void start() {
            this.charging = true;
            this.horseman.setAggressive(true);
        }

        @Override
        public void stop() {
            this.horseman.setAggressive(false);
            this.horseman.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = this.horseman.getTarget();
            if (target == null) {
                return;
            }
            this.horseman.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (this.attackCooldown > 0) {
                this.attackCooldown--;
            }
            if (this.charging) {
                if (isPathHallowed(this.horseman.level(), this.horseman.position(), target.position())) {
                    this.wheelAway(target, 30);
                    return;
                }
                if (this.horseman.getNavigation().isDone() || this.horseman.tickCount % 10 == 0) {
                    this.horseman.getNavigation().moveTo(target, CHARGE_SPEED);
                }
                if (this.attackCooldown <= 0 && this.horseman.isWithinMeleeAttackRange(target) && this.horseman.level() instanceof ServerLevel level) {
                    this.horseman.swingForAttack(net.minecraft.world.InteractionHand.MAIN_HAND);
                    this.horseman.doHurtTarget(level, target);
                    this.attackCooldown = 20;
                    this.wheelAway(target, RETREAT_TICKS);
                }
            } else {
                this.retreatTicks--;
                if (this.retreatTo != null && (this.horseman.getNavigation().isDone() || this.horseman.tickCount % 20 == 0)) {
                    this.horseman.getNavigation().moveTo(this.retreatTo.x, this.retreatTo.y, this.retreatTo.z, RETREAT_SPEED);
                }
                if (this.retreatTicks <= 0 || (this.horseman.getNavigation().isDone() && this.horseman.distanceTo(target) >= RETREAT_DISTANCE * 0.6)) {
                    this.charging = true;
                }
            }
        }

        private void wheelAway(LivingEntity target, int ticks) {
            this.charging = false;
            this.retreatTicks = ticks;
            this.retreatTo = DefaultRandomPos.getPosAway(this.horseman, (int) RETREAT_DISTANCE, 6, target.position());
        }
    }
}

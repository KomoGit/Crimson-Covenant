package com.zestudio.crimsoncovenant.entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.block.BearTrapBlock;
import com.zestudio.crimsoncovenant.registry.ModDamageTypes;
import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.registry.ModEntities;
import com.zestudio.crimsoncovenant.registry.ModTags;
import com.zestudio.crimsoncovenant.vampirism.Vampirism;
import com.zestudio.crimsoncovenant.world.BloodMoon;
import com.zestudio.crimsoncovenant.skill.BloodSkill;
import com.zestudio.crimsoncovenant.guide.SkillLore;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.TrailParticleOption;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/// A vampire lord: much tougher than a regular vampire, drains the life of every enemy standing near it,
/// sends telegraphed bat swarms down a fixed path, grows blood armor and calls spiders and vampire bats
/// to its side when badly hurt. It knows a vampire's blood skills too: it hurls Blood Spears and boils the ground
/// under its prey with Boiling Hemorrhage. The summons attack whatever it's fighting
/// and crumble to dust when it dies, even if that happens out of their sight. Unlike other vampires it hunts turned players too, and it only walks the
/// world during a blood moon. Shows a boss bar to nearby players.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public class MasterVampire extends Vampire {
    /// The chance a Master Vampire slain by a player under a Blood Moon leaves the Ancient Tome of Blood Parade, the only
    /// way it's found
    public static final float BLOOD_PARADE_CHANCE = 0.1F;
    // Life drain aura
    private static final double DRAIN_RANGE = 6.0;
    private static final int DRAIN_INTERVAL = 20;
    private static final float DRAIN_DAMAGE = 1.0F;

    // Summoning
    private static final float SUMMON_HEALTH_THRESHOLD = 0.4F;
    private static final int SUMMON_COOLDOWN = 600;
    private static final int SPIDERS_PER_SUMMON = 3;
    private static final int BATS_PER_SUMMON = 3;
    private static final int MAX_MINIONS = 9;
    private static final int MINION_UPDATE_INTERVAL = 10;
    /// How long a bear trap holds a master before it tears the thing apart
    private static final int TRAP_STRUGGLE_TICKS = 40;
    /// How often a summon checks that its master still lives, and how far it looks for it
    private static final int MASTER_CHECK_INTERVAL = 20;
    private static final double MASTER_RANGE = 64.0;

    // Blood armor
    private static final float BLOOD_ARMOR_THRESHOLD = 0.4F;
    private static final float BLOOD_ARMOR_REDUCTION = 0.3F;

    // Bat swarm
    private static final int SWARM_FIRST_COOLDOWN = 100;
    private static final int SWARM_MIN_COOLDOWN = 120;
    private static final int SWARM_EXTRA_COOLDOWN = 80;
    private static final int SWARM_WINDUP = 30;
    // Just far enough past the target's spot to fly through its body
    private static final double SWARM_OVERSHOOT = 1.5;
    private static final double SWARM_MIN_RANGE = 5.0;
    private static final double SWARM_MAX_RANGE = 24.0;

    // Blood skills
    private static final int SPEAR_FIRST_COOLDOWN = 60;
    private static final int SPEAR_MIN_COOLDOWN = 100;
    private static final int SPEAR_EXTRA_COOLDOWN = 60;
    private static final double SPEAR_MIN_RANGE = 5.0;
    private static final double SPEAR_MAX_RANGE = 24.0;
    private static final float SPEAR_SPEED = 2.4F;
    private static final int HEMORRHAGE_FIRST_COOLDOWN = 120;
    private static final int HEMORRHAGE_MIN_COOLDOWN = 300;
    private static final int HEMORRHAGE_EXTRA_COOLDOWN = 100;
    private static final double HEMORRHAGE_RANGE = 16.0;

    /// Entity tag put on summoned minions, see Vampire.isFamiliar
    public static final String FAMILIAR_TAG = "crimsoncovenant.familiar";

    private final ServerBossEvent bossEvent = new ServerBossEvent(Mth.createInsecureUUID(this.random), this.getDisplayName(),
            BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
    private final List<UUID> minions = new ArrayList<>();
    private int summonCooldown = 0;
    private int trapStruggle = 0;
    private int spearCooldown = SPEAR_FIRST_COOLDOWN;
    private int hemorrhageCooldown = HEMORRHAGE_FIRST_COOLDOWN;
    private boolean wasBloodArmored = false;
    private int swarmCooldown = SWARM_FIRST_COOLDOWN;
    private int swarmWindup = 0;
    private Vec3 swarmOrigin = Vec3.ZERO;
    private Vec3 swarmAim = Vec3.ZERO;

    public MasterVampire(EntityType<? extends MasterVampire> type, Level level) {
        super(type, level);
        this.xpReward = 50;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 80.0)
                .add(Attributes.MOVEMENT_SPEED, 0.33)
                .add(Attributes.ATTACK_DAMAGE, 9.0)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5)
                .add(Attributes.FOLLOW_RANGE, 40.0);
    }

    // Masters fight with their claws
    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (this.tickCount % DRAIN_INTERVAL == 0) {
            this.drainLife(level);
        }
        if (this.tickCount % MINION_UPDATE_INTERVAL == 0) {
            this.commandMinions(level);
        }
        if (this.summonCooldown > 0) {
            this.summonCooldown--;
        } else if (this.canSummon()) {
            this.summonMinions(level);
            this.summonCooldown = SUMMON_COOLDOWN;
        }
        this.tickBloodArmor(level);
        this.tickBatSwarm(level);
        this.tearFreeOfTraps(level);
        this.castBloodSkills(level);
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(0, new SwarmCastGoal());
        // A master answers to no one: turned players are prey like any other
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // ---- Life drain ----

    /// Every living enemy nearby loses a little health each second, and the master gains it. No line of sight
    /// needed. Undead have no living blood to drain.
    private void drainLife(ServerLevel level) {
        // Holy Water suppresses the aura for as long as its flame burns
        if (this.hasEffect(ModEffects.HOLY_FLAME)) {
            return;
        }
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(DRAIN_RANGE), this::isDrainTarget);
        for (LivingEntity victim : victims) {
            if (victim.hurtServer(level, level.damageSources().source(ModDamageTypes.LIFE_DRAIN, this), DRAIN_DAMAGE)) {
                this.heal(DRAIN_DAMAGE);
                Vec3 from = victim.position().add(0.0, victim.getBbHeight() * 0.6, 0.0);
                Vec3 to = this.position().add(0.0, this.getBbHeight() * 0.6, 0.0);
                for (int i = 0; i < 3; i++) {
                    level.sendParticles(new TrailParticleOption(to, 0x7A0A1E, 15 + this.random.nextInt(10)),
                            from.x + (this.random.nextDouble() - 0.5) * 0.6, from.y + (this.random.nextDouble() - 0.5) * 0.6,
                            from.z + (this.random.nextDouble() - 0.5) * 0.6, 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
        }
    }

    private boolean isDrainTarget(LivingEntity entity) {
        return entity != this && this.isEnemy(entity) && Vampirism.hasLivingBlood(entity);
    }

    /// What the master's aura and bat swarm hurt: every player (vampire or not), vampire prey, and whatever it's
    /// fighting
    public boolean isEnemy(LivingEntity entity) {
        if (entity instanceof Player player) {
            return player.isAlive() && !player.isCreative() && !player.isSpectator();
        }
        return isEnemyOfVampires(entity, this.getTarget());
    }

    public static boolean isEnemyOfVampires(LivingEntity entity, @Nullable LivingEntity currentTarget) {
        if (!entity.isAlive() || entity instanceof Vampire || entity instanceof VampireBat || isFamiliar(entity)) {
            return false;
        }
        if (entity instanceof Player player) {
            return !player.isCreative() && !player.isSpectator() && !Vampirism.isVampire(player);
        }
        return entity == currentTarget || entity.is(ModTags.Entities.VAMPIRE_PREY);
    }

    // ---- Blood skills ----

    private void castBloodSkills(ServerLevel level) {
        if (this.spearCooldown > 0) {
            this.spearCooldown--;
        }
        if (this.hemorrhageCooldown > 0) {
            this.hemorrhageCooldown--;
        }
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive() || this.isCastingBatSwarm() || !this.getSensing().hasLineOfSight(target)) {
            return;
        }
        double distance = this.distanceTo(target);
        if (this.hemorrhageCooldown <= 0 && distance <= HEMORRHAGE_RANGE && target.onGround()) {
            this.boilHemorrhage(level, target);
            this.hemorrhageCooldown = HEMORRHAGE_MIN_COOLDOWN + this.random.nextInt(HEMORRHAGE_EXTRA_COOLDOWN);
        } else if (this.spearCooldown <= 0 && distance >= SPEAR_MIN_RANGE && distance <= SPEAR_MAX_RANGE) {
            this.hurlBloodSpear(level, target);
            this.spearCooldown = SPEAR_MIN_COOLDOWN + this.random.nextInt(SPEAR_EXTRA_COOLDOWN);
        }
    }

    /// The Blood Spear skill: a spear of blood that drives its victim back and slams them into any wall behind
    public void hurlBloodSpear(ServerLevel level, LivingEntity target) {
        BloodSpear spear = new BloodSpear(level, this);
        spear.pickup = AbstractArrow.Pickup.DISALLOWED;
        spear.setPos(this.getX(), this.getEyeY() - 0.1, this.getZ());
        double dx = target.getX() - spear.getX();
        double dy = target.getY(0.5) - spear.getY();
        double dz = target.getZ() - spear.getZ();
        spear.shoot(dx, dy + Math.sqrt(dx * dx + dz * dz) * 0.05, dz, SPEAR_SPEED, 1.0F);
        level.addFreshEntity(spear);
        this.swingForAttack(InteractionHand.MAIN_HAND);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.TRIDENT_THROW.value(), SoundSource.HOSTILE, 1.5F, 0.6F);
        level.sendParticles(new DustParticleOptions(0x9B0A14, 1.4F), this.getX(), this.getEyeY(), this.getZ(), 12, 0.3, 0.3, 0.3, 0.0);
    }

    /// The Boiling Hemorrhage skill, under the target's feet. It spares the master's own kind, and whatever dies in it
    /// heals the master.
    public void boilHemorrhage(ServerLevel level, LivingEntity target) {
        level.addFreshEntity(HemorrhageZone.create(level, this, target.getX(), target.getY(), target.getZ()));
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.LAVA_AMBIENT, SoundSource.HOSTILE, 1.5F, 0.6F);
        this.playSound(SoundEvents.EVOKER_CAST_SPELL, 1.5F, 0.6F);
    }

    // ---- Bear traps ----

    /// No trap holds a master for long: it thrashes for a couple of seconds, then rips the jaws apart
    private void tearFreeOfTraps(ServerLevel level) {
        Optional<BlockPos> trap = BearTrapBlock.heldIn(this);
        if (trap.isEmpty()) {
            this.trapStruggle = 0;
            return;
        }
        if (this.trapStruggle % 10 == 0) {
            level.sendParticles(new DustParticleOptions(0x9B0A14, 1.2F), this.getX(), this.getY(0.2), this.getZ(), 6, 0.3, 0.1, 0.3, 0.0);
            this.playSound(SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, 0.6F, 0.6F);
        }
        if (++this.trapStruggle >= TRAP_STRUGGLE_TICKS) {
            BearTrapBlock.breakOpen(level, trap.get(), this, false);
            this.trapStruggle = 0;
        }
    }

    // ---- Blood armor ----

    /// Below 40% health a crimson shell (drawn like the wither's armor) takes the edge off every hit
    public boolean isBloodArmored() {
        return this.getHealth() < this.getMaxHealth() * BLOOD_ARMOR_THRESHOLD;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (this.isBloodArmored() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            damage *= 1.0F - BLOOD_ARMOR_REDUCTION;
        }
        return super.hurtServer(level, source, damage);
    }

    private void tickBloodArmor(ServerLevel level) {
        boolean armored = this.isBloodArmored();
        if (armored && !this.wasBloodArmored) {
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 1.0F, 1.4F);
            level.sendParticles(new DustParticleOptions(0x9B0A14, 2.0F), this.getX(), this.getY(0.5), this.getZ(), 40, 0.6, 0.9, 0.6, 0.0);
        }
        this.wasBloodArmored = armored;
    }

    // ---- Bat swarm ----

    public boolean isCastingBatSwarm() {
        return this.swarmWindup > 0;
    }

    /// Starts the telegraph: the vampire stands still and marks the swarm's path, which is locked in now
    /// so the target can step out of the way. The bats are released when the wind-up ends.
    public void beginBatSwarm(LivingEntity target) {
        this.swarmOrigin = this.position().add(0.0, this.getBbHeight() * 0.7, 0.0);
        this.swarmAim = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
        this.swarmWindup = SWARM_WINDUP;
        this.playSound(SoundEvents.EVOKER_PREPARE_ATTACK, 2.0F, 0.6F);
    }

    private void tickBatSwarm(ServerLevel level) {
        if (this.swarmWindup > 0) {
            if (this.swarmWindup % 2 == 0) {
                this.drawSwarmPath(level);
            }
            if (--this.swarmWindup == 0) {
                this.releaseBatSwarm(level);
            }
            return;
        }
        if (this.swarmCooldown > 0) {
            this.swarmCooldown--;
            return;
        }
        LivingEntity target = this.getTarget();
        if (target != null && target.isAlive() && this.getSensing().hasLineOfSight(target)) {
            double distance = this.distanceTo(target);
            if (distance >= SWARM_MIN_RANGE && distance <= SWARM_MAX_RANGE) {
                this.beginBatSwarm(target);
                this.swarmCooldown = SWARM_MIN_COOLDOWN + this.random.nextInt(SWARM_EXTRA_COOLDOWN);
            }
        }
    }

    private Vec3 swarmDirection() {
        Vec3 direction = this.swarmAim.subtract(this.swarmOrigin);
        return direction.lengthSqr() < 1.0E-4 ? this.getLookAngle() : direction.normalize();
    }

    // The swarm flies to where the target stood (and just through it), then vanishes
    private double swarmRange() {
        return this.swarmAim.distanceTo(this.swarmOrigin) + SWARM_OVERSHOOT;
    }

    // A line of blood mist along the whole path, plus smoke gathering around the vampire
    private void drawSwarmPath(ServerLevel level) {
        Vec3 direction = this.swarmDirection();
        for (double d = 1.0; d < this.swarmRange(); d += 1.0) {
            Vec3 point = this.swarmOrigin.add(direction.scale(d));
            level.sendParticles(new DustParticleOptions(0x5A0010, 1.2F), point.x, point.y, point.z, 1, 0.05, 0.05, 0.05, 0.0);
        }
        level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY(0.6), this.getZ(), 3, 0.4, 0.5, 0.4, 0.01);
    }

    private void releaseBatSwarm(ServerLevel level) {
        SwarmBat.launchSwarm(level, this, this.swarmOrigin, this.swarmDirection(), this.swarmRange());
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BAT_TAKEOFF, SoundSource.HOSTILE, 2.0F, 0.6F);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.PHANTOM_SWOOP, SoundSource.HOSTILE, 1.5F, 0.8F);
    }

    /// Holds the vampire still and facing down the swarm's path during the wind-up
    private class SwarmCastGoal extends Goal {
        SwarmCastGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return MasterVampire.this.isCastingBatSwarm();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            MasterVampire.this.getNavigation().stop();
            Vec3 aim = MasterVampire.this.swarmAim;
            MasterVampire.this.getLookControl().setLookAt(aim.x, aim.y, aim.z);
        }
    }

    // ---- Summoning ----

    private boolean canSummon() {
        return this.getHealth() < this.getMaxHealth() * SUMMON_HEALTH_THRESHOLD
                && this.getTarget() != null
                && this.minions.size() + SPIDERS_PER_SUMMON + BATS_PER_SUMMON <= MAX_MINIONS;
    }

    private void summonMinions(ServerLevel level) {
        for (int i = 0; i < SPIDERS_PER_SUMMON; i++) {
            this.summonMinion(level, EntityTypes.SPIDER);
        }
        for (int i = 0; i < BATS_PER_SUMMON; i++) {
            this.summonMinion(level, ModEntities.VAMPIRE_BAT.get());
        }
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1.5F, 0.6F);
    }

    private void summonMinion(ServerLevel level, EntityType<? extends Mob> type) {
        BlockPos pos = this.findSummonPos(level, type);
        if (pos == null) {
            return;
        }
        Mob minion = type.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (minion == null) {
            return;
        }
        minion.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, this.random.nextFloat() * 360.0F, 0.0F);
        minion.addTag(FAMILIAR_TAG);
        minion.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.MOB_SUMMONED, null);
        level.addFreshEntity(minion);
        this.minions.add(minion.getUUID());
        this.setMinionTarget(minion, this.getTarget());

        level.sendParticles(ParticleTypes.LARGE_SMOKE, minion.getX(), minion.getY() + 0.5, minion.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
        level.sendParticles(new DustParticleOptions(0x9B0A14, 1.2F), minion.getX(), minion.getY() + 0.5, minion.getZ(), 10, 0.3, 0.4, 0.3, 0.0);
    }

    private @Nullable BlockPos findSummonPos(ServerLevel level, EntityType<?> type) {
        if (type == ModEntities.VAMPIRE_BAT.get()) {
            return this.findAirPos(level, type);
        }
        for (int attempt = 0; attempt < 10; attempt++) {
            BlockPos column = this.blockPosition().offset(this.random.nextInt(7) - 3, 0, this.random.nextInt(7) - 3);
            for (int dy = 2; dy >= -2; dy--) {
                BlockPos pos = column.above(dy);
                if (SpawnPlacementTypes.ON_GROUND.isSpawnPositionOk(level, pos, type) && level.noCollision(type.getSpawnAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5))) {
                    return pos;
                }
            }
        }
        return null;
    }

    // Bats appear in the air around the master's head
    private @Nullable BlockPos findAirPos(ServerLevel level, EntityType<?> type) {
        for (int attempt = 0; attempt < 10; attempt++) {
            BlockPos pos = this.blockPosition().offset(this.random.nextInt(5) - 2, 1 + this.random.nextInt(2), this.random.nextInt(5) - 2);
            if (level.noCollision(type.getSpawnAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5)) && level.getFluidState(pos).isEmpty()) {
                return pos;
            }
        }
        return null;
    }

    /// Keeps every summoned minion attacking whatever the master is attacking, and forgets dead ones.
    private void commandMinions(ServerLevel level) {
        LivingEntity target = this.getTarget();
        this.minions.removeIf(uuid -> {
            Entity entity = level.getEntity(uuid);
            if (!(entity instanceof Mob minion) || !minion.isAlive()) {
                return true;
            }
            if (target != null && minion.getTarget() != target) {
                this.setMinionTarget(minion, target);
            }
            return false;
        });
    }

    private void setMinionTarget(Mob minion, @Nullable LivingEntity target) {
        if (target == null) {
            return;
        }
        minion.setTarget(target);
    }

    public List<UUID> getMinions() {
        return List.copyOf(this.minions);
    }

    /// The summons are bound to the master and crumble away when it dies or despawns.
    private void dismissMinions() {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        for (UUID uuid : this.minions) {
            Entity minion = level.getEntity(uuid);
            if (minion != null && minion.isAlive()) {
                crumble(level, minion);
            }
        }
        this.minions.clear();
    }

    private static void crumble(ServerLevel level, Entity minion) {
        level.sendParticles(ParticleTypes.LARGE_SMOKE, minion.getX(), minion.getY() + 0.5, minion.getZ(), 15, 0.3, 0.4, 0.3, 0.02);
        level.sendParticles(new DustParticleOptions(0x9B0A14, 1.2F), minion.getX(), minion.getY() + 0.5, minion.getZ(), 10, 0.3, 0.4, 0.3, 0.0);
        minion.discard();
    }

    /// A summon whose master is gone (killed while the summon was out of reach, or unloaded and never came back)
    /// crumbles too
    @SubscribeEvent
    static void minionsOutliveNoMaster(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (entity.tickCount % MASTER_CHECK_INTERVAL != 0 || !isFamiliar(entity) || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        UUID uuid = entity.getUUID();
        boolean bound = !level.getEntitiesOfClass(MasterVampire.class, new AABB(entity.blockPosition()).inflate(MASTER_RANGE),
                master -> master.isAlive() && master.minions.contains(uuid)).isEmpty();
        if (!bound) {
            crumble(level, entity);
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        this.dismissMinions();
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (reason.shouldDestroy()) {
            this.dismissMinions();
        }
        super.remove(reason);
    }

    // ---- Boss bar ----

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
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
        output.store("Minions", UUIDUtil.CODEC.listOf(), List.copyOf(this.minions));
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.minions.clear();
        input.read("Minions", UUIDUtil.CODEC.listOf()).ifPresent(this.minions::addAll);
        if (this.hasCustomName()) {
            this.bossEvent.setName(this.getDisplayName());
        }
    }

    // Under a Blood Moon, one slain by a player now and then leaves the Ancient Tome of Blood Parade
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
        super.dropCustomDeathLoot(level, source, killedByPlayer);
        if (dropsBloodParade(level, source, this.random.nextFloat())) {
            this.spawnAtLocation(level, new ItemStack(BloodSkill.BLOOD_PARADE.tome()));
        }
    }

    /// Whether the Ancient Tome of Blood Parade drops, for a death by `source` and a roll of `roll` (0 to 1)
    public static boolean dropsBloodParade(ServerLevel level, DamageSource source, float roll) {
        return source.getEntity() instanceof Player player && SkillLore.isUnlocked(player, BloodSkill.BLOOD_PARADE) && BloodMoon.isActive(level)
                && roll < BLOOD_PARADE_CHANCE;
    }
}

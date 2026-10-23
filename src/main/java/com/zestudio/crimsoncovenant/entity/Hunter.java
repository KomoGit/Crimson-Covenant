package com.zestudio.crimsoncovenant.entity;

import java.util.Comparator;
import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.registry.ModEntities;
import com.zestudio.crimsoncovenant.world.BloodMoon;
import com.zestudio.crimsoncovenant.vampirism.Vampirism;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.CommonHooks;

/// A human monster hunter. Hunters roam in parties of three led by a banner-carrying leader.
/// They attack every undead mob (vampires included) and every infected or vampire player on sight,
/// and fight back against anything that hurts them.
public class Hunter extends PathfinderMob implements CrossbowAttackMob {
    private static final EntityDataAccessor<Boolean> IS_CHARGING_CROSSBOW = SynchedEntityData.defineId(Hunter.class, EntityDataSerializers.BOOLEAN);
    public static final int PARTY_SIZE = 3;
    /// Chance that a naturally spawned party is led by a Master Hunter
    public static final float MASTER_HUNTER_PARTY_CHANCE = 0.15F;

    private boolean leader = false;
    private boolean ascensionPending = false;

    public Hunter(EntityType<? extends Hunter> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 24.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.ATTACK_DAMAGE, 2.0)
                .add(Attributes.ARMOR, 2.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // The crossbow goal only runs while holding a crossbow, otherwise the melee goal takes over
        this.goalSelector.addGoal(2, new RangedCrossbowAttackGoal<>(this, 1.0, 12.0F));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, false));
        this.goalSelector.addGoal(4, new FollowPartyLeaderGoal(this, 1.0));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        // Retaliate against anything that hurts us (and call nearby hunters to help), except other hunters
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, Hunter.class).setAlertOthers());
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, true,
                (target, level) -> target.is(EntityTypeTags.UNDEAD)));
        // Werewolves too, even though they're not undead
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Werewolf.class, true));
        // Hunters can tell when someone carries the vampire curse, even before they've fully turned
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true,
                (target, level) -> target instanceof Player player && Vampirism.isTainted(player)));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(IS_CHARGING_CROSSBOW, false);
    }

    public boolean isLeader() {
        return this.leader;
    }

    public void setLeader(boolean leader) {
        this.leader = leader;
    }

    // ---- Spawning & equipment ----

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        // A spawn egg summons a whole party, with the egg's hunter as the leader
        boolean fromSpawnEgg = spawnReason == EntitySpawnReason.SPAWN_ITEM_USE;
        if (fromSpawnEgg) {
            this.leader = true;
        }

        RandomSource random = level.getRandom();
        this.populateDefaultEquipmentSlots(random, difficulty);

        if (fromSpawnEgg) {
            spawnFollowers(level.getLevel(), this.blockPosition(), PARTY_SIZE - 1, random);
        }
        return super.finalizeSpawn(level, difficulty, spawnReason, groupData);
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        // Leaders always carry an axe, everyone else has an even chance of an axe or a crossbow
        if (this.leader || random.nextBoolean()) {
            this.setItemSlot(EquipmentSlot.MAINHAND, createHunterAxe(random, this.leader));
        } else {
            this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
        }

        if (this.leader) {
            this.setItemSlot(EquipmentSlot.HEAD, createHunterBanner(this.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN)));
            // Values above 1 guarantee the drop, same as the pillager captain's banner
            this.setDropChance(EquipmentSlot.HEAD, 2.0F);
        }
    }

    // Only some hunters carry a Smite axe: 30% of regular hunters (Smite I-II), 50% of leaders (Smite I-III)
    private ItemStack createHunterAxe(RandomSource random, boolean forLeader) {
        ItemStack axe = new ItemStack(Items.IRON_AXE);
        float smiteChance = forLeader ? 0.5F : 0.3F;
        if (random.nextFloat() < smiteChance) {
            enchantSmite(axe, 1 + random.nextInt(forLeader ? 3 : 2));
        }
        return axe;
    }

    protected void enchantSmite(ItemStack weapon, int level) {
        weapon.enchant(this.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SMITE), level);
    }

    public static ItemStack createHunterBanner(HolderGetter<BannerPattern> patterns) {
        ItemStack banner = new ItemStack(Items.BANNER.pick(DyeColor.RED));
        banner.set(DataComponents.BANNER_PATTERNS, new BannerPatternLayers.Builder()
                .addIfRegistered(patterns, BannerPatterns.STRAIGHT_CROSS, DyeColor.WHITE)
                .addIfRegistered(patterns, BannerPatterns.TRIANGLES_BOTTOM, DyeColor.BLACK)
                .addIfRegistered(patterns, BannerPatterns.BORDER, DyeColor.BLACK)
                .build());
        banner.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.BANNER_PATTERNS, true));
        banner.set(DataComponents.ITEM_NAME, Component.translatable("item.crimsoncovenant.hunter_banner"));
        banner.set(DataComponents.RARITY, Rarity.UNCOMMON);
        return banner;
    }

    /// Spawns a full party of hunters around the given position. The first hunter becomes the leader, and during
    /// a blood moon some parties are led by a Master Hunter. Returns how many hunters were actually spawned.
    public static int spawnParty(ServerLevel level, BlockPos origin, RandomSource random, EntitySpawnReason reason) {
        return spawnParty(level, origin, random, reason, pickLeaderType(level, random));
    }

    /// Master Hunters only take to the field during a blood moon
    public static EntityType<? extends Hunter> pickLeaderType(ServerLevel level, RandomSource random) {
        return BloodMoon.isActive(level) && random.nextFloat() < MASTER_HUNTER_PARTY_CHANCE ? ModEntities.MASTER_HUNTER.get() : ModEntities.HUNTER.get();
    }

    public static int spawnParty(ServerLevel level, BlockPos origin, RandomSource random, EntitySpawnReason reason, EntityType<? extends Hunter> leaderType) {
        BlockPos leaderPos = findGroundNear(level, origin);
        Hunter leader = leaderPos == null ? null : createAt(level, leaderType, leaderPos, reason);
        if (leader == null) {
            return 0;
        }
        leader.setLeader(true);
        leader.finalizeSpawn(level, level.getCurrentDifficultyAt(leaderPos), reason, null);
        level.addFreshEntityWithPassengers(leader);
        return 1 + spawnFollowers(level, leaderPos, PARTY_SIZE - 1, random);
    }

    private static int spawnFollowers(ServerLevel level, BlockPos origin, int count, RandomSource random) {
        int spawned = 0;
        for (int attempt = 0; attempt < count * 4 && spawned < count; attempt++) {
            BlockPos pos = findGroundNear(level, origin.offset(random.nextInt(5) - random.nextInt(5), 0, random.nextInt(5) - random.nextInt(5)));
            Hunter follower = pos == null ? null : createAt(level, ModEntities.HUNTER.get(), pos, EntitySpawnReason.MOB_SUMMONED);
            if (follower != null) {
                follower.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.MOB_SUMMONED, null);
                level.addFreshEntityWithPassengers(follower);
                spawned++;
            }
        }
        return spawned;
    }

    // Looks a few blocks up and down for a spot a hunter can stand on, so followers stay at the leader's level
    private static @Nullable BlockPos findGroundNear(ServerLevel level, BlockPos pos) {
        EntityType<Hunter> type = ModEntities.HUNTER.get();
        for (int dy = 3; dy >= -3; dy--) {
            BlockPos candidate = pos.above(dy);
            if (SpawnPlacementTypes.ON_GROUND.isSpawnPositionOk(level, candidate, type)) {
                return candidate;
            }
        }
        return null;
    }

    private static @Nullable Hunter createAt(ServerLevel level, EntityType<? extends Hunter> type, BlockPos pos, EntitySpawnReason reason) {
        if (!SpawnPlacementTypes.ON_GROUND.isSpawnPositionOk(level, pos, type)) {
            return null;
        }
        Hunter hunter = type.create(level, reason);
        if (hunter != null) {
            hunter.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
        }
        return hunter;
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        // Like pillager patrols, parties only despawn once the player is really far away (128 blocks)
        return distSqr > 16384.0;
    }

    // ---- Combat ----

    @Override
    public boolean killedEntity(ServerLevel level, LivingEntity victim, DamageSource source) {
        boolean result = super.killedEntity(level, victim, source);
        // A regular hunter that slays a Master Vampire becomes a Master Hunter (on its next tick, not mid-attack)
        if (victim instanceof MasterVampire && this.getType() == ModEntities.HUNTER.get()) {
            this.ascensionPending = true;
        }
        return result;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (this.ascensionPending) {
            this.ascensionPending = false;
            MasterHunter master = this.convertTo(ModEntities.MASTER_HUNTER.get(), ConversionParams.single(this, false, false), converted -> {
                converted.setHealth(converted.getMaxHealth());
                converted.populateDefaultEquipmentSlots(level.getRandom(), level.getCurrentDifficultyAt(converted.blockPosition()));
            });
            if (master != null) {
                level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, master.getX(), master.getY(1.0), master.getZ(), 60, 0.5, 0.8, 0.5, 0.3);
                level.playSound(null, master.getX(), master.getY(), master.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 1.0F, 0.8F);
            }
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        // No friendly fire between hunters (stray crossbow bolts, sweeping axes)
        if (source.getEntity() instanceof Hunter) {
            return false;
        }
        return super.hurtServer(level, source, damage);
    }

    public boolean isChargingCrossbow() {
        return this.entityData.get(IS_CHARGING_CROSSBOW);
    }

    @Override
    public void setChargingCrossbow(boolean isCharging) {
        this.entityData.set(IS_CHARGING_CROSSBOW, isCharging);
    }

    @Override
    public void onCrossbowAttackPerformed() {
        this.noActionTime = 0;
    }

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        this.performCrossbowAttack(this, 1.6F);
    }

    // Hunters have unlimited arrows, like pillagers and skeletons. Only Monster provides this by default,
    // and without it the crossbow has nothing to load and keeps charging forever.
    @Override
    public ItemStack getProjectile(ItemStack heldWeapon) {
        if (heldWeapon.getItem() instanceof ProjectileWeaponItem weapon) {
            ItemStack heldProjectile = ProjectileWeaponItem.getHeldProjectile(this, weapon.getSupportedHeldProjectiles(heldWeapon));
            return CommonHooks.getProjectile(this, heldWeapon, heldProjectile.isEmpty() ? new ItemStack(Items.ARROW) : heldProjectile);
        }
        return super.getProjectile(heldWeapon);
    }

    // ---- Sounds ----

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    // ---- Save data ----

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("Leader", this.leader);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.leader = input.getBooleanOr("Leader", false);
    }

    /// Keeps party members close to their leader while they aren't fighting.
    static class FollowPartyLeaderGoal extends Goal {
        private static final double SEARCH_RANGE = 32.0;
        private static final double START_FOLLOW_DIST_SQR = 10.0 * 10.0;
        private static final double STOP_FOLLOW_DIST_SQR = 4.0 * 4.0;

        private final Hunter hunter;
        private final double speedModifier;
        private @Nullable Hunter partyLeader;
        private int timeToRecalcPath;

        FollowPartyLeaderGoal(Hunter hunter, double speedModifier) {
            this.hunter = hunter;
            this.speedModifier = speedModifier;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (this.hunter.isLeader() || this.hunter.getTarget() != null) {
                return false;
            }
            this.partyLeader = this.hunter.level()
                    .getEntitiesOfClass(Hunter.class, this.hunter.getBoundingBox().inflate(SEARCH_RANGE), h -> h.isLeader() && h.isAlive())
                    .stream()
                    .min(Comparator.comparingDouble(this.hunter::distanceToSqr))
                    .orElse(null);
            return this.partyLeader != null && this.hunter.distanceToSqr(this.partyLeader) > START_FOLLOW_DIST_SQR;
        }

        @Override
        public boolean canContinueToUse() {
            return this.partyLeader != null
                    && this.partyLeader.isAlive()
                    && this.hunter.getTarget() == null
                    && this.hunter.distanceToSqr(this.partyLeader) > STOP_FOLLOW_DIST_SQR;
        }

        @Override
        public void start() {
            this.timeToRecalcPath = 0;
        }

        @Override
        public void stop() {
            this.partyLeader = null;
            this.hunter.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (this.partyLeader != null && --this.timeToRecalcPath <= 0) {
                this.timeToRecalcPath = this.adjustedTickDelay(10);
                this.hunter.getNavigation().moveTo(this.partyLeader, this.speedModifier);
            }
        }
    }
}

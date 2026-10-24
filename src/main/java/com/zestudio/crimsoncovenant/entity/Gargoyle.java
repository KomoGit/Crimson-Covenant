package com.zestudio.crimsoncovenant.entity;

import java.util.EnumSet;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.block.BearTrapBlock;
import com.zestudio.crimsoncovenant.hunter.HunterRanks;
import com.zestudio.crimsoncovenant.item.Silvering;
import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.registry.ModItems;
import com.zestudio.crimsoncovenant.skill.Enthrall;
import com.zestudio.crimsoncovenant.vampirism.Vampirism;

import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/// A stone guardian, carved and woken by a player (see item.GargoyleStatueItem). It comes in two kinds, one for each
/// side of the war:
///
/// - The Cathedral Gargoyle (silver and stone) is the hunters'. It wakes only for someone free of the vampire curse,
///   hunts what hunters hunt (the undead, werewolves, tainted players), and its silver claws wound like a silvered
///   blade.
/// - The Blood Gargoyle (blood and stone) is the vampires'. It wakes only for a vampire, hunts what vampires hunt
///   (the living, hunters, Cathedral Gargoyles) and drinks from what it claws. Blood magic makes it undead, so it's
///   weak to silver and Holy Water like any vampire.
///
/// Either way, sunlight turns a gargoyle to stone: out under a bright sky it sits frozen on its perch, all but
/// unbreakable and slowly mending, and wakes when the sun goes down (or when it's carried into the shade).
/// Its maker can right-click it to have it follow them or guard where it stands, and sneak-right-click to take it
/// back as a statue. A gargoyle never turns on its maker, unless its maker turns: a hunter who catches the curse,
/// or a vampire who loses it, is no longer its master.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public class Gargoyle extends PathfinderMob {
    private static final EntityDataAccessor<Boolean> STONE = SynchedEntityData.defineId(Gargoyle.class, EntityDataSerializers.BOOLEAN);

    /// How far a guarding gargoyle strays from its post
    public static final int GUARD_RADIUS = 12;
    private static final double FOLLOW_START = 6.0;
    private static final double FOLLOW_STOP = 3.0;
    private static final double TELEPORT_DISTANCE = 24.0;
    private static final double DEFEND_RANGE = 16.0;
    private static final int STONE_CHECK_INTERVAL = 20;
    private static final int STONE_HEAL_INTERVAL = 40;
    /// What's left of a blow struck at a gargoyle while it's stone
    private static final float STONE_DAMAGE_FACTOR = 0.2F;
    /// How much of a Blood Gargoyle's bite comes back to it as health
    private static final float BLOOD_DRINK_FRACTION = 0.5F;
    private static final int BLOOD_LOSS_DURATION = 100;

    public enum Side {
        /// The hunters' Cathedral Gargoyle
        HUNTER,
        /// The vampires' Blood Gargoyle
        VAMPIRE
    }

    private final Side side;
    private @Nullable UUID owner;
    private boolean following = false;

    public Gargoyle(EntityType<? extends Gargoyle> type, Level level, Side side) {
        super(type, level);
        this.side = side;
        this.xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 50.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(STONE, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // Stone holds every other goal still, see StoneGoal
        this.goalSelector.addGoal(0, new StoneGoal());
        this.goalSelector.addGoal(2, new LeapAtTargetGoal(this, 0.5F));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.2, true));
        this.goalSelector.addGoal(4, new FollowMakerGoal());
        this.goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this, 1.0));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new DefendMakerGoal());
        this.targetSelector.addGoal(2, new HurtByTargetGoal(this, Gargoyle.class));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, LivingEntity.class, true, (target, level) -> this.isEnemy(target)));
    }

    public Side getSide() {
        return this.side;
    }

    // ---- Who's who ----

    /// What this gargoyle hunts on sight
    public boolean isEnemy(LivingEntity target) {
        if (!target.isAlive() || this.isAlly(target)) {
            return false;
        }
        if (target instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return false;
        }
        return switch (this.side) {
            case HUNTER -> target.is(EntityTypeTags.UNDEAD) || target instanceof Werewolf
                    || (target instanceof Player player && Vampirism.isTainted(player));
            case VAMPIRE -> MasterVampire.isEnemyOfVampires(target, this.getTarget());
        };
    }

    /// Who this gargoyle never attacks, even when they hurt it: its maker, its own side, and the maker's thralls
    public boolean isAlly(Entity entity) {
        if (entity instanceof Player player) {
            return this.isMaker(player) || this.belongsToSide(player);
        }
        if (entity instanceof Gargoyle other) {
            return other.side == this.side;
        }
        return switch (this.side) {
            case HUNTER -> entity instanceof Hunter;
            case VAMPIRE -> entity instanceof Vampire || entity instanceof VampireBat || Vampire.isFamiliar(entity)
                    || (this.owner != null && Enthrall.thrall(entity).map(thrall -> thrall.master().equals(this.owner)).orElse(false));
        };
    }

    /// Whether a player is on this gargoyle's side of the war: a hunter free of the curse, or a vampire
    public boolean belongsToSide(Player player) {
        return switch (this.side) {
            case HUNTER -> HunterRanks.isHunter(player) && !Vampirism.isTainted(player);
            case VAMPIRE -> Vampirism.isVampire(player);
        };
    }

    /// Whether a player could wake (and keep) a gargoyle of this side: hunters' stone won't answer the cursed, and
    /// blood magic only answers a vampire
    public static boolean canCommand(Side side, Player player) {
        return switch (side) {
            case HUNTER -> !Vampirism.isTainted(player);
            case VAMPIRE -> Vampirism.isVampire(player);
        };
    }

    // Covers every targeting check (nearest enemy, hit back, defend the maker)
    @Override
    public boolean canAttack(LivingEntity target) {
        return !this.isAlly(target) && super.canAttack(target);
    }

    // ---- The maker ----

    public void setOwner(Player player) {
        this.owner = player.getUUID();
    }

    public @Nullable UUID getOwnerUUID() {
        return this.owner;
    }

    /// The player who woke this gargoyle, as long as they're still fit to command it
    public boolean isMaker(Player player) {
        return player.getUUID().equals(this.owner) && canCommand(this.side, player);
    }

    private @Nullable Player getMaker() {
        if (this.owner == null) {
            return null;
        }
        Player player = this.level().getPlayerByUUID(this.owner);
        return player != null && this.isMaker(player) ? player : null;
    }

    public boolean isFollowing() {
        return this.following;
    }

    /// Stands guard over a post: the gargoyle stays within GUARD_RADIUS of it, and only fights what comes near
    public void guard(BlockPos post) {
        this.following = false;
        this.setHomeTo(post, GUARD_RADIUS);
    }

    public void follow() {
        this.following = true;
        this.clearHome();
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!player.getItemInHand(hand).isEmpty() || !player.getUUID().equals(this.owner)) {
            return super.mobInteract(player, hand);
        }
        if (!(this.level() instanceof ServerLevel)) {
            return InteractionResult.SUCCESS;
        }
        if (!this.isMaker(player)) {
            player.sendOverlayMessage(Component.translatable("message.crimsoncovenant.gargoyle.refuses").withStyle(ChatFormatting.GRAY));
            return InteractionResult.FAIL;
        }
        if (player.isSecondaryUseActive()) {
            // Back into a statue, to be set down somewhere else
            ItemStack statue = new ItemStack(this.side == Side.HUNTER ? ModItems.CATHEDRAL_GARGOYLE_STATUE.get() : ModItems.BLOOD_GARGOYLE_STATUE.get());
            if (this.hasCustomName()) {
                statue.set(DataComponents.CUSTOM_NAME, this.getCustomName());
            }
            player.getInventory().placeItemBackInInventory(statue, Prediction.SERVER_ONLY);
            this.crumbleEffects();
            this.discard();
            return InteractionResult.SUCCESS;
        }
        if (this.following) {
            this.guard(this.blockPosition());
            player.sendOverlayMessage(Component.translatable("message.crimsoncovenant.gargoyle.guarding").withStyle(ChatFormatting.GRAY));
        } else {
            this.follow();
            player.sendOverlayMessage(Component.translatable("message.crimsoncovenant.gargoyle.following").withStyle(ChatFormatting.GRAY));
        }
        return InteractionResult.SUCCESS;
    }

    // ---- Stone by day ----

    public boolean isStone() {
        return this.entityData.get(STONE);
    }

    public void setStone(boolean stone) {
        if (stone == this.isStone()) {
            return;
        }
        this.entityData.set(STONE, stone);
        if (stone) {
            this.getNavigation().stop();
            this.setDeltaMovement(this.getDeltaMovement().multiply(0.0, 1.0, 0.0));
            this.playSound(SoundEvents.STONE_PLACE, 1.0F, 0.6F);
        } else {
            this.playSound(SoundEvents.STONE_BREAK, 1.0F, 0.7F);
        }
        this.crumbleEffects();
    }

    /// Out in the open while the sun is up
    public boolean isInSunlight() {
        Level level = this.level();
        return level.isBrightOutside() && level.canSeeSky(BlockPos.containing(this.getX(), this.getEyeY(), this.getZ()));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        if (this.tickCount % STONE_CHECK_INTERVAL == 0) {
            this.setStone(this.isInSunlight());
        }
        if (this.isStone()) {
            if (this.tickCount % STONE_HEAL_INTERVAL == 0) {
                this.heal(1.0F);
            }
        } else if (this.side == Side.VAMPIRE && this.tickCount % 40 == 0) {
            level.sendParticles(new DustParticleOptions(0x9B0A14, 1.0F), this.getX(), this.getY(0.6), this.getZ(), 2, 0.25, 0.4, 0.25, 0.0);
        }
        // Wings spread: it glides down rather than falling
        Vec3 motion = this.getDeltaMovement();
        if (!this.onGround() && !this.isStone() && motion.y < -0.2) {
            this.setDeltaMovement(motion.x, -0.2, motion.z);
        }
    }

    private void crumbleEffects() {
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                    this.getX(), this.getY(0.5), this.getZ(), 30, 0.3, 0.6, 0.3, 0.1);
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (this.isStone() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            damage *= STONE_DAMAGE_FACTOR;
        }
        return super.hurtServer(level, source, damage);
    }

    @Override
    public void knockback(double power, double xd, double zd, DamageSource source, float damage, boolean comesFromEffect) {
        if (!this.isStone()) {
            super.knockback(power, xd, zd, source, damage, comesFromEffect);
        }
    }

    @Override
    public boolean isPushable() {
        return !this.isStone() && super.isPushable();
    }

    // Wings break the fall, and stone needs no air
    @Override
    public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
        return false;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    // ---- Claws ----

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        float healthBefore = target instanceof LivingEntity living ? living.getHealth() : 0.0F;
        boolean hit = super.doHurtTarget(level, target);
        if (hit && this.side == Side.VAMPIRE && target instanceof LivingEntity living && Vampirism.hasLivingBlood(living)) {
            // It drinks what it spills
            this.heal(Math.max(0.0F, healthBefore - living.getHealth()) * BLOOD_DRINK_FRACTION);
            living.addEffect(new MobEffectInstance(ModEffects.BLOOD_LOSS, BLOOD_LOSS_DURATION), this);
        }
        return hit;
    }

    /// A Cathedral Gargoyle's silver claws hit the undead (and werewolves, and vampire players) like a silvered blade
    @SubscribeEvent
    static void silverClaws(LivingIncomingDamageEvent event) {
        if (event.getSource().getDirectEntity() instanceof Gargoyle gargoyle && gargoyle.side == Side.HUNTER
                && event.getSource().getEntity() == gargoyle && BearTrapBlock.isWeakToSilver(event.getEntity())) {
            event.setAmount(event.getAmount() + Silvering.BONUS_DAMAGE);
            event.getEntity().addEffect(new MobEffectInstance(ModEffects.SILVER_WOUND, Silvering.WOUND_DURATION), gargoyle);
        }
    }

    // ---- Saving ----

    // Made by hand, so never despawns
    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.storeNullable("Owner", UUIDUtil.CODEC, this.owner);
        output.putBoolean("Following", this.following);
        output.putBoolean("Stone", this.isStone());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.owner = input.read("Owner", UUIDUtil.CODEC).orElse(null);
        this.following = input.getBooleanOr("Following", false);
        this.entityData.set(STONE, input.getBooleanOr("Stone", false));
    }

    // ---- Sounds: grinding stone, and a low, rasping growl ----

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return this.isStone() ? null : SoundEvents.PHANTOM_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.STONE_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.STONE_BREAK;
    }

    @Override
    public float getVoicePitch() {
        return 0.5F + this.random.nextFloat() * 0.1F;
    }

    // ---- Goals ----

    /// Holds a stone gargoyle still: while it runs, no other goal can move it, turn its head, or swing its claws
    private class StoneGoal extends Goal {
        StoneGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return Gargoyle.this.isStone();
        }

        @Override
        public void start() {
            Gargoyle.this.getNavigation().stop();
        }
    }

    /// Keeps a following gargoyle at its maker's side, and brings it along when they get too far ahead
    private class FollowMakerGoal extends Goal {
        private @Nullable Player maker;

        FollowMakerGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!Gargoyle.this.following || Gargoyle.this.getTarget() != null) {
                return false;
            }
            this.maker = Gargoyle.this.getMaker();
            return this.maker != null && !this.maker.isSpectator() && Gargoyle.this.distanceTo(this.maker) > FOLLOW_START;
        }

        @Override
        public boolean canContinueToUse() {
            return this.maker != null && Gargoyle.this.following && Gargoyle.this.getTarget() == null
                    && Gargoyle.this.distanceTo(this.maker) > FOLLOW_STOP;
        }

        @Override
        public void stop() {
            this.maker = null;
            Gargoyle.this.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (this.maker == null) {
                return;
            }
            Gargoyle.this.getLookControl().setLookAt(this.maker, 10.0F, Gargoyle.this.getMaxHeadXRot());
            if (Gargoyle.this.distanceTo(this.maker) > TELEPORT_DISTANCE && this.maker.onGround()) {
                Gargoyle.this.crumbleEffects();
                Gargoyle.this.snapTo(this.maker.getX(), this.maker.getY(), this.maker.getZ(), Gargoyle.this.getYRot(), Gargoyle.this.getXRot());
                Gargoyle.this.getNavigation().stop();
                Gargoyle.this.crumbleEffects();
            } else if (Gargoyle.this.tickCount % 10 == 0) {
                Gargoyle.this.getNavigation().moveTo(this.maker, 1.2);
            }
        }
    }

    /// Goes for whatever last hurt its maker, or whatever its maker last hurt, while they're close by
    private class DefendMakerGoal extends TargetGoal {
        private int lastSeenHurtBy;
        private int lastSeenHurt;

        DefendMakerGoal() {
            super(Gargoyle.this, false);
            this.setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            Player maker = Gargoyle.this.getMaker();
            if (maker == null || Gargoyle.this.distanceTo(maker) > DEFEND_RANGE) {
                return false;
            }
            LivingEntity attacker = maker.getLastHurtByMob();
            if (attacker != null && maker.getLastHurtByMobTimestamp() != this.lastSeenHurtBy
                    && this.canAttack(attacker, TargetingConditions.DEFAULT)) {
                this.lastSeenHurtBy = maker.getLastHurtByMobTimestamp();
                this.targetMob = attacker;
                return true;
            }
            LivingEntity victim = maker.getLastHurtMob();
            if (victim != null && maker.getLastHurtMobTimestamp() != this.lastSeenHurt
                    && this.canAttack(victim, TargetingConditions.DEFAULT)) {
                this.lastSeenHurt = maker.getLastHurtMobTimestamp();
                this.targetMob = victim;
                return true;
            }
            return false;
        }

        @Override
        public void start() {
            Gargoyle.this.setTarget(this.targetMob);
            super.start();
        }
    }
}

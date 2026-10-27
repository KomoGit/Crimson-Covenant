package com.zestudio.crimsoncovenant.entity;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.equine.ZombieHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/// The Headless Horseman's own horse: a zombie horse in golden barding, bound to his Crooked Tree. Kill it and it
/// won't stay dead while he (or his spirit) is still abroad: it lies buried under the tree for a while, then claws its
/// way up out of the ground beside it, as the Warden does, and gallops back to him. Nothing can harm it while it's
/// still coming up. No one but the Horseman can ride it.
public class HorsemansSteed extends ZombieHorse {
    /// How long it lies in the ground before it starts to dig its way out
    public static final int BURIED_TICKS = 160;
    /// How long it takes to climb out
    public static final int EMERGE_TICKS = 60;
    /// How far under the ground it lies
    private static final double DEPTH = 1.8;
    /// How far off it'll go looking for its rider
    private static final double RIDER_RANGE = 64.0;
    /// How far from the tree the Horseman (or his spirit) still counts as abroad
    private static final double HAUNT_RANGE = 160.0;

    private @Nullable BlockPos tree;
    private int buried;
    private int emerging;

    public HorsemansSteed(EntityType<? extends HorsemansSteed> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    /// A horse for the Horseman of the tree at `tree` (if he has one), in golden barding
    public static HorsemansSteed bred(Level level, @Nullable BlockPos tree) {
        HorsemansSteed steed = ModEntities.HORSEMANS_STEED.get().create(level, EntitySpawnReason.JOCKEY);
        steed.tree = tree;
        steed.setItemSlot(EquipmentSlot.BODY, new ItemStack(Items.GOLDEN_HORSE_ARMOR));
        steed.setDropChance(EquipmentSlot.BODY, 0.0F);
        return steed;
    }

    /// The horse buried under the tree at `tree`, to dig its way out beside it once BURIED_TICKS have passed
    public static HorsemansSteed buryAt(ServerLevel level, BlockPos tree) {
        HorsemansSteed steed = bred(level, tree);
        Vec3 spot = emergingSpot(level, tree);
        steed.snapTo(spot.x, spot.y - DEPTH, spot.z, level.getRandom().nextFloat() * 360.0F, 0.0F);
        steed.finalizeSpawn(level, level.getCurrentDifficultyAt(tree), EntitySpawnReason.EVENT, null);
        steed.buried = BURIED_TICKS;
        steed.emerging = EMERGE_TICKS;
        steed.setNoGravity(true);
        level.addFreshEntity(steed);
        return steed;
    }

    /// Open ground just beside the tree's foot, about as high as it, where the horse comes up
    static Vec3 emergingSpot(ServerLevel level, BlockPos tree) {
        for (int ring = 3; ring <= 6; ring++) {
            for (int i = 0; i < 8; i++) {
                float angle = i * Mth.TWO_PI / 8.0F;
                int x = Mth.floor(tree.getX() + 0.5 + Mth.cos(angle) * ring);
                int z = Mth.floor(tree.getZ() + 0.5 + Mth.sin(angle) * ring);
                for (int dy = 0; dy <= 4; dy++) {
                    // Level with the tree first, then a little above or below it
                    BlockPos at = new BlockPos(x, tree.getY() + (dy % 2 == 0 ? dy / 2 : -(dy + 1) / 2), z);
                    if (isOpenGround(level, at)) {
                        return Vec3.atBottomCenterOf(at);
                    }
                }
            }
        }
        return Vec3.atBottomCenterOf(tree.offset(3, 0, 0));
    }

    // Firm ground under it, and room for a horse above
    private static boolean isOpenGround(ServerLevel level, BlockPos at) {
        return level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)
                && level.getBlockState(at).getCollisionShape(level, at).isEmpty() && level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty();
    }

    public @Nullable BlockPos getTree() {
        return this.tree;
    }

    /// Whether it's still in the ground, lying there or digging its way out
    public boolean isRising() {
        return this.buried > 0 || this.emerging > 0;
    }

    // Nothing stirs it while it's in the ground
    @Override
    public boolean isImmobile() {
        return super.isImmobile() || this.isRising();
    }

    @Override
    protected void addBehaviourGoals() {
        this.goalSelector.addGoal(1, new ReturnToRiderGoal());
    }

    @Override
    public void tick() {
        boolean rising = this.isRising();
        if (rising) {
            this.noPhysics = true;
        }
        super.tick();
        if (!rising) {
            return;
        }
        this.noPhysics = false;
        if (this.level() instanceof ServerLevel level) {
            this.rise(level);
        }
    }

    // Lies still a while, the ground rumbling more as it nears the surface; then climbs up out of it
    private void rise(ServerLevel level) {
        BlockPos ground = BlockPos.containing(this.getX(), this.getY() + DEPTH - 0.5, this.getZ());
        BlockState soil = level.getBlockState(ground);
        if (this.buried > 0) {
            this.buried--;
            this.setDeltaMovement(Vec3.ZERO);
            if (this.buried < 60 && this.buried % 10 == 0) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, soil), this.getX(), ground.getY() + 1.0, this.getZ(), 8, 0.6, 0.0, 0.6, 0.0);
                level.playSound(null, this.getX(), ground.getY(), this.getZ(), SoundEvents.WARDEN_DIG, SoundSource.HOSTILE, 1.0F, 0.8F);
            }
            if (this.buried == 0) {
                level.playSound(null, this.getX(), ground.getY(), this.getZ(), SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 2.0F, 1.2F);
            }
            return;
        }
        this.emerging--;
        this.setDeltaMovement(Vec3.ZERO);
        this.setPos(this.getX(), this.getY() + DEPTH / EMERGE_TICKS, this.getZ());
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, soil), this.getX(), ground.getY() + 1.0, this.getZ(), 6, 0.7, 0.1, 0.7, 0.0);
        if (this.emerging % 15 == 0) {
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ZOMBIE_HORSE_ANGRY, SoundSource.HOSTILE, 1.5F, 0.7F);
        }
        if (this.emerging == 0) {
            this.setNoGravity(false);
            this.setDeltaMovement(Vec3.ZERO);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY(0.5), this.getZ(), 15, 0.6, 0.6, 0.6, 0.02);
        }
    }

    // Slain while the Horseman, or his spirit, is still abroad, it'll be back: buried under his tree
    @Override
    public void die(DamageSource source) {
        // He's thrown to the ground at once, to fight on, on foot
        this.ejectPassengers();
        super.die(source);
        if (this.tree != null && this.level() instanceof ServerLevel level && this.isDeadOrDying() && isHorsemanAbroad(level, this.tree)) {
            buryAt(level, this.tree);
        }
    }

    /// Whether the Horseman of the tree at `tree`, or his spirit, is still about
    public static boolean isHorsemanAbroad(ServerLevel level, BlockPos tree) {
        return !level.getEntitiesOfClass(LivingEntity.class, new AABB(tree).inflate(HAUNT_RANGE),
                entity -> entity.isAlive() && ((entity instanceof HeadlessHorseman horseman && horseman.hasBossBar() && tree.equals(horseman.getTree()))
                        || (entity instanceof HalloweenSpirit spirit && tree.equals(spirit.getTree())))).isEmpty();
    }

    /// Crumbles away for good, with its master's spirit banished
    public void crumble() {
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY(0.5), this.getZ(), 30, 0.6, 0.6, 0.6, 0.03);
            level.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY(0.5), this.getZ(), 15, 0.6, 0.6, 0.6, 0.03);
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ZOMBIE_HORSE_DEATH, SoundSource.HOSTILE, 1.5F, 0.6F);
        }
        this.ejectPassengers();
        this.discard();
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        if (this.isRising() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return true;
        }
        return super.isInvulnerableTo(level, source);
    }

    // His alone
    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        return InteractionResult.PASS;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return !this.isRising() && super.isPushable();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.storeNullable("Tree", BlockPos.CODEC, this.tree);
        output.putInt("Buried", this.buried);
        output.putInt("Emerging", this.emerging);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.tree = input.read("Tree", BlockPos.CODEC).orElse(null);
        this.buried = input.getIntOr("Buried", 0);
        this.emerging = input.getIntOr("Emerging", 0);
        this.setNoGravity(this.isRising());
    }

    /// Riderless, it gallops back to the Horseman if he's on foot nearby, for him to mount again (see HeadlessHorseman)
    class ReturnToRiderGoal extends Goal {
        private static final double SPEED = 1.8;
        private @Nullable HeadlessHorseman rider;

        ReturnToRiderGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (HorsemansSteed.this.isVehicle() || HorsemansSteed.this.isRising()) {
                return false;
            }
            this.rider = HorsemansSteed.this.level().getEntitiesOfClass(HeadlessHorseman.class, HorsemansSteed.this.getBoundingBox().inflate(RIDER_RANGE),
                    horseman -> horseman.isAlive() && horseman.hasBossBar() && !horseman.isPassenger()).stream()
                    .min(java.util.Comparator.comparingDouble(HorsemansSteed.this::distanceToSqr)).orElse(null);
            return this.rider != null;
        }

        @Override
        public boolean canContinueToUse() {
            return this.rider != null && this.rider.isAlive() && !this.rider.isPassenger() && !HorsemansSteed.this.isVehicle();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (this.rider == null) {
                return;
            }
            if (HorsemansSteed.this.getNavigation().isDone() || HorsemansSteed.this.tickCount % 10 == 0) {
                HorsemansSteed.this.getNavigation().moveTo(this.rider, SPEED);
            }
            // No way round found: it makes straight for him
            if (HorsemansSteed.this.getNavigation().isDone()) {
                HorsemansSteed.this.getMoveControl().setWantedPosition(this.rider.getX(), this.rider.getY(), this.rider.getZ(), SPEED);
            }
        }

        @Override
        public void stop() {
            this.rider = null;
            HorsemansSteed.this.getNavigation().stop();
        }
    }
}

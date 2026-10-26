package com.zestudio.crimsoncovenant.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.block.RatBurrowBlock;
import com.zestudio.crimsoncovenant.block.RatTrapBlock;
import com.zestudio.crimsoncovenant.effect.CleanseEffect;
import com.zestudio.crimsoncovenant.registry.ModBlocks;
import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.registry.ModEntities;
import com.zestudio.crimsoncovenant.registry.ModItems;
import com.zestudio.crimsoncovenant.registry.ModPotions;
import com.zestudio.crimsoncovenant.vampirism.Vampirism;
import com.zestudio.crimsoncovenant.world.GarlicDoors;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.InteractGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.LookAtTradingPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.TradeWithPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/// The Plague Doctor: a beaked, black-robed travelling healer who comes when the Miasma settles over a village (see
/// MiasmaCloud) and stays a couple of days after it's gone.
///
/// - He cures, for emeralds: use him with an emerald in hand and he takes what's owed from your purse and cures the
///   Plague and the Miasma's sickness. A vampire's curse that hasn't yet taken (Sanguine Vampiris) he leaves to a
///   drunk Potion of Cleanse (see CleanseEffect); a full vampire is past his help.
/// - Otherwise he trades: Potions of Cleanse, rat traps, and ferns for his brewing. Vampires he won't deal with.
/// - He clears the Miasma himself, throwing Cleanse into the cloud, and at anyone sick with it or the Plague.
/// - He carries silver for the undead: any that comes after him gets a splash Potion of Silver and runs from it.
/// - He hunts the rats: a baited rat trap on the doorstep of every burrow he finds, and sprung ones set again.
/// - He readies the village for the vampires: a garlic braid over every bare door he sees (see world.GarlicDoors).
///
/// The Plague Doctor is also a villager's job, at the Hunter's Table: that one sells the hunters' silver gear as well,
/// cures the same way (see world.FactionVillagers), and hangs garlic over the doors he passes.
///
/// His mask keeps the Miasma and the Plague out.
public class PlagueDoctor extends AbstractVillager {
    /// Two days, and longer while the Miasma's about
    public static final int STAY_TICKS = 48000;
    private static final double CALL_RANGE = 128.0;
    public static final int CURE_PLAGUE = 3;
    public static final int CURE_MIASMA = 1;

    private int despawnDelay = STAY_TICKS;

    public PlagueDoctor(EntityType<? extends PlagueDoctor> type, Level level) {
        super(type, level);
        this.setItemSlot(EquipmentSlot.MAINHAND, cleanse(Items.SPLASH_POTION));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED, 0.5).add(Attributes.FOLLOW_RANGE, 32.0);
    }

    public static ItemStack cleanse(net.minecraft.world.item.Item container) {
        return PotionContents.createItemStack(container, ModPotions.CLEANSE);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new TradeWithPlayerGoal(this));
        this.goalSelector.addGoal(1, new AvoidEntityGoal<>(this, LivingEntity.class, 8.0F, 0.5, 0.6,
                entity -> entity instanceof Enemy && !(entity instanceof Rat)));
        this.goalSelector.addGoal(1, new PanicGoal(this, 0.6));
        this.goalSelector.addGoal(1, new LookAtTradingPlayerGoal(this));
        this.goalSelector.addGoal(1, new WardOffUndeadGoal(this));
        this.goalSelector.addGoal(2, new ThrowCleanseGoal(this));
        this.goalSelector.addGoal(3, new SetRatTrapsGoal(this));
        this.goalSelector.addGoal(4, new HangGarlicGoal(this));
        this.goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 0.4));
        this.goalSelector.addGoal(9, new InteractGoal(this, Player.class, 3.0F, 1.0F));
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Mob.class, 8.0F));
    }

    // ---- Coming to a village ----

    /// The Miasma has settled at `pos`: unless a Plague Doctor is already about, one arrives from a little way off
    public static @Nullable PlagueDoctor answerTheCall(ServerLevel level, BlockPos pos) {
        if (!level.getEntitiesOfClass(PlagueDoctor.class, new AABB(pos).inflate(CALL_RANGE)).isEmpty()) {
            return null;
        }
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
            int distance = 12 + level.getRandom().nextInt(8);
            BlockPos column = pos.offset((int) (Math.cos(angle) * distance), 0, (int) (Math.sin(angle) * distance));
            BlockPos spot = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
            if (!level.isLoaded(spot) || !level.getFluidState(spot.below()).isEmpty() || !level.getBlockState(spot).isAir()) {
                continue;
            }
            PlagueDoctor doctor = ModEntities.PLAGUE_DOCTOR.get().create(level, EntitySpawnReason.EVENT);
            if (doctor == null) {
                return null;
            }
            doctor.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
            doctor.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), EntitySpawnReason.EVENT, null);
            level.addFreshEntity(doctor);
            return doctor;
        }
        return null;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        if (this.tickCount % 100 == 0 && !level.getEntitiesOfClass(MiasmaCloud.class, this.getBoundingBox().inflate(64.0)).isEmpty()) {
            // Not leaving while there's work to do
            this.despawnDelay = STAY_TICKS;
        }
        if (this.despawnDelay > 0 && !this.isTrading() && --this.despawnDelay == 0) {
            this.discard();
        }
    }

    // ---- Cures ----

    /// What curing this player would cost, in emeralds: nothing if there's nothing he can cure
    public static int cureCost(Player player) {
        int cost = 0;
        if (player.hasEffect(ModEffects.PLAGUE)) {
            cost += CURE_PLAGUE;
        }
        if (player.hasEffect(ModEffects.MIASMA)) {
            cost += CURE_MIASMA;
        }
        return cost;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!this.isAlive() || this.isTrading() || held.is(ModItems.PLAGUE_DOCTOR_SPAWN_EGG.get())) {
            return super.mobInteract(player, hand);
        }
        // Hunter faction: nothing to do with vampires
        if (Vampirism.isVampire(player)) {
            if (!this.level().isClientSide()) {
                refuse(this, player, "message.crimsoncovenant.plague_doctor_refuses");
            }
            return InteractionResult.SUCCESS;
        }
        if (held.is(Items.EMERALD) && cureCost(player) > 0) {
            if (!this.level().isClientSide()) {
                treat(this, player);
            }
            return InteractionResult.SUCCESS;
        }
        if (hand == InteractionHand.MAIN_HAND) {
            player.awardStat(Stats.TALKED_TO_VILLAGER);
        }
        if (!this.level().isClientSide() && !this.getOffers().isEmpty()) {
            this.setTradingPlayer(player);
            this.openTradingScreen(player, this.getDisplayName(), 1);
        }
        return InteractionResult.SUCCESS;
    }

    public boolean treat(Player player) {
        return treat(this, player);
    }

    /// A doctor (this wandering one, or a Plague Doctor villager) cures everything the player has that he can, if they
    /// can pay for all of it. Returns whether he did.
    public static boolean treat(AbstractVillager doctor, Player player) {
        int cost = cureCost(player);
        if (cost == 0) {
            if (Vampirism.isVampire(player)) {
                refuse(doctor, player, "message.crimsoncovenant.plague_doctor.too_late");
            }
            return false;
        }
        boolean free = player.getAbilities().instabuild;
        if (!free && countEmeralds(player.getInventory()) < cost) {
            refuse(doctor, player, "message.crimsoncovenant.plague_doctor.price", cost);
            return false;
        }
        if (!free) {
            takeEmeralds(player.getInventory(), cost);
        }
        CleanseEffect.cleanse(player);
        player.sendOverlayMessage(Component.translatable("message.crimsoncovenant.plague_doctor.cured", cost).withStyle(ChatFormatting.GREEN));
        if (doctor.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY(1.0), player.getZ(), 12, 0.4, 0.4, 0.4, 0.0);
            level.playSound(null, doctor.getX(), doctor.getY(), doctor.getZ(), SoundEvents.BREWING_STAND_BREW, SoundSource.NEUTRAL, 1.0F, 1.2F);
            level.playSound(null, doctor.getX(), doctor.getY(), doctor.getZ(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 0.8F);
        }
        return true;
    }

    private static void refuse(AbstractVillager doctor, Player player, String messageKey, Object... args) {
        player.sendOverlayMessage(Component.translatable(messageKey, args).withStyle(ChatFormatting.GRAY));
        doctor.setUnhappyCounter(40);
        doctor.playSound(SoundEvents.VILLAGER_NO, 1.0F, 0.8F);
    }

    private static int countEmeralds(Inventory inventory) {
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(Items.EMERALD)) {
                count += inventory.getItem(slot).getCount();
            }
        }
        return count;
    }

    private static void takeEmeralds(Inventory inventory, int count) {
        for (int slot = 0; slot < inventory.getContainerSize() && count > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(Items.EMERALD)) {
                int taken = Math.min(count, stack.getCount());
                stack.shrink(taken);
                count -= taken;
            }
        }
    }

    // ---- Trades ----

    @Override
    protected void updateTrades(ServerLevel level) {
        MerchantOffers offers = this.getOffers();
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 1), cleanse(Items.POTION), 12, 2, 0.05F));
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 2), cleanse(Items.SPLASH_POTION), 12, 2, 0.05F));
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 4), cleanse(Items.LINGERING_POTION), 6, 5, 0.05F));
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(ModItems.RAT_TRAP.get(), 2), 12, 2, 0.05F));
        offers.add(new MerchantOffer(new ItemCost(Items.FERN, 6), new ItemStack(Items.EMERALD), 16, 2, 0.05F));
    }

    @Override
    protected void rewardTradeXp(MerchantOffer offer) {
        if (offer.shouldRewardExp()) {
            this.level().addFreshEntity(new ExperienceOrb(this.level(), this.getX(), this.getY() + 0.5, this.getZ(), 3 + this.random.nextInt(4)));
        }
    }

    @Override
    public boolean showProgressBar() {
        return false;
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return this.isTrading() ? SoundEvents.WANDERING_TRADER_TRADE : SoundEvents.WANDERING_TRADER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WANDERING_TRADER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WANDERING_TRADER_DEATH;
    }

    @Override
    protected SoundEvent getTradeUpdatedSound(boolean validTrade) {
        return validTrade ? SoundEvents.WANDERING_TRADER_YES : SoundEvents.WANDERING_TRADER_NO;
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return SoundEvents.WANDERING_TRADER_YES;
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 0.75F;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("DespawnDelay", this.despawnDelay);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.despawnDelay = input.getIntOr("DespawnDelay", STAY_TICKS);
    }

    // ---- Goals ----

    /// Cleanse thrown into the Miasma, or at someone sick with it or the Plague
    static class ThrowCleanseGoal extends Goal {
        private static final double THROW_RANGE = 7.0;
        private static final double SEEK_RANGE = 24.0;
        private static final int COOLDOWN = 60;
        private final PlagueDoctor doctor;
        private @Nullable Vec3 target;
        private int cooldown;

        ThrowCleanseGoal(PlagueDoctor doctor) {
            this.doctor = doctor;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (this.cooldown > 0) {
                this.cooldown--;
                return false;
            }
            this.target = findTarget(this.doctor);
            return this.target != null;
        }

        @Override
        public boolean canContinueToUse() {
            return this.target != null && !this.doctor.isTrading();
        }

        @Override
        public void tick() {
            if (this.target == null) {
                return;
            }
            this.doctor.getLookControl().setLookAt(this.target.x, this.target.y, this.target.z);
            if (this.doctor.position().distanceToSqr(this.target) > THROW_RANGE * THROW_RANGE) {
                if (this.doctor.getNavigation().isDone()) {
                    this.doctor.getNavigation().moveTo(this.target.x, this.target.y, this.target.z, 0.55);
                }
                return;
            }
            this.doctor.getNavigation().stop();
            throwCleanseAt(this.doctor, this.target);
            this.target = null;
            this.cooldown = reducedTickDelay(COOLDOWN);
        }

        private static @Nullable Vec3 findTarget(PlagueDoctor doctor) {
            Level level = doctor.level();
            List<MiasmaCloud> clouds = level.getEntitiesOfClass(MiasmaCloud.class, doctor.getBoundingBox().inflate(SEEK_RANGE));
            if (!clouds.isEmpty()) {
                MiasmaCloud nearest = clouds.stream().min((a, b) -> Double.compare(a.distanceToSqr(doctor), b.distanceToSqr(doctor))).get();
                return nearest.position().add(0.0, 0.5, 0.0);
            }
            return level.getEntitiesOfClass(LivingEntity.class, doctor.getBoundingBox().inflate(SEEK_RANGE / 2),
                            entity -> entity != doctor && entity.isAlive() && !(entity instanceof Enemy)
                                    && (entity.hasEffect(ModEffects.PLAGUE) || entity.hasEffect(ModEffects.MIASMA)))
                    .stream().min((a, b) -> Double.compare(a.distanceToSqr(doctor), b.distanceToSqr(doctor)))
                    .map(entity -> entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0)).orElse(null);
        }
    }

    public static void throwCleanseAt(PlagueDoctor doctor, Vec3 target) {
        throwPotionAt(doctor, cleanse(Items.SPLASH_POTION), target);
    }

    private static void throwPotionAt(PlagueDoctor doctor, ItemStack potion, Vec3 target) {
        if (!(doctor.level() instanceof ServerLevel level)) {
            return;
        }
        double dx = target.x - doctor.getX();
        double dy = target.y - doctor.getEyeY() + 0.5;
        double dz = target.z - doctor.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        Projectile.spawnProjectileUsingShoot(ThrownSplashPotion::new, level, potion, doctor, dx, dy + horizontal * 0.2, dz, 0.75F, 4.0F);
        doctor.swingForAttack(InteractionHand.MAIN_HAND);
        doctor.playSound(SoundEvents.WITCH_THROW, 1.0F, 0.9F);
    }

    /// Any undead coming for him (after him, or the last thing to hurt him) gets a splash Potion of Silver, and runs
    static class WardOffUndeadGoal extends Goal {
        private static final double RANGE = 10.0;
        private static final int COOLDOWN = 60;
        private final PlagueDoctor doctor;
        private @Nullable LivingEntity undead;
        private int cooldown;

        WardOffUndeadGoal(PlagueDoctor doctor) {
            this.doctor = doctor;
            this.setFlags(EnumSet.of(Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (this.cooldown > 0) {
                this.cooldown--;
                return false;
            }
            this.undead = threat(this.doctor);
            return this.undead != null;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            if (this.undead == null) {
                return;
            }
            this.doctor.getLookControl().setLookAt(this.undead);
            throwPotionAt(this.doctor, PotionContents.createItemStack(Items.SPLASH_POTION, ModPotions.SILVER), this.undead.position().add(0.0, this.undead.getBbHeight() * 0.5, 0.0));
            this.cooldown = reducedTickDelay(COOLDOWN);
            this.undead = null;
        }

        /// The nearest undead after the doctor, not already fleeing silver
        static @Nullable LivingEntity threat(PlagueDoctor doctor) {
            LivingEntity attacker = doctor.getLastHurtByMob();
            return doctor.level().getEntitiesOfClass(LivingEntity.class, doctor.getBoundingBox().inflate(RANGE),
                            entity -> entity.isAlive() && Vampirism.isUndead(entity) && !entity.hasEffect(ModEffects.SILVER_DREAD)
                                    && (entity == attacker || (entity instanceof Mob mob && mob.getTarget() == doctor)) && doctor.hasLineOfSight(entity))
                    .stream().min((a, b) -> Double.compare(a.distanceToSqr(doctor), b.distanceToSqr(doctor))).orElse(null);
        }
    }

    /// A baited trap on the doorstep of every rat burrow nearby, and any sprung trap set and baited again
    static class SetRatTrapsGoal extends Goal {
        private static final int SEARCH = 12;
        private static final int COOLDOWN = 200;
        private final PlagueDoctor doctor;
        private @Nullable BlockPos spot;
        private int cooldown;
        private int timeout;

        SetRatTrapsGoal(PlagueDoctor doctor) {
            this.doctor = doctor;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (this.cooldown > 0) {
                this.cooldown--;
                return false;
            }
            this.cooldown = reducedTickDelay(COOLDOWN);
            this.spot = findTrapSpot(this.doctor.level(), this.doctor.blockPosition(), SEARCH).orElse(null);
            this.timeout = 400;
            return this.spot != null;
        }

        @Override
        public boolean canContinueToUse() {
            return this.spot != null && this.timeout > 0 && !this.doctor.isTrading();
        }

        @Override
        public void tick() {
            this.timeout--;
            if (this.spot == null) {
                return;
            }
            this.doctor.getLookControl().setLookAt(this.spot.getX() + 0.5, this.spot.getY(), this.spot.getZ() + 0.5);
            if (this.doctor.distanceToSqr(Vec3.atBottomCenterOf(this.spot)) > 4.0) {
                if (this.doctor.getNavigation().isDone()) {
                    this.doctor.getNavigation().moveTo(this.spot.getX() + 0.5, this.spot.getY(), this.spot.getZ() + 0.5, 0.5);
                }
                return;
            }
            setTrap(this.doctor, this.spot);
            this.spot = null;
        }

        @Override
        public void stop() {
            this.spot = null;
            this.doctor.getNavigation().stop();
        }
    }

    /// Off to any bare door he can see, to hang garlic over it
    static class HangGarlicGoal extends Goal {
        private static final int SEARCH = 12;
        private static final int COOLDOWN = 200;
        private final PlagueDoctor doctor;
        private @Nullable BlockPos spot;
        private int cooldown;
        private int timeout;

        HangGarlicGoal(PlagueDoctor doctor) {
            this.doctor = doctor;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (this.cooldown > 0) {
                this.cooldown--;
                return false;
            }
            this.cooldown = reducedTickDelay(COOLDOWN);
            this.spot = GarlicDoors.findBareDoor(this.doctor, SEARCH).orElse(null);
            this.timeout = 400;
            return this.spot != null;
        }

        @Override
        public boolean canContinueToUse() {
            return this.spot != null && this.timeout > 0 && !this.doctor.isTrading();
        }

        @Override
        public void tick() {
            this.timeout--;
            if (this.spot == null) {
                return;
            }
            this.doctor.getLookControl().setLookAt(Vec3.atCenterOf(this.spot));
            BlockPos below = this.spot.below(2);
            if (this.doctor.distanceToSqr(Vec3.atBottomCenterOf(below)) > 4.0) {
                if (this.doctor.getNavigation().isDone()) {
                    this.doctor.getNavigation().moveTo(below.getX() + 0.5, below.getY(), below.getZ() + 0.5, 0.5);
                }
                return;
            }
            GarlicDoors.hang(this.doctor, this.spot);
            this.spot = null;
        }

        @Override
        public void stop() {
            this.spot = null;
            this.doctor.getNavigation().stop();
        }
    }

    /// Where the doctor should set (or set again) a trap: a sprung trap nearby, or the doorstep of a burrow that has
    /// none by it, whichever is nearest
    public static Optional<BlockPos> findTrapSpot(Level level, BlockPos around, int range) {
        List<BlockPos> sprung = new ArrayList<>();
        List<BlockPos> doorsteps = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(around.offset(-range, -3, -range), around.offset(range, 3, range))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(ModBlocks.RAT_TRAP.get()) && state.getValue(RatTrapBlock.SPRUNG)) {
                sprung.add(pos.immutable());
            }
            if (RatBurrowBlock.isBurrow(state)) {
                doorsteps.add(RatBurrowBlock.doorstep(state, pos));
            }
        }
        Comparator<BlockPos> nearest = Comparator.comparingDouble(pos -> pos.distSqr(around));
        if (!sprung.isEmpty()) {
            return sprung.stream().min(nearest);
        }
        doorsteps.sort(nearest);
        for (BlockPos door : doorsteps) {
            boolean trapped = BlockPos.betweenClosedStream(door.offset(-2, -1, -2), door.offset(2, 1, 2))
                    .anyMatch(pos -> level.getBlockState(pos).is(ModBlocks.RAT_TRAP.get()));
            if (!trapped && level.getBlockState(door).isAir() && level.getBlockState(door.below()).isFaceSturdy(level, door.below(), Direction.UP)) {
                return Optional.of(door);
            }
        }
        return Optional.empty();
    }

    /// Sets a baited trap at `pos`, or sets and baits the sprung one already there
    public static void setTrap(PlagueDoctor doctor, BlockPos pos) {
        Level level = doctor.level();
        BlockState state = level.getBlockState(pos);
        BlockState trap = state.is(ModBlocks.RAT_TRAP.get())
                ? state.setValue(RatTrapBlock.SPRUNG, false).setValue(RatTrapBlock.BAITED, true)
                : state.isAir() ? ModBlocks.RAT_TRAP.get().defaultBlockState().setValue(RatTrapBlock.BAITED, true)
                        .setValue(RatTrapBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(doctor.getRandom())) : null;
        if (trap == null) {
            return;
        }
        level.setBlock(pos, trap, 3);
        doctor.swingForAttack(InteractionHand.MAIN_HAND);
        level.playSound(null, pos, SoundEvents.TRIPWIRE_ATTACH, SoundSource.NEUTRAL, 1.0F, 1.2F);
    }
}

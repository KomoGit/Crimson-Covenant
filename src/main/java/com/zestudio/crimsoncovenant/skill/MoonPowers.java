package com.zestudio.crimsoncovenant.skill;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.entity.BloodSpear;
import com.zestudio.crimsoncovenant.entity.SilverArrow;
import com.zestudio.crimsoncovenant.item.Silvering;
import com.zestudio.crimsoncovenant.item.WerewolfBelt;
import com.zestudio.crimsoncovenant.registry.ModAttachments;
import com.zestudio.crimsoncovenant.registry.ModDamageTypes;
import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.vampirism.BloodPool;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariants;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/// What a werewolf player (see item.WerewolfBelt) has in place of hunger: moonbars, shown where the hunger bar would be.
/// Every shape-change starts them at 0, every kill adds half the victim's health, and raw flesh is half a moon. They pay
/// for the four powers every werewolf has from the start, on the skill keys. They can't be swapped or upgraded.
///
/// A werewolf's blows also drive what they strike back a few blocks, the way a Blood Spear does: into a wall, and it's
/// slammed against it for extra damage.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public final class MoonPowers {
    public enum Power {
        /// Three wolves come to fight at the werewolf's side for a minute
        CALL_OF_THE_PACK("call_of_the_pack", 10),
        /// Frenzy on the werewolf and on every creature around them, their own pack aside (see effect.FrenzyEffect)
        MADDENING_HOWL("maddening_howl", 20),
        /// A great leap, and where it comes down everything nearby is hurt and flung away
        MOONFALL("moonfall", 30),
        /// For a minute blows land at a tenth of their force, unless they're silver. It's a long while coming back.
        ECLIPSE_HIDE("eclipse_hide", 50);

        private final String name;
        private final int cost;

        Power(String name, int cost) {
            this.name = name;
            this.cost = cost;
        }

        public String getName() {
            return this.name;
        }

        public int cost() {
            return this.cost;
        }

        public Component displayName() {
            return Component.translatable("moon_power.crimsoncovenant." + this.name);
        }
    }

    public static final int MAX_MOON = 100;
    public static final int PACK_SIZE = 3;
    public static final int PACK_TICKS = 1200;
    public static final double HOWL_RADIUS = 16.0;
    public static final int HOWL_TICKS = 300;
    public static final double MOONFALL_RADIUS = 5.0;
    public static final float MOONFALL_DAMAGE = 8.0F;
    public static final int ECLIPSE_HIDE_TICKS = 1200;
    /// Five minutes from the moment it's called on: a couple of times in a night
    public static final int ECLIPSE_HIDE_COOLDOWN = 6000;
    public static final float ECLIPSE_HIDE_DAMAGE_LEFT = 0.1F;
    /// A moon on the bar is worth 10, so this is half of one
    public static final int RAW_FLESH_MOON = 5;
    /// How far a blow drives what it strikes: about 3 blocks, unless a wall stops it first
    private static final double BLOW_DRIVE_SPEED = 0.8;
    private static final double MOONFALL_FLING_SPEED = 1.2;
    private static final int DRIVE_TICKS = 4;
    public static final float SLAM_DAMAGE = 6.0F;
    private static final double LEAP_FORWARD = 1.4;
    private static final double LEAP_UP = 0.85;
    /// A leap isn't over until it's been in the air this long: the server only hears the player has left the ground a
    /// moment after they do
    private static final int LEAP_MIN_TICKS = 5;

    private MoonPowers() {}

    public static int get(Player player) {
        return player.getData(ModAttachments.MOON);
    }

    public static void set(Player player, int moon) {
        int clamped = Mth.clamp(moon, 0, MAX_MOON);
        if (clamped != get(player)) {
            player.setData(ModAttachments.MOON, clamped);
        }
    }

    /// Uses the power, if the player is a werewolf with the moon to pay for it
    public static boolean tryCast(ServerPlayer player, Power power) {
        if (!WerewolfBelt.isTurned(player)) {
            return false;
        }
        if (get(player) < power.cost()) {
            player.sendOverlayMessage(Component.translatable("message.crimsoncovenant.moon.not_enough", power.displayName(), power.cost(), get(player)).withStyle(ChatFormatting.GRAY));
            return false;
        }
        boolean cast = switch (power) {
            case CALL_OF_THE_PACK -> callThePack(player);
            case MADDENING_HOWL -> maddeningHowl(player);
            case MOONFALL -> leap(player);
            case ECLIPSE_HIDE -> eclipseHide(player);
        };
        if (cast) {
            set(player, get(player) - power.cost());
            player.sendOverlayMessage(power.displayName().copy().withStyle(ChatFormatting.AQUA));
        }
        return cast;
    }

    private static void howl(ServerPlayer player, float pitch) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WOLF_SOUNDS.get(WolfSoundVariants.SoundSet.BIG).adultSounds().ambientSound().value(),
                SoundSource.PLAYERS, 2.0F, pitch);
    }

    // ---- Call of the Pack ----

    public static boolean isPackWolf(LivingEntity entity) {
        return entity instanceof Wolf wolf && wolf.hasData(ModAttachments.PACK_WOLF_UNTIL);
    }

    private static boolean callThePack(ServerPlayer player) {
        ServerLevel level = player.level();
        for (int i = 0; i < PACK_SIZE; i++) {
            Wolf wolf = EntityTypes.WOLF.create(level, EntitySpawnReason.MOB_SUMMONED);
            if (wolf == null) {
                continue;
            }
            float angle = i * Mth.TWO_PI / PACK_SIZE;
            BlockPos pos = BlockPos.containing(player.getX() + Mth.cos(angle) * 1.5, player.getY(), player.getZ() + Mth.sin(angle) * 1.5);
            // Beside the werewolf, or right on them if a wall's in the way
            boolean clear = level.noCollision(wolf.getType().getSpawnAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
            wolf.snapTo(clear ? pos.getX() + 0.5 : player.getX(), player.getY(), clear ? pos.getZ() + 0.5 : player.getZ(), player.getYRot(), 0.0F);
            wolf.finalizeSpawn(level, level.getCurrentDifficultyAt(wolf.blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
            wolf.tame(player);
            wolf.setData(ModAttachments.PACK_WOLF_UNTIL, level.getGameTime() + PACK_TICKS);
            level.addFreshEntity(wolf);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, wolf.getX(), wolf.getY(0.5), wolf.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
        }
        howl(player, 1.0F);
        return true;
    }

    // The pack goes back to the night when its time is up, or when its werewolf isn't one any more
    @SubscribeEvent
    static void packDisperses(EntityTickEvent.Post event) {
        if (!isPackWolf(event.getEntity() instanceof LivingEntity living ? living : null) || !(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        Wolf wolf = (Wolf) event.getEntity();
        if (level.getGameTime() >= wolf.getData(ModAttachments.PACK_WOLF_UNTIL) || !(wolf.getOwner() instanceof Player owner) || !WerewolfBelt.isTurned(owner)) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, wolf.getX(), wolf.getY(0.5), wolf.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
            wolf.discard();
        }
    }

    // ---- Maddening Howl ----

    private static boolean maddeningHowl(ServerPlayer player) {
        ServerLevel level = player.level();
        for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(HOWL_RADIUS),
                mob -> mob.isAlive() && mob.distanceTo(player) <= HOWL_RADIUS && !(mob instanceof OwnableEntity pet && pet.getOwner() == player))) {
            mob.addEffect(new MobEffectInstance(ModEffects.FRENZY, HOWL_TICKS), player);
        }
        player.addEffect(new MobEffectInstance(ModEffects.FRENZY, HOWL_TICKS));
        level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY(0.5), player.getZ(), 40, 1.5, 1.0, 1.5, 0.05);
        howl(player, 0.7F);
        return true;
    }

    // ---- Moonfall ----

    public static boolean isLeaping(Player player) {
        return player.getData(ModAttachments.MOONFALL_LEAPT_AT) != 0L;
    }

    private static boolean leap(ServerPlayer player) {
        if (isLeaping(player)) {
            player.sendOverlayMessage(Component.translatable("message.crimsoncovenant.skill.already_active").withStyle(ChatFormatting.GRAY));
            return false;
        }
        player.setData(ModAttachments.MOONFALL_LEAPT_AT, player.level().getGameTime());
        Vec3 ahead = Vec3.directionFromRotation(0.0F, player.getYRot());
        player.setDeltaMovement(ahead.x * LEAP_FORWARD, LEAP_UP, ahead.z * LEAP_FORWARD);
        player.syncVelocity = true;
        howl(player, 1.2F);
        return true;
    }

    @SubscribeEvent
    static void tick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            tickPlayer(player);
        }
    }

    /// Brings a leap down, and lets everyone nearby see whether Eclipse Hide is up
    public static void tickPlayer(ServerPlayer player) {
        if (isLeaping(player) && (player.onGround() || player.isInWater()) && player.level().getGameTime() - player.getData(ModAttachments.MOONFALL_LEAPT_AT) >= LEAP_MIN_TICKS) {
            land(player);
        }
        boolean shelled = player.hasEffect(ModEffects.ECLIPSE_HIDE);
        if (player.getData(ModAttachments.ECLIPSE_HIDE_VISIBLE) != shelled) {
            player.setData(ModAttachments.ECLIPSE_HIDE_VISIBLE, shelled);
        }
    }

    /// The leap comes down: everything near is hurt and flung away from where it landed
    public static void land(ServerPlayer player) {
        ServerLevel level = player.level();
        player.setData(ModAttachments.MOONFALL_LEAPT_AT, 0L);
        DamageSource source = level.damageSources().playerAttack(player);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(MOONFALL_RADIUS),
                victim -> victim != player && victim.isAlive() && victim.distanceTo(player) <= MOONFALL_RADIUS && !(victim instanceof ArmorStand)
                        && !(victim instanceof OwnableEntity pet && pet.getOwner() == player))) {
            victim.hurtServer(level, source, MOONFALL_DAMAGE);
            Vec3 away = victim.position().subtract(player.position()).horizontal();
            if (victim.isAlive() && away.lengthSqr() > 1.0E-4) {
                BloodSpear.drive(victim, away.normalize().scale(MOONFALL_FLING_SPEED), DRIVE_TICKS, player, SLAM_DAMAGE, false);
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY(0.1), player.getZ(), 3, 1.5, 0.2, 1.5, 0.0);
        level.sendParticles(ParticleTypes.POOF, player.getX(), player.getY(0.1), player.getZ(), 40, 2.0, 0.2, 2.0, 0.1);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.5F, 0.7F);
    }

    // A leap comes down on its feet
    @SubscribeEvent
    static void noFallFromALeap(LivingFallEvent event) {
        if (event.getEntity() instanceof Player player && isLeaping(player)) {
            event.setCanceled(true);
        }
    }

    // ---- Eclipse Hide ----

    /// How much of Eclipse Hide's cooldown is still to run, from 1 down to 0, for its icon
    public static float eclipseHideCooldown(Player player) {
        return Mth.clamp((player.getData(ModAttachments.ECLIPSE_HIDE_READY_AT) - player.level().getGameTime()) / (float) ECLIPSE_HIDE_COOLDOWN, 0.0F, 1.0F);
    }

    private static boolean eclipseHide(ServerPlayer player) {
        if (player.hasEffect(ModEffects.ECLIPSE_HIDE)) {
            player.sendOverlayMessage(Component.translatable("message.crimsoncovenant.skill.already_active").withStyle(ChatFormatting.GRAY));
            return false;
        }
        long ticksLeft = player.getData(ModAttachments.ECLIPSE_HIDE_READY_AT) - player.level().getGameTime();
        if (ticksLeft > 0L) {
            player.sendOverlayMessage(Component.translatable("message.crimsoncovenant.moon.recharging", Power.ECLIPSE_HIDE.displayName(), Mth.ceil(ticksLeft / 20.0F)).withStyle(ChatFormatting.GRAY));
            return false;
        }
        player.setData(ModAttachments.ECLIPSE_HIDE_READY_AT, player.level().getGameTime() + ECLIPSE_HIDE_COOLDOWN);
        player.addEffect(new MobEffectInstance(ModEffects.ECLIPSE_HIDE, ECLIPSE_HIDE_TICKS));
        player.level().sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY(0.5), player.getZ(), 30, 0.5, 0.8, 0.5, 0.02);
        howl(player, 0.8F);
        return true;
    }

    /// Whether a blow is silver: a silvered weapon, a silver arrow, silver traps or dust
    public static boolean isSilver(DamageSource source) {
        ItemStack weapon = source.getWeaponItem();
        return source.is(ModDamageTypes.SILVER_TRAP) || source.is(ModDamageTypes.SILVER_DUST) || source.getDirectEntity() instanceof SilverArrow
                || (weapon != null && Silvering.isSilvered(weapon));
    }

    @SubscribeEvent
    static void eclipseHideTurnsTheBlow(LivingIncomingDamageEvent event) {
        if (event.getEntity().hasEffect(ModEffects.ECLIPSE_HIDE) && !isSilver(event.getSource()) && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setAmount(event.getAmount() * ECLIPSE_HIDE_DAMAGE_LEFT);
        }
    }

    // ---- The hunt ----

    /// Raw meat of any kind, and rotten flesh
    public static boolean isRawFlesh(ItemStack stack) {
        return stack.is(Tags.Items.FOODS_RAW_MEAT) || stack.is(Items.ROTTEN_FLESH);
    }

    @SubscribeEvent
    static void rawFleshFeedsTheMoon(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof ServerPlayer player && WerewolfBelt.isTurned(player) && isRawFlesh(event.getItem())) {
            set(player, get(player) + RAW_FLESH_MOON);
        }
    }

    @SubscribeEvent
    static void killsFillTheMoon(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (event.getSource().getEntity() instanceof ServerPlayer player && player != victim && WerewolfBelt.isTurned(player)) {
            set(player, get(player) + BloodPool.bloodFrom(victim));
        }
    }

    /// A werewolf's blow drives its victim back, away from them; not while it's reeling from a slam (the slam itself is
    /// the werewolf's blow, and would only drive it back into the same wall)
    @SubscribeEvent
    static void blowsDriveBack(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || event.getSource().getDirectEntity() != player || player == victim
                || !WerewolfBelt.isTurned(player) || !victim.isAlive() || BloodSpear.isStaggered(victim)) {
            return;
        }
        Vec3 away = victim.position().subtract(player.position()).horizontal();
        if (away.lengthSqr() > 1.0E-4) {
            BloodSpear.drive(victim, away.normalize().scale(BLOW_DRIVE_SPEED), DRIVE_TICKS, player, SLAM_DAMAGE, false);
        }
    }
}

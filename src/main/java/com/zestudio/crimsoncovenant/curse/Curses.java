package com.zestudio.crimsoncovenant.curse;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.entity.SpiritHand;
import com.zestudio.crimsoncovenant.entity.SpiritWyrms;
import com.zestudio.crimsoncovenant.hunter.HunterRanks;
import com.zestudio.crimsoncovenant.registry.ModAttachments;
import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.skill.Enthrall;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/// Unleashing a weapon's curse, and what each curse does while it's loose. Press the curse key holding a cursed weapon
/// (see ModKeyMappings) and, if you're a hunter of high enough rank and the curse has had time to stir again, it's
/// unleashed for a short while (see Curse for how long, and how long it then needs). Only blows and arrows from the
/// cursed weapon carry it.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public final class Curses {
    /// A curse let loose: which, how deep, the game time it's spent, and (for Gluttony) the kills made meanwhile
    public record Unleashed(Curse curse, int level, long until, int kills) {
        Unleashed withKill() {
            return new Unleashed(this.curse, this.level, this.until, this.kills + 1);
        }
    }

    public enum Outcome { UNLEASHED, NO_CURSE, TOO_LOW_RANK, STIRRING, ALREADY_LOOSE }

    // ---- Tuning ----
    /// Exile's Mark: how long the banished stay banished
    public static int banishTicks(int level) {
        return 60 + 40 * level;
    }

    /// Shackles of the Damned: how long the chained stay chained
    public static int chainTicks(int level) {
        return 20 + 20 * level;
    }

    /// Gravecaller: how long the risen serve
    public static int serviceTicks(int level) {
        return 200 + 200 * level;
    }

    /// Ravenous Maw: health each kill feeds, and kills it demands before it's spent
    public static float gluttonyHeal(int level) {
        return 4.0F + 2.0F * level;
    }

    public static int gluttonyHunger(int level) {
        return level;
    }

    private static final double GUST_RANGE = 6.0;
    private static final double GUST_CONE = 0.45;

    private Curses() {}

    // ---- Unleashing ----

    /// The weapon in hand that bears a curse: the main hand's, or else the off hand's
    public static @Nullable ItemStack cursedWeapon(Player player) {
        for (ItemStack held : List.of(player.getMainHandItem(), player.getOffhandItem())) {
            if (WeaponCurse.of(held) != null) {
                return held;
            }
        }
        return null;
    }

    public static Optional<Unleashed> unleashed(Player player) {
        return player.getData(ModAttachments.UNLEASHED_CURSE);
    }

    /// The level `curse` is loose at, if it is and `weapon` (what the blow or arrow came from) bears it; otherwise 0
    public static int looseLevel(Player player, Curse curse, @Nullable ItemStack weapon) {
        Optional<Unleashed> loose = unleashed(player);
        if (loose.isEmpty() || loose.get().curse() != curse || weapon == null || WeaponCurse.levelOf(weapon, curse) == 0) {
            return 0;
        }
        return loose.get().level();
    }

    /// The game time `curse` can next be unleashed by this player
    public static long readyAt(Player player, Curse curse) {
        return player.getData(ModAttachments.CURSE_COOLDOWNS).getOrDefault(curse, 0L);
    }

    public static Outcome unleash(ServerPlayer player) {
        ItemStack weapon = cursedWeapon(player);
        WeaponCurse laid = weapon == null ? null : WeaponCurse.of(weapon);
        ServerLevel level = player.level();
        long now = level.getGameTime();
        Outcome outcome;
        if (laid == null) {
            outcome = Outcome.NO_CURSE;
        } else if (HunterRanks.get(player) < Curse.rankFor(laid.level())) {
            outcome = Outcome.TOO_LOW_RANK;
        } else if (unleashed(player).isPresent()) {
            outcome = Outcome.ALREADY_LOOSE;
        } else if (readyAt(player, laid.curse()) > now) {
            outcome = Outcome.STIRRING;
        } else {
            outcome = Outcome.UNLEASHED;
        }
        tell(player, outcome, laid, now);
        if (outcome != Outcome.UNLEASHED) {
            return outcome;
        }
        Curse curse = laid.curse();
        player.setData(ModAttachments.UNLEASHED_CURSE, Optional.of(new Unleashed(curse, laid.level(), now + curse.duration(laid.level()), 0)));
        Map<Curse, Long> cooldowns = new EnumMap<>(Curse.class);
        cooldowns.putAll(player.getData(ModAttachments.CURSE_COOLDOWNS));
        cooldowns.put(curse, now + curse.cooldown(laid.level()));
        player.setData(ModAttachments.CURSE_COOLDOWNS, cooldowns);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.2F, 0.7F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.8F, 1.4F);
        level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY(1.0), player.getZ(), 16, 0.4, 0.4, 0.4, 0.03);
        switch (curse) {
            case SPIRIT_HAND -> SpiritHand.summon(level, player, weapon, laid.level(), curse.duration(laid.level()));
            case GUST -> gust(level, player, laid.level());
            default -> {
            }
        }
        return outcome;
    }

    private static void tell(ServerPlayer player, Outcome outcome, @Nullable WeaponCurse laid, long now) {
        Component message = switch (outcome) {
            case NO_CURSE -> Component.translatable("message.crimsoncovenant.curse.none");
            case TOO_LOW_RANK -> Component.translatable("message.crimsoncovenant.curse.rank", laid.curse().titleAt(laid.level()),
                    HunterRanks.name(Curse.rankFor(laid.level())));
            case ALREADY_LOOSE -> Component.translatable("message.crimsoncovenant.curse.loose");
            case STIRRING -> Component.translatable("message.crimsoncovenant.curse.stirring", laid.curse().title(),
                    (readyAt(player, laid.curse()) - now + 19) / 20);
            case UNLEASHED -> Component.translatable("message.crimsoncovenant.curse.unleashed", laid.curse().titleAt(laid.level()));
        };
        player.sendOverlayMessage(message.copy().withStyle(outcome == Outcome.UNLEASHED ? ChatFormatting.AQUA : ChatFormatting.GRAY));
    }

    /// The curse is spent. Ravenous Maw takes its due from whoever didn't feed it enough.
    public static void spend(Player player) {
        Optional<Unleashed> loose = unleashed(player);
        player.setData(ModAttachments.UNLEASHED_CURSE, Optional.empty());
        if (loose.isEmpty()) {
            return;
        }
        Unleashed spent = loose.get();
        if (spent.curse() == Curse.GLUTTONY && spent.kills() < gluttonyHunger(spent.level())) {
            player.addEffect(new MobEffectInstance(MobEffects.WITHER, 200, spent.level() - 1));
            player.sendOverlayMessage(Component.translatable("message.crimsoncovenant.curse.gluttony_unfed").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @SubscribeEvent
    static void tickUnleashed(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Optional<Unleashed> loose = unleashed(player);
        if (loose.isEmpty()) {
            return;
        }
        if (level.getGameTime() >= loose.get().until() || !player.isAlive()) {
            spend(player);
            return;
        }
        // A cold haze hangs about the cursed weapon while it's loose
        if (player.tickCount % 4 == 0) {
            level.sendParticles(new DustParticleOptions(Curse.COLOUR, 0.7F), player.getX(), player.getY(0.6), player.getZ(), 2, 0.35, 0.3, 0.35, 0.0);
        }
    }

    /// A cursed weapon names its curse, right under its own name
    @SubscribeEvent
    static void describe(ItemTooltipEvent event) {
        WeaponCurse laid = WeaponCurse.of(event.getItemStack());
        if (laid != null) {
            int line = Math.min(1, event.getToolTip().size());
            event.getToolTip().add(line, laid.curse().titleAt(laid.level()));
            event.getToolTip().add(line + 1, Component.translatable("item.crimsoncovenant.cursed_weapon.hint", laid.curse().plainName(),
                    HunterRanks.name(Curse.rankFor(laid.level()))).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    // ---- What they do ----

    private static @Nullable Player cursedAttacker(DamageSource source) {
        return source.getEntity() instanceof Player player && unleashed(player).isPresent() ? player : null;
    }

    private static boolean isMeleeBlow(DamageSource source, Player player) {
        return source.getDirectEntity() == player && source.is(DamageTypes.PLAYER_ATTACK);
    }

    /// Blows and arrows from a cursed weapon, while the curse is loose
    @SubscribeEvent
    static void cursedHits(LivingDamageEvent.Post event) {
        DamageSource source = event.getSource();
        Player player = cursedAttacker(source);
        LivingEntity target = event.getEntity();
        if (player == null || !(player.level() instanceof ServerLevel level) || target == player) {
            return;
        }
        ItemStack weapon = source.getWeaponItem();
        boolean melee = isMeleeBlow(source, player);
        boolean arrow = source.getDirectEntity() instanceof AbstractArrow;
        if (!melee && !arrow) {
            return;
        }
        int chain = looseLevel(player, Curse.ENCHAIN, weapon);
        if (chain > 0) {
            target.addEffect(new MobEffectInstance(ModEffects.ENCHAINED, chainTicks(chain)), player);
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.0F, 0.7F);
        }
        int banish = arrow ? looseLevel(player, Curse.BANISH, weapon) : 0;
        if (banish > 0) {
            target.addEffect(new MobEffectInstance(ModEffects.BANISHED, banishTicks(banish)), player);
            if (target instanceof Mob mob) {
                mob.setTarget(null);
            }
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.0F, 1.3F);
            level.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY(0.5), target.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
        }
        int gust = melee ? looseLevel(player, Curse.GUST, weapon) : 0;
        if (gust > 0) {
            gust(level, player, gust);
        }
    }

    /// Banshee's Gale: a cone of wind from the wielder, striking and hurling back whatever's in front of them
    public static int gust(ServerLevel level, Player player, int curseLevel) {
        Vec3 eyes = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        int struck = 0;
        DamageSource wind = level.damageSources().indirectMagic(player, player);
        for (LivingEntity creature : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(GUST_RANGE), entity -> entity != player && entity.isAlive()
                && !Enthrall.isThrallOf(entity, player))) {
            Vec3 toward = creature.position().add(0.0, creature.getBbHeight() * 0.5, 0.0).subtract(eyes);
            if (toward.length() > GUST_RANGE || toward.normalize().dot(look) < GUST_CONE) {
                continue;
            }
            float damage = 2.0F + curseLevel;
            creature.hurtServer(level, wind, damage);
            creature.knockback(1.0 + 0.5 * curseLevel, -look.x, -look.z, wind, damage);
            struck++;
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
        for (int step = 1; step <= 5; step++) {
            Vec3 at = eyes.add(look.scale(step * 1.1));
            level.sendParticles(ParticleTypes.GUST, at.x, at.y - 0.3, at.z, 1, 0.2 * step, 0.2, 0.2 * step, 0.0);
        }
        return struck;
    }

    /// Kills by a cursed weapon, while the curse is loose
    @SubscribeEvent
    static void cursedKills(LivingDeathEvent event) {
        DamageSource source = event.getSource();
        Player player = cursedAttacker(source);
        LivingEntity dead = event.getEntity();
        if (player == null || !(player.level() instanceof ServerLevel level) || dead == player) {
            return;
        }
        ItemStack weapon = source.getWeaponItem();
        boolean melee = isMeleeBlow(source, player);
        boolean arrow = source.getDirectEntity() instanceof AbstractArrow;
        int enthrall = melee || arrow ? looseLevel(player, Curse.ENTHRALL, weapon) : 0;
        if (enthrall > 0 && dead instanceof Mob mob) {
            raise(level, mob, player, enthrall);
        }
        int gluttony = melee ? looseLevel(player, Curse.GLUTTONY, weapon) : 0;
        if (gluttony > 0) {
            player.heal(gluttonyHeal(gluttony));
            unleashed(player).ifPresent(loose -> player.setData(ModAttachments.UNLEASHED_CURSE, Optional.of(loose.withKill())));
            level.sendParticles(new DustParticleOptions(Curse.COLOUR, 1.0F), player.getX(), player.getY(0.7), player.getZ(), 10, 0.3, 0.3, 0.3, 0.0);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EAT.value(), SoundSource.PLAYERS, 0.8F, 0.6F);
        }
    }

    /// Gravecaller: what fell gets up again, the same creature, to serve its killer a while (then crumbles)
    public static @Nullable Mob raise(ServerLevel level, Mob dead, Player raiser, int curseLevel) {
        if (dead.is(Tags.EntityTypes.BOSSES) || !(dead.getType().create(level, EntitySpawnReason.MOB_SUMMONED) instanceof Mob risen)) {
            return null;
        }
        risen.snapTo(dead.getX(), dead.getY(), dead.getZ(), dead.getYRot(), 0.0F);
        risen.finalizeSpawn(level, level.getCurrentDifficultyAt(dead.blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
        risen.addTag(Enthrall.RISEN_TAG);
        level.addFreshEntity(risen);
        Enthrall.enthrall(level, risen, raiser, serviceTicks(curseLevel));
        level.sendParticles(ParticleTypes.SOUL, risen.getX(), risen.getY(0.5), risen.getZ(), 20, 0.3, 0.6, 0.3, 0.02);
        level.sendParticles(new DustParticleOptions(Curse.COLOUR, 1.2F), risen.getX(), risen.getY(0.5), risen.getZ(), 20, 0.3, 0.6, 0.3, 0.0);
        level.playSound(null, risen.getX(), risen.getY(), risen.getZ(), SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.PLAYERS, 0.8F, 1.2F);
        return risen;
    }

    // ---- Exile's Mark: the banished can't hurt anyone, or heal ----

    @SubscribeEvent
    static void banishedCantHurt(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof LivingEntity attacker && attacker.hasEffect(ModEffects.BANISHED)
                && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void banishedCantHeal(LivingHealEvent event) {
        if (event.getEntity().hasEffect(ModEffects.BANISHED)) {
            event.setCanceled(true);
        }
    }

    // ---- Arrows: Wraithseeker, and the Wrath of the Twin Wyrms ----

    @SubscribeEvent
    static void cursedArrows(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof AbstractArrow arrow) || !(event.getLevel() instanceof ServerLevel level) || !(arrow.getOwner() instanceof Player player)) {
            return;
        }
        ItemStack bow = arrow.getWeaponItem();
        int seeking = looseLevel(player, Curse.TRACING_SHOT, bow);
        if (seeking > 0) {
            arrow.setData(ModAttachments.SEEKING, seeking);
        }
        int wyrms = looseLevel(player, Curse.DRAGONSTRIKE, bow);
        if (wyrms > 0) {
            // The arrow becomes the wyrms, and the curse is spent on them
            SpiritWyrms.loose(level, player, arrow.position(), arrow.getDeltaMovement(), wyrms);
            event.setCanceled(true);
            spend(player);
        }
    }

    /// Wraithseeker: the arrow bends toward the nearest creature ahead of it
    @SubscribeEvent
    static void seek(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof AbstractArrow arrow) || arrow.level().isClientSide()) {
            return;
        }
        int seeking = arrow.getData(ModAttachments.SEEKING);
        // (A stuck arrow has no speed left, so it's let be below)
        if (seeking <= 0 || arrow.tickCount < 2) {
            return;
        }
        Vec3 motion = arrow.getDeltaMovement();
        double speed = motion.length();
        if (speed < 0.2) {
            return;
        }
        LivingEntity quarry = quarry(arrow, motion.normalize(), 16.0 + 4.0 * seeking);
        if (quarry == null) {
            return;
        }
        Vec3 toward = quarry.position().add(0.0, quarry.getBbHeight() * 0.5, 0.0).subtract(arrow.position()).normalize();
        Vec3 turned = motion.normalize().lerp(toward, 0.2 + 0.1 * seeking).normalize().scale(speed);
        arrow.setDeltaMovement(turned);
        if (arrow.level() instanceof ServerLevel level && arrow.tickCount % 2 == 0) {
            level.sendParticles(new DustParticleOptions(Curse.COLOUR, 0.6F), arrow.getX(), arrow.getY(), arrow.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static boolean canSee(Entity from, Entity to) {
        Vec3 target = to.position().add(0.0, to.getBbHeight() * 0.5, 0.0);
        return from.level().clip(new ClipContext(from.position(), target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, from)).getType() == HitResult.Type.MISS;
    }

    // The nearest living thing ahead of the arrow (not its shooter, nor their thralls) it can see
    private static @Nullable LivingEntity quarry(AbstractArrow arrow, Vec3 heading, double range) {
        Entity shooter = arrow.getOwner();
        return arrow.level().getEntitiesOfClass(LivingEntity.class, new AABB(arrow.position(), arrow.position()).inflate(range), entity -> entity.isAlive()
                        && entity != shooter && !(entity instanceof Player) && !Enthrall.isThrallOf(entity, shooter)
                        && entity.position().subtract(arrow.position()).normalize().dot(heading) > 0.3 && canSee(arrow, entity))
                .stream().min((a, b) -> Double.compare(a.distanceToSqr(arrow), b.distanceToSqr(arrow))).orElse(null);
    }
}

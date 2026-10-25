package com.zestudio.crimsoncovenant.curse;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.effect.EnchainedEffect;
import com.zestudio.crimsoncovenant.entity.SpiritWyrms;
import com.zestudio.crimsoncovenant.hunter.HunterRanks;
import com.zestudio.crimsoncovenant.registry.ModAttachments;
import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.skill.Enthrall;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
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
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/// Unleashing a weapon's curse, and what each curse does while it's loose. Press the curse key holding a cursed weapon
/// (see ModKeyMappings) and, if you're a hunter of high enough rank and the curse has had time to stir again, it's
/// unleashed for a short while (see Curse for how long, and how long it then needs). The curses that never sleep
/// (Revenant's Grip, the Ravenous Maw, Wraithseeker) need no unleashing: they're at work whenever their weapon is.
/// Only blows and arrows from the cursed weapon carry a curse. The banished are drawn faint, like ghosts (see
/// CrimsonCovenantClient).
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public final class Curses {
    /// A curse let loose: which, how deep, and the game time it's spent
    public record Unleashed(Curse curse, int level, long until) {}

    public enum Outcome { UNLEASHED, NO_CURSE, TOO_LOW_RANK, STIRRING, ALREADY_LOOSE, ALWAYS_AWAKE }

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

    /// Ravenous Maw: health each kill feeds
    public static float gluttonyHeal(int level) {
        return 4.0F + 2.0F * level;
    }

    /// Ravenous Maw: how long it can be wielded without a kill before it starts to gnaw at its wielder. Deeper curses
    /// are hungrier.
    public static int gluttonyPatience(int level) {
        return 1200 - 200 * (level - 1);
    }

    /// When the Maw last fed, while it's put away: never
    public static final long MAW_PUT_AWAY = Long.MIN_VALUE;

    private static final double GUST_RANGE = 6.0;
    /// Wraithseeker: how long an arrow hunts before it gives up (long enough to cross a good way), and the least speed it
    /// hunts at
    private static final int SEEK_TICKS = 200;
    private static final double MIN_SEEK_SPEED = 1.0;
    /// Wraithseeker: this few ticks from its mark, an arrow flies straight at it
    private static final double LAST_TICKS = 4.0;
    /// Each hunting arrow's quarry: its hunter's mark when it was loosed. Not saved.
    private static final Map<AbstractArrow, LivingEntity> QUARRIES = new WeakHashMap<>();
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

    /// The level `curse` is loose at, if it is and `weapon` (what the blow or arrow came from) bears it; otherwise 0. A
    /// curse that never sleeps is loose whenever its weapon is used by a hunter of high enough rank.
    public static int looseLevel(Player player, Curse curse, @Nullable ItemStack weapon) {
        if (curse.alwaysAwake()) {
            int level = weapon == null ? 0 : WeaponCurse.levelOf(weapon, curse);
            return level > 0 && HunterRanks.get(player) >= Curse.rankFor(level) ? level : 0;
        }
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
        } else if (laid.curse().alwaysAwake()) {
            outcome = Outcome.ALWAYS_AWAKE;
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
        player.setData(ModAttachments.UNLEASHED_CURSE, Optional.of(new Unleashed(curse, laid.level(), now + curse.duration(laid.level()))));
        Map<Curse, Long> cooldowns = new EnumMap<>(Curse.class);
        cooldowns.putAll(player.getData(ModAttachments.CURSE_COOLDOWNS));
        cooldowns.put(curse, now + curse.cooldown(laid.level()));
        player.setData(ModAttachments.CURSE_COOLDOWNS, cooldowns);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.2F, 0.7F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.8F, 1.4F);
        level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY(1.0), player.getZ(), 16, 0.4, 0.4, 0.4, 0.03);
        if (curse == Curse.GUST) {
            gust(level, player, laid.level());
        } else if (curse == Curse.EARTHSHATTER) {
            // Brought down at once, and spent
            Earthshatter.quake(level, player, laid.level());
            spend(player);
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
            case ALWAYS_AWAKE -> Component.translatable("message.crimsoncovenant.curse.always_awake", laid.curse().titleAt(laid.level()));
        };
        player.sendOverlayMessage(message.copy().withStyle(outcome == Outcome.UNLEASHED ? ChatFormatting.AQUA : ChatFormatting.GRAY));
    }

    /// The curse is spent
    public static void spend(Player player) {
        player.setData(ModAttachments.UNLEASHED_CURSE, Optional.empty());
    }

    @SubscribeEvent
    static void tickUnleashed(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        gnaw(level, player);
        if (player.tickCount % 10 == 0 && player.getData(ModAttachments.WRAITHSEEKER_MARK) >= 0 && markOf(player) == null) {
            player.setData(ModAttachments.WRAITHSEEKER_MARK, -1);
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

    /// The Ravenous Maw, held too long without a kill, gnaws at its wielder: hungry for as long as it stays unfed
    public static void gnaw(ServerLevel level, Player player) {
        int maw = looseLevel(player, Curse.GLUTTONY, player.getMainHandItem());
        long now = level.getGameTime();
        if (maw == 0) {
            // Put away, it waits: its patience starts over when it's next taken up
            player.removeData(ModAttachments.MAW_FED);
            return;
        }
        long fed = player.getData(ModAttachments.MAW_FED);
        if (fed == MAW_PUT_AWAY) {
            player.setData(ModAttachments.MAW_FED, now);
            return;
        }
        if (now - fed > gluttonyPatience(maw) && player.tickCount % 20 == 0) {
            if (!player.hasEffect(MobEffects.HUNGER)) {
                player.sendOverlayMessage(Component.translatable("message.crimsoncovenant.curse.gluttony_unfed").withStyle(ChatFormatting.DARK_GRAY));
            }
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 40, maw - 1, false, false, true));
        }
    }

    /// A cursed weapon names its curse, right under its own name
    @SubscribeEvent
    static void describe(ItemTooltipEvent event) {
        WeaponCurse laid = WeaponCurse.of(event.getItemStack());
        if (laid != null) {
            int line = Math.min(1, event.getToolTip().size());
            event.getToolTip().add(line, laid.curse().titleAt(laid.level()));
            String hint = laid.curse().alwaysAwake() ? "item.crimsoncovenant.cursed_weapon.hint.always_awake" : "item.crimsoncovenant.cursed_weapon.hint";
            event.getToolTip().add(line + 1, Component.translatable(hint, laid.curse().plainName(), HunterRanks.name(Curse.rankFor(laid.level())))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    // ---- What they do ----

    // Whoever's blow or arrow it was, if a player's: whether it carried a curse is up to looseLevel
    private static @Nullable Player cursedAttacker(DamageSource source) {
        return source.getEntity() instanceof Player player ? player : null;
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
            target.setData(ModAttachments.ENCHAINED_LOOK, true);
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.2F, 0.6F);
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.3F, 1.6F);
            EnchainedEffect.showChains(level, target);
        }
        if (arrow && looseLevel(player, Curse.TRACING_SHOT, weapon) > 0) {
            mark(player, target);
        }
        int banish = arrow ? looseLevel(player, Curse.BANISH, weapon) : 0;
        if (banish > 0) {
            target.addEffect(new MobEffectInstance(ModEffects.BANISHED, banishTicks(banish)), player);
            target.setData(ModAttachments.BANISHED_LOOK, true);
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

    /// Kills by a cursed weapon, while its curse is loose
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
            player.setData(ModAttachments.MAW_FED, level.getGameTime());
            player.removeEffect(MobEffects.HUNGER);
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

    // When the banishment wears off (or is cleared), the creature is solid again; when the shackles do, the chains go
    @SubscribeEvent
    static void curseWearsOff(MobEffectEvent.Expired event) {
        if (event.getEffectInstance() != null) {
            curseLifted(event.getEntity(), event.getEffectInstance().getEffect());
        }
    }

    @SubscribeEvent
    static void curseCleared(MobEffectEvent.Remove event) {
        curseLifted(event.getEntity(), event.getEffect());
    }

    private static void curseLifted(LivingEntity entity, Holder<MobEffect> effect) {
        if (effect.is(ModEffects.BANISHED)) {
            entity.setData(ModAttachments.BANISHED_LOOK, false);
        } else if (effect.is(ModEffects.ENCHAINED)) {
            entity.setData(ModAttachments.ENCHAINED_LOOK, false);
        } else if (effect.is(ModEffects.KNOCKED_DOWN)) {
            entity.setData(ModAttachments.KNOCKED_DOWN_UNTIL, 0L);
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
        LivingEntity mark = seeking > 0 ? markOf(player) : null;
        if (mark != null) {
            // It hunts the hunter's mark; with none, it flies as any arrow does (and marks what it strikes)
            arrow.setData(ModAttachments.SEEKING, seeking);
            QUARRIES.put(arrow, mark);
        }
        int wyrms = looseLevel(player, Curse.DRAGONSTRIKE, bow);
        if (wyrms > 0) {
            // The arrow becomes the wyrms, and the curse is spent on them
            SpiritWyrms.loose(level, player, arrow.position(), arrow.getDeltaMovement(), wyrms);
            event.setCanceled(true);
            spend(player);
        }
    }

    /// Wraithseeker's mark: what the hunter's Wraithseeker arrows last struck, while it lives (and is in their world)
    public static @Nullable LivingEntity markOf(Player player) {
        int id = player.getData(ModAttachments.WRAITHSEEKER_MARK);
        return id >= 0 && player.level().getEntity(id) instanceof LivingEntity mark && mark.isAlive() ? mark : null;
    }

    /// Marks `target` for the hunter's Wraithseeker arrows (never the hunter, another player, or one of their thralls)
    public static void mark(Player player, LivingEntity target) {
        if (target != player && !(target instanceof Player) && !Enthrall.isThrallOf(target, player)) {
            player.setData(ModAttachments.WRAITHSEEKER_MARK, target.getId());
        }
    }

    /// Wraithseeker: the arrow hunts down its hunter's mark, wherever it is. It flies level while it hunts, turning a
    /// little each tick (more keenly at higher levels, and harder as it closes) so it's seen to chase, keeping its speed,
    /// a cold trail behind it, wheeling round if it flies past. It gives up, falling as any arrow does, if its quarry dies
    /// first, or it hasn't struck in ten seconds. Each turn is sent to the players watching at once: an arrow's own
    /// updates come too seldom to show the chase.
    @SubscribeEvent
    static void seek(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof AbstractArrow arrow) || !(arrow.level() instanceof ServerLevel level)) {
            return;
        }
        int seeking = arrow.getData(ModAttachments.SEEKING);
        if (seeking <= 0 || arrow.tickCount < 2) {
            return;
        }
        Vec3 motion = arrow.getDeltaMovement();
        double speed = motion.length();
        LivingEntity quarry = QUARRIES.get(arrow);
        // Stuck in something (no speed left), hunted long enough, or nothing left to hunt
        if (speed < 0.2 || arrow.tickCount > SEEK_TICKS || quarry == null || !quarry.isAlive() || quarry.level() != level) {
            arrow.setData(ModAttachments.SEEKING, 0);
            arrow.setNoGravity(false);
            QUARRIES.remove(arrow);
            return;
        }
        arrow.setNoGravity(true);
        level.sendParticles(new DustParticleOptions(Curse.COLOUR, 0.7F), arrow.getX(), arrow.getY(), arrow.getZ(), 2, 0.02, 0.02, 0.02, 0.0);
        if (arrow.tickCount % 6 == 0) {
            level.sendParticles(ParticleTypes.SOUL, arrow.getX(), arrow.getY(), arrow.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        }
        Vec3 heading = motion.scale(1.0 / speed);
        double flight = Math.max(speed, MIN_SEEK_SPEED);
        // Where the quarry will be when the arrow gets there, as near as it can tell: it leads a moving mark
        Vec3 centre = quarry.position().add(0.0, quarry.getBbHeight() * 0.5, 0.0);
        double ticksAway = centre.distanceTo(arrow.position()) / flight;
        Vec3 aim = centre.add(quarry.getKnownMovement().scale(Math.min(ticksAway, 20.0))).subtract(arrow.position());
        Vec3 toward = aim.lengthSqr() < 1.0E-6 ? heading : aim.normalize();
        // Far off, it turns a share of the way each tick, keener at higher levels and the faster it's going (so even a
        // shot loosed straight up comes round in time); heading away, it wheels hard; and in its last few ticks it flies
        // straight at the mark, so it can't dive past into the ground beside it
        double keenness = Math.max(0.15 + 0.1 * seeking, 2.0 / Math.max(ticksAway, 0.1));
        if (heading.dot(toward) < 0.3) {
            keenness = Math.max(keenness, 0.6);
        }
        Vec3 direction = ticksAway < LAST_TICKS || keenness >= 1.0 || heading.dot(toward) < -0.95 ? toward : heading.lerp(toward, keenness).normalize();
        Vec3 turned = direction.scale(flight);
        arrow.setDeltaMovement(turned);
        arrow.setYRot((float) (Mth.atan2(turned.x, turned.z) * Mth.RAD_TO_DEG));
        arrow.setXRot((float) (Mth.atan2(turned.y, turned.horizontalDistance()) * Mth.RAD_TO_DEG));
        // Tell the watching players about the turn now, rather than leave them to see it fly straight on
        arrow.needsSync = true;
    }
}

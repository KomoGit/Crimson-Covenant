package com.zestudio.crimsoncovenant.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.network.BloodMoonPayload;
import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.registry.ModEntities;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/// The Blood Moon: a full-moon night, at least 13 days after the previous one (full moons come every 8 days,
/// so in practice every other full moon). Vampires spawn far more often and hunters ride out in force, and
/// vampires under the open sky (players included) are filled with Bloodlust. Starts at dusk, ends at the next dawn.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public final class BloodMoon {
    public static final int DAYS_BETWEEN = 13;
    private static final long NIGHT_START = 13000L;
    private static final long NIGHT_END = 23000L;
    /// Extra vampire weight added to every overworld monster spawn while it lasts (regular vampires are 25)
    private static final int EXTRA_VAMPIRE_WEIGHT = 80;
    private static final int BLOODLUST_DURATION = 60;

    private BloodMoon() {}

    public static boolean isActive(Level level) {
        return level instanceof ServerLevel serverLevel && serverLevel.dimension() == Level.OVERWORLD && data(serverLevel).active;
    }

    /// Whether a blood moon should rise tonight: night time, a full moon, and long enough since the last one
    public static boolean shouldRise(long timeOfDay, MoonPhase moon, long day, long lastBloodMoonDay) {
        return timeOfDay >= NIGHT_START && timeOfDay < NIGHT_END && moon == MoonPhase.FULL_MOON && day - lastBloodMoonDay >= DAYS_BETWEEN;
    }

    public static void start(ServerLevel level) {
        Data data = data(level);
        if (data.active) {
            return;
        }
        data.active = true;
        data.lastBloodMoonDay = day(level);
        data.endsAt = nextDawn(level.getOverworldClockTime());
        data.setDirty();
        announce(level, Component.translatable("event.crimsoncovenant.blood_moon.rise").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        level.players().forEach(player -> level.playSound(null, player.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.AMBIENT, 0.6F, 0.6F));
        sync(level, true);
    }

    public static void stop(ServerLevel level) {
        Data data = data(level);
        if (!data.active) {
            return;
        }
        data.active = false;
        data.setDirty();
        announce(level, Component.translatable("event.crimsoncovenant.blood_moon.set").withStyle(ChatFormatting.GRAY));
        sync(level, false);
    }

    /// Vampires under the open sky during a blood moon gain Bloodlust. Called every second for vampire mobs and players.
    public static void tickBloodlust(LivingEntity vampire) {
        Level level = vampire.level();
        if (isActive(level) && level.canSeeSky(BlockPos.containing(vampire.getX(), vampire.getEyeY(), vampire.getZ()))) {
            vampire.addEffect(new MobEffectInstance(ModEffects.BLOODLUST, BLOODLUST_DURATION, 0, true, true, true));
        }
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD || level.getGameTime() % 20 != 0) {
            return;
        }
        long time = level.getOverworldClockTime();
        long timeOfDay = time % 24000L;
        Data data = data(level);
        if (data.active) {
            if (time >= data.endsAt) {
                stop(level);
            }
        } else if (shouldRise(timeOfDay, moonPhase(level), day(level), data.lastBloodMoonDay)) {
            start(level);
        }
    }

    // Many more vampires in the night's monster spawns
    @SubscribeEvent
    static void onPotentialSpawns(LevelEvent.PotentialSpawns event) {
        if (event.getMobCategory() == MobCategory.MONSTER && isActive(event.getLevel() instanceof Level level ? level : null)) {
            event.addSpawnerData(new Weighted<>(new MobSpawnSettings.SpawnerData(ModEntities.VAMPIRE.get(), UniformInt.of(1, 3)), EXTRA_VAMPIRE_WEIGHT));
        }
    }

    // Players joining mid-event need to know, for the red sky
    @SubscribeEvent
    static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player, isActive(player.level().getServer().overworld()));
        }
    }

    private static void sync(ServerLevel level, boolean active) {
        level.getServer().getPlayerList().getPlayers().forEach(player -> sync(player, active));
    }

    // Only to clients that have the mod (vanilla clients, and test players, can't receive it)
    private static void sync(ServerPlayer player, boolean active) {
        if (player.connection.hasChannel(BloodMoonPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, new BloodMoonPayload(active));
        }
    }

    private static void announce(ServerLevel level, Component message) {
        level.players().forEach(player -> player.sendSystemMessage(message));
    }

    /// Tonight's moon. It's a per-position value in 26.x, but the same everywhere in the overworld.
    public static MoonPhase moonPhase(ServerLevel level) {
        return level.getServer().overworld().environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, BlockPos.ZERO);
    }

    /// When the coming dawn breaks. A blood moon started by command in the daytime lasts through the night ahead.
    public static long nextDawn(long time) {
        long dawnToday = time - time % 24000L + NIGHT_END;
        return time < dawnToday ? dawnToday : dawnToday + 24000L;
    }

    private static long day(ServerLevel level) {
        return level.getOverworldClockTime() / 24000L;
    }

    private static Data data(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(Data.TYPE);
    }

    /// Saved with the world: whether a blood moon is up and when it ends, and the day of the last one
    static final class Data extends SavedData {
        static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.fieldOf("active").forGetter(d -> d.active),
                Codec.LONG.fieldOf("last_blood_moon_day").forGetter(d -> d.lastBloodMoonDay),
                Codec.LONG.optionalFieldOf("ends_at", 0L).forGetter(d -> d.endsAt)
        ).apply(i, Data::new));
        static final SavedDataType<Data> TYPE = new SavedDataType<>(CrimsonCovenant.id("blood_moon"), Data::new, CODEC);

        boolean active;
        // So the very first blood moon can come on the first full moon after day 13
        long lastBloodMoonDay;
        long endsAt;

        Data() {
            this(false, 0L, 0L);
        }

        Data(boolean active, long lastBloodMoonDay, long endsAt) {
            this.active = active;
            this.lastBloodMoonDay = lastBloodMoonDay;
            this.endsAt = endsAt;
        }
    }
}

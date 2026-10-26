package com.zestudio.crimsoncovenant.registry;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.skill.Enthrall;
import com.zestudio.crimsoncovenant.skill.AfterimageStrike;
import com.zestudio.crimsoncovenant.curse.Curse;
import com.zestudio.crimsoncovenant.curse.Curses;
import com.zestudio.crimsoncovenant.vampirism.VampireAge;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/// Extra data stored on entities (players, for now).
public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, CrimsonCovenant.MODID);

    /// Ticks left until an infected player turns into a vampire. 0 means not infected. Survives death.
    public static final Supplier<AttachmentType<Integer>> INFECTION_TICKS = ATTACHMENT_TYPES.register("infection_ticks",
            () -> AttachmentType.builder(() -> 0).serialize(Codec.INT.fieldOf("ticks")).copyOnDeath().build());

    /// Whether the player has fully turned into a vampire. Survives death, and is sent to the player's own client
    /// so it knows to show the blood bar.
    public static final Supplier<AttachmentType<Boolean>> IS_VAMPIRE = ATTACHMENT_TYPES.register("is_vampire",
            () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL.fieldOf("vampire")).copyOnDeath()
                    .sync((holder, to) -> holder == to, ByteBufCodecs.BOOL).build());

    /// Blood skills (by name) a vampire has unlocked in their Codex Sanguinis, so their tomes can be found and read (see
    /// guide.SkillLore). Saved, survives death, and sent to the player's own client for the Codex.
    public static final Supplier<AttachmentType<List<String>>> UNLOCKED_SKILLS = ATTACHMENT_TYPES.register("unlocked_skills",
            () -> AttachmentType.builder(() -> List.<String>of()).serialize(Codec.STRING.listOf().fieldOf("skills")).copyOnDeath()
                    .sync((holder, to) -> holder == to, ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list())).build());

    /// Blood skills (by name) whose tomes a vampire has had translated: their Codex pages are filled in
    public static final Supplier<AttachmentType<List<String>>> TRANSLATED_SKILLS = ATTACHMENT_TYPES.register("translated_skills",
            () -> AttachmentType.builder(() -> List.<String>of()).serialize(Codec.STRING.listOf().fieldOf("skills")).copyOnDeath()
                    .sync((holder, to) -> holder == to, ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list())).build());

    /// Creatures (by entity type id) a player has come across: their bestiary entries in the guide (see guide.Bestiary)
    public static final Supplier<AttachmentType<List<String>>> ENCOUNTERED = ATTACHMENT_TYPES.register("encountered",
            () -> AttachmentType.builder(() -> List.<String>of()).serialize(Codec.STRING.listOf().fieldOf("creatures")).copyOnDeath()
                    .sync((holder, to) -> holder == to, ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list())).build());

    /// Whether a player has been given their guide book on first joining (see guide.GuideBooks)
    public static final Supplier<AttachmentType<Boolean>> GUIDE_GIVEN = ATTACHMENT_TYPES.register("guide_given",
            () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL.fieldOf("given")).copyOnDeath().build());

    /// The game time a vampire player's age counts from (see vampirism.VampireAge), or VampireAge.NOT_STARTED. Survives
    /// death (which moves it on ten days).
    public static final Supplier<AttachmentType<Long>> VAMPIRE_SINCE = ATTACHMENT_TYPES.register("vampire_since",
            () -> AttachmentType.builder(() -> VampireAge.NOT_STARTED).serialize(Codec.LONG.fieldOf("since")).copyOnDeath().build());

    /// A vampire player's age in days, up to the cap, as last worked out: sent to their own client, for the blood bar's
    /// day count and blood skills' costs. Survives death.
    public static final Supplier<AttachmentType<Integer>> VAMPIRE_DAYS = ATTACHMENT_TYPES.register("vampire_days",
            () -> AttachmentType.builder(() -> 0).serialize(Codec.INT.fieldOf("days")).copyOnDeath()
                    .sync((holder, to) -> holder == to, ByteBufCodecs.VAR_INT).build());

    /// The game time an old vampire's Last Stand can next rise (see vampirism.VampireAge). Not saved.
    public static final Supplier<AttachmentType<Long>> LAST_STAND_READY_AT = ATTACHMENT_TYPES.register("last_stand_ready_at",
            () -> AttachmentType.builder(() -> 0L).build());

    /// Whether a vampire player has turned their night vision off (see Vampirism.toggleNightVision). Survives death.
    public static final Supplier<AttachmentType<Boolean>> NIGHT_VISION_OFF = ATTACHMENT_TYPES.register("night_vision_off",
            () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL.fieldOf("off")).copyOnDeath().build());

    /// A player's rank in the hunters, 0 when not one of them (see hunter.HunterRanks). Survives death, and is sent to
    /// the player's own client.
    public static final Supplier<AttachmentType<Integer>> HUNTER_RANK = ATTACHMENT_TYPES.register("hunter_rank",
            () -> AttachmentType.builder(() -> 0).serialize(Codec.INT.fieldOf("rank")).copyOnDeath()
                    .sync((holder, to) -> holder == to, ByteBufCodecs.VAR_INT).build());

    /// On a brewing stand: the hunter rank of whoever last opened it, which decides which hunter potions it will brew
    /// (see hunter.HunterGear)
    public static final Supplier<AttachmentType<Integer>> BREWER_RANK = ATTACHMENT_TYPES.register("brewer_rank",
            () -> AttachmentType.builder(() -> 0).serialize(Codec.INT.fieldOf("rank")).build());

    /// A vampire player's blood points, spent on blood skills (see BloodPool). Lost on death, and sent to the
    /// player's own client for the blood bar.
    public static final Supplier<AttachmentType<Integer>> BLOOD = ATTACHMENT_TYPES.register("blood",
            () -> AttachmentType.builder(() -> 0).serialize(Codec.INT.fieldOf("blood"))
                    .sync((holder, to) -> holder == to, ByteBufCodecs.VAR_INT).build());

    /// Skill items set aside when their owner died, handed back on respawn (see SkillKeeper)
    public static final Supplier<AttachmentType<List<ItemStack>>> KEPT_SKILLS = ATTACHMENT_TYPES.register("kept_skills",
            () -> AttachmentType.builder(() -> List.<ItemStack>of()).serialize(ItemStack.CODEC.listOf().fieldOf("items")).copyOnDeath().build());

    /// Whether the player has Armor of Ancient up, sent to everyone nearby so they can see the blood shell
    /// (effects themselves only reach the player's own client). Not saved.
    public static final Supplier<AttachmentType<Boolean>> ANCIENT_ARMOR_VISIBLE = ATTACHMENT_TYPES.register("ancient_armor_visible",
            () -> AttachmentType.builder(() -> false).sync(ByteBufCodecs.BOOL).build());

    /// The skills a player is wearing, one per skill slot (see SkillSlots). Survives death, and is sent to the
    /// player's own client for the skill bar.
    public static final Supplier<AttachmentType<List<ItemStack>>> EQUIPPED_SKILLS = ATTACHMENT_TYPES.register("equipped_skills",
            () -> AttachmentType.builder(() -> List.<ItemStack>of()).serialize(ItemStack.OPTIONAL_CODEC.listOf().fieldOf("skills")).copyOnDeath()
                    .sync((holder, to) -> holder == to, ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list())).build());

    /// Whether the player is a ghost from the Potion of Ephemeral, sent to everyone nearby so they can draw them
    /// faint (effects themselves only reach the player's own client). Not saved.
    public static final Supplier<AttachmentType<Boolean>> GHOSTLY = ATTACHMENT_TYPES.register("ghostly",
            () -> AttachmentType.builder(() -> false).sync(ByteBufCodecs.BOOL).build());

    /// A player coming down from flight, to be caught near the ground (see effect.SoftLanding). Saved, so logging out
    /// mid-fall doesn't lose it.
    public static final Supplier<AttachmentType<Boolean>> SOFT_LANDING = ATTACHMENT_TYPES.register("soft_landing",
            () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL.fieldOf("landing")).build());

    /// The bear trap holding a creature fast, if one is. Sent to a player's own client, which moves them, so the
    /// trap can hold them there too. Not saved: logging out slips the trap.
    public static final Supplier<AttachmentType<Optional<BlockPos>>> TRAPPED_IN = ATTACHMENT_TYPES.register("trapped_in",
            () -> AttachmentType.builder(() -> Optional.<BlockPos>empty()).sync((holder, to) -> holder == to, ByteBufCodecs.optional(BlockPos.STREAM_CODEC)).build());

    /// A creature under a vampire's spell after an Enthrall possession (see skill.Enthrall). Not saved.
    public static final Supplier<AttachmentType<Optional<Enthrall.Thrall>>> THRALL = ATTACHMENT_TYPES.register("thrall",
            () -> AttachmentType.builder(() -> Optional.<Enthrall.Thrall>empty()).build());

    /// Whether the possession a player is in was cast with the Enthrall upgrade. Not saved.
    public static final Supplier<AttachmentType<Boolean>> POSSESSION_ENTHRALLS = ATTACHMENT_TYPES.register("possession_enthralls",
            () -> AttachmentType.builder(() -> false).build());

    /// Whether the player has the Crimson Shroud on, sent to everyone nearby so they see its wings. Not saved.
    public static final Supplier<AttachmentType<Boolean>> CRIMSON_SHROUD_WORN = ATTACHMENT_TYPES.register("crimson_shroud_worn",
            () -> AttachmentType.builder(() -> false).sync(ByteBufCodecs.BOOL).build());

    /// Afterimage Strike (upgraded Dash): whether the dash under way leaves slashing afterimages, and the afterimages
    /// with the slashes they still owe (see skill.AfterimageStrike). Not saved.
    public static final Supplier<AttachmentType<Boolean>> DASH_STRIKING = ATTACHMENT_TYPES.register("dash_striking",
            () -> AttachmentType.builder(() -> false).build());
    public static final Supplier<AttachmentType<AfterimageStrike.Trail>> DASH_AFTERIMAGES = ATTACHMENT_TYPES.register("dash_afterimages",
            () -> AttachmentType.builder(AfterimageStrike.Trail::new).build());

    /// Final Feast (upgraded Defy Death): the game time until which the vampire's next blow heals them. Not saved.
    public static final Supplier<AttachmentType<Long>> FINAL_FEAST_UNTIL = ATTACHMENT_TYPES.register("final_feast_until",
            () -> AttachmentType.builder(() -> 0L).build());

    /// Where a player's Shadow Step anchor is, if they've placed one. Survives death.
    public static final Supplier<AttachmentType<Optional<ShadowAnchor>>> SHADOW_ANCHOR = ATTACHMENT_TYPES.register("shadow_anchor",
            () -> AttachmentType.builder(() -> Optional.<ShadowAnchor>empty()).serialize(ShadowAnchor.CODEC.optionalFieldOf("anchor")).copyOnDeath().build());

    /// A mob fleeing from a Night of Terror: where to run from, and the game time it stops being afraid. Not saved.
    public static final Supplier<AttachmentType<Optional<Terror>>> TERROR = ATTACHMENT_TYPES.register("terror",
            () -> AttachmentType.builder(() -> Optional.<Terror>empty()).build());

    /// The creature a player has possessed and become (see Possession): its type and saved data. Sent to every
    /// client that can see the player, so they can draw the creature in the player's place. Saved, so the creature
    /// isn't lost if the server stops mid-possession.
    public static final Supplier<AttachmentType<Optional<PossessedForm>>> POSSESSED_FORM = ATTACHMENT_TYPES.register("possessed_form",
            () -> AttachmentType.builder(() -> Optional.<PossessedForm>empty()).serialize(PossessedForm.CODEC.optionalFieldOf("form"))
                    .sync(ByteBufCodecs.optional(PossessedForm.STREAM_CODEC)).build());

    /// Ticks left on a possessed creeper's fuse, 0 when it isn't lit. Sent to everyone nearby so they see it
    /// swell. Not saved.
    public static final Supplier<AttachmentType<Integer>> CREEPER_FUSE = ATTACHMENT_TYPES.register("creeper_fuse",
            () -> AttachmentType.builder(() -> 0).sync(ByteBufCodecs.VAR_INT).build());

    /// How a possession is going: time left, the creature's health and attack, and ability cooldowns. Saved.
    public static final Supplier<AttachmentType<PossessionState>> POSSESSION_STATE = ATTACHMENT_TYPES.register("possession_state",
            () -> AttachmentType.builder(() -> PossessionState.NONE).serialize(PossessionState.CODEC.fieldOf("state")).build());

    /// How long a possessing vampire has held the attack button to draw a bow (or charge a fireball), 0 when not
    /// charging. Sent to everyone nearby so the creature is seen taking aim. Not saved.
    public static final Supplier<AttachmentType<Integer>> POSSESSED_CHARGE = ATTACHMENT_TYPES.register("possessed_charge",
            () -> AttachmentType.builder(() -> 0).sync(ByteBufCodecs.VAR_INT).build());

    /// A Dash in progress: the direction and the ticks left. Sent to everyone nearby, who draw afterimages behind the
    /// dashing player. Not saved.
    public static final Supplier<AttachmentType<DashState>> DASH = ATTACHMENT_TYPES.register("dash",
            () -> AttachmentType.builder(() -> DashState.NONE).sync(DashState.STREAM_CODEC).build());

    /// A Blood Charge under way (see skill.BloodSkills.bloodCharge): the ticks it has left to drive its vampire on. Sent
    /// to everyone nearby, who draw afterimages behind them. Not saved.
    public static final Supplier<AttachmentType<Integer>> BLOOD_CHARGE = ATTACHMENT_TYPES.register("blood_charge",
            () -> AttachmentType.builder(() -> 0).sync(ByteBufCodecs.VAR_INT).build());

    /// A creature carried off by a flying vampire who struck it (see effect.FlightLift). Sent to everyone nearby, who
    /// hold it in the vampire's hands themselves so it keeps up with them. Not saved.
    public static final Supplier<AttachmentType<Optional<Lift>>> LIFTED = ATTACHMENT_TYPES.register("lifted",
            () -> AttachmentType.builder(() -> Optional.<Lift>empty()).sync(ByteBufCodecs.optional(Lift.STREAM_CODEC)).build());

    /// The game time a flying vampire last let go of this creature (see effect.FlightLift), so it isn't snatched straight
    /// back up as it falls. Not saved.
    public static final Supplier<AttachmentType<Long>> DROPPED = ATTACHMENT_TYPES.register("dropped",
            () -> AttachmentType.builder(() -> Long.MIN_VALUE).build());

    /// Who's carrying a lifted creature
    public record Lift(UUID carrier) {
        public static final StreamCodec<ByteBuf, Lift> STREAM_CODEC = UUIDUtil.STREAM_CODEC.map(Lift::new, Lift::carrier);
    }

    /// A mob under a Death March: the way it's marching, and the game time it stops. Not saved.
    public static final Supplier<AttachmentType<Optional<DeathMarch>>> DEATH_MARCH = ATTACHMENT_TYPES.register("death_march",
            () -> AttachmentType.builder(() -> Optional.<DeathMarch>empty()).build());

    /// A creature a Blood Spear (or a whip) is driving back (see BloodSpear.drive). Not saved.
    public static final Supplier<AttachmentType<Optional<SpearDrive>>> SPEAR_DRIVE = ATTACHMENT_TYPES.register("spear_drive",
            () -> AttachmentType.builder(() -> Optional.<SpearDrive>empty()).build());

    /// How long a creature has been breathing the Miasma, in seconds, and when it last did (see entity.MiasmaCloud).
    /// Not saved.
    public static final Supplier<AttachmentType<MiasmaExposure>> MIASMA_EXPOSURE = ATTACHMENT_TYPES.register("miasma_exposure",
            () -> AttachmentType.builder(() -> MiasmaExposure.NONE).build());

    public record MiasmaExposure(long lastBreath, int seconds) {
        public static final MiasmaExposure NONE = new MiasmaExposure(Long.MIN_VALUE / 2, 0);
    }

    public record DashState(Vec3 velocity, int ticksLeft) {
        public static final DashState NONE = new DashState(Vec3.ZERO, 0);
        public static final StreamCodec<ByteBuf, DashState> STREAM_CODEC = StreamCodec.composite(
                Vec3.STREAM_CODEC, DashState::velocity,
                ByteBufCodecs.VAR_INT, DashState::ticksLeft,
                DashState::new);
    }

    /// `raiser`: the vampire whose Grave March raises the creature if it dies on the march
    public record DeathMarch(Vec3 direction, long until, Optional<UUID> raiser) {}

    /// A creature being carried along by a Blood Spear or a whip: its speed and heading, the ticks left, who threw or
    /// struck it, how hard a wall hits it, and whether that's blood magic (the spear's, which pays its blood back for
    /// a kill) or a plain blow
    public record SpearDrive(Vec3 velocity, int ticksLeft, Optional<UUID> thrower, float slamDamage, boolean bloodMagic) {
        public SpearDrive next(int ticksLeft) {
            return new SpearDrive(this.velocity, ticksLeft, this.thrower, this.slamDamage, this.bloodMagic);
        }

        public SpearDrive scaled(double share) {
            return new SpearDrive(this.velocity.scale(share), this.ticksLeft, this.thrower, this.slamDamage, this.bloodMagic);
        }
    }

    public record ShadowAnchor(ResourceKey<Level> dimension, Vec3 position) {
        public static final Codec<ShadowAnchor> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(ShadowAnchor::dimension),
                Vec3.CODEC.fieldOf("position").forGetter(ShadowAnchor::position)).apply(instance, ShadowAnchor::new));
    }

    /// `contagious`: Panic Contagion, the fleeing creature spreads its terror to others it passes
    public record Terror(Vec3 source, long until, boolean contagious) {}

    public record PossessedForm(Identifier type, CompoundTag data) {
        public static final Codec<PossessedForm> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("type").forGetter(PossessedForm::type),
                CompoundTag.CODEC.fieldOf("data").forGetter(PossessedForm::data)).apply(instance, PossessedForm::new));
        public static final StreamCodec<ByteBuf, PossessedForm> STREAM_CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, PossessedForm::type,
                ByteBufCodecs.COMPOUND_TAG, PossessedForm::data,
                PossessedForm::new);
    }

    public record PossessionState(int ticksLeft, float health, float attackDamage, int abilityCooldown, int attackCooldown) {
        public static final PossessionState NONE = new PossessionState(0, 0.0F, 0.0F, 0, 0);
        public static final Codec<PossessionState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("ticks_left").forGetter(PossessionState::ticksLeft),
                Codec.FLOAT.fieldOf("health").forGetter(PossessionState::health),
                Codec.FLOAT.fieldOf("attack_damage").forGetter(PossessionState::attackDamage),
                Codec.INT.fieldOf("ability_cooldown").forGetter(PossessionState::abilityCooldown),
                Codec.INT.fieldOf("attack_cooldown").forGetter(PossessionState::attackCooldown)).apply(instance, PossessionState::new));
    }

    /// The curse a hunter has let loose, while it lasts (see curse.Curses). Not saved.
    public static final Supplier<AttachmentType<Optional<Curses.Unleashed>>> UNLEASHED_CURSE = ATTACHMENT_TYPES.register("unleashed_curse",
            () -> AttachmentType.<Optional<Curses.Unleashed>>builder(() -> Optional.empty()).build());

    /// When each curse can next be unleashed, by game time. Not saved.
    public static final Supplier<AttachmentType<Map<Curse, Long>>> CURSE_COOLDOWNS = ATTACHMENT_TYPES.register("curse_cooldowns",
            () -> AttachmentType.<Map<Curse, Long>>builder(() -> Map.of()).build());

    /// A creature under Exile's Mark, so the players about can draw it faint, as a ghost (see curse.Curses). Not saved.
    public static final Supplier<AttachmentType<Boolean>> BANISHED_LOOK = ATTACHMENT_TYPES.register("banished_look",
            () -> AttachmentType.builder(() -> false).sync(ByteBufCodecs.BOOL).build());

    /// The game time a hunter's Ravenous Maw last fed (or was taken up), or Curses.MAW_PUT_AWAY. Not saved: logging in,
    /// its patience starts over.
    public static final Supplier<AttachmentType<Long>> MAW_FED = ATTACHMENT_TYPES.register("maw_fed",
            () -> AttachmentType.builder(() -> Curses.MAW_PUT_AWAY).build());

    /// A creature in the Shackles of the Damned, so the players about can draw the ghostly chains round it (see
    /// client.renderer.SpectralChains). Not saved.
    public static final Supplier<AttachmentType<Boolean>> ENCHAINED_LOOK = ATTACHMENT_TYPES.register("enchained_look",
            () -> AttachmentType.builder(() -> false).sync(ByteBufCodecs.BOOL).build());

    /// The game time a creature thrown down by Gravequake gets up again, or 0 if it's not down: players about see it
    /// lying flat until then (see curse.Earthshatter)
    public static final Supplier<AttachmentType<Long>> KNOCKED_DOWN_UNTIL = ATTACHMENT_TYPES.register("knocked_down_until",
            () -> AttachmentType.builder(() -> 0L).sync(ByteBufCodecs.VAR_LONG).build());

    /// A player being hauled in by Wraith's Tether (see curse.Grapple). Not saved.
    public static final Supplier<AttachmentType<Optional<GrappleLine>>> GRAPPLE = ATTACHMENT_TYPES.register("grapple",
            () -> AttachmentType.builder(() -> Optional.<GrappleLine>empty()).build());

    /// Where Wraith's Tether hauls its wielder (where their feet end up), and the game time it lets go regardless
    public record GrappleLine(Vec3 target, long until) {}

    /// Wraithseeker's mark: the entity id of what the hunter's Wraithseeker arrows last struck, which every arrow after
    /// hunts down while it lives; -1 for none. Sent to the hunter's own client, which outlines it. Not saved (ids don't
    /// outlast the world being loaded).
    public static final Supplier<AttachmentType<Integer>> WRAITHSEEKER_MARK = ATTACHMENT_TYPES.register("wraithseeker_mark",
            () -> AttachmentType.builder(() -> -1).sync((holder, to) -> holder == to, ByteBufCodecs.VAR_INT).build());

    /// On an arrow loosed under Wraithseeker: how keenly it seeks. Not saved.
    public static final Supplier<AttachmentType<Integer>> SEEKING = ATTACHMENT_TYPES.register("seeking",
            () -> AttachmentType.builder(() -> 0).build());

    private ModAttachments() {}
}

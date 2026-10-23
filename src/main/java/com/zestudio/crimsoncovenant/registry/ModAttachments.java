package com.zestudio.crimsoncovenant.registry;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zestudio.crimsoncovenant.CrimsonCovenant;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
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

    /// A mob under a Death March: the way it's marching, and the game time it stops. Not saved.
    public static final Supplier<AttachmentType<Optional<DeathMarch>>> DEATH_MARCH = ATTACHMENT_TYPES.register("death_march",
            () -> AttachmentType.builder(() -> Optional.<DeathMarch>empty()).build());

    public record DashState(Vec3 velocity, int ticksLeft) {
        public static final DashState NONE = new DashState(Vec3.ZERO, 0);
        public static final StreamCodec<ByteBuf, DashState> STREAM_CODEC = StreamCodec.composite(
                Vec3.STREAM_CODEC, DashState::velocity,
                ByteBufCodecs.VAR_INT, DashState::ticksLeft,
                DashState::new);
    }

    public record DeathMarch(Vec3 direction, long until) {}

    public record ShadowAnchor(ResourceKey<Level> dimension, Vec3 position) {
        public static final Codec<ShadowAnchor> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(ShadowAnchor::dimension),
                Vec3.CODEC.fieldOf("position").forGetter(ShadowAnchor::position)).apply(instance, ShadowAnchor::new));
    }

    public record Terror(Vec3 source, long until) {}

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

    private ModAttachments() {}
}

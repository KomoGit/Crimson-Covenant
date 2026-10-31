package com.zestudio.crimsoncovenant.network;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.effect.FlightLift;
import com.zestudio.crimsoncovenant.skill.BloodSkills;
import com.zestudio.crimsoncovenant.skill.CreatureAbilities;
import com.zestudio.crimsoncovenant.skill.MoonPowers;
import com.zestudio.crimsoncovenant.skill.Possession;
import com.zestudio.crimsoncovenant.skill.SkillSlots;
import com.zestudio.crimsoncovenant.skill.SkillsMenu;
import com.zestudio.crimsoncovenant.vampirism.Vampirism;
import com.zestudio.crimsoncovenant.curse.Curses;
import com.zestudio.crimsoncovenant.item.WerewolfBelt;
import com.zestudio.crimsoncovenant.item.WhipItem;
import com.zestudio.crimsoncovenant.guide.SkillLore;
import com.zestudio.crimsoncovenant.skill.BloodSkill;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.ChatFormatting;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/// What the skill keys send to the server
public final class SkillPayloads {
    private SkillPayloads() {}

    /// Cast the skill worn in this slot
    public record CastSkill(int slot) implements CustomPacketPayload {
        public static final Type<CastSkill> TYPE = new Type<>(CrimsonCovenant.id("cast_skill"));
        public static final StreamCodec<ByteBuf, CastSkill> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(CastSkill::new, CastSkill::slot);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /// Open the Blood Skills screen
    /// The Crimson Shroud's right click (see BloodSkills.bloodCharge)
    public record BloodCharge() implements CustomPacketPayload {
        public static final Type<BloodCharge> TYPE = new Type<>(CrimsonCovenant.id("blood_charge"));
        public static final StreamCodec<ByteBuf, BloodCharge> STREAM_CODEC = StreamCodec.unit(new BloodCharge());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /// A right click with a snatched-up creature in hand: let it go (see FlightLift.drop)
    public record DropCarried() implements CustomPacketPayload {
        public static final Type<DropCarried> TYPE = new Type<>(CrimsonCovenant.id("drop_carried"));
        public static final StreamCodec<ByteBuf, DropCarried> STREAM_CODEC = StreamCodec.unit(new DropCarried());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /// Unleash the curse on the weapon in hand (see curse.Curses)
    public record UnleashCurse() implements CustomPacketPayload {
        public static final Type<UnleashCurse> TYPE = new Type<>(CrimsonCovenant.id("unleash_curse"));
        public static final StreamCodec<ByteBuf, UnleashCurse> STREAM_CODEC = StreamCodec.unit(new UnleashCurse());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /// The whip in hand cracked (the attack key, see client.ModKeyMappings)
    public record WhipCrack() implements CustomPacketPayload {
        public static final Type<WhipCrack> TYPE = new Type<>(CrimsonCovenant.id("whip_crack"));
        public static final StreamCodec<ByteBuf, WhipCrack> STREAM_CODEC = StreamCodec.unit(new WhipCrack());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /// Unlock this blood skill (by ordinal) in the Codex Sanguinis (see guide.SkillLore)
    public record UnlockSkill(int skill) implements CustomPacketPayload {
        public static final Type<UnlockSkill> TYPE = new Type<>(CrimsonCovenant.id("unlock_skill"));
        public static final StreamCodec<ByteBuf, UnlockSkill> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(UnlockSkill::new, UnlockSkill::skill);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /// Turn a vampire's night vision on or off (see Vampirism.toggleNightVision)
    public record ToggleNightVision() implements CustomPacketPayload {
        public static final Type<ToggleNightVision> TYPE = new Type<>(CrimsonCovenant.id("toggle_night_vision"));
        public static final StreamCodec<ByteBuf, ToggleNightVision> STREAM_CODEC = StreamCodec.unit(new ToggleNightVision());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record OpenSkills() implements CustomPacketPayload {
        public static final Type<OpenSkills> TYPE = new Type<>(CrimsonCovenant.id("open_skills"));
        public static final StreamCodec<ByteBuf, OpenSkills> STREAM_CODEC = StreamCodec.unit(new OpenSkills());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /// The attack button went down (with what the crosshair was on, -1 for nothing) or came back up, while the
    /// player is possessing a creature
    public record PossessedAttack(boolean pressed, int targetId) implements CustomPacketPayload {
        public static final Type<PossessedAttack> TYPE = new Type<>(CrimsonCovenant.id("possessed_attack"));
        public static final StreamCodec<ByteBuf, PossessedAttack> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, PossessedAttack::pressed,
                ByteBufCodecs.VAR_INT, PossessedAttack::targetId,
                PossessedAttack::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /// The skills key: opens the blood skills menu for a vampire, and only for a vampire. Returns whether it opened.
    public static boolean tryOpenSkillsScreen(ServerPlayer player) {
        if (!Vampirism.isVampire(player)) {
            player.sendOverlayMessage(Component.translatable("message.crimsoncovenant.skills.not_a_vampire").withStyle(ChatFormatting.GRAY));
            return false;
        }
        openSkillsScreen(player);
        return true;
    }

    public static void openSkillsScreen(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((containerId, inventory, owner) -> new SkillsMenu(containerId, inventory, new SkillSlots.Container(owner)),
                Component.translatable("container.crimsoncovenant.skills")));
    }

    @EventBusSubscriber(modid = CrimsonCovenant.MODID)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            var registrar = event.registrar("1");
            registrar.playToServer(CastSkill.TYPE, CastSkill.STREAM_CODEC, (payload, context) -> {
                if (context.player() instanceof ServerPlayer player && payload.slot() >= 0 && payload.slot() < SkillSlots.SLOTS) {
                    // The same keys are a werewolf's powers
                    if (WerewolfBelt.isTurned(player)) {
                        MoonPowers.tryCast(player, MoonPowers.Power.values()[payload.slot()]);
                    } else {
                        BloodSkills.tryCast(player, payload.slot());
                    }
                }
            });
            registrar.playToServer(BloodCharge.TYPE, BloodCharge.STREAM_CODEC, (payload, context) -> {
                if (context.player() instanceof ServerPlayer player) {
                    BloodSkills.bloodCharge(player);
                }
            });
            registrar.playToServer(DropCarried.TYPE, DropCarried.STREAM_CODEC, (payload, context) -> FlightLift.drop(context.player()));
            registrar.playToServer(UnleashCurse.TYPE, UnleashCurse.STREAM_CODEC, (payload, context) -> {
                if (context.player() instanceof ServerPlayer player) {
                    Curses.unleash(player);
                }
            });
            registrar.playToServer(WhipCrack.TYPE, WhipCrack.STREAM_CODEC, (payload, context) -> {
                if (context.player() instanceof ServerPlayer player) {
                    WhipItem.crack(player);
                }
            });
            registrar.playToServer(UnlockSkill.TYPE, UnlockSkill.STREAM_CODEC, (payload, context) -> {
                if (context.player() instanceof ServerPlayer player && payload.skill() >= 0 && payload.skill() < BloodSkill.values().length) {
                    SkillLore.unlock(player, BloodSkill.values()[payload.skill()]);
                }
            });
            registrar.playToServer(ToggleNightVision.TYPE, ToggleNightVision.STREAM_CODEC, (payload, context) -> {
                if (context.player() instanceof ServerPlayer player) {
                    Vampirism.toggleNightVision(player);
                }
            });
            registrar.playToServer(OpenSkills.TYPE, OpenSkills.STREAM_CODEC, (payload, context) -> {
                if (context.player() instanceof ServerPlayer player) {
                    tryOpenSkillsScreen(player);
                }
            });
            registrar.playToServer(PossessedAttack.TYPE, PossessedAttack.STREAM_CODEC, (payload, context) -> {
                if (context.player() instanceof ServerPlayer player && Possession.isPossessing(player)) {
                    if (payload.pressed()) {
                        CreatureAbilities.press(player, payload.targetId() < 0 ? null : player.level().getEntity(payload.targetId()));
                    } else {
                        CreatureAbilities.release(player);
                    }
                }
            });
        }
    }
}

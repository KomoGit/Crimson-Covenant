package com.zestudio.crimsoncovenant.network;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.skill.BloodSkills;
import com.zestudio.crimsoncovenant.skill.CreatureAbilities;
import com.zestudio.crimsoncovenant.skill.Possession;
import com.zestudio.crimsoncovenant.skill.SkillSlots;
import com.zestudio.crimsoncovenant.skill.SkillsMenu;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
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
                    BloodSkills.tryCast(player, payload.slot());
                }
            });
            registrar.playToServer(OpenSkills.TYPE, OpenSkills.STREAM_CODEC, (payload, context) -> {
                if (context.player() instanceof ServerPlayer player) {
                    openSkillsScreen(player);
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

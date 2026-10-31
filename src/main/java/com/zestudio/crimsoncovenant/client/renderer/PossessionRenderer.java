package com.zestudio.crimsoncovenant.client.renderer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.entity.Werewolf;
import com.zestudio.crimsoncovenant.item.WerewolfBelt;
import com.zestudio.crimsoncovenant.registry.ModAttachments;
import com.zestudio.crimsoncovenant.registry.ModAttachments.PossessedForm;
import com.zestudio.crimsoncovenant.registry.ModEntities;
import com.zestudio.crimsoncovenant.skill.CreatureAbilities;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.CreeperRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.renderstate.AvatarRenderStateModifier;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

/// Draws a possessing vampire as the creature they've become (see skill.Possession). Each such player gets a copy
/// of the creature, built from its saved data (so a sheep keeps its colour and a skeleton its bow), that follows
/// the player's every move and is drawn in their place. Their own hand disappears from first person too.
///
/// A player turned by a Werewolf Belt (see item.WerewolfBelt) is drawn the same way, as a fresh werewolf that also
/// swings and carries what they do. They keep their hands: only possession has none.
@EventBusSubscriber(modid = CrimsonCovenant.MODID, value = Dist.CLIENT)
public final class PossessionRenderer {
    private static final ContextKey<Entity> BODY = new ContextKey<>(CrimsonCovenant.id("possessed_body"));
    private static final ContextKey<Integer> FUSE = new ContextKey<>(CrimsonCovenant.id("possessed_fuse"));
    /// Player entity id -> the creature drawn in their place
    private static final Map<Integer, Body> BODIES = new HashMap<>();
    /// Players seen turned by a Werewolf Belt: they need their eye height worked out again when that changes
    private static final Set<Integer> TURNED = new HashSet<>();

    /// The shape of a belt's wearer: there's no saved creature behind it
    private static final PossessedForm WEREWOLF_BODY = new PossessedForm(CrimsonCovenant.id("werewolf"), new CompoundTag());

    private record Body(PossessedForm form, Entity entity) {}

    private PossessionRenderer() {}

    @SubscribeEvent
    static void followPlayers(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            BODIES.clear();
            TURNED.clear();
            return;
        }
        Set<Integer> possessing = new HashSet<>();
        for (AbstractClientPlayer player : level.players()) {
            if (WerewolfBelt.isTurned(player) ? TURNED.add(player.getId()) : TURNED.remove(player.getId())) {
                player.refreshDimensions();
            }
            Optional<PossessedForm> form = formOf(player);
            if (form.isEmpty()) {
                continue;
            }
            possessing.add(player.getId());
            Body body = BODIES.get(player.getId());
            if (body == null || !body.form().equals(form.get()) || body.entity().level() != level) {
                Entity entity = createBody(level, form.get());
                if (entity == null) {
                    continue;
                }
                // Never added to the world, so it needs an id of its own (rendering held items asks for one).
                // Negative, so it can't clash with a real entity's.
                entity.setId(-1 - player.getId());
                body = new Body(form.get(), entity);
                BODIES.put(player.getId(), body);
            }
            follow(body.entity(), player);
        }
        BODIES.keySet().retainAll(possessing);
    }

    private static Optional<PossessedForm> formOf(Player player) {
        Optional<PossessedForm> possessed = player.getData(ModAttachments.POSSESSED_FORM);
        return possessed.isEmpty() && WerewolfBelt.isTurned(player) ? Optional.of(WEREWOLF_BODY) : possessed;
    }

    private static @Nullable Entity createBody(ClientLevel level, PossessedForm form) {
        if (form == WEREWOLF_BODY) {
            return ModEntities.WEREWOLF.get().create(level, EntitySpawnReason.LOAD);
        }
        return BuiltInRegistries.ENTITY_TYPE.getOptional(form.type())
                .flatMap(type -> EntityType.create(type, TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), form.data()), level, EntitySpawnReason.LOAD))
                .orElse(null);
    }

    /// Puts the creature exactly where the player is, facing where they face, walking as they walk
    private static void follow(Entity body, Player player) {
        body.xo = body.xOld = player.xOld;
        body.yo = body.yOld = player.yOld;
        body.zo = body.zOld = player.zOld;
        body.setPos(player.getX(), player.getY(), player.getZ());
        body.yRotO = player.yRotO;
        body.setYRot(player.getYRot());
        body.xRotO = player.xRotO;
        body.setXRot(player.getXRot());
        body.setOnGround(player.onGround());
        body.tickCount = player.tickCount;
        if (body instanceof LivingEntity living) {
            living.yBodyRotO = player.yBodyRotO;
            living.yBodyRot = player.yBodyRot;
            living.yHeadRotO = player.yHeadRotO;
            living.yHeadRot = player.yHeadRot;
            float moved = (float) Math.min(Mth.length(player.getX() - player.xOld, player.getZ() - player.zOld) * 4.0, 1.0);
            living.walkAnimation.update(moved, 0.4F, 1.0F);
            living.hurtTime = player.hurtTime;
        }
        // Taking aim while the attack button is held: a skeleton raises its bow, a ghast opens its mouth
        boolean charging = player.getData(ModAttachments.POSSESSED_CHARGE) > 0;
        if (body instanceof Ghast ghast) {
            ghast.setCharging(charging);
        } else if (body instanceof Mob mob) {
            mob.setAggressive(charging);
        }
        if (body instanceof LivingEntity living && player.getData(ModAttachments.POSSESSED_FORM).isEmpty()) {
            living.setItemSlot(EquipmentSlot.MAINHAND, player.getMainHandItem());
            living.setItemSlot(EquipmentSlot.OFFHAND, player.getOffhandItem());
        }
    }

    @SubscribeEvent
    static void registerRenderStateModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerAvatarEntityModifier(new AvatarRenderStateModifier() {
            @Override
            public <T extends Avatar & ClientAvatarEntity> void accept(T avatar, AvatarRenderState state) {
                Body body = BODIES.get(avatar.getId());
                // A werewolf under a Potion of Invisibility is as unseen as anyone (possession hides its vampire with it)
                boolean unseen = body != null && body.form() == WEREWOLF_BODY && avatar.isInvisible();
                state.setRenderData(BODY, body == null || unseen ? null : body.entity());
                state.setRenderData(FUSE, avatar instanceof Player player ? player.getData(ModAttachments.CREEPER_FUSE) : 0);
                state.setRenderData(EclipseHideLayer.ACTIVE, avatar instanceof Player player && player.getData(ModAttachments.ECLIPSE_HIDE_VISIBLE));
            }
        });
    }

    /// The player isn't drawn: the creature is, in the same spot
    @SubscribeEvent
    static void drawCreatureInstead(RenderLivingEvent.Pre<?, ?, ?> event) {
        if (!(event.getRenderState() instanceof AvatarRenderState playerState)) {
            return;
        }
        Entity body = playerState.getRenderData(BODY);
        if (body == null) {
            return;
        }
        event.setCanceled(true);
        Minecraft minecraft = Minecraft.getInstance();
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        EntityRenderState bodyState = dispatcher.extractEntity(body, event.getPartialTick());
        bodyState.lightCoords = playerState.lightCoords;
        // A lit creeper swells up (and flashes) as its fuse burns down, as a real one does
        int fuse = playerState.getRenderDataOrDefault(FUSE, 0);
        if (fuse > 0 && bodyState instanceof CreeperRenderState creeper) {
            creeper.swelling = Mth.clamp((CreatureAbilities.CREEPER_FUSE - fuse + event.getPartialTick()) / CreatureAbilities.CREEPER_FUSE, 0.0F, 1.0F);
        }
        // A werewolf swings and crouches with the player inside it
        if (body instanceof Werewolf && bodyState instanceof HumanoidRenderState humanoid) {
            humanoid.currentSwing = playerState.currentSwing;
            humanoid.swingAnimation = playerState.swingAnimation;
            humanoid.isCrouching = playerState.isCrouching;
            humanoid.setRenderData(EclipseHideLayer.ACTIVE, playerState.getRenderDataOrDefault(EclipseHideLayer.ACTIVE, false));
        }
        CameraRenderState camera = minecraft.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        submit(dispatcher, bodyState, event.getPoseStack(), event.getSubmitNodeCollector(), camera);
    }

    private static <S extends EntityRenderState> void submit(EntityRenderDispatcher dispatcher, S state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        dispatcher.getRenderer(state).submit(state, poseStack, collector, camera);
    }

    /// In first person, the creature has no human hand
    @SubscribeEvent
    static void hideHand(RenderHandEvent event) {
        Player player = Minecraft.getInstance().player;
        if (player != null && player.getData(ModAttachments.POSSESSED_FORM).isPresent()) {
            event.setCanceled(true);
        }
    }
}

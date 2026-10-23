package com.zestudio.crimsoncovenant.client.renderer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.registry.ModAttachments;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/// Ghostly copies of a dashing player, left behind along the way. Each tick of a Dash leaves one where the player
/// was, and each lingers for a few ticks. They're drawn the way the game draws an invisible player to a teammate:
/// faint and see-through.
@EventBusSubscriber(modid = CrimsonCovenant.MODID, value = Dist.CLIENT)
public final class DashAfterimages {
    private static final int LINGER_TICKS = 8;
    /// Player entity id -> their afterimages, oldest first
    private static final Map<Integer, Deque<Afterimage>> TRAILS = new HashMap<>();
    private static boolean drawingAfterimage = false;

    private record Afterimage(Vec3 position, float bodyRot, float headRot, float xRot, float walkPosition, float walkSpeed, long tick) {}

    private DashAfterimages() {}

    @SubscribeEvent
    static void recordTrails(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            TRAILS.clear();
            return;
        }
        long now = level.getGameTime();
        Set<Integer> present = new HashSet<>();
        for (AbstractClientPlayer player : level.players()) {
            present.add(player.getId());
            if (player.getData(ModAttachments.DASH).ticksLeft() > 0) {
                TRAILS.computeIfAbsent(player.getId(), id -> new ArrayDeque<>()).addLast(new Afterimage(player.position(), player.yBodyRot, player.yHeadRot,
                        player.getXRot(), player.walkAnimation.position(), player.walkAnimation.speed(), now));
            }
        }
        TRAILS.keySet().retainAll(present);
        TRAILS.values().forEach(trail -> trail.removeIf(image -> now - image.tick() > LINGER_TICKS));
        TRAILS.values().removeIf(Deque::isEmpty);
    }

    @SubscribeEvent
    static void drawTrail(RenderLivingEvent.Post<?, ?, ?> event) {
        if (drawingAfterimage || !(event.getRenderState() instanceof AvatarRenderState playerState)) {
            return;
        }
        Deque<Afterimage> trail = TRAILS.get(playerState.id);
        Minecraft minecraft = Minecraft.getInstance();
        if (trail == null || minecraft.level == null || !(minecraft.level.getEntity(playerState.id) instanceof AbstractClientPlayer player)) {
            return;
        }
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        CameraRenderState camera = minecraft.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        PoseStack poseStack = event.getPoseStack();
        drawingAfterimage = true;
        try {
            for (Afterimage image : trail) {
                if (!(dispatcher.extractEntity(player, event.getPartialTick()) instanceof AvatarRenderState ghost)) {
                    continue;
                }
                // Posed as the player was at that moment, and see-through
                ghost.bodyRot = image.bodyRot();
                ghost.yRot = image.headRot() - image.bodyRot();
                ghost.xRot = image.xRot();
                ghost.walkAnimationPos = image.walkPosition();
                ghost.walkAnimationSpeed = image.walkSpeed();
                ghost.isInvisible = true;
                ghost.isInvisibleToPlayer = false;
                ghost.nameTag = null;
                ghost.shadowPieces.clear();
                poseStack.pushPose();
                poseStack.translate(image.position().x - playerState.x, image.position().y - playerState.y, image.position().z - playerState.z);
                dispatcher.getRenderer(ghost).submit(ghost, poseStack, event.getSubmitNodeCollector(), camera);
                poseStack.popPose();
            }
        } finally {
            drawingAfterimage = false;
        }
    }
}

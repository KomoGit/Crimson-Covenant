package com.zestudio.crimsoncovenant.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.entity.WhipLash;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/// A whip's lash: a plaited leather cord from the wielder's hand, thrown out along their aim in a rolling arc that
/// straightens as it reaches full length (where it cracks), then drawn back in, sagging as it comes; tipped with
/// silver
public class WhipLashRenderer extends EntityRenderer<WhipLash, WhipLashRenderer.State> {
    private static final Identifier LEATHER = CrimsonCovenant.id("textures/entity/whip_lash.png");
    private static final int POINTS = 20;
    private static final int SIDES = 5;
    /// Out over the first part of the lash, held a moment at full length, then drawn back
    private static final float OUT = 0.4F;
    private static final float HOLD = 0.55F;
    private static final int LEATHER_COLOR = 0xFFFFFFFF;
    private static final int SILVER_COLOR = 0xFFD8E0E8;

    public static class State extends EntityRenderState {
        public Vec3[] points = new Vec3[0];
    }

    public WhipLashRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        Vec3[] points = state.points;
        if (points.length < 2) {
            return;
        }
        int light = state.lightCoords;
        submitNodeCollector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(LEATHER), (pose, buffer) -> {
            double[] radii = new double[points.length];
            for (int i = 0; i < radii.length; i++) {
                radii[i] = Mth.lerp((double) i / (radii.length - 1), 0.045, 0.015);
            }
            SpectralShapes.tube(pose, buffer, points, radii, SIDES, LEATHER_COLOR, 0.5, light);
            // The silver tip
            Vec3 end = points[points.length - 1];
            Vec3 along = end.subtract(points[points.length - 2]).normalize();
            SpectralShapes.tube(pose, buffer, new Vec3[] {end.subtract(along.scale(0.12)), end.add(along.scale(0.05))}, new double[] {0.04, 0.02}, SIDES,
                    SILVER_COLOR, 0.0, light);
        });
        super.submit(state, poseStack, submitNodeCollector, camera);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(WhipLash entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        LivingEntity wielder = entity.getWielder();
        if (wielder == null) {
            state.points = new Vec3[0];
            return;
        }
        // How far out it is: thrown out, held (a moment, or for as long as a tether's taut), drawn back
        float age = entity.tickCount + partialTicks;
        float lifetime = Math.max(entity.getLifetime(), 1);
        float outTicks = WhipLash.LASH_TICKS * OUT;
        float backTicks = WhipLash.LASH_TICKS * (1.0F - HOLD);
        float backFrom = Math.max(outTicks, lifetime - backTicks);
        float out = age < outTicks ? smooth(age / outTicks) : age < backFrom ? 1.0F : 1.0F - smooth((age - backFrom) / backTicks);
        float progress = Mth.clamp(age / lifetime, 0.0F, 1.0F);
        boolean drawingBack = age >= backFrom;
        Vec3 view = wielder.getViewVector(partialTicks);
        Vec3 tip = entity.tipOf(wielder, partialTicks);
        Vec3 hand;
        Minecraft minecraft = Minecraft.getInstance();
        if (wielder == minecraft.getCameraEntity() && minecraft.options.getCameraType().isFirstPerson()) {
            // In your own eyes: from the corner of your view, where your hand is
            Vec3 side = view.cross(new Vec3(0.0, 1.0, 0.0));
            side = side.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
            hand = wielder.getEyePosition(partialTicks).add(side.scale(0.35)).add(side.cross(view).normalize().scale(-0.35));
        } else {
            // Seen from outside: from the wielder's right hand, raised out in front of them
            float yaw = Mth.lerp(partialTicks, wielder.yBodyRotO, wielder.yBodyRot) * Mth.DEG_TO_RAD;
            Vec3 bodyRight = new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
            Vec3 bodyForward = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
            hand = wielder.getPosition(partialTicks).add(0.0, wielder.getBbHeight() * 0.7, 0.0).add(bodyRight.scale(0.4)).add(bodyForward.scale(0.3));
        }
        // Out from the hand to where it cracks
        Vec3 toTip = tip.subtract(hand);
        double reach = toTip.length();
        Vec3 look = reach < 1.0E-4 ? view : toTip.scale(1.0 / reach);
        Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
        right = right.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
        Vec3 up = right.cross(look).normalize();
        Vec3 origin = new Vec3(state.x, state.y, state.z);
        double length = reach * Math.max(out, 0.05F);
        Vec3[] points = new Vec3[POINTS];
        for (int i = 0; i < POINTS; i++) {
            double s = (double) i / (POINTS - 1);
            Vec3 at = hand.add(look.scale(length * s));
            // Rolling out in an arc over the top that straightens at full length; sagging as it's drawn back
            double arc = Math.sin(Math.PI * s) * 0.7 * (1.0 - out);
            double roll = Math.sin(s * 9.0 - progress * 20.0) * 0.08 * (1.0 - s * 0.5) * (1.0 - out * 0.6);
            at = at.add(up.scale(drawingBack ? -arc * 0.8 : arc)).add(right.scale(roll));
            points[i] = at.subtract(origin);
        }
        state.points = points;
    }

    private static float smooth(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    @Override
    protected boolean affectedByCulling(WhipLash entity) {
        return false;
    }
}

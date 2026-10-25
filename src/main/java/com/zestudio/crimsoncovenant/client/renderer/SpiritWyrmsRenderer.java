package com.zestudio.crimsoncovenant.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zestudio.crimsoncovenant.entity.SpiritWyrms;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/// The Wrath of the Twin Wyrms: two long spirit dragons of glowing light-blue light, coiling round each other along
/// their line of flight (the same coil the server strikes along, SpiritWyrms.coil). Each has a scaled, see-through body
/// tapering to its tail, a crest of fins down its back, a horned head with a snapping jaw, burning eyes and long
/// whiskers streaming back, all in a soft glow. They uncoil out of the bow as they go, and fade at the end.
public class SpiritWyrmsRenderer extends EntityRenderer<SpiritWyrms, SpiritWyrmsRenderer.State> {
    private static final double BODY_LENGTH = SpiritWyrms.BODY_LENGTH;
    private static final int BODY_POINTS = 64;
    private static final int SIDES = 8;
    /// One tile of scales every this many blocks along the body
    private static final double SCALE_TILE = 0.9;
    private static final double FIN_SPACING = 0.6;
    /// Wyrm 0 in the curses' pale blue, wyrm 1 a deeper blue
    private static final int[] TINTS = {0xEEFFFFFF, 0xEEA8C8FF};
    private static final int GLOW = 0x70FFFFFF;
    private static final int EYES = 0xFFFFFFFF;

    public static class State extends EntityRenderState {
        public Vec3 heading = new Vec3(0.0, 0.0, 1.0);
    }

    public SpiritWyrmsRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        float age = state.ageInTicks;
        double fade = Math.min(Mth.clamp(age / 3.0, 0.0, 1.0), Mth.clamp((SpiritWyrms.LIFETIME - age) / 8.0, 0.0, 1.0));
        // As long as they've flown, out of the bow, up to their full length
        double length = Math.min(BODY_LENGTH, age * SpiritWyrms.SPEED + 0.3);
        Wyrm[] wyrms = {wyrm(state.heading, 0, age, length), wyrm(state.heading, 1, age, length)};
        submitNodeCollector.submitCustomGeometry(poseStack, SpectralShapes.renderType(SpectralShapes.GLOW), (pose, buffer) -> {
            int glow = SpectralShapes.faded(GLOW, fade);
            int eyes = SpectralShapes.faded(EYES, fade);
            for (Wyrm wyrm : wyrms) {
                SpectralShapes.tube(pose, buffer, wyrm.body, scaled(wyrm.radii, 1.9), SIDES, glow, 0.0);
                SpectralShapes.tube(pose, buffer, wyrm.skull, scaled(wyrm.skullRadii, 1.7), SIDES, glow, 0.0);
                for (Vec3[] eye : wyrm.eyes) {
                    // Twice over: they burn
                    SpectralShapes.tube(pose, buffer, eye, new double[] {0.075, 0.075}, 6, eyes, 0.0);
                    SpectralShapes.tube(pose, buffer, eye, new double[] {0.12, 0.12}, 6, glow, 0.0);
                }
            }
        });
        submitNodeCollector.submitCustomGeometry(poseStack, SpectralShapes.renderType(SpectralShapes.WYRM_SCALES), (pose, buffer) -> {
            for (int i = 0; i < wyrms.length; i++) {
                Wyrm wyrm = wyrms[i];
                int tint = SpectralShapes.faded(TINTS[i], fade);
                SpectralShapes.tube(pose, buffer, wyrm.body, wyrm.radii, SIDES, tint, SCALE_TILE);
                SpectralShapes.tube(pose, buffer, wyrm.skull, wyrm.skullRadii, SIDES, tint, SCALE_TILE * 0.5);
                SpectralShapes.tube(pose, buffer, wyrm.jaw, new double[] {0.2, 0.14, 0.06}, SIDES, tint, SCALE_TILE * 0.5);
                for (Vec3[] horn : wyrm.horns) {
                    SpectralShapes.tube(pose, buffer, horn, new double[] {0.065, 0.04, 0.008}, 5, tint, SCALE_TILE);
                }
                for (Vec3[] whisker : wyrm.whiskers) {
                    double[] radii = new double[whisker.length];
                    for (int j = 0; j < radii.length; j++) {
                        radii[j] = Mth.lerp((double) j / (radii.length - 1), 0.028, 0.006);
                    }
                    SpectralShapes.tube(pose, buffer, whisker, radii, 4, tint, SCALE_TILE);
                }
                for (Vec3[] fin : wyrm.fins) {
                    fin(pose, buffer, fin, tint);
                }
            }
        });
        super.submit(state, poseStack, submitNodeCollector, camera);
    }

    private record Wyrm(Vec3[] body, double[] radii, Vec3[] skull, double[] skullRadii, Vec3[] jaw, Vec3[][] horns, Vec3[][] whiskers,
            Vec3[][] eyes, Vec3[][] fins) {}

    // Where the wyrm's spine runs `back` blocks behind the front, relative to the entity
    private static Vec3 spine(Vec3 heading, int index, double back, double age) {
        return heading.scale(-back).add(SpiritWyrms.coil(heading, index, back, age));
    }

    // Thickest a little behind the head, tapering to a wisp at the tail
    private static double radiusAt(double back) {
        double t = back / BODY_LENGTH;
        return t < 0.15 ? Mth.lerp(t / 0.15, 0.26, 0.36) : Mth.lerp((t - 0.15) / 0.85, 0.36, 0.03);
    }

    private static Wyrm wyrm(Vec3 heading, int index, double age, double length) {
        Vec3[] body = new Vec3[BODY_POINTS];
        double[] radii = new double[BODY_POINTS];
        for (int i = 0; i < BODY_POINTS; i++) {
            double back = length * i / (BODY_POINTS - 1);
            body[i] = spine(heading, index, back, age);
            radii[i] = radiusAt(back);
        }
        // The head's own frame: facing where the coil's carrying it, kept upright
        Vec3 head = body[0];
        Vec3 forward = head.subtract(spine(heading, index, 0.25, age)).normalize();
        Vec3 up = new Vec3(0.0, 1.0, 0.0).subtract(forward.scale(forward.y));
        up = up.lengthSqr() < 1.0E-4 ? SpectralShapes.perpendicular(forward) : up.normalize();
        Vec3 side = forward.cross(up).normalize();

        Vec3[] skull = {at(head, forward, up, side, -0.1, 0.0, 0.0), at(head, forward, up, side, 0.25, 0.06, 0.0),
                at(head, forward, up, side, 0.6, 0.02, 0.0), at(head, forward, up, side, 0.85, -0.02, 0.0)};
        double[] skullRadii = {0.3, 0.27, 0.18, 0.08};
        // The jaw snaps open and shut as it flies
        double gape = 0.25 + 0.18 * Math.sin(age * 0.5 + index * 1.7);
        Vec3 jawForward = forward.scale(Math.cos(gape)).subtract(up.scale(Math.sin(gape)));
        Vec3 jawRoot = head.subtract(up.scale(0.12));
        Vec3[] jaw = {jawRoot, jawRoot.add(jawForward.scale(0.35)), jawRoot.add(jawForward.scale(0.7))};

        Vec3[][] horns = new Vec3[2][];
        Vec3[][] whiskers = new Vec3[2][];
        Vec3[][] eyes = new Vec3[2][];
        for (int s = 0; s < 2; s++) {
            double sign = s == 0 ? 1.0 : -1.0;
            Vec3 hornRoot = at(head, forward, up, side, -0.05, 0.2, 0.15 * sign);
            horns[s] = new Vec3[] {hornRoot, hornRoot.add(forward.scale(-0.3)).add(up.scale(0.2)).add(side.scale(0.05 * sign)),
                    hornRoot.add(forward.scale(-0.65)).add(up.scale(0.28)).add(side.scale(0.12 * sign))};
            // Streaming back from the snout, rippling
            Vec3 whiskerRoot = at(head, forward, up, side, 0.7, 0.0, 0.12 * sign);
            whiskers[s] = new Vec3[9];
            for (int j = 0; j < 9; j++) {
                whiskers[s][j] = whiskerRoot.add(forward.scale(-0.22 * j)).add(side.scale(0.04 * j * sign))
                        .add(up.scale(0.12 * Math.sin(age * 0.4 - j * 0.7 + s)));
            }
            Vec3 eye = at(head, forward, up, side, 0.32, 0.16, 0.2 * sign);
            eyes[s] = new Vec3[] {eye, eye.add(forward.scale(0.08))};
        }

        // A crest of fins down its back (its back being the outside of the coil)
        int finCount = (int) Math.max(0, (length - 0.9) / FIN_SPACING);
        Vec3[][] fins = new Vec3[finCount][];
        for (int f = 0; f < finCount; f++) {
            double back = 0.6 + f * FIN_SPACING;
            double size = 0.24 * (1.0 - back / BODY_LENGTH) + 0.04;
            fins[f] = new Vec3[] {crest(heading, index, back, age, 0.0), crest(heading, index, back + 0.1, age, size),
                    crest(heading, index, back + 0.35, age, 0.0)};
        }
        return new Wyrm(body, radii, skull, skullRadii, jaw, horns, whiskers, eyes, fins);
    }

    // A point on the head, so far forward, up and to the side
    private static Vec3 at(Vec3 head, Vec3 forward, Vec3 up, Vec3 side, double ahead, double above, double across) {
        return head.add(forward.scale(ahead)).add(up.scale(above)).add(side.scale(across));
    }

    // On the wyrm's back, `rise` above its scales
    private static Vec3 crest(Vec3 heading, int index, double back, double age, double rise) {
        Vec3 out = SpiritWyrms.coil(heading, index, back, age).normalize();
        return spine(heading, index, back, age).add(out.scale(radiusAt(back) * 0.9 + rise));
    }

    // A fin: a triangle, drawn as a quad with its tip twice
    private static void fin(PoseStack.Pose pose, VertexConsumer buffer, Vec3[] fin, int tint) {
        SpectralShapes.vertex(pose, buffer, fin[0], tint, 0.0F, 1.0F);
        SpectralShapes.vertex(pose, buffer, fin[1], tint, 0.3F, 0.0F);
        SpectralShapes.vertex(pose, buffer, fin[1], tint, 0.3F, 0.0F);
        SpectralShapes.vertex(pose, buffer, fin[2], tint, 1.0F, 1.0F);
    }

    private static double[] scaled(double[] radii, double by) {
        double[] out = new double[radii.length];
        for (int i = 0; i < radii.length; i++) {
            out[i] = radii[i] * by;
        }
        return out;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(SpiritWyrms entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.heading = entity.getHeading();
    }

    @Override
    protected boolean affectedByCulling(SpiritWyrms entity) {
        return false;
    }
}

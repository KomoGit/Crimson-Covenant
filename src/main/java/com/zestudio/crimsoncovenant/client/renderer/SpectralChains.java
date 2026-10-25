package com.zestudio.crimsoncovenant.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.registry.ModAttachments;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

/// The Shackles of the Damned, seen: light-blue spirit chains bound round a chained creature in square loops (two or
/// three, each turned a little from the last, the links slowly creeping round), staked into the ground from the
/// corners of the lowest, glowing, and see-through. Drawn for any creature the server says is chained
/// (ModAttachments.ENCHAINED_LOOK).
@EventBusSubscriber(modid = CrimsonCovenant.MODID, value = Dist.CLIENT)
public final class SpectralChains {
    private static final ContextKey<Boolean> CHAINED = new ContextKey<>(CrimsonCovenant.id("enchained"));
    /// How thick a chain is (half of it), and how long one tile of two links is: four times as long as it's thick
    private static final double HALF_THICKNESS = 0.07;
    private static final double TILE_LENGTH = HALF_THICKNESS * 8.0;
    private static final int CHAIN_COLOR = 0xEEFFFFFF;
    private static final int GLOW_COLOR = 0xFFFFFFFF;

    private SpectralChains() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    @SubscribeEvent
    static void registerRenderStateModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier((Class) LivingEntityRenderer.class, (entity, state) ->
                ((EntityRenderState) state).setRenderData(CHAINED, ((LivingEntity) entity).getData(ModAttachments.ENCHAINED_LOOK)));
    }

    @SubscribeEvent
    static void drawChains(RenderLivingEvent.Post<?, ?, ?> event) {
        LivingEntityRenderState state = event.getRenderState();
        if (!state.getRenderDataOrDefault(CHAINED, false)) {
            return;
        }
        PoseStack poseStack = event.getPoseStack();
        SubmitNodeCollector collector = event.getSubmitNodeCollector();
        float age = state.ageInTicks;
        double width = state.boundingBoxWidth;
        double height = state.boundingBoxHeight;
        // Drawn a touch tighter and looser by turns, as if straining against what they hold
        double half = (width * 0.5 + 0.08) * (1.0 + 0.03 * Math.sin(age * 0.25));
        float pulse = 0.85F + 0.15F * (float) Math.sin(age * 0.18);
        int loops = height > 1.2 ? 3 : 2;
        Edge[][] built = new Edge[loops][];
        for (int i = 0; i < loops; i++) {
            double heightFraction = loops == 3 ? 0.22 + 0.27 * i : 0.3 + 0.35 * i;
            // Each loop turned a little from the last, its links creeping round its own way
            double turn = Math.toRadians(25.0 * i);
            float creep = age * 0.02F * (i % 2 == 0 ? 1 : -1);
            built[i] = square(height * heightFraction, half, turn, creep);
        }
        Edge[] lowest = built[0];
        Vec3 up = new Vec3(0.0, 1.0, 0.0);
        collector.submitCustomGeometry(poseStack, SpectralShapes.renderType(SpectralShapes.GLOW), (pose, buffer) -> {
            int glow = SpectralShapes.faded(GLOW_COLOR, 0.55 * pulse);
            for (Edge[] loop : built) {
                for (Edge edge : loop) {
                    SpectralShapes.strip(pose, buffer, edge.line(), new Vec3[] {up, up}, HALF_THICKNESS * 3.2, glow, 0.0F, 1.0, 0.0F, 1.0F);
                }
            }
        });
        collector.submitCustomGeometry(poseStack, SpectralShapes.renderType(SpectralShapes.CHAIN), (pose, buffer) -> {
            int chain = SpectralShapes.faded(CHAIN_COLOR, pulse);
            for (Edge[] loop : built) {
                for (Edge edge : loop) {
                    // Two planes crossed, like the chain block: the links turn one way, then the other
                    SpectralShapes.strip(pose, buffer, edge.line(), new Vec3[] {up, up}, HALF_THICKNESS, chain, edge.creep(), TILE_LENGTH, 0.0F, 0.5F);
                    SpectralShapes.strip(pose, buffer, edge.line(), new Vec3[] {edge.out(), edge.out()}, HALF_THICKNESS, chain, edge.creep(), TILE_LENGTH, 0.5F, 1.0F);
                }
            }
            // Staked down: from each corner of the lowest loop, out to the ground
            for (Edge edge : lowest) {
                Vec3 start = edge.corner();
                Vec3 outward = new Vec3(start.x, 0.0, start.z).normalize();
                Vec3 end = outward.scale(start.horizontalDistance() + 0.55).add(0.0, -0.05, 0.0);
                Vec3[] line = {start, end};
                Vec3 along = end.subtract(start).normalize();
                Vec3 side = along.cross(outward).normalize();
                Vec3 face = along.cross(side).normalize();
                SpectralShapes.strip(pose, buffer, line, new Vec3[] {side, side}, HALF_THICKNESS, chain, 0.0F, TILE_LENGTH, 0.0F, 0.5F);
                SpectralShapes.strip(pose, buffer, line, new Vec3[] {face, face}, HALF_THICKNESS, chain, 0.0F, TILE_LENGTH, 0.5F, 1.0F);
            }
        });
    }

    /// One side of a square loop of chain: from one corner to the next (run on a little past both, so the corners
    /// close), the way out from it, and how far its links have crept
    private record Edge(Vec3[] line, Vec3 corner, Vec3 out, float creep) {}

    // A level square of chain round the creature, `half` blocks from its middle to each side, turned `turn` round
    private static Edge[] square(double y, double half, double turn, float creep) {
        Edge[] edges = new Edge[4];
        for (int side = 0; side < 4; side++) {
            double facing = turn + side * Math.PI / 2.0;
            Vec3 out = new Vec3(Math.cos(facing), 0.0, Math.sin(facing));
            Vec3 along = new Vec3(-out.z, 0.0, out.x);
            Vec3 middle = out.scale(half).add(0.0, y, 0.0);
            double reach = half + HALF_THICKNESS;
            Vec3 from = middle.subtract(along.scale(reach));
            Vec3 to = middle.add(along.scale(reach));
            edges[side] = new Edge(new Vec3[] {from, to}, middle.add(along.scale(half)), out, creep);
        }
        return edges;
    }
}

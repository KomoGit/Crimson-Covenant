package com.zestudio.crimsoncovenant.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zestudio.crimsoncovenant.CrimsonCovenant;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;

/// Shapes drawn in the curses' spirit-light: see-through and glowing, never shaded by the world's light. Strips (for
/// chains seen as two crossed planes, like the chain block) and tubes (for spirit wyrms' bodies) along a line of points,
/// in the pose's own coordinates.
public final class SpectralShapes {
    /// Ghostly chain links: two rows, the top one plane of a chain, the bottom the plane crossed with it
    public static final Identifier CHAIN = CrimsonCovenant.id("textures/entity/spectral_chain.png");
    /// A soft glow, bright along its middle row
    public static final Identifier GLOW = CrimsonCovenant.id("textures/entity/spectral_glow.png");
    public static final Identifier WYRM_SCALES = CrimsonCovenant.id("textures/entity/spirit_wyrm.png");
    private static final int FULL_BRIGHT = LightCoordsUtil.FULL_BRIGHT;

    private SpectralShapes() {}

    public static RenderType renderType(Identifier texture) {
        return RenderTypes.entityTranslucentEmissive(texture);
    }

    /// One corner. Every face is lit as if facing straight up, so nothing on a spirit is ever in shadow.
    public static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Vec3 at, int color, float u, float v) {
        vertex(pose, buffer, at, color, u, v, FULL_BRIGHT);
    }

    /// One corner, lit by `light` (for solid things drawn the same way, like a whip's lash)
    public static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Vec3 at, int color, float u, float v, int light) {
        buffer.addVertex(pose, (float) at.x, (float) at.y, (float) at.z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    /// A strip along `points`, `halfWidths[i]` either side of each along `across[i]`. The texture runs along it, a tile
    /// every `tileLength` blocks, starting at `uStart`, and from `vTop` to `vBottom` across it.
    public static void strip(PoseStack.Pose pose, VertexConsumer buffer, Vec3[] points, Vec3[] across, double halfWidth, int color,
            float uStart, double tileLength, float vTop, float vBottom) {
        double travelled = 0.0;
        for (int i = 0; i + 1 < points.length; i++) {
            double length = points[i].distanceTo(points[i + 1]);
            float u0 = uStart + (float) (travelled / tileLength);
            float u1 = uStart + (float) ((travelled + length) / tileLength);
            Vec3 a = across[i].scale(halfWidth);
            Vec3 b = across[i + 1].scale(halfWidth);
            vertex(pose, buffer, points[i].add(a), color, u0, vTop);
            vertex(pose, buffer, points[i + 1].add(b), color, u1, vTop);
            vertex(pose, buffer, points[i + 1].subtract(b), color, u1, vBottom);
            vertex(pose, buffer, points[i].subtract(a), color, u0, vBottom);
            travelled += length;
        }
    }

    /// A tube through `points`, `radii[i]` round each, `sides` faces round. The texture wraps round it once, and runs
    /// along it a tile every `tileLength` blocks; or, with `tileLength` 0, the whole tube takes the texture's middle
    /// (for a glow of even strength).
    public static void tube(PoseStack.Pose pose, VertexConsumer buffer, Vec3[] points, double[] radii, int sides, int color, double tileLength) {
        tube(pose, buffer, points, radii, sides, color, tileLength, FULL_BRIGHT);
    }

    /// The same tube, lit by `light`
    public static void tube(PoseStack.Pose pose, VertexConsumer buffer, Vec3[] points, double[] radii, int sides, int color, double tileLength, int light) {
        int count = points.length;
        if (count < 2) {
            return;
        }
        Vec3[][] rings = new Vec3[count][sides + 1];
        // A frame carried along the tube, turning as little as it can, so the faces don't twist
        Vec3 normal = null;
        for (int i = 0; i < count; i++) {
            Vec3 tangent = points[Math.min(i + 1, count - 1)].subtract(points[Math.max(i - 1, 0)]);
            tangent = tangent.lengthSqr() < 1.0E-10 ? new Vec3(0.0, 0.0, 1.0) : tangent.normalize();
            if (normal == null) {
                normal = Math.abs(tangent.y) < 0.9 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0);
            }
            normal = normal.subtract(tangent.scale(normal.dot(tangent)));
            normal = normal.lengthSqr() < 1.0E-10 ? perpendicular(tangent) : normal.normalize();
            Vec3 binormal = tangent.cross(normal);
            for (int j = 0; j <= sides; j++) {
                double angle = Math.PI * 2.0 * j / sides;
                rings[i][j] = points[i].add(normal.scale(Math.cos(angle) * radii[i])).add(binormal.scale(Math.sin(angle) * radii[i]));
            }
        }
        double travelled = 0.0;
        for (int i = 0; i + 1 < count; i++) {
            double length = points[i].distanceTo(points[i + 1]);
            float v0 = tileLength > 0.0 ? (float) (travelled / tileLength) : 0.5F;
            float v1 = tileLength > 0.0 ? (float) ((travelled + length) / tileLength) : 0.5F;
            for (int j = 0; j < sides; j++) {
                float u0 = tileLength > 0.0 ? (float) j / sides : 0.5F;
                float u1 = tileLength > 0.0 ? (float) (j + 1) / sides : 0.5F;
                vertex(pose, buffer, rings[i][j], color, u0, v0, light);
                vertex(pose, buffer, rings[i][j + 1], color, u1, v0, light);
                vertex(pose, buffer, rings[i + 1][j + 1], color, u1, v1, light);
                vertex(pose, buffer, rings[i + 1][j], color, u0, v1, light);
            }
            travelled += length;
        }
    }

    public static Vec3 perpendicular(Vec3 direction) {
        Vec3 other = Math.abs(direction.y) < 0.9 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0);
        return direction.cross(other).normalize();
    }

    /// `color` with its alpha scaled by `fade` (0 to 1)
    public static int faded(int color, double fade) {
        int alpha = (int) Math.round((color >>> 24) * Math.clamp(fade, 0.0, 1.0));
        return (alpha << 24) | (color & 0xFFFFFF);
    }
}

package com.zestudio.crimsoncovenant.client.renderer;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/// Silvered weapons are drawn from their own textures, silvered as the atlas is built: a bright silver edge just
/// inside the blade's dark outline, and a faint silver sheen over the rest of the head, so the weapon still looks
/// like iron, gold or diamond underneath. The handle is left alone. Each texture's silvered copy is named after it
/// with "_silvered" on the end (minecraft:item/iron_sword_silvered), and the item definitions switch to it when
/// the weapon is silvered (see assets/minecraft/items).
///
/// The handle is found by colour: anything coloured like a stick. Netherite handles aren't, so a weapon can name
/// another texture of the same shape to take the handle from (the iron one).
public record SilveredWeaponSprites(List<Weapon> weapons) implements SpriteSource {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String SUFFIX = "_silvered";
    private static final Identifier STICK = Identifier.withDefaultNamespace("item/stick");
    /// Silver from dark to bright, as ARGB
    private static final int[] SILVER = {0xFF2E323E, 0xFF767C8C, 0xFFBAC0CE, 0xFFF4F7FC};
    /// How much of the head takes on silver: the edge almost all the way, the rest only a sheen
    private static final float EDGE = 0.9F;
    private static final float SHEEN = 0.3F;

    public record Weapon(Identifier texture, Optional<Identifier> handle) {
        public static final Codec<Weapon> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("texture").forGetter(Weapon::texture),
                Identifier.CODEC.optionalFieldOf("handle").forGetter(Weapon::handle))
                .apply(i, Weapon::new));
    }

    public static final MapCodec<SilveredWeaponSprites> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Weapon.CODEC.listOf().fieldOf("weapons").forGetter(SilveredWeaponSprites::weapons))
            .apply(i, SilveredWeaponSprites::new));

    @Override
    public void run(ResourceManager resourceManager, SpriteSource.Output output) {
        for (Weapon weapon : this.weapons) {
            Optional<Resource> texture = resourceManager.getResource(TEXTURE_ID_CONVERTER.idToFile(weapon.texture()));
            if (texture.isEmpty()) {
                LOGGER.warn("Unable to find weapon texture {} to silver", weapon.texture());
                continue;
            }
            Optional<Resource> handle = weapon.handle().flatMap(id -> resourceManager.getResource(TEXTURE_ID_CONVERTER.idToFile(id)));
            Optional<Resource> stick = resourceManager.getResource(TEXTURE_ID_CONVERTER.idToFile(STICK));
            Identifier silveredId = weapon.texture().withSuffix(SUFFIX);
            output.add(silveredId, (SpriteSource.DiscardableLoader) loader -> load(silveredId, texture.get(), handle.orElse(null), stick.orElse(null)));
        }
    }

    private static @Nullable SpriteContents load(Identifier id, Resource texture, @Nullable Resource handle, @Nullable Resource stick) {
        try {
            NativeImage image = read(texture);
            Set<Integer> stickColours = new HashSet<>();
            if (stick != null) {
                try (NativeImage stickImage = read(stick)) {
                    forEachOpaque(stickImage, (x, y, colour) -> stickColours.add(colour & 0xFFFFFF));
                }
            }
            boolean[] isHandle = new boolean[image.getWidth() * image.getHeight()];
            forEachOpaque(image, (x, y, colour) -> isHandle[x + y * image.getWidth()] = stickColours.contains(colour & 0xFFFFFF));
            if (handle != null) {
                try (NativeImage handleImage = read(handle)) {
                    if (handleImage.getWidth() == image.getWidth() && handleImage.getHeight() == image.getHeight()) {
                        forEachOpaque(handleImage, (x, y, colour) -> isHandle[x + y * image.getWidth()] |= stickColours.contains(colour & 0xFFFFFF));
                    }
                }
            }
            silver(image, isHandle);
            return new SpriteContents(id, new FrameSize(image.getWidth(), image.getHeight()), image);
        } catch (IOException e) {
            LOGGER.error("Unable to silver {}", id, e);
            return null;
        }
    }

    private static NativeImage read(Resource resource) throws IOException {
        try (InputStream stream = resource.open()) {
            return NativeImage.read(stream);
        }
    }

    private interface PixelVisitor {
        void visit(int x, int y, int colour);
    }

    private static void forEachOpaque(NativeImage image, PixelVisitor visitor) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int colour = image.getPixel(x, y);
                if (ARGB.alpha(colour) > 0) {
                    visitor.visit(x, y, colour);
                }
            }
        }
    }

    private static void silver(NativeImage image, boolean[] isHandle) {
        int width = image.getWidth();
        int height = image.getHeight();
        // The head's shading runs from its darkest pixel to its brightest; silver follows the same shading
        float darkest = 1.0F;
        float brightest = 0.0F;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (isHead(image, isHandle, x, y)) {
                    float lightness = lightness(image.getPixel(x, y));
                    darkest = Math.min(darkest, lightness);
                    brightest = Math.max(brightest, lightness);
                }
            }
        }
        if (darkest > brightest) {
            return;
        }
        int[] silvered = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int colour = image.getPixel(x, y);
                silvered[x + y * width] = colour;
                if (!isHead(image, isHandle, x, y)) {
                    continue;
                }
                float shade = brightest > darkest ? (lightness(colour) - darkest) / (brightest - darkest) : 0.5F;
                if (onOutline(image, x, y)) {
                    // The dark outline stays, so the weapon still stands out against the inventory slot
                    silvered[x + y * width] = ARGB.color(ARGB.alpha(colour), lerp(colour, silverAt(shade), SHEEN));
                } else if (alongEdge(image, isHandle, x, y)) {
                    silvered[x + y * width] = ARGB.color(ARGB.alpha(colour), lerp(colour, silverAt(0.55F + 0.45F * shade), EDGE));
                } else {
                    silvered[x + y * width] = ARGB.color(ARGB.alpha(colour), lerp(colour, silverAt(shade), SHEEN));
                }
            }
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setPixel(x, y, silvered[x + y * width]);
            }
        }
    }

    private static boolean isHead(NativeImage image, boolean[] isHandle, int x, int y) {
        return opaque(image, x, y) && !isHandle[x + y * image.getWidth()];
    }

    private static boolean opaque(NativeImage image, int x, int y) {
        return x >= 0 && y >= 0 && x < image.getWidth() && y < image.getHeight() && ARGB.alpha(image.getPixel(x, y)) > 0;
    }

    /// Touching the empty space around the weapon
    private static boolean onOutline(NativeImage image, int x, int y) {
        return !opaque(image, x + 1, y) || !opaque(image, x - 1, y) || !opaque(image, x, y + 1) || !opaque(image, x, y - 1);
    }

    /// Just inside the outline: next to an outline pixel of the head
    private static boolean alongEdge(NativeImage image, boolean[] isHandle, int x, int y) {
        int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] side : sides) {
            int nx = x + side[0];
            int ny = y + side[1];
            if (opaque(image, nx, ny) && !isHandle[nx + ny * image.getWidth()] && onOutline(image, nx, ny)) {
                return true;
            }
        }
        return false;
    }

    private static float lightness(int colour) {
        return (0.299F * ARGB.red(colour) + 0.587F * ARGB.green(colour) + 0.114F * ARGB.blue(colour)) / 255.0F;
    }

    /// The silver ramp at a point from 0 (dark) to 1 (bright)
    private static int silverAt(float shade) {
        float position = Mth.clamp(shade, 0.0F, 1.0F) * (SILVER.length - 1);
        int index = Math.min((int) position, SILVER.length - 2);
        return lerp(SILVER[index], SILVER[index + 1], position - index);
    }

    /// Blends two colours' RGB, dropping alpha
    private static int lerp(int from, int to, float amount) {
        return ARGB.color(0,
                Math.round(Mth.lerp(amount, ARGB.red(from), ARGB.red(to))),
                Math.round(Mth.lerp(amount, ARGB.green(from), ARGB.green(to))),
                Math.round(Mth.lerp(amount, ARGB.blue(from), ARGB.blue(to)))) & 0xFFFFFF;
    }

    @Override
    public MapCodec<SilveredWeaponSprites> codec() {
        return MAP_CODEC;
    }
}

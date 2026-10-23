package com.zestudio.crimsoncovenant.client.renderer;

import java.util.function.Consumer;

import org.joml.Vector3fc;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.client.ModModelLayers;
import com.zestudio.crimsoncovenant.client.model.BloodSpearModel;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;

/// The Blood Spear in hand: its own model (see BloodSpearModel), the same as the thrown spear. Plugged into the item model
/// (assets/crimsoncovenant/items/blood_spear.json) the way vanilla's trident renderer is, so it gets the trident's
/// poses for holding and for drawing back to throw.
public class BloodSpearItemRenderer implements NoDataSpecialModelRenderer {
    public static final Identifier ID = CrimsonCovenant.id("blood_spear");
    private final BloodSpearModel model;

    public BloodSpearItemRenderer(BloodSpearModel model) {
        this.model = model;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, int overlayCoords, boolean hasFoil, int outlineColor) {
        submitNodeCollector.submitModel(this.model, Unit.INSTANCE, poseStack, BloodSpearModel.TEXTURE, lightCoords, overlayCoords, outlineColor);
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        this.model.root().getExtentsForGui(new PoseStack(), output);
    }

    public record Unbaked() implements NoDataSpecialModelRenderer.Unbaked {
        public static final MapCodec<BloodSpearItemRenderer.Unbaked> MAP_CODEC = MapCodec.unit(new BloodSpearItemRenderer.Unbaked());

        @Override
        public MapCodec<BloodSpearItemRenderer.Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public BloodSpearItemRenderer bake(SpecialModelRenderer.BakingContext context) {
            return new BloodSpearItemRenderer(new BloodSpearModel(context.entityModelSet().bakeLayer(ModModelLayers.BLOOD_SPEAR)));
        }
    }
}

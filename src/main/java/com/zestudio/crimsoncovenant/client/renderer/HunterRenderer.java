package com.zestudio.crimsoncovenant.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.client.ModModelLayers;
import com.zestudio.crimsoncovenant.entity.Hunter;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;

// Hunters use the regular player model, so the texture is a standard 64x64 player skin.
// Also renders the Master Hunter, with its own texture and a slightly larger size.
public class HunterRenderer extends HumanoidMobRenderer<Hunter, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
    public static final Identifier HUNTER_TEXTURE = CrimsonCovenant.id("textures/entity/hunter/hunter.png");
    public static final Identifier MASTER_HUNTER_TEXTURE = CrimsonCovenant.id("textures/entity/hunter/master_hunter.png");

    private final Identifier texture;
    private final float scale;

    public HunterRenderer(EntityRendererProvider.Context context) {
        this(context, HUNTER_TEXTURE, 1.0F);
    }

    public HunterRenderer(EntityRendererProvider.Context context, Identifier texture, float scale) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModModelLayers.HUNTER)), 0.5F * scale);
        this.texture = texture;
        this.scale = scale;
    }

    @Override
    public HumanoidRenderState createRenderState() {
        return new HumanoidRenderState();
    }

    @Override
    public Identifier getTextureLocation(HumanoidRenderState state) {
        return this.texture;
    }

    @Override
    protected void scale(HumanoidRenderState state, PoseStack poseStack) {
        poseStack.scale(this.scale, this.scale, this.scale);
    }

    @Override
    protected HumanoidModel.ArmPose getArmPose(Hunter hunter, HumanoidArm arm) {
        ItemStack heldItem = hunter.getItemHeldByArm(arm);
        if (heldItem.getItem() instanceof CrossbowItem) {
            if (hunter.isChargingCrossbow()) {
                return HumanoidModel.ArmPose.CROSSBOW_CHARGE;
            }
            if (hunter.isAggressive() || CrossbowItem.isCharged(heldItem)) {
                return HumanoidModel.ArmPose.CROSSBOW_HOLD;
            }
        }
        return super.getArmPose(hunter, arm);
    }
}

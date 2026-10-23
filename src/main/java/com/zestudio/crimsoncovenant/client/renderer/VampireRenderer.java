package com.zestudio.crimsoncovenant.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.client.ModModelLayers;
import com.zestudio.crimsoncovenant.entity.MasterVampire;
import com.zestudio.crimsoncovenant.entity.Vampire;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.BowItem;

// Vampires use the regular player model too, so the texture is a standard 64x64 player skin.
// Also renders the Master Vampire, with its own texture, a larger size and its low-health blood armor.
public class VampireRenderer extends HumanoidMobRenderer<Vampire, VampireRenderState, HumanoidModel<VampireRenderState>> {
    public static final Identifier VAMPIRE_TEXTURE = CrimsonCovenant.id("textures/entity/vampire/vampire.png");
    public static final Identifier MASTER_VAMPIRE_TEXTURE = CrimsonCovenant.id("textures/entity/vampire/master_vampire.png");

    private final Identifier texture;
    private final float scale;

    public VampireRenderer(EntityRendererProvider.Context context) {
        this(context, VAMPIRE_TEXTURE, 1.0F);
    }

    public VampireRenderer(EntityRendererProvider.Context context, Identifier texture, float scale) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModModelLayers.VAMPIRE)), 0.5F * scale);
        this.texture = texture;
        this.scale = scale;
        this.addLayer(new BloodArmorLayer(this, context.getModelSet()));
    }

    @Override
    public VampireRenderState createRenderState() {
        return new VampireRenderState();
    }

    @Override
    public void extractRenderState(Vampire vampire, VampireRenderState state, float partialTicks) {
        super.extractRenderState(vampire, state, partialTicks);
        state.bloodArmor = vampire instanceof MasterVampire master && master.isBloodArmored();
    }

    @Override
    public Identifier getTextureLocation(VampireRenderState state) {
        return this.texture;
    }

    @Override
    protected void scale(VampireRenderState state, PoseStack poseStack) {
        poseStack.scale(this.scale, this.scale, this.scale);
    }

    @Override
    protected HumanoidModel.ArmPose getArmPose(Vampire vampire, HumanoidArm arm) {
        if (vampire.getMainArm() == arm && vampire.isAggressive() && vampire.getMainHandItem().getItem() instanceof BowItem) {
            return HumanoidModel.ArmPose.BOW_AND_ARROW;
        }
        return super.getArmPose(vampire, arm);
    }
}

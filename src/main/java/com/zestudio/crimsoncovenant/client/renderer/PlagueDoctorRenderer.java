package com.zestudio.crimsoncovenant.client.renderer;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.client.ModModelLayers;
import com.zestudio.crimsoncovenant.client.model.PlagueDoctorModel;
import com.zestudio.crimsoncovenant.entity.PlagueDoctor;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.resources.Identifier;

public class PlagueDoctorRenderer extends MobRenderer<PlagueDoctor, VillagerRenderState, PlagueDoctorModel> {
    private static final Identifier TEXTURE = CrimsonCovenant.id("textures/entity/plague_doctor.png");

    public PlagueDoctorRenderer(EntityRendererProvider.Context context) {
        super(context, new PlagueDoctorModel(context.bakeLayer(ModModelLayers.PLAGUE_DOCTOR)), 0.5F);
    }

    @Override
    public Identifier getTextureLocation(VillagerRenderState state) {
        return TEXTURE;
    }

    @Override
    public VillagerRenderState createRenderState() {
        return new VillagerRenderState();
    }

    @Override
    public void extractRenderState(PlagueDoctor entity, VillagerRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.isUnhappy = entity.getUnhappyCounter() > 0;
    }
}

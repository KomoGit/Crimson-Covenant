package com.zestudio.crimsoncovenant.client.model;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/// The werewolf's body: a regular humanoid (classic 64x32 limb layout, left limbs mirrored) with a wolf's snout
/// and ears on the head and a tail on the body. The extra parts use the lower half of the 64x64 texture.
public final class WerewolfModel {
    private WerewolfModel() {}

    public static LayerDefinition createBodyLayer() {
        return createLayer(CubeDeformation.NONE);
    }

    /// `deformation` swells every part: for a shell drawn just outside the body
    public static LayerDefinition createLayer(CubeDeformation deformation) {
        MeshDefinition mesh = HumanoidModel.createMesh(deformation, 0.0F);
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.getChild("head");
        head.addOrReplaceChild("snout", CubeListBuilder.create().texOffs(0, 32).addBox(-2.0F, -4.0F, -8.0F, 4.0F, 3.0F, 4.0F, deformation), PartPose.ZERO);
        head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(16, 32).addBox(-3.5F, -11.0F, -1.0F, 2.0F, 3.0F, 1.0F, deformation), PartPose.ZERO);
        head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(24, 32).addBox(1.5F, -11.0F, -1.0F, 2.0F, 3.0F, 1.0F, deformation), PartPose.ZERO);
        root.getChild("body").addOrReplaceChild("tail", CubeListBuilder.create().texOffs(32, 32).addBox(-1.0F, 0.0F, 0.0F, 2.0F, 2.0F, 8.0F, deformation),
                PartPose.offsetAndRotation(0.0F, 10.0F, 2.0F, -0.7F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }
}

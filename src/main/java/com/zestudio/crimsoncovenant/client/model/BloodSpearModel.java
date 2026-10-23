package com.zestudio.crimsoncovenant.client.model;

import com.zestudio.crimsoncovenant.CrimsonCovenant;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;

/// The Blood Spear: a dark crimson shaft wrapped in bone at the grip, a stepped leaf-shaped blade with two barbs
/// swept back from it, small bat-wing fins under the gold socket, and a blood crystal for a pommel.
///
/// Laid out like the trident (tip toward -Y, shaft running down +Y), so the trident's poses in hand and its
/// orientation in flight fit it as they are.
public class BloodSpearModel extends Model<Unit> {
    public static final Identifier TEXTURE = CrimsonCovenant.id("textures/entity/projectiles/blood_spear.png");

    public BloodSpearModel(ModelPart root) {
        super(root, RenderTypes::entityCutout);
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition shaft = root.addOrReplaceChild("shaft", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.5F, 3.0F, -0.5F, 1.0F, 23.0F, 1.0F)
                // Bone wrapping around the grip
                .texOffs(4, 0).addBox(-0.5F, 14.0F, -0.5F, 1.0F, 6.0F, 1.0F, new CubeDeformation(0.3F)), PartPose.ZERO);

        // The head: a socket, then a leaf-shaped blade, widest in the middle, drawn out to a long point
        shaft.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(8, 0).addBox(-1.0F, 1.0F, -1.0F, 2.0F, 2.0F, 2.0F)
                .texOffs(16, 0).addBox(-1.5F, -1.0F, -0.5F, 3.0F, 2.0F, 1.0F)
                .texOffs(16, 3).addBox(-2.5F, -4.0F, -0.5F, 5.0F, 3.0F, 1.0F)
                .texOffs(16, 7).addBox(-1.5F, -7.0F, -0.5F, 3.0F, 3.0F, 1.0F)
                .texOffs(24, 7).addBox(-0.5F, -9.0F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.ZERO);

        // Barbs hooking back from the base of the blade
        shaft.addOrReplaceChild("left_barb", CubeListBuilder.create().texOffs(28, 0).addBox(-1.0F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F),
                PartPose.offsetAndRotation(-1.5F, -0.5F, 0.0F, 0.0F, 0.0F, -0.6F));
        shaft.addOrReplaceChild("right_barb", CubeListBuilder.create().texOffs(28, 0).mirror().addBox(0.0F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F),
                PartPose.offsetAndRotation(1.5F, -0.5F, 0.0F, 0.0F, 0.0F, 0.6F));

        // Small bat-wing fins flaring out a little way down the shaft
        shaft.addOrReplaceChild("left_fin", CubeListBuilder.create().texOffs(32, 0).addBox(-2.0F, 0.0F, -0.5F, 2.0F, 3.0F, 1.0F),
                PartPose.offsetAndRotation(-0.5F, 5.0F, 0.0F, 0.0F, 0.0F, 0.6F));
        shaft.addOrReplaceChild("right_fin", CubeListBuilder.create().texOffs(32, 0).mirror().addBox(0.0F, 0.0F, -0.5F, 2.0F, 3.0F, 1.0F),
                PartPose.offsetAndRotation(0.5F, 5.0F, 0.0F, 0.0F, 0.0F, -0.6F));

        // A blood crystal at the butt
        shaft.addOrReplaceChild("pommel", CubeListBuilder.create().texOffs(40, 0).addBox(-1.0F, 26.0F, -1.0F, 2.0F, 3.0F, 2.0F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }
}

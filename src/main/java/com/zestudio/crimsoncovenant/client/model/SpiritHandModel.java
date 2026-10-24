package com.zestudio.crimsoncovenant.client.model;

import com.zestudio.crimsoncovenant.CrimsonCovenant;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;

/// Revenant's Grip: a ghostly right hand, its fingers closed round a grip (the weapon goes in it, see
/// SpiritHandRenderer), trailing a wisp of a wrist. Drawn see-through.
public class SpiritHandModel extends Model<Unit> {
    public static final Identifier TEXTURE = CrimsonCovenant.id("textures/entity/spirit_hand.png");

    public SpiritHandModel(ModelPart root) {
        super(root, RenderTypes::entityTranslucent);
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // The palm, upright, the grip running front to back through the curl of the fingers
        PartDefinition palm = root.addOrReplaceChild("palm", CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, -2.5F, -2.0F, 1.5F, 5.0F, 4.0F), PartPose.ZERO);
        for (int i = 0; i < 4; i++) {
            float z = -1.75F + i * 1.1F;
            palm.addOrReplaceChild("finger_" + i, CubeListBuilder.create().texOffs(12, 0).addBox(0.0F, -0.5F, -0.45F, 3.0F, 1.0F, 0.9F),
                    PartPose.offsetAndRotation(-0.5F, -2.3F, z + 0.45F, 0.0F, 0.0F, 0.35F));
            palm.addOrReplaceChild("finger_tip_" + i, CubeListBuilder.create().texOffs(12, 2).addBox(-0.5F, 0.0F, -0.45F, 1.0F, 2.5F, 0.9F),
                    PartPose.offset(2.3F, -1.8F, z + 0.45F));
        }
        palm.addOrReplaceChild("thumb", CubeListBuilder.create().texOffs(20, 0).addBox(0.0F, -0.5F, -0.5F, 2.5F, 1.0F, 1.0F),
                PartPose.offsetAndRotation(-0.5F, 1.5F, -1.8F, 0.0F, 0.4F, -0.3F));
        palm.addOrReplaceChild("wrist", CubeListBuilder.create().texOffs(0, 10).addBox(-1.75F, 2.5F, -1.5F, 1.25F, 4.0F, 3.0F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 16);
    }
}

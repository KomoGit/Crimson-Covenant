package com.zestudio.crimsoncovenant.client;

import com.zestudio.crimsoncovenant.CrimsonCovenant;

import net.minecraft.client.model.geom.ModelLayerLocation;

public final class ModModelLayers {
    public static final ModelLayerLocation HUNTER = new ModelLayerLocation(CrimsonCovenant.id("hunter"), "main");
    public static final ModelLayerLocation VAMPIRE = new ModelLayerLocation(CrimsonCovenant.id("vampire"), "main");
    public static final ModelLayerLocation WEREWOLF = new ModelLayerLocation(CrimsonCovenant.id("werewolf"), "main");
    public static final ModelLayerLocation GHOST = new ModelLayerLocation(CrimsonCovenant.id("ghost"), "main");
    public static final ModelLayerLocation BLOOD_SPEAR = new ModelLayerLocation(CrimsonCovenant.id("blood_spear"), "main");
    public static final ModelLayerLocation VAMPIRE_BLOOD_ARMOR = new ModelLayerLocation(CrimsonCovenant.id("vampire"), "blood_armor");

    private ModModelLayers() {}
}

package com.zestudio.crimsoncovenant.registry;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.item.SilveringRecipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, CrimsonCovenant.MODID);

    /// Silvering a weapon at the smithing table
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<SilveringRecipe>> SILVERING = RECIPE_SERIALIZERS.register("silvering",
            () -> SilveringRecipe.SERIALIZER);

    private ModRecipes() {}
}

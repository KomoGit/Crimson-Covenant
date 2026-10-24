package com.zestudio.crimsoncovenant.mixin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.zestudio.crimsoncovenant.hunter.HunterGear;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.CrafterBlock;

/// A crafter has no rank in the hunters, so it can't make their gear (see HunterGear)
@Mixin(CrafterBlock.class)
abstract class CrafterBlockMixin {
    @Inject(method = "getPotentialResults", at = @At("RETURN"), cancellable = true)
    private static void crimsoncovenant$noHunterGearFromCrafters(ServerLevel level, CraftingInput input, CallbackInfoReturnable<Optional<RecipeHolder<CraftingRecipe>>> cir) {
        Optional<RecipeHolder<CraftingRecipe>> recipe = cir.getReturnValue();
        if (recipe.isPresent() && HunterGear.requiredRank(recipe.get().value().assemble(input)) > 0) {
            cir.setReturnValue(Optional.empty());
        }
    }
}

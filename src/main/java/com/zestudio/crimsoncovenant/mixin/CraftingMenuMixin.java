package com.zestudio.crimsoncovenant.mixin;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.zestudio.crimsoncovenant.hunter.HunterGear;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

/// Hunter gear only comes out of a crafting grid (the crafting table's or the inventory's) for a hunter of high
/// enough rank (see HunterGear). The result is decided in one place for both grids: every value stored in its
/// result variable passes through here first.
@Mixin(CraftingMenu.class)
abstract class CraftingMenuMixin {
    @ModifyVariable(method = "slotChangedCraftingGrid", at = @At("STORE"), ordinal = 0)
    private static ItemStack crimsoncovenant$keepHunterGearToHunters(ItemStack result, AbstractContainerMenu menu, ServerLevel level, Player player,
            CraftingContainer container, ResultContainer resultSlots, @Nullable RecipeHolder<CraftingRecipe> recipeHint) {
        return HunterGear.gate(player, result);
    }
}

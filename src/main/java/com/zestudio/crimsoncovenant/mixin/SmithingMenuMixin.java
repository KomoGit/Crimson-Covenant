package com.zestudio.crimsoncovenant.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.zestudio.crimsoncovenant.hunter.HunterGear;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;

/// Silvering a weapon at the smithing table is hunter work (see HunterGear): the silvered weapon only appears in the
/// result slot for a hunter of high enough rank.
@Mixin(SmithingMenu.class)
abstract class SmithingMenuMixin extends ItemCombinerMenu {
    private SmithingMenuMixin(MenuType<?> menuType, int containerId, Inventory inventory, ContainerLevelAccess access, ItemCombinerMenuSlotDefinition itemInputSlots) {
        super(menuType, containerId, inventory, access, itemInputSlots);
    }

    @Inject(method = "createResult", at = @At("TAIL"))
    private void crimsoncovenant$keepSilveringToHunters(CallbackInfo ci) {
        ItemStack result = this.resultSlots.getItem(0);
        if (!this.player.level().isClientSide() && HunterGear.gate(this.player, result) != result) {
            this.resultSlots.setRecipeUsed(null);
            this.resultSlots.setItem(0, ItemStack.EMPTY);
        }
    }
}

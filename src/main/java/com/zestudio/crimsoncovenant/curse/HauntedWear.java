package com.zestudio.crimsoncovenant.curse;

import com.zestudio.crimsoncovenant.CrimsonCovenant;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.GrindstoneEvent;

/// A haunted weapon wears out, and nothing mends it: no anvil (any anvil, the Haunted Anvil worked as one included)
/// will restore its durability or lay Mending on it, no grindstone will join it with another, and the Haunted Anvil
/// won't haunt a weapon that already mends itself (see CurseforgeMenu.forge). Joining two in a crafting grid makes a
/// new, plain weapon: the haunting doesn't survive it.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public final class HauntedWear {
    private HauntedWear() {}

    public static boolean isHaunted(ItemStack stack) {
        return WeaponCurse.of(stack) != null;
    }

    /// Whether it mends itself from experience (Mending, or anything like it)
    public static boolean mends(ItemStack stack) {
        return EnchantmentHelper.has(stack, EnchantmentEffectComponents.REPAIR_WITH_XP);
    }

    @SubscribeEvent
    static void noMendingAtAnvils(AnvilUpdateEvent event) {
        ItemStack result = event.getVanillaResult().output();
        if (isHaunted(result) && (result.getDamageValue() < event.getLeft().getDamageValue() || mends(result))) {
            event.setCanceled(true);
        }
    }

    // Two items joined at a grindstone are joined in durability too: never with a haunted one
    @SubscribeEvent
    static void noMendingAtGrindstones(GrindstoneEvent.OnPlaceItem event) {
        if (!event.getTopItem().isEmpty() && !event.getBottomItem().isEmpty() && (isHaunted(event.getTopItem()) || isHaunted(event.getBottomItem()))) {
            event.setCanceled(true);
        }
    }
}

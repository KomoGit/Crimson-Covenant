package com.zestudio.crimsoncovenant.client;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.curse.WeaponCurse;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.IItemDecorator;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;

/// A haunted weapon's icon, wherever it's shown in a slot (hotbar, inventory, chests), bears a little ghost in its top
/// left corner, so it's known for haunted at a glance
@EventBusSubscriber(modid = CrimsonCovenant.MODID, value = Dist.CLIENT)
public final class HauntedWeaponMark {
    private static final Identifier MARK = CrimsonCovenant.id("haunted_mark");
    private static final int SIZE = 7;

    private HauntedWeaponMark() {}

    @SubscribeEvent
    static void register(RegisterItemDecorationsEvent event) {
        IItemDecorator mark = (graphics, font, stack, x, y) -> {
            if (WeaponCurse.of(stack) == null) {
                return false;
            }
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, MARK, x, y, SIZE, SIZE);
            return true;
        };
        // Any item can be haunted that's in the curseable tags, which aren't loaded yet: so every item gets the check
        for (Item item : BuiltInRegistries.ITEM) {
            event.register(item, mark);
        }
    }
}

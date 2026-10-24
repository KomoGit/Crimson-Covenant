package com.zestudio.crimsoncovenant.item;

import java.util.function.Consumer;

import com.zestudio.crimsoncovenant.block.IronMaidenBlockEntity;
import com.zestudio.crimsoncovenant.registry.ModDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

/// An Iron Maiden, carried (see block.IronMaidenBlock): use it on a villager to take them captive, then set it down
/// by your coffin
public class IronMaidenItem extends BlockItem {
    public IronMaidenItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        IronMaidenBlockEntity.Captive captive = stack.get(ModDataComponents.CAPTIVE.get());
        if (captive != null) {
            builder.accept(Component.translatable("item.crimsoncovenant.iron_maiden.captive", captive.blood()).withStyle(ChatFormatting.DARK_RED));
        } else {
            builder.accept(Component.translatable("item.crimsoncovenant.iron_maiden.hint").withStyle(ChatFormatting.GRAY));
        }
    }
}

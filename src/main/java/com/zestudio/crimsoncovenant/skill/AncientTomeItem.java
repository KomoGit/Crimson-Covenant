package com.zestudio.crimsoncovenant.skill;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/// A blood skill written in a forgotten tongue. An Ancient Translator turns it into the skill itself.
public class AncientTomeItem extends Item {
    private final BloodSkill skill;

    public AncientTomeItem(BloodSkill skill, Item.Properties properties) {
        super(properties);
        this.skill = skill;
    }

    public BloodSkill skill() {
        return this.skill;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.crimsoncovenant.ancient_tome.tier", this.skill.tier().displayName()).withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("item.crimsoncovenant.ancient_tome.hint").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}

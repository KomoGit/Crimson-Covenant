package com.zestudio.crimsoncovenant.curse;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/// A tome a curse is bound in. Forged into a weapon at a Curseforge, with a ghost to carry it over, it lays the curse
/// on the weapon (or deepens it, if the weapon already bears it). The hunters' Plague Doctors deal in them.
public class CursedTomeItem extends Item {
    private final Curse curse;

    public CursedTomeItem(Curse curse, Item.Properties properties) {
        super(properties);
        this.curse = curse;
    }

    public Curse curse() {
        return this.curse;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(this.curse.title().copy().withColor(Curse.COLOUR));
        builder.accept(Component.translatable("item.crimsoncovenant.cursed_tome.plain", this.curse.plainName()).withStyle(ChatFormatting.DARK_AQUA));
        builder.accept(this.curse.description().copy().withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("item.crimsoncovenant.cursed_tome.weapons", this.curse.weaponsName()).withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("item.crimsoncovenant.cursed_tome.hint").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}

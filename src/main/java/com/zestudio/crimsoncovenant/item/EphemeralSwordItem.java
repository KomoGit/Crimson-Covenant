package com.zestudio.crimsoncovenant.item;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.registry.ModItems;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/// A sword of solidified blood from the Ephemeral Sword skill: as good as an iron sword, and it never wears out,
/// but it can't be thrown away and it fades once its time is up (or when its owner dies). See ConjuredItems.
public class EphemeralSwordItem extends Item implements ConjuredItems.Conjured {
    /// One minute
    public static final int LIFETIME = 1200;

    public EphemeralSwordItem(Item.Properties properties) {
        super(properties);
    }

    public static ItemStack create(long gameTime) {
        return ConjuredItems.create(ModItems.EPHEMERAL_SWORD.get(), gameTime, LIFETIME);
    }

    public static void removeAll(Player player) {
        ConjuredItems.removeAll(player, ModItems.EPHEMERAL_SWORD.get());
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        ConjuredItems.tickFade(stack, level, owner, LIFETIME);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.crimsoncovenant.ephemeral_sword.hint").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}

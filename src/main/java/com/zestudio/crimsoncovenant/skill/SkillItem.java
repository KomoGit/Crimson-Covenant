package com.zestudio.crimsoncovenant.skill;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.vampirism.VampireAge;
import com.zestudio.crimsoncovenant.vampirism.Vampirism;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/// A translated blood skill. It isn't held to be used: it's worn in one of the skill slots (use it to put it on, or
/// open the Blood Skills screen) and cast with that slot's key, paying its blood cost. Only vampires can cast.
/// Skills stay with their owner through death (see SkillKeeper), but can be moved around and traded freely.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public class SkillItem extends Item {
    private final BloodSkill skill;

    public SkillItem(BloodSkill skill, Item.Properties properties) {
        super(properties);
        this.skill = skill;
    }

    public BloodSkill skill() {
        return this.skill;
    }

    /// Using a skill puts it on, in the first free skill slot
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            if (SkillSlots.isWearing(player, this.skill)) {
                serverPlayer.sendOverlayMessage(Component.translatable("message.crimsoncovenant.skill.already_worn").withStyle(ChatFormatting.GRAY));
                return InteractionResult.FAIL;
            }
            if (!SkillSlots.equip(player, stack)) {
                serverPlayer.sendOverlayMessage(Component.translatable("message.crimsoncovenant.skill.slots_full").withStyle(ChatFormatting.GRAY));
                return InteractionResult.FAIL;
            }
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
        }
        return InteractionResult.SUCCESS;
    }

    /// The skill's cost, right under its description: what it costs whoever's looking, cheaper for an old vampire (see
    /// VampireAge)
    @SubscribeEvent
    static void describeCost(ItemTooltipEvent event) {
        if (!(event.getItemStack().getItem() instanceof SkillItem item) || event.getToolTip().isEmpty()) {
            return;
        }
        ItemStack stack = event.getItemStack();
        Player player = event.getEntity();
        int fullCost = item.skill.cost() + SkillUpgrade.extraCost(stack);
        int cost = player != null && Vampirism.isVampire(player) ? VampireAge.discounted(fullCost, player) : fullCost;
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("item.crimsoncovenant.skill.cost", cost).withStyle(ChatFormatting.DARK_RED));
        if (cost < fullCost) {
            lines.add(Component.translatable("item.crimsoncovenant.skill.cost.aged", fullCost, VampireAge.tierName(VampireAge.tier(player))).withStyle(ChatFormatting.DARK_GRAY));
        }
        // After the name and the description
        // Right under its name
        event.getToolTip().addAll(Math.min(1, event.getToolTip().size()), lines);
    }

    /// Its cost (from describeCost) and whether it's upgraded: all else about it is in the Codex Sanguinis
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        SkillUpgrade upgrade = SkillUpgrade.forSkill(this.skill);
        if (upgrade != null && SkillUpgrade.isUpgraded(stack)) {
            builder.accept(Component.translatable("item.crimsoncovenant.skill.upgraded", Component.translatable("skill_upgrade.crimsoncovenant." + upgrade.getName()))
                    .withStyle(ChatFormatting.RED));
        }
    }
}

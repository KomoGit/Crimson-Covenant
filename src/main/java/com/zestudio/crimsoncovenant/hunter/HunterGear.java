package com.zestudio.crimsoncovenant.hunter;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.item.Silvering;
import com.zestudio.crimsoncovenant.registry.ModAttachments;
import com.zestudio.crimsoncovenant.registry.ModItems;
import com.zestudio.crimsoncovenant.registry.ModPotions;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.BrewingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/// The hunters keep their craft to themselves. Silver arrows, traps, silvered weapons and the hunters' potions can
/// only be crafted, silvered or brewed by a hunter of high enough rank (see HunterRanks):
///
/// - Initiate: Silver Arrows, Potions of Garlic
/// - Tracker: Bear Traps, Holy Water
/// - Slayer: Silver Spikes, silvering weapons at the smithing table
/// - Warden: Silver Net Traps, Potions of Silver
/// - Grand Hunter: lingering hunter potions
///
/// Anyone can still use the gear, buy it from the Plague Doctor or find it. The crafting table, the smithing table and
/// crafters are gated by mixins; a brewing stand brews for the rank of whoever last opened it.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public final class HunterGear {
    private HunterGear() {}

    /// The rank needed to make this, or 0 if anyone can
    public static int requiredRank(ItemStack stack) {
        if (stack.is(ModItems.SILVER_ARROW.get())) {
            return HunterRanks.INITIATE;
        }
        if (stack.is(ModItems.BEAR_TRAP.get())) {
            return HunterRanks.TRACKER;
        }
        if (stack.is(ModItems.SILVER_SPIKES.get()) || Silvering.isSilvered(stack)) {
            return HunterRanks.SLAYER;
        }
        if (stack.is(ModItems.SILVER_NET_TRAP.get())) {
            return HunterRanks.WARDEN;
        }
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null) {
            return 0;
        }
        int rank = 0;
        if (contents.is(ModPotions.GARLIC) || contents.is(ModPotions.LONG_GARLIC) || contents.is(ModPotions.STRONG_GARLIC)) {
            rank = HunterRanks.INITIATE;
        } else if (contents.is(ModPotions.HOLY_WATER)) {
            rank = HunterRanks.TRACKER;
        } else if (contents.is(ModPotions.SILVER)) {
            rank = HunterRanks.WARDEN;
        }
        return rank > 0 && stack.is(Items.LINGERING_POTION) ? HunterRanks.GRAND_HUNTER : rank;
    }

    public static boolean mayMake(Player player, ItemStack result) {
        return player.getAbilities().instabuild || HunterRanks.get(player) >= requiredRank(result);
    }

    /// What the player gets out of a crafting or smithing table: nothing, if the result is beyond their rank
    public static ItemStack gate(Player player, ItemStack result) {
        if (result.isEmpty() || mayMake(player, result)) {
            return result;
        }
        player.sendOverlayMessage(Component.translatable("message.crimsoncovenant.hunter.gear_locked", result.getHoverName(),
                HunterRanks.name(requiredRank(result))).withStyle(ChatFormatting.GRAY));
        return ItemStack.EMPTY;
    }

    /// The gear a rank unlocks, as a line of text
    public static Component unlockedAt(int rank) {
        return HunterRanks.name(rank).copy().append(": ").append(Component.translatable("hunter_rank.crimsoncovenant.unlocks." + rank));
    }

    // ---- Brewing ----

    /// Whoever opens a brewing stand brews with their own rank, until someone else opens it
    @SubscribeEvent
    static void rememberBrewer(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide() && event.getLevel().getBlockEntity(event.getPos()) instanceof BrewingStandBlockEntity stand) {
            Player player = event.getEntity();
            stand.setData(ModAttachments.BREWER_RANK, player.getAbilities().instabuild ? HunterRanks.MAX_RANK : HunterRanks.get(player));
        }
    }

    /// Called from BrewingStandBlockEntityMixin: whether the stand may brew what's in it, given its brewer's rank
    public static boolean mayBrew(ServerLevel level, BrewingStandBlockEntity stand) {
        int brewerRank = stand.getData(ModAttachments.BREWER_RANK);
        ItemStack reagent = stand.getItem(3);
        for (int slot = 0; slot < 3; slot++) {
            BrewingInput input = new BrewingInput(stand.getItem(slot), reagent);
            var recipe = level.recipeAccess().getRecipeFor(RecipeType.BREWING, input, level);
            if (recipe.isPresent() && requiredRank(recipe.get().value().assemble(input)) > brewerRank) {
                return false;
            }
        }
        return true;
    }
}

package com.zestudio.crimsoncovenant.guide;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.block.GravestoneBlock;
import com.zestudio.crimsoncovenant.entity.Ghost;
import com.zestudio.crimsoncovenant.registry.ModItems;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/// Souls: ghosts, caught in bottles. A Silvered Bottle holds one at peace (the Ghost in a Bottle); a plain glass bottle
/// crams one in, in torment (the Tormented Soul), no silver needed. Either is a soul, for unlocking blood skills in the
/// Codex Sanguinis (see SkillLore) or pouring into an anvil (see block.CurseforgeBlock). Catching a graveyard's ghost
/// takes it from its gravestone for good.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public final class Souls {
    private Souls() {}

    public static boolean isSoul(ItemStack stack) {
        return stack.is(ModItems.GHOST_IN_A_BOTTLE.get()) || stack.is(ModItems.TORMENTED_SOUL.get());
    }

    /// The bottle a soul leaves behind once it's spent
    public static ItemStack emptyBottle(ItemStack soul) {
        return soul.is(ModItems.TORMENTED_SOUL.get()) ? new ItemStack(Items.GLASS_BOTTLE) : new ItemStack(ModItems.SILVERED_BOTTLE.get());
    }

    /// An empty glass bottle used on a ghost crams it in
    @SubscribeEvent
    static void tormentGhosts(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        ItemStack held = player.getItemInHand(event.getHand());
        if (!(event.getTarget() instanceof Ghost ghost) || !held.is(Items.GLASS_BOTTLE) || !ghost.isAlive()) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!player.level().isClientSide()) {
            capture(player, ghost, event.getHand(), new ItemStack(ModItems.TORMENTED_SOUL.get()));
        }
    }

    /// The ghost goes into the bottle in the player's hand, which becomes `filled`. A graveyard's ghost is gone from its
    /// gravestone for good.
    public static void capture(Player player, Ghost ghost, InteractionHand hand, ItemStack filled) {
        if (ghost.getGrave() != null && ghost.level() instanceof ServerLevel level) {
            GravestoneBlock.setSpirit(level, ghost.getGrave(), GravestoneBlock.Spirit.NONE);
        }
        boolean tormented = filled.is(ModItems.TORMENTED_SOUL.get());
        ghost.level().playSound(null, ghost.getX(), ghost.getY(), ghost.getZ(), SoundEvents.BOTTLE_FILL_DRAGONBREATH, SoundSource.PLAYERS, 1.0F, tormented ? 0.5F : 0.8F);
        if (tormented) {
            ghost.level().playSound(null, ghost.getX(), ghost.getY(), ghost.getZ(), SoundEvents.GHAST_HURT, SoundSource.PLAYERS, 0.5F, 1.4F);
        }
        ghost.discard();
        player.setItemInHand(hand, ItemUtils.createFilledResult(player.getItemInHand(hand), player, filled));
    }

    /// How many souls the player is carrying
    public static int count(Player player) {
        Inventory inventory = player.getInventory();
        int souls = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isSoul(stack)) {
                souls += stack.getCount();
            }
        }
        return souls;
    }

    /// Spends `souls` of the player's souls (tormented ones first: they're the cheaper), giving back their empty bottles.
    /// Returns whether they had that many.
    public static boolean spend(Player player, int souls) {
        if (count(player) < souls) {
            return false;
        }
        Inventory inventory = player.getInventory();
        for (boolean tormentedFirst : new boolean[] {true, false}) {
            for (int slot = 0; slot < inventory.getContainerSize() && souls > 0; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (!isSoul(stack) || stack.is(ModItems.TORMENTED_SOUL.get()) != tormentedFirst) {
                    continue;
                }
                int taken = Math.min(souls, stack.getCount());
                ItemStack empty = emptyBottle(stack).copyWithCount(taken);
                stack.shrink(taken);
                souls -= taken;
                inventory.placeItemBackInInventory(empty, Prediction.SERVER_ONLY);
            }
        }
        return true;
    }
}

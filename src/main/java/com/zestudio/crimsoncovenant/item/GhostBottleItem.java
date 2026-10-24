package com.zestudio.crimsoncovenant.item;

import java.util.Map;

import com.zestudio.crimsoncovenant.entity.Ghost;
import com.zestudio.crimsoncovenant.registry.ModEntities;
import com.zestudio.crimsoncovenant.registry.ModItems;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/// A Silvered Bottle with a ghost in it (see block.SilveredBottleBlock). Set down, it places the bottle with its
/// ghost; used on thin air, it uncorks the bottle and lets the ghost out.
public class GhostBottleItem extends BlockItem {
    public GhostBottleItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    // The empty bottle is the block's item; this one is only the block with a ghost in it
    @Override
    public void registerBlocks(Map<Block, Item> map, Item item) {
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel serverLevel) {
            release(serverLevel, player.getEyePosition().add(player.getLookAngle().scale(1.5)));
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 1.0F, 0.7F);
        }
        return InteractionResult.SUCCESS.heldItemTransformedTo(ItemUtils.createFilledResult(stack, player, new ItemStack(ModItems.SILVERED_BOTTLE.get())));
    }

    public static Ghost release(ServerLevel level, Vec3 at) {
        Ghost ghost = ModEntities.GHOST.get().create(level, EntitySpawnReason.TRIGGERED);
        if (ghost != null) {
            ghost.snapTo(at.x, at.y - ghost.getBbHeight() / 2.0, at.z, level.getRandom().nextFloat() * 360.0F, 0.0F);
            level.addFreshEntity(ghost);
        }
        return ghost;
    }
}

package com.zestudio.crimsoncovenant.item;

import java.util.function.Supplier;

import com.zestudio.crimsoncovenant.entity.Gargoyle;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

/// A carved gargoyle, waiting to be woken. Set down on a block, it wakes as a Gargoyle that serves whoever set it
/// down, standing guard where it was placed. Only someone of the statue's side can wake it (see
/// Gargoyle.canCommand): hunters' silver won't answer the cursed, and blood magic only answers a vampire.
public class GargoyleStatueItem extends Item {
    private final Supplier<? extends EntityType<? extends Gargoyle>> type;
    private final Gargoyle.Side side;

    public GargoyleStatueItem(Supplier<? extends EntityType<? extends Gargoyle>> type, Gargoyle.Side side, Item.Properties properties) {
        super(properties);
        this.type = type;
        this.side = side;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        if (!Gargoyle.canCommand(this.side, player)) {
            player.sendOverlayMessage(Component.translatable("message.crimsoncovenant.gargoyle.wont_wake." + this.side.name().toLowerCase()).withStyle(ChatFormatting.GRAY));
            return InteractionResult.FAIL;
        }
        BlockPos pos = context.getClickedPos();
        BlockPos at = level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() ? pos : pos.relative(context.getClickedFace());
        Gargoyle gargoyle = this.type.get().create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (gargoyle == null) {
            return InteractionResult.FAIL;
        }
        // Facing whoever set it down
        gargoyle.snapTo(Vec3.atBottomCenterOf(at), player.getYRot() + 180.0F, 0.0F);
        gargoyle.setYHeadRot(gargoyle.getYRot());
        gargoyle.setYBodyRot(gargoyle.getYRot());
        if (!level.noCollision(gargoyle)) {
            return InteractionResult.FAIL;
        }
        ItemStack stack = context.getItemInHand();
        gargoyle.setOwner(player);
        gargoyle.guard(at);
        gargoyle.setPersistenceRequired();
        gargoyle.setCustomName(stack.get(DataComponents.CUSTOM_NAME));
        level.addFreshEntity(gargoyle);
        level.playSound(null, gargoyle.getX(), gargoyle.getY(), gargoyle.getZ(), SoundEvents.STONE_PLACE, SoundSource.NEUTRAL, 1.0F, 0.6F);
        stack.consume(1, player);
        return InteractionResult.SUCCESS;
    }
}

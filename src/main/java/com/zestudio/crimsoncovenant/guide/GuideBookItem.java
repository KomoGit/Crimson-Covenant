package com.zestudio.crimsoncovenant.guide;

import com.zestudio.crimsoncovenant.client.screen.GuideScreen;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/// A guide book (see GuideBooks): use it to read it
public class GuideBookItem extends Item {
    private final GuideBooks.Book book;

    public GuideBookItem(GuideBooks.Book book, Item.Properties properties) {
        super(properties);
        this.book = book;
    }

    public GuideBooks.Book book() {
        return this.book;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            // Only ever reached on a client, so the screen's class is only loaded there
            GuideScreen.open(this.book);
        }
        player.playSound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 1.0F);
        return InteractionResult.SUCCESS;
    }
}

package com.zestudio.crimsoncovenant.client.screen;

import com.zestudio.crimsoncovenant.curse.CurseforgeMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ItemCombinerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/// The Curseforge's screen, laid out as the smithing table's: a cursed tome, the weapon and a Ghost in a Bottle, and the
/// cursed weapon out on the right. A cross shows where the three can't go together.
public class CurseforgeScreen extends ItemCombinerScreen<CurseforgeMenu> {
    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/smithing.png");
    private static final Identifier ERROR_SPRITE = Identifier.withDefaultNamespace("container/smithing/error");

    public CurseforgeScreen(CurseforgeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, BACKGROUND);
        this.titleLabelX = 44;
        this.titleLabelY = 15;
    }

    // All three in, and nothing to show for it: they don't go together
    @Override
    protected void extractErrorIcon(GuiGraphicsExtractor graphics, int xo, int yo) {
        boolean filled = this.menu.getSlot(CurseforgeMenu.TOME_SLOT).hasItem() && this.menu.getSlot(CurseforgeMenu.WEAPON_SLOT).hasItem()
                && this.menu.getSlot(CurseforgeMenu.GHOST_SLOT).hasItem();
        if (filled && !this.menu.getSlot(CurseforgeMenu.RESULT_SLOT).hasItem()) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ERROR_SPRITE, xo + 65, yo + 46, 28, 21);
        }
    }
}

package com.zestudio.crimsoncovenant.client.screen;

import com.zestudio.crimsoncovenant.curse.CurseforgeMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ItemCombinerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/// The Curseforge's screen, laid out as the smithing table's: a cursed tome, the weapon and a Ghost in a Bottle, and the
/// cursed weapon out on the right. A cross shows where the three can't go together; the souls it costs show beneath,
/// red while there aren't enough.
public class CurseforgeScreen extends ItemCombinerScreen<CurseforgeMenu> {
    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/smithing.png");
    private static final Identifier ERROR_SPRITE = Identifier.withDefaultNamespace("container/smithing/error");

    public CurseforgeScreen(CurseforgeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, BACKGROUND);
        this.titleLabelX = 44;
        this.titleLabelY = 15;
    }

    // What the haunting costs, under the result, the way an anvil shows its price
    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        int cost = this.menu.getCost();
        if (cost <= 0) {
            return;
        }
        boolean affordable = this.menu.canAfford() || this.minecraft.player.hasInfiniteMaterials();
        Component line = Component.translatable("container.crimsoncovenant.curseforge.cost", cost);
        int x = this.imageWidth - 8 - this.font.width(line) - 2;
        graphics.fill(x - 2, 67, this.imageWidth - 8, 79, 0x4F000000);
        graphics.text(this.font, line, x, 69, affordable ? 0xFF8FE3FF : 0xFFFF6060);
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

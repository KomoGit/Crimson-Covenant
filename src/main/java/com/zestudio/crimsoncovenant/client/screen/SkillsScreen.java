package com.zestudio.crimsoncovenant.client.screen;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.client.ModKeyMappings;
import com.zestudio.crimsoncovenant.skill.SkillSlots;
import com.zestudio.crimsoncovenant.skill.SkillsMenu;
import com.zestudio.crimsoncovenant.vampirism.VampireAge;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/// The Blood Skills screen: four skill slots over the player's inventory, each labelled with its key, and how old a
/// vampire the player is (see VampireAge) across from the title
public class SkillsScreen extends AbstractContainerScreen<SkillsMenu> {
    private static final Identifier TEXTURE = CrimsonCovenant.id("textures/gui/container/skills.png");
    private static final int KEY_LABEL_COLOR = 0xFF8A1020;

    public SkillsScreen(SkillsMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 133);
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int xo = (this.width - this.imageWidth) / 2;
        int yo = (this.height - this.imageHeight) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, xo, yo, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
        super.extractLabels(graphics, xm, ym);
        Component age = VampireAge.of(this.minecraft.player).displayName();
        graphics.text(this.font, age, this.imageWidth - 8 - this.font.width(age), this.titleLabelY, -1, false);
        // Each slot's key, just under it
        for (int i = 0; i < SkillSlots.SLOTS; i++) {
            String key = ModKeyMappings.CAST_SKILL[i].getTranslatedKeyMessage().getString();
            graphics.centeredText(this.font, key, SkillsMenu.SLOT_X + i * 18 + 8, SkillsMenu.SLOT_Y + 18, KEY_LABEL_COLOR);
        }
    }
}

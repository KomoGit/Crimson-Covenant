package com.zestudio.crimsoncovenant.client.hud;

import java.util.List;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.client.ModKeyMappings;
import com.zestudio.crimsoncovenant.skill.SkillSlots;
import com.zestudio.crimsoncovenant.vampirism.Vampirism;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.gui.GuiLayer;

/// A vampire's worn skills, down the left edge of the screen: each one's icon, its cooldown, and its key
public class SkillBarLayer implements GuiLayer {
    public static final Identifier ID = CrimsonCovenant.id("skill_bar");
    private static final Identifier SLOT = Identifier.withDefaultNamespace("hud/hotbar_offhand_left");
    private static final int SLOT_SIZE = 22;
    private static final int KEY_LABEL_COLOR = 0xFFFF5060;

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.hud.isHidden() || !(minecraft.getCameraEntity() instanceof Player player) || !Vampirism.isVampire(player) || player.isSpectator()) {
            return;
        }
        List<ItemStack> skills = SkillSlots.get(player);
        if (skills.stream().allMatch(ItemStack::isEmpty)) {
            return;
        }
        int x = 4;
        int top = graphics.guiHeight() / 2 - SkillSlots.SLOTS * (SLOT_SIZE + 2) / 2;
        for (int i = 0; i < SkillSlots.SLOTS; i++) {
            int y = top + i * (SLOT_SIZE + 2);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, x, y, 29, 24);
            ItemStack skill = skills.get(i);
            if (!skill.isEmpty()) {
                graphics.item(skill, x + 3, y + 4);
                // The cooldown sweep, the same as on the hotbar
                graphics.itemDecorations(minecraft.font, skill, x + 3, y + 4);
            }
            String key = ModKeyMappings.CAST_SKILL[i].getTranslatedKeyMessage().getString();
            graphics.text(minecraft.font, key, x + 22, y + 12, KEY_LABEL_COLOR, true);
        }
    }
}

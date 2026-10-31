package com.zestudio.crimsoncovenant.client.hud;

import java.util.List;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.client.ModKeyMappings;
import com.zestudio.crimsoncovenant.item.WerewolfBelt;
import com.zestudio.crimsoncovenant.skill.MoonPowers;
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

/// A vampire's worn skills, down the left edge of the screen: each one's icon, its cooldown, and its key. A werewolf's four
/// powers go in the same place, dimmed until there's the moon to pay for them.
public class SkillBarLayer implements GuiLayer {
    public static final Identifier ID = CrimsonCovenant.id("skill_bar");
    private static final Identifier SLOT = Identifier.withDefaultNamespace("hud/hotbar_offhand_left");
    private static final int SLOT_SIZE = 22;
    private static final int KEY_LABEL_COLOR = 0xFFFF5060;
    private static final int MOON_KEY_LABEL_COLOR = 0xFFA9C1E8;
    private static final int TOO_DEAR = 0xB0000000;

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.hud.isHidden() || !(minecraft.getCameraEntity() instanceof Player player) || player.isSpectator()) {
            return;
        }
        if (WerewolfBelt.isTurned(player)) {
            renderMoonPowers(graphics, minecraft, player);
            return;
        }
        if (!Vampirism.isVampire(player)) {
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

    private static void renderMoonPowers(GuiGraphicsExtractor graphics, Minecraft minecraft, Player player) {
        MoonPowers.Power[] powers = MoonPowers.Power.values();
        int moon = MoonPowers.get(player);
        int x = 4;
        int top = graphics.guiHeight() / 2 - powers.length * (SLOT_SIZE + 2) / 2;
        for (int i = 0; i < powers.length; i++) {
            int y = top + i * (SLOT_SIZE + 2);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, x, y, 29, 24);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, CrimsonCovenant.id("hud/moon_power_" + powers[i].getName()), x + 3, y + 4, 16, 16);
            if (moon < powers[i].cost()) {
                graphics.fill(x + 3, y + 4, x + 19, y + 20, TOO_DEAR);
            }
            String key = ModKeyMappings.CAST_SKILL[i].getTranslatedKeyMessage().getString();
            graphics.text(minecraft.font, key, x + 22, y + 12, MOON_KEY_LABEL_COLOR, true);
        }
    }
}

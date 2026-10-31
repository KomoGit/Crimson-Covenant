package com.zestudio.crimsoncovenant.client.hud;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.item.WerewolfBelt;
import com.zestudio.crimsoncovenant.skill.MoonPowers;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.gui.GuiLayer;

/// A werewolf's moonbars: a row of ten moons in the hunger bar's place (werewolves have none, see item.WerewolfBelt),
/// each worth 10 (half a moon is 5). Only drawn when the hunger bar would be (not in creative or spectator).
public class MoonBarLayer implements GuiLayer {
    public static final Identifier ID = CrimsonCovenant.id("moon_bar");
    private static final Identifier FULL = CrimsonCovenant.id("hud/moon_full");
    private static final Identifier HALF = CrimsonCovenant.id("hud/moon_half");
    private static final Identifier EMPTY = CrimsonCovenant.id("hud/moon_empty");
    private static final int MOONS = 10;

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        Hud hud = minecraft.gui.hud;
        if (hud.isHidden() || minecraft.gameMode == null || !minecraft.gameMode.canHurtPlayer()
                || !(minecraft.getCameraEntity() instanceof Player player) || !WerewolfBelt.isTurned(player)) {
            return;
        }
        BloodBarLayer.drawRow(graphics, graphics.guiWidth() / 2 + 91, graphics.guiHeight() - hud.rightHeight, MOONS, MoonPowers.get(player), FULL, HALF, EMPTY);
        hud.rightHeight += 10;
    }
}

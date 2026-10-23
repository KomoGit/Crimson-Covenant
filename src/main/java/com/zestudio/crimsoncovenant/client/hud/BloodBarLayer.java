package com.zestudio.crimsoncovenant.client.hud;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.vampirism.BloodPool;
import com.zestudio.crimsoncovenant.vampirism.Vampirism;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.gui.GuiLayer;

/// The vampire's blood bar: a row of ten drops in the hunger bar's place (vampires have none), each drop worth 10
/// blood (half a drop is 5). Only drawn for vampire players, and only when the hunger bar would be (not in
/// creative or spectator).
public class BloodBarLayer implements GuiLayer {
    public static final Identifier ID = CrimsonCovenant.id("blood_bar");
    private static final Identifier FULL = CrimsonCovenant.id("hud/blood_full");
    private static final Identifier HALF = CrimsonCovenant.id("hud/blood_half");
    private static final Identifier EMPTY = CrimsonCovenant.id("hud/blood_empty");
    private static final int DROPS = 10;
    private static final int BLOOD_PER_DROP = BloodPool.MAX_BLOOD / DROPS;

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        Hud hud = minecraft.gui.hud;
        if (hud.isHidden() || minecraft.gameMode == null || !minecraft.gameMode.canHurtPlayer()
                || !(minecraft.getCameraEntity() instanceof Player player) || !Vampirism.isVampire(player)) {
            return;
        }
        int blood = BloodPool.get(player);
        int xRight = graphics.guiWidth() / 2 + 91;
        int y = graphics.guiHeight() - hud.rightHeight;
        // Same layout as the hunger bar: filled from the right
        for (int i = 0; i < DROPS; i++) {
            int x = xRight - i * 8 - 9;
            int dropBlood = blood - i * BLOOD_PER_DROP;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, EMPTY, x, y, 9, 9);
            if (dropBlood >= BLOOD_PER_DROP) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FULL, x, y, 9, 9);
            } else if (dropBlood >= BLOOD_PER_DROP / 2) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HALF, x, y, 9, 9);
            }
        }
        hud.rightHeight += 10;
    }
}

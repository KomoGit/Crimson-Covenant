package com.zestudio.crimsoncovenant.client.hud;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.vampirism.BloodPool;
import com.zestudio.crimsoncovenant.vampirism.VampireAge;
import com.zestudio.crimsoncovenant.vampirism.Vampirism;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.gui.GuiLayer;

/// The vampire's blood bar: a row of ten drops in the hunger bar's place (vampires have none), each drop worth 10
/// blood (half a drop is 5). What an older vampire holds past the first 100 (see VampireAge) is a row of golden drops
/// above it, 10 blood each: one for a Neo, five for an Unusual, ten for an Ancient. Over them both, the vampire's age
/// once it's past its first ten days. Only drawn for vampire players, and only when the hunger bar would be (not in
/// creative or spectator).
public class BloodBarLayer implements GuiLayer {
    public static final Identifier ID = CrimsonCovenant.id("blood_bar");
    private static final Identifier FULL = CrimsonCovenant.id("hud/blood_full");
    private static final Identifier HALF = CrimsonCovenant.id("hud/blood_half");
    private static final Identifier EMPTY = CrimsonCovenant.id("hud/blood_empty");
    private static final Identifier GOLD_FULL = CrimsonCovenant.id("hud/blood_gold_full");
    private static final Identifier GOLD_HALF = CrimsonCovenant.id("hud/blood_gold_half");
    private static final Identifier GOLD_EMPTY = CrimsonCovenant.id("hud/blood_gold_empty");
    private static final int DROPS = 10;
    public static final int BLOOD_PER_DROP = 10;
    /// What a fledgling holds: the red row; anything past it is gold
    private static final int RED_BLOOD = DROPS * BLOOD_PER_DROP;
    private static final int AGE_COLOR = 0xFFC8303C;

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
        drawRow(graphics, xRight, graphics.guiHeight() - hud.rightHeight, DROPS, blood, FULL, HALF, EMPTY);
        hud.rightHeight += 10;
        int goldDrops = goldDrops(BloodPool.max(player));
        if (goldDrops > 0) {
            drawRow(graphics, xRight, graphics.guiHeight() - hud.rightHeight, goldDrops, blood - RED_BLOOD, GOLD_FULL, GOLD_HALF, GOLD_EMPTY);
            hud.rightHeight += 10;
        }
        // The vampire's age, right-aligned over the drops
        int tier = VampireAge.tier(player);
        if (tier > 0) {
            Component age = VampireAge.tierName(tier);
            graphics.text(minecraft.font, age, xRight - minecraft.font.width(age), graphics.guiHeight() - hud.rightHeight, AGE_COLOR, true);
            hud.rightHeight += 10;
        }
    }

    /// How many golden drops a vampire who can hold `maxBlood` has: one for every 10 past the first 100
    public static int goldDrops(int maxBlood) {
        return Math.max(0, maxBlood - RED_BLOOD) / BLOOD_PER_DROP;
    }

    // Same layout as the hunger bar: filled from the right
    static void drawRow(GuiGraphicsExtractor graphics, int xRight, int y, int drops, int blood, Identifier full, Identifier half, Identifier empty) {
        for (int i = 0; i < drops; i++) {
            int x = xRight - i * 8 - 9;
            int dropBlood = blood - i * BLOOD_PER_DROP;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, empty, x, y, 9, 9);
            if (dropBlood >= BLOOD_PER_DROP) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, full, x, y, 9, 9);
            } else if (dropBlood >= BLOOD_PER_DROP / 2) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, half, x, y, 9, 9);
            }
        }
    }
}

package com.zestudio.crimsoncovenant.client.hud;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.entity.WhipLash;
import com.zestudio.crimsoncovenant.item.WhipItem;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.gui.GuiLayer;

/// Under the crosshair, with a whip in hand and a creature in its sights: red brackets round a dot when the lash would
/// reach it, grey ones thrown wide when it's too far. Worked out just as the lash itself is (see WhipLash.reached).
public class WhipReachLayer implements GuiLayer {
    public static final Identifier ID = CrimsonCovenant.id("whip_reach");
    private static final Identifier IN_REACH = CrimsonCovenant.id("whip_in_reach");
    private static final Identifier OUT_OF_REACH = CrimsonCovenant.id("whip_out_of_reach");
    private static final int WIDTH = 13;
    private static final int HEIGHT = 9;
    /// How far off a creature can be and still be shown as out of reach
    private static final double SIGHT = 32.0;
    /// Below the crosshair, clear of the attack indicator under it
    private static final int BELOW_CROSSHAIR = 15;

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.hud.isHidden() || !minecraft.options.getCameraType().isFirstPerson() || minecraft.level == null
                || !(minecraft.getCameraEntity() instanceof Player player) || player.isSpectator() || !WhipItem.isWhip(player.getMainHandItem())) {
            return;
        }
        Identifier sprite;
        if (!WhipLash.reached(minecraft.level, player).isEmpty()) {
            sprite = IN_REACH;
        } else if (!WhipLash.along(minecraft.level, player, SIGHT).isEmpty()) {
            sprite = OUT_OF_REACH;
        } else {
            return;
        }
        int x = (graphics.guiWidth() - WIDTH) / 2;
        int y = graphics.guiHeight() / 2 + BELOW_CROSSHAIR;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, WIDTH, HEIGHT);
    }
}

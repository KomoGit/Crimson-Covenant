package com.zestudio.crimsoncovenant.client.hud;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.registry.ModDataComponents;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.gui.GuiLayer;

/// Conjured weapons (the Ephemeral Sword, the Blood Spear) about to fade: their hotbar slot pulses red for the
/// last 5 seconds, faster as the end comes. Drawn over the hotbar, at the same spots vanilla puts the items.
public class FadingItemsLayer implements GuiLayer {
    public static final Identifier ID = CrimsonCovenant.id("fading_items");
    public static final int WARNING_TICKS = 100;
    /// In the last 2 seconds the pulse quickens
    private static final int URGENT_TICKS = 40;

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.hud.isHidden() || minecraft.level == null || !(minecraft.getCameraEntity() instanceof Player player) || player.isSpectator()) {
            return;
        }
        long gameTime = minecraft.level.getGameTime();
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        int center = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() - 16 - 3;
        for (int slot = 0; slot < 9; slot++) {
            warn(graphics, player.getInventory().getItem(slot), gameTime, partialTick, center - 90 + slot * 20 + 2, y);
        }
        int offhandX = player.getMainArm().getOpposite() == HumanoidArm.LEFT ? center - 91 - 26 : center + 91 + 10;
        warn(graphics, player.getOffhandItem(), gameTime, partialTick, offhandX, y);
    }

    private static void warn(GuiGraphicsExtractor graphics, ItemStack stack, long gameTime, float partialTick, int x, int y) {
        Long expiresAt = stack.get(ModDataComponents.EXPIRES_AT.get());
        if (expiresAt == null) {
            return;
        }
        // Counted from the expiry, so the float stays small enough to be smooth
        float left = (expiresAt - gameTime) - partialTick;
        if (left <= 0.0F || left > WARNING_TICKS) {
            return;
        }
        float speed = left < URGENT_TICKS ? 0.9F : 0.45F;
        float pulse = 0.5F + 0.5F * Mth.sin(left * speed);
        graphics.fill(x - 1, y - 1, x + 17, y + 17, ARGB.color((int) (40 + 80 * pulse), 220, 20, 40));
        graphics.outline(x - 2, y - 2, 20, 20, ARGB.color((int) (120 + 135 * pulse), 255, 40, 60));
    }
}

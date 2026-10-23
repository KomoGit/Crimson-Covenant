package com.zestudio.crimsoncovenant.client.screen;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.network.SkillPayloads;
import com.zestudio.crimsoncovenant.vampirism.Vampirism;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/// A small blood-drop button in a vampire's inventory, next to the recipe book button, that opens the Blood
/// Skills screen (the same as the K key)
@EventBusSubscriber(modid = CrimsonCovenant.MODID, value = Dist.CLIENT)
public class SkillsButton extends ImageButton {
    private static final WidgetSprites SPRITES = new WidgetSprites(CrimsonCovenant.id("skills_button"), CrimsonCovenant.id("skills_button_highlighted"));
    private static final int GAP = 2;

    private SkillsButton() {
        super(0, 0, 20, 18, SPRITES, button -> ClientPacketDistributor.sendToServer(new SkillPayloads.OpenSkills()));
        this.setTooltip(Tooltip.create(Component.translatable("container.crimsoncovenant.skills")));
    }

    @SubscribeEvent
    static void addToInventory(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof InventoryScreen) {
            SkillsButton button = new SkillsButton();
            event.addListener(button);
            button.follow(event.getScreen().children());
        }
    }

    // The recipe book shifts the inventory sideways when it opens, so the button keeps to the recipe button's right
    @SubscribeEvent
    static void keepInPlace(ScreenEvent.Render.Pre event) {
        if (event.getScreen() instanceof InventoryScreen screen) {
            for (GuiEventListener child : screen.children()) {
                if (child instanceof SkillsButton button) {
                    button.follow(screen.children());
                }
            }
        }
    }

    private void follow(Iterable<? extends GuiEventListener> siblings) {
        this.visible = Minecraft.getInstance().player != null && Vampirism.isVampire(Minecraft.getInstance().player);
        for (GuiEventListener sibling : siblings) {
            if (sibling instanceof ImageButton recipeButton && !(sibling instanceof SkillsButton)) {
                this.setPosition(recipeButton.getX() + recipeButton.getWidth() + GAP, recipeButton.getY());
                return;
            }
        }
    }
}

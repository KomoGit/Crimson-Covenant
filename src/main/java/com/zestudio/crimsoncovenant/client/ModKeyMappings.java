package com.zestudio.crimsoncovenant.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.network.SkillPayloads;
import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.skill.SkillSlots;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/// The blood skill keys: one per skill slot, and one to open the Blood Skills screen; and the hunters' curse key, to
/// unleash the curse on the weapon in hand; and one for a vampire to turn their night vision on or off. All can be changed in Options > Controls, under Crimson Covenant.
@EventBusSubscriber(modid = CrimsonCovenant.MODID, value = Dist.CLIENT)
public final class ModKeyMappings {
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(CrimsonCovenant.id("skills"));
    private static final int[] DEFAULT_SKILL_KEYS = {InputConstants.KEY_R, InputConstants.KEY_G, InputConstants.KEY_V, InputConstants.KEY_B};

    public static final KeyMapping OPEN_SKILLS = new KeyMapping("key.crimsoncovenant.open_skills", InputConstants.Type.KEYBOARD, InputConstants.KEY_K, CATEGORY);
    public static final KeyMapping[] CAST_SKILL = new KeyMapping[SkillSlots.SLOTS];
    public static final KeyMapping UNLEASH_CURSE = new KeyMapping("key.crimsoncovenant.unleash_curse", InputConstants.Type.KEYBOARD, InputConstants.KEY_Z, CATEGORY);
    public static final KeyMapping NIGHT_VISION = new KeyMapping("key.crimsoncovenant.night_vision", InputConstants.Type.KEYBOARD, InputConstants.KEY_N, CATEGORY);

    static {
        for (int i = 0; i < SkillSlots.SLOTS; i++) {
            CAST_SKILL[i] = new KeyMapping("key.crimsoncovenant.skill_" + (i + 1), InputConstants.Type.KEYBOARD, DEFAULT_SKILL_KEYS[i], CATEGORY);
        }
    }

    private ModKeyMappings() {}

    @SubscribeEvent
    static void register(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(OPEN_SKILLS);
        event.register(UNLEASH_CURSE);
        event.register(NIGHT_VISION);
        for (KeyMapping key : CAST_SKILL) {
            event.register(key);
        }
    }

    /// Gliding in the Crimson Shroud, a right click is a Blood Charge instead of whatever it would otherwise do
    @SubscribeEvent
    static void bloodCharge(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.isUseItem() && minecraft.player != null && minecraft.player.isFallFlying() && minecraft.player.hasEffect(ModEffects.CRIMSON_SHROUD)) {
            event.setCanceled(true);
            event.setSwingHand(false);
            if (event.getHand() == InteractionHand.MAIN_HAND) {
                ClientPacketDistributor.sendToServer(new SkillPayloads.BloodCharge());
            }
        }
    }

    @SubscribeEvent
    static void handleKeys(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gui.screen() != null) {
            return;
        }
        while (OPEN_SKILLS.consumeClick()) {
            ClientPacketDistributor.sendToServer(new SkillPayloads.OpenSkills());
        }
        while (UNLEASH_CURSE.consumeClick()) {
            ClientPacketDistributor.sendToServer(new SkillPayloads.UnleashCurse());
        }
        while (NIGHT_VISION.consumeClick()) {
            ClientPacketDistributor.sendToServer(new SkillPayloads.ToggleNightVision());
        }
        for (int i = 0; i < CAST_SKILL.length; i++) {
            while (CAST_SKILL[i].consumeClick()) {
                ClientPacketDistributor.sendToServer(new SkillPayloads.CastSkill(i));
            }
        }
    }
}

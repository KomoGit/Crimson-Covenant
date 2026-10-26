package com.zestudio.crimsoncovenant;

import com.zestudio.crimsoncovenant.client.ModModelLayers;
import com.zestudio.crimsoncovenant.client.hud.BloodBarLayer;
import com.zestudio.crimsoncovenant.client.hud.FadingItemsLayer;
import com.zestudio.crimsoncovenant.client.hud.SkillBarLayer;
import com.zestudio.crimsoncovenant.client.screen.SkillsScreen;
import com.zestudio.crimsoncovenant.client.model.BloodSpearModel;
import com.zestudio.crimsoncovenant.client.model.BatWingsModel;
import com.zestudio.crimsoncovenant.client.model.GargoyleModel;
import com.zestudio.crimsoncovenant.client.model.GhostModel;
import com.zestudio.crimsoncovenant.client.model.PlagueDoctorModel;
import com.zestudio.crimsoncovenant.client.model.RatModel;
import com.zestudio.crimsoncovenant.client.model.WerewolfModel;
import com.zestudio.crimsoncovenant.client.renderer.AncientArmorLayer;
import com.zestudio.crimsoncovenant.client.renderer.BloodSpearItemRenderer;
import com.zestudio.crimsoncovenant.client.renderer.BloodSpearRenderer;
import com.zestudio.crimsoncovenant.client.renderer.CrimsonLightningRenderer;
import com.zestudio.crimsoncovenant.client.renderer.GargoyleRenderer;
import com.zestudio.crimsoncovenant.client.renderer.GhostRenderer;
import com.zestudio.crimsoncovenant.entity.Gargoyle;
import com.zestudio.crimsoncovenant.client.renderer.HeadlessHorsemanRenderer;
import com.zestudio.crimsoncovenant.client.renderer.HunterRenderer;
import com.zestudio.crimsoncovenant.client.renderer.CrimsonShroudLayer;
import com.zestudio.crimsoncovenant.client.renderer.PlagueDoctorRenderer;
import com.zestudio.crimsoncovenant.client.renderer.RatRenderer;
import com.zestudio.crimsoncovenant.client.renderer.SilverArrowRenderer;
import com.zestudio.crimsoncovenant.client.renderer.SilveredWeaponSprites;
import com.zestudio.crimsoncovenant.client.renderer.VampireBatRenderer;
import com.zestudio.crimsoncovenant.client.renderer.VampireRenderer;
import com.zestudio.crimsoncovenant.client.renderer.WerewolfRenderer;
import com.zestudio.crimsoncovenant.network.BloodMoonPayload;
import com.zestudio.crimsoncovenant.registry.ModAttachments;
import com.zestudio.crimsoncovenant.registry.ModEntities;
import com.zestudio.crimsoncovenant.registry.ModMenus;
import com.zestudio.crimsoncovenant.vampirism.Vampirism;
import com.zestudio.crimsoncovenant.client.renderer.SpiritHandRenderer;
import com.zestudio.crimsoncovenant.client.renderer.SpiritWyrmsRenderer;
import com.zestudio.crimsoncovenant.client.screen.CurseforgeScreen;
import com.zestudio.crimsoncovenant.client.renderer.HauntedObjectRenderer;
import com.zestudio.crimsoncovenant.client.renderer.WhipLashRenderer;
import com.zestudio.crimsoncovenant.curse.Earthshatter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent;
import net.neoforged.neoforge.client.event.RegisterSpriteSourcesEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.renderstate.AvatarRenderStateModifier;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import com.google.common.reflect.TypeToken;

// This class never loads on dedicated servers, so client-only code (renderers, screens, keybinds) is safe here
@Mod(value = CrimsonCovenant.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = CrimsonCovenant.MODID, value = Dist.CLIENT)
public class CrimsonCovenantClient {
    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        CrimsonCovenant.LOGGER.info("Crimson Covenant client setup");
    }

    // A blood moon turns the night fog (and with it the horizon) blood red
    @SubscribeEvent
    static void tintBloodMoonFog(ViewportEvent.ComputeFogColor event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (BloodMoonPayload.clientBloodMoon && minecraft.level != null && minecraft.level.dimension() == Level.OVERWORLD) {
            event.setRed(Mth.lerp(0.55F, event.getRed(), 0.55F));
            event.setGreen(event.getGreen() * 0.3F);
            event.setBlue(event.getBlue() * 0.3F);
        }
    }

    @SubscribeEvent
    static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        // Standard (wide-arm) player model, so hunter textures use the 64x64 player skin layout
        event.registerLayerDefinition(ModModelLayers.HUNTER, () -> LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64));
        event.registerLayerDefinition(ModModelLayers.VAMPIRE, () -> LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64));
        event.registerLayerDefinition(ModModelLayers.GHOST, GhostModel::createBodyLayer);
        event.registerLayerDefinition(ModModelLayers.WEREWOLF, WerewolfModel::createBodyLayer);
        event.registerLayerDefinition(ModModelLayers.GARGOYLE, GargoyleModel::createBodyLayer);
        event.registerLayerDefinition(ModModelLayers.BLOOD_SPEAR, BloodSpearModel::createLayer);
        event.registerLayerDefinition(ModModelLayers.RAT, RatModel::createBodyLayer);
        event.registerLayerDefinition(ModModelLayers.PLAGUE_DOCTOR, PlagueDoctorModel::createBodyLayer);
        event.registerLayerDefinition(ModModelLayers.BAT_WINGS, BatWingsModel::createLayer);
        event.registerLayerDefinition(ModModelLayers.RAT_BABY, () -> RatModel.createBodyLayer().apply(RatModel.BABY_TRANSFORMER));
        // Slightly inflated so the blood armor shell sits just outside the body, like the wither's armor
        event.registerLayerDefinition(ModModelLayers.VAMPIRE_BLOOD_ARMOR, () -> LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(0.5F), false), 64, 64));
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.HUNTER.get(), HunterRenderer::new);
        event.registerEntityRenderer(ModEntities.HUNTER_CHEMIST.get(), context -> new HunterRenderer(context, HunterRenderer.HUNTER_CHEMIST_TEXTURE, 1.0F));
        event.registerEntityRenderer(ModEntities.MASTER_HUNTER.get(), context -> new HunterRenderer(context, HunterRenderer.MASTER_HUNTER_TEXTURE, 1.1F));
        event.registerEntityRenderer(ModEntities.VAMPIRE.get(), VampireRenderer::new);
        event.registerEntityRenderer(ModEntities.VAMPIRE_BAT.get(), VampireBatRenderer::new);
        event.registerEntityRenderer(ModEntities.SWARM_BAT.get(), VampireBatRenderer::new);
        event.registerEntityRenderer(ModEntities.HEMORRHAGE_ZONE.get(), NoopRenderer::new);
        event.registerEntityRenderer(ModEntities.CRIMSON_LIGHTNING.get(), CrimsonLightningRenderer::new);
        event.registerEntityRenderer(ModEntities.HEADLESS_HORSEMAN.get(), HeadlessHorsemanRenderer::new);
        event.registerEntityRenderer(ModEntities.GHOST.get(), GhostRenderer::new);
        event.registerEntityRenderer(ModEntities.WEREWOLF.get(), WerewolfRenderer::new);
        event.registerEntityRenderer(ModEntities.CATHEDRAL_GARGOYLE.get(), context -> new GargoyleRenderer(context, Gargoyle.Side.HUNTER));
        event.registerEntityRenderer(ModEntities.BLOOD_GARGOYLE.get(), context -> new GargoyleRenderer(context, Gargoyle.Side.VAMPIRE));
        event.registerEntityRenderer(ModEntities.RAT.get(), RatRenderer::new);
        event.registerEntityRenderer(ModEntities.PLAGUE_DOCTOR.get(), PlagueDoctorRenderer::new);
        event.registerEntityRenderer(ModEntities.MIASMA_CLOUD.get(), NoopRenderer::new);
        event.registerEntityRenderer(ModEntities.SILVER_DUST_CLOUD.get(), NoopRenderer::new);
        event.registerEntityRenderer(ModEntities.THRONE_SEAT.get(), NoopRenderer::new);
        event.registerEntityRenderer(ModEntities.SILVER_ARROW.get(), SilverArrowRenderer::new);
        event.registerEntityRenderer(ModEntities.MASTER_VAMPIRE.get(), context -> new VampireRenderer(context, VampireRenderer.MASTER_VAMPIRE_TEXTURE, 1.15F));
        event.registerEntityRenderer(ModEntities.BLOOD_SPEAR.get(), BloodSpearRenderer::new);
        event.registerEntityRenderer(ModEntities.SPIRIT_HAND.get(), SpiritHandRenderer::new);
        event.registerEntityRenderer(ModEntities.SPIRIT_WYRMS.get(), SpiritWyrmsRenderer::new);
        event.registerEntityRenderer(ModEntities.HAUNTED_OBJECT.get(), HauntedObjectRenderer::new);
        event.registerEntityRenderer(ModEntities.WHIP_LASH.get(), WhipLashRenderer::new);
        // A big, glowing ball of blood
        event.registerEntityRenderer(ModEntities.BLOOD_ORB.get(), context -> new ThrownItemRenderer<>(context, 3.0F, true));
        // A glowing red bat, always facing you
        event.registerEntityRenderer(ModEntities.BLOOD_BAT.get(), context -> new ThrownItemRenderer<>(context, 1.5F, true));
    }

    @SubscribeEvent
    static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.FOOD_LEVEL, BloodBarLayer.ID, new BloodBarLayer());
        event.registerAbove(VanillaGuiLayers.HOTBAR, SkillBarLayer.ID, new SkillBarLayer());
        event.registerAbove(VanillaGuiLayers.HOTBAR, FadingItemsLayer.ID, new FadingItemsLayer());
    }

    // Vampires don't hunger, so they have no hunger bar: the blood bar takes its place
    @SubscribeEvent
    static void hideVampireHunger(RenderGuiLayerEvent.Pre event) {
        if (event.getName().equals(VanillaGuiLayers.FOOD_LEVEL) && Minecraft.getInstance().getCameraEntity() instanceof Player player && Vampirism.isVampire(player)) {
            event.setCanceled(true);
        }
    }

    // The Blood Spear held in hand is drawn like a trident
    @SubscribeEvent
    static void registerSpecialItemRenderers(RegisterSpecialModelRendererEvent event) {
        event.register(BloodSpearItemRenderer.ID, BloodSpearItemRenderer.Unbaked.MAP_CODEC);
    }

    // Silvered weapons' textures, made from the plain ones as the items atlas is built
    @SubscribeEvent
    static void registerSpriteSources(RegisterSpriteSourcesEvent event) {
        event.register(CrimsonCovenant.id("silvered_weapons"), SilveredWeaponSprites.MAP_CODEC);
    }

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.SKILLS.get(), SkillsScreen::new);
        event.register(ModMenus.CURSEFORGE.get(), CurseforgeScreen::new);
    }

    // Armor of Ancient's blood shell and the Crimson Shroud's wings, on every player model
    @SubscribeEvent
    static void addPlayerLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerModelType skin : event.getSkins()) {
            AvatarRenderer<AbstractClientPlayer> renderer = event.getPlayerRenderer(skin);
            if (renderer != null) {
                renderer.addLayer(new AncientArmorLayer(renderer, event.getEntityModels()));
                renderer.addLayer(new CrimsonShroudLayer(renderer, event.getEntityModels()));
            }
        }
    }

    @SubscribeEvent
    static void registerRenderStateModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerAvatarEntityModifier(new AvatarRenderStateModifier() {
            @Override
            public <T extends Avatar & ClientAvatarEntity> void accept(T avatar, AvatarRenderState state) {
                state.setRenderData(AncientArmorLayer.ACTIVE, avatar instanceof Player player && player.getData(ModAttachments.ANCIENT_ARMOR_VISIBLE));
                state.setRenderData(CrimsonShroudLayer.ACTIVE, avatar instanceof Player player && player.getData(ModAttachments.CRIMSON_SHROUD_WORN));
                // A ghost from the Potion of Ephemeral: drawn faint, the way an invisible player looks to a teammate
                if (avatar instanceof Player player && player.getData(ModAttachments.GHOSTLY)) {
                    state.isInvisible = true;
                    state.isInvisibleToPlayer = false;
                }
            }
        });
        // Under Exile's Mark, any creature is drawn faint the same way, a ghost of itself
        event.registerEntityModifier(new TypeToken<LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?>>() {}, (LivingEntity entity, LivingEntityRenderState state) -> {
            if (entity.getData(ModAttachments.BANISHED_LOOK)) {
                state.isInvisible = true;
                state.isInvisibleToPlayer = false;
            }
            // Thrown down by Gravequake: tipped over on its side the way a dying creature is, falling over quickly, then
            // getting up again over its last half second
            long until = entity.getData(ModAttachments.KNOCKED_DOWN_UNTIL);
            if (until != 0L && state.deathTime == 0.0F) {
                float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
                float left = until - entity.level().getGameTime() - partial;
                float down = Earthshatter.KNOCKDOWN_TICKS - left;
                float flat = Mth.clamp(Math.min(down / 4.0F, left / 10.0F), 0.0F, 1.0F);
                if (flat > 0.0F) {
                    // A dying creature lies flat from a death time of about 13.5
                    state.deathTime = 1.0F + 12.5F * flat * flat;
                }
            }
        });
    }
}

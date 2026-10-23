package com.zestudio.crimsoncovenant.registry;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.effect.BloodFlightEffect;
import com.zestudio.crimsoncovenant.effect.BloodLossEffect;
import com.zestudio.crimsoncovenant.effect.BloodlustEffect;
import com.zestudio.crimsoncovenant.effect.HolyFlameEffect;
import com.zestudio.crimsoncovenant.effect.HolyWaterEffect;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, CrimsonCovenant.MODID);

    /// The vampire infection. It's only a visible countdown: the real infection state lives in
    /// ModAttachments.INFECTION_TICKS, so drinking milk or dying can't cure it. See Vampirism.
    public static final DeferredHolder<MobEffect, MobEffect> SANGUINE_VAMPIRIS = MOB_EFFECTS.register("sanguine_vampiris",
            () -> new MobEffect(MobEffectCategory.HARMFUL, 0x8A0303) {});

    /// Burns undead. Used by the Holy Water potion (see ModPotions).
    public static final DeferredHolder<MobEffect, HolyWaterEffect> HOLY_WATER = MOB_EFFECTS.register("holy_water", HolyWaterEffect::new);

    /// The lasting burn Holy Water leaves on undead. Water doesn't put it out.
    public static final DeferredHolder<MobEffect, HolyFlameEffect> HOLY_FLAME = MOB_EFFECTS.register("holy_flame", HolyFlameEffect::new);

    /// From the Master Vampire's bat swarm: leaves the victim cold
    public static final DeferredHolder<MobEffect, BloodLossEffect> BLOOD_LOSS = MOB_EFFECTS.register("blood_loss", BloodLossEffect::new);

    /// Given to vampires under the open sky during a blood moon
    public static final DeferredHolder<MobEffect, BloodlustEffect> BLOODLUST = MOB_EFFECTS.register("bloodlust", BloodlustEffect::new);

    // ---- Blood skills that stay active for a while. A hit of Holy Water washes all of them away. ----

    /// The Flight skill
    public static final DeferredHolder<MobEffect, BloodFlightEffect> BLOOD_FLIGHT = MOB_EFFECTS.register("blood_flight", BloodFlightEffect::new);

    /// The Armor of Ancient skill: as much protection as a full set of diamond armor
    public static final DeferredHolder<MobEffect, MobEffect> ANCIENT_ARMOR = MOB_EFFECTS.register("ancient_armor",
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0x7A0A16) {}
                    .addAttributeModifier(Attributes.ARMOR, CrimsonCovenant.id("effect.ancient_armor.armor"), 20.0, AttributeModifier.Operation.ADD_VALUE)
                    .addAttributeModifier(Attributes.ARMOR_TOUGHNESS, CrimsonCovenant.id("effect.ancient_armor.toughness"), 8.0, AttributeModifier.Operation.ADD_VALUE));

    /// The Daywalker skill: a vampire player who doesn't burn in the sun. Checked in Vampirism.
    public static final DeferredHolder<MobEffect, MobEffect> DAYWALKER = MOB_EFFECTS.register("daywalker",
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0xF2C14E) {});

    /// The Defy Death skill: deadly blows cost blood instead of a life. Checked in BloodSkills.
    public static final DeferredHolder<MobEffect, MobEffect> DEFY_DEATH = MOB_EFFECTS.register("defy_death",
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0x4A0010) {});

    /// The Stalker's Gaze skill: hostile creatures nearby are outlined through walls, for the caster's eyes only.
    /// Drawn by client.renderer.StalkersGazeHighlight.
    public static final DeferredHolder<MobEffect, MobEffect> STALKERS_GAZE = MOB_EFFECTS.register("stalkers_gaze",
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0xC0102A) {});

    private ModEffects() {}
}

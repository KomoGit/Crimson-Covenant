package com.zestudio.crimsoncovenant.registry;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.effect.BlackBloodEffect;
import com.zestudio.crimsoncovenant.effect.BloodFlightEffect;
import com.zestudio.crimsoncovenant.effect.BloodLossEffect;
import com.zestudio.crimsoncovenant.effect.BloodlustEffect;
import com.zestudio.crimsoncovenant.effect.EphemeralEffect;
import com.zestudio.crimsoncovenant.effect.GarlicEffect;
import com.zestudio.crimsoncovenant.effect.OverhealEffect;
import com.zestudio.crimsoncovenant.effect.SilverDreadEffect;
import com.zestudio.crimsoncovenant.effect.SilverWoundEffect;
import com.zestudio.crimsoncovenant.effect.HolyFlameEffect;
import com.zestudio.crimsoncovenant.effect.HolyWaterEffect;
import com.zestudio.crimsoncovenant.effect.PlagueEffect;
import com.zestudio.crimsoncovenant.effect.MiasmaEffect;
import com.zestudio.crimsoncovenant.effect.CrimsonShroudEffect;
import com.zestudio.crimsoncovenant.effect.CleanseEffect;
import com.zestudio.crimsoncovenant.curse.Curse;

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

    /// From the Black Blood potion: melee attackers are hurt back, like Thorns
    public static final DeferredHolder<MobEffect, BlackBloodEffect> BLACK_BLOOD = MOB_EFFECTS.register("black_blood", BlackBloodEffect::new);

    /// From the Potion of Garlic: undead hit softer and move slower
    public static final DeferredHolder<MobEffect, GarlicEffect> GARLIC = MOB_EFFECTS.register("garlic", GarlicEffect::new);

    /// From the Potion of Ephemeral: the drinker is a ghost, floating through walls
    public static final DeferredHolder<MobEffect, EphemeralEffect> EPHEMERAL = MOB_EFFECTS.register("ephemeral", EphemeralEffect::new);

    /// From the Potion of Silver: undead flee from where it touched them
    public static final DeferredHolder<MobEffect, SilverDreadEffect> SILVER_DREAD = MOB_EFFECTS.register("silver_dread", SilverDreadEffect::new);

    /// From Blood Parade: healing past full health, held as extra hearts that slowly fade once the orb is gone
    public static final DeferredHolder<MobEffect, OverhealEffect> OVERHEAL = MOB_EFFECTS.register("overheal", OverhealEffect::new);

    /// Thorned Shell (upgraded Armor of Ancient): melee attackers are hurt back, like Black Blood (see BlackBloodEffect)
    public static final DeferredHolder<MobEffect, MobEffect> THORNED_SHELL = MOB_EFFECTS.register("thorned_shell",
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0x6E0A16) {});

    /// From a silvered weapon: nothing heals, and a vampire's blood doesn't come back
    public static final DeferredHolder<MobEffect, SilverWoundEffect> SILVER_WOUND = MOB_EFFECTS.register("silver_wound", SilverWoundEffect::new);

    /// Carried by rats: saps the sick and spreads to those near them
    public static final DeferredHolder<MobEffect, PlagueEffect> PLAGUE = MOB_EFFECTS.register("plague", PlagueEffect::new);

    /// Breathing the Miasma over a rat-infested village: a weak poison that turns the stomach
    public static final DeferredHolder<MobEffect, MiasmaEffect> MIASMA = MOB_EFFECTS.register("miasma", MiasmaEffect::new);

    /// Exile's Mark (the Banish curse): can't hurt anyone, or heal (see curse.Curses)
    public static final DeferredHolder<MobEffect, MobEffect> BANISHED = MOB_EFFECTS.register("banished",
            () -> new MobEffect(MobEffectCategory.HARMFUL, Curse.COLOUR) {});

    /// Shackles of the Damned (the Enchain curse): chained where it stands
    public static final DeferredHolder<MobEffect, MobEffect> ENCHAINED = MOB_EFFECTS.register("enchained",
            () -> new MobEffect(MobEffectCategory.HARMFUL, 0x4A5A6A) {}
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, CrimsonCovenant.id("effect.enchained.speed"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.FLYING_SPEED, CrimsonCovenant.id("effect.enchained.flying_speed"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.JUMP_STRENGTH, CrimsonCovenant.id("effect.enchained.jump"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    /// Crimson Shroud (upgraded Flight): wings of blood that glide like an elytra, boosted by Blood Charges
    public static final DeferredHolder<MobEffect, CrimsonShroudEffect> CRIMSON_SHROUD = MOB_EFFECTS.register("crimson_shroud", CrimsonShroudEffect::new);

    /// The Potion of Cleanse: cures the Plague and the Miasma, and clears the Miasma where it's thrown
    public static final DeferredHolder<MobEffect, CleanseEffect> CLEANSE = MOB_EFFECTS.register("cleanse", CleanseEffect::new);

    private ModEffects() {}
}

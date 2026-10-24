package com.zestudio.crimsoncovenant.skill;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.registry.ModDataComponents;

import net.minecraft.world.item.ItemStack;

/// An upgrade a Blood Altar can work into a blood skill. An upgraded skill does more, and costs more blood to
/// cast. The upgrade is kept on the skill item itself (ModDataComponents.SKILL_UPGRADED), so it goes wherever the
/// skill goes.
public enum SkillUpgrade {
    /// Flight: twice as long in the air, and it ends in a diving slam that hurts what's below instead of the vampire
    CRIMSON_SHROUD("crimson_shroud", BloodSkill.FLIGHT, 10),
    /// Dash: the dash slashes every hostile it passes through
    AFTERIMAGE_STRIKE("afterimage_strike", BloodSkill.DASH, 5),
    /// Armor of Ancient: melee attackers are hurt in turn, like Black Blood
    THORNED_SHELL("thorned_shell", BloodSkill.ARMOR_OF_ANCIENT, 10),
    /// Blood Spear: a bolt of red lightning strikes whatever the spear hits
    CRIMSON_LIGHTNING("crimson_lightning", BloodSkill.BLOOD_SPEAR, 5),
    /// Boiling Hemorrhage: the boiling patch spreads every time something dies in it
    SPREADING_BOIL("spreading_boil", BloodSkill.BOILING_HEMORRHAGE, 10),
    /// Possess: the creature left behind when the possession ends serves the vampire for a minute
    ENTHRALL("enthrall", BloodSkill.POSSESS, 10),
    /// Bat Swarm: every bite brings the vampire blood
    LEECHING_SWARM("leeching_swarm", BloodSkill.BAT_SWARM, 5),
    /// Night of Terror: the fleeing spread their panic to creatures they run past
    PANIC_CONTAGION("panic_contagion", BloodSkill.NIGHT_OF_TERROR, 15),
    /// Defy Death: after a defied death, the vampire's next blow heals them by the damage it does
    FINAL_FEAST("final_feast", BloodSkill.DEFY_DEATH, 10),
    /// Shadow Step: stepping to the anchor leaves a new one behind, so the vampire can step straight back
    RETURNING_STEP("returning_step", BloodSkill.SHADOW_STEP, 5),
    /// Ephemeral Sword: every kill with it keeps it 10 seconds longer
    THIRSTING_BLADE("thirsting_blade", BloodSkill.EPHEMERAL_SWORD, 10),
    /// Death March: a creature that dies on the march rises as a zombie that serves the vampire for 30 seconds
    GRAVE_MARCH("grave_march", BloodSkill.DEATH_MARCH, 15),
    /// Stalker's Gaze: the creatures it outlines take 20% more damage from the vampire
    MARKED_PREY("marked_prey", BloodSkill.STALKERS_GAZE, 5);

    private final String name;
    private final BloodSkill skill;
    private final int extraCost;

    SkillUpgrade(String name, BloodSkill skill, int extraCost) {
        this.name = name;
        this.skill = skill;
        this.extraCost = extraCost;
    }

    public String getName() {
        return this.name;
    }

    /// Extra blood the upgraded skill costs to cast
    public int extraCost() {
        return this.extraCost;
    }

    /// The upgrade this skill can take, if any
    public static @Nullable SkillUpgrade forSkill(BloodSkill skill) {
        for (SkillUpgrade upgrade : values()) {
            if (upgrade.skill == skill) {
                return upgrade;
            }
        }
        return null;
    }

    public static boolean isUpgraded(ItemStack stack) {
        return Boolean.TRUE.equals(stack.get(ModDataComponents.SKILL_UPGRADED.get()));
    }

    /// Extra blood an upgraded skill item costs to cast, 0 for one that isn't upgraded
    public static int extraCost(ItemStack stack) {
        if (!isUpgraded(stack) || !(stack.getItem() instanceof SkillItem skillItem)) {
            return 0;
        }
        SkillUpgrade upgrade = forSkill(skillItem.skill());
        return upgrade == null ? 0 : upgrade.extraCost;
    }
}

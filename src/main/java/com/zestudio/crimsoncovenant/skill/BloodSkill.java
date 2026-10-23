package com.zestudio.crimsoncovenant.skill;

import com.zestudio.crimsoncovenant.registry.ModItems;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/// The vampire's blood skills. Each one comes as an Ancient Tome (bought from an Ancient Butler or found in
/// chests) that the Ancient Translator turns into a skill item. Skills are worn in the skill slots (see SkillSlots)
/// and cast with the skill keys, paid for in blood.
public enum BloodSkill {
    FLIGHT("flight", 40, 100, Rarity.UNCOMMON),
    DASH("dash", 20, 20, Rarity.UNCOMMON),
    ARMOR_OF_ANCIENT("armor_of_ancient", 50, 240, Rarity.RARE),
    BLOOD_SPEAR("blood_spear", 10, 10, Rarity.RARE),
    DAYWALKER("daywalker", 100, 40, Rarity.UNCOMMON),
    /// Spends the whole bar, but needs at least `cost` blood to cast
    BLOOD_PARADE("blood_parade", 30, 100, Rarity.EPIC),
    BAT_SWARM("bat_swarm", 30, 160, Rarity.UNCOMMON),
    NIGHT_OF_TERROR("night_of_terror", 60, 600, Rarity.EPIC),
    /// Every deadly hit taken while it lasts costs another 10 blood
    DEFY_DEATH("defy_death", 30, 400, Rarity.RARE),
    POSSESS("possess", 50, 600, Rarity.EPIC),
    /// Placing the anchor is free; only the jump back to it costs blood, and uses the anchor up
    SHADOW_STEP("shadow_step", 20, 20, Rarity.UNCOMMON),
    EPHEMERAL_SWORD("ephemeral_sword", 40, 200, Rarity.UNCOMMON),
    BOILING_HEMORRHAGE("boiling_hemorrhage", 45, 400, Rarity.RARE),
    DEATH_MARCH("death_march", 35, 300, Rarity.UNCOMMON),
    STALKERS_GAZE("stalkers_gaze", 25, 600, Rarity.UNCOMMON);

    private final String name;
    private final int cost;
    private final int cooldown;
    private final Rarity rarity;

    BloodSkill(String name, int cost, int cooldown, Rarity rarity) {
        this.name = name;
        this.cost = cost;
        this.cooldown = cooldown;
        this.rarity = rarity;
    }

    public String getName() {
        return this.name;
    }

    /// Blood needed to use the skill
    public int cost() {
        return this.cost;
    }

    public boolean spendsAllBlood() {
        return this == BLOOD_PARADE;
    }

    /// Ticks before the skill item can be used again
    public int cooldown() {
        return this.cooldown;
    }

    public Rarity rarity() {
        return this.rarity;
    }

    public Item skillItem() {
        return ModItems.SKILLS.get(this).get();
    }

    public Item tome() {
        return ModItems.TOMES.get(this).get();
    }
}

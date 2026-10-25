package com.zestudio.crimsoncovenant.skill;

import com.zestudio.crimsoncovenant.registry.ModItems;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/// The vampire's blood skills. Each one comes as an Ancient Tome (bought from an Ancient Butler or found in
/// chests) that the Ancient Translator turns into a skill item, taking longer the higher the skill's tier. Skills are worn in the skill slots (see SkillSlots)
/// and cast with the skill keys, paid for in blood.
public enum BloodSkill {
    FLIGHT("flight", 40, 100, Rarity.UNCOMMON, Tier.UNUSUAL),
    DASH("dash", 20, 20, Rarity.UNCOMMON, Tier.NEO),
    ARMOR_OF_ANCIENT("armor_of_ancient", 50, 240, Rarity.RARE, Tier.UNUSUAL),
    BLOOD_SPEAR("blood_spear", 10, 10, Rarity.RARE, Tier.UNUSUAL),
    DAYWALKER("daywalker", 100, 40, Rarity.UNCOMMON, Tier.NEO),
    /// Spends the whole bar, every drop, and gives none back (its kills don't feed the vampire); needs at least `cost`
    /// blood to cast
    BLOOD_PARADE("blood_parade", 30, 100, Rarity.EPIC, Tier.ANCIENT),
    BAT_SWARM("bat_swarm", 30, 160, Rarity.UNCOMMON, Tier.NEO),
    NIGHT_OF_TERROR("night_of_terror", 60, 600, Rarity.EPIC, Tier.ANCIENT),
    /// Every deadly hit taken while it lasts costs another 10 blood
    DEFY_DEATH("defy_death", 30, 400, Rarity.RARE, Tier.UNUSUAL),
    POSSESS("possess", 50, 600, Rarity.EPIC, Tier.ANCIENT),
    /// Placing the anchor is free; only the jump back to it costs blood, and uses the anchor up
    SHADOW_STEP("shadow_step", 20, 20, Rarity.UNCOMMON, Tier.NEO),
    EPHEMERAL_SWORD("ephemeral_sword", 40, 200, Rarity.UNCOMMON, Tier.NEO),
    BOILING_HEMORRHAGE("boiling_hemorrhage", 45, 400, Rarity.RARE, Tier.UNUSUAL),
    DEATH_MARCH("death_march", 35, 300, Rarity.UNCOMMON, Tier.NEO),
    STALKERS_GAZE("stalkers_gaze", 25, 600, Rarity.UNCOMMON, Tier.NEO);

    private final String name;
    private final int cost;
    private final int cooldown;
    private final Rarity rarity;
    private final Tier tier;

    /// How old and strange the tongue a skill's tome is written in: the higher the tier, the longer an Ancient
    /// Translator takes over it, fed by a single Iron Maiden (each one more feeding it makes it go that much faster)
    public enum Tier {
        /// Four minutes
        NEO("neo", 4 * 1200, ChatFormatting.AQUA),
        /// Ten minutes
        UNUSUAL("unusual", 10 * 1200, ChatFormatting.GOLD),
        /// Twenty minutes: a whole day
        ANCIENT("ancient", 20 * 1200, ChatFormatting.DARK_RED);

        private final String name;
        private final int translationTicks;
        private final ChatFormatting colour;

        Tier(String name, int translationTicks, ChatFormatting colour) {
            this.name = name;
            this.translationTicks = translationTicks;
            this.colour = colour;
        }

        /// Ticks of reading it takes, fed by one Iron Maiden
        public int translationTicks() {
            return this.translationTicks;
        }

        public Component displayName() {
            return Component.translatable("blood_skill_tier.crimsoncovenant." + this.name).withStyle(this.colour);
        }
    }

    BloodSkill(String name, int cost, int cooldown, Rarity rarity, Tier tier) {
        this.name = name;
        this.cost = cost;
        this.cooldown = cooldown;
        this.rarity = rarity;
        this.tier = tier;
    }

    public Tier tier() {
        return this.tier;
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

    /// Its name: "Dash", "Blood Parade"
    public Component displayName() {
        return this.skillItem().getName(this.skillItem().getDefaultInstance());
    }

    public Item skillItem() {
        return ModItems.SKILLS.get(this).get();
    }

    public Item tome() {
        return ModItems.TOMES.get(this).get();
    }
}

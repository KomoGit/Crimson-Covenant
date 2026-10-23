package com.zestudio.crimsoncovenant.registry;

import java.util.EnumMap;
import java.util.Map;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.item.BloodBottleItem;
import com.zestudio.crimsoncovenant.item.EphemeralSwordItem;
import com.zestudio.crimsoncovenant.item.SilverArrowItem;
import com.zestudio.crimsoncovenant.skill.AncientTomeItem;
import com.zestudio.crimsoncovenant.skill.BloodSkill;
import com.zestudio.crimsoncovenant.skill.SkillItem;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// All of the mod's items are registered here. Every item registered here shows up in the mod's creative tab
// (apart from the blood orb).
public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CrimsonCovenant.MODID);

    // Trophies dropped when a player kills a vampire or a hunter. They'll be used as trade currency.
    public static final DeferredItem<Item> TOKEN_OF_BLOOD = ITEMS.registerSimpleItem("token_of_blood", properties -> properties.rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> HUNTERS_MARK = ITEMS.registerSimpleItem("hunters_mark", properties -> properties.rarity(Rarity.UNCOMMON));
    /// Dropped by Ghosts when Holy Water banishes them
    public static final DeferredItem<Item> ECTOPLASM = ITEMS.registerSimpleItem("ectoplasm", properties -> properties.rarity(Rarity.UNCOMMON));

    // ---- Silver: ore -> raw silver -> ingot <-> nuggets -> dust (brewed into Holy Water) ----
    public static final DeferredItem<Item> RAW_SILVER = ITEMS.registerSimpleItem("raw_silver");
    public static final DeferredItem<Item> SILVER_INGOT = ITEMS.registerSimpleItem("silver_ingot");
    public static final DeferredItem<Item> SILVER_NUGGET = ITEMS.registerSimpleItem("silver_nugget");
    public static final DeferredItem<Item> SILVER_DUST = ITEMS.registerSimpleItem("silver_dust");
    /// Extra damage to undead and werewolves, and slows them
    public static final DeferredItem<SilverArrowItem> SILVER_ARROW = ITEMS.registerItem("silver_arrow", SilverArrowItem::new);

    // ---- Blood skills: an Ancient Tome of each, and the skill the Ancient Translator makes from it ----
    public static final Map<BloodSkill, DeferredItem<AncientTomeItem>> TOMES = registerTomes();
    public static final Map<BloodSkill, DeferredItem<SkillItem>> SKILLS = registerSkills();
    /// What the Blood Parade's orb looks like in flight. Not obtainable.
    public static final DeferredItem<Item> BLOOD_ORB = ITEMS.registerSimpleItem("blood_orb");
    /// Drunk to refill a vampire's blood bar. Sold by the Ancient Butler.
    public static final DeferredItem<BloodBottleItem> BLOOD_BOTTLE = ITEMS.registerItem("blood_bottle", BloodBottleItem::new,
            properties -> properties.component(DataComponents.CONSUMABLE, Consumables.defaultDrink().build()).usingConvertsTo(Items.GLASS_BOTTLE)
                    .craftRemainder(Items.GLASS_BOTTLE).stacksTo(16));
    /// From the Ephemeral Sword skill: as good as iron, unbreakable, and gone after a minute
    public static final DeferredItem<EphemeralSwordItem> EPHEMERAL_SWORD = ITEMS.registerItem("ephemeral_sword", EphemeralSwordItem::new,
            properties -> properties.sword(ToolMaterial.IRON, 3.0F, -2.4F).component(DataComponents.UNBREAKABLE, Unit.INSTANCE).rarity(Rarity.UNCOMMON));

    // ---- Block items ----
    public static final DeferredItem<BlockItem> SILVER_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.SILVER_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_SILVER_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.DEEPSLATE_SILVER_ORE);
    public static final DeferredItem<BlockItem> GRAVESTONE = ITEMS.registerSimpleBlockItem(ModBlocks.GRAVESTONE);
    public static final DeferredItem<BlockItem> MOSSY_GRAVESTONE = ITEMS.registerSimpleBlockItem(ModBlocks.MOSSY_GRAVESTONE);
    public static final DeferredItem<BlockItem> CRACKED_GRAVESTONE = ITEMS.registerSimpleBlockItem(ModBlocks.CRACKED_GRAVESTONE);
    public static final DeferredItem<BlockItem> STONE_CROSS = ITEMS.registerSimpleBlockItem(ModBlocks.STONE_CROSS);
    public static final DeferredItem<BlockItem> DEEPSLATE_OBELISK = ITEMS.registerSimpleBlockItem(ModBlocks.DEEPSLATE_OBELISK);
    public static final DeferredItem<BlockItem> STONE_TOMB = ITEMS.registerSimpleBlockItem(ModBlocks.STONE_TOMB);
    public static final DeferredItem<BlockItem> HUNTERS_TABLE = ITEMS.registerSimpleBlockItem(ModBlocks.HUNTERS_TABLE);
    public static final DeferredItem<BlockItem> BLOOD_ALTAR = ITEMS.registerSimpleBlockItem(ModBlocks.BLOOD_ALTAR);
    public static final DeferredItem<BlockItem> ANCIENT_TRANSLATOR = ITEMS.registerSimpleBlockItem(ModBlocks.ANCIENT_TRANSLATOR);

    // Entity types are registered before items, so the hunter type is available here
    public static final DeferredItem<SpawnEggItem> HUNTER_SPAWN_EGG = ITEMS.registerItem("hunter_spawn_egg", SpawnEggItem::new,
            properties -> properties.spawnEgg(ModEntities.HUNTER.get()));
    public static final DeferredItem<SpawnEggItem> MASTER_HUNTER_SPAWN_EGG = ITEMS.registerItem("master_hunter_spawn_egg", SpawnEggItem::new,
            properties -> properties.spawnEgg(ModEntities.MASTER_HUNTER.get()));
    public static final DeferredItem<SpawnEggItem> VAMPIRE_SPAWN_EGG = ITEMS.registerItem("vampire_spawn_egg", SpawnEggItem::new,
            properties -> properties.spawnEgg(ModEntities.VAMPIRE.get()));
    public static final DeferredItem<SpawnEggItem> VAMPIRE_BAT_SPAWN_EGG = ITEMS.registerItem("vampire_bat_spawn_egg", SpawnEggItem::new,
            properties -> properties.spawnEgg(ModEntities.VAMPIRE_BAT.get()));
    public static final DeferredItem<SpawnEggItem> HEADLESS_HORSEMAN_SPAWN_EGG = ITEMS.registerItem("headless_horseman_spawn_egg", SpawnEggItem::new,
            properties -> properties.spawnEgg(ModEntities.HEADLESS_HORSEMAN.get()));
    public static final DeferredItem<SpawnEggItem> GHOST_SPAWN_EGG = ITEMS.registerItem("ghost_spawn_egg", SpawnEggItem::new,
            properties -> properties.spawnEgg(ModEntities.GHOST.get()));
    public static final DeferredItem<SpawnEggItem> WEREWOLF_SPAWN_EGG = ITEMS.registerItem("werewolf_spawn_egg", SpawnEggItem::new,
            properties -> properties.spawnEgg(ModEntities.WEREWOLF.get()));
    public static final DeferredItem<SpawnEggItem> MASTER_VAMPIRE_SPAWN_EGG = ITEMS.registerItem("master_vampire_spawn_egg", SpawnEggItem::new,
            properties -> properties.spawnEgg(ModEntities.MASTER_VAMPIRE.get()));

    private ModItems() {}

    private static Map<BloodSkill, DeferredItem<AncientTomeItem>> registerTomes() {
        Map<BloodSkill, DeferredItem<AncientTomeItem>> tomes = new EnumMap<>(BloodSkill.class);
        for (BloodSkill skill : BloodSkill.values()) {
            tomes.put(skill, ITEMS.registerItem(skill.getName() + "_tome", properties -> new AncientTomeItem(skill, properties),
                    properties -> properties.stacksTo(1).rarity(skill.rarity())));
        }
        return tomes;
    }

    private static Map<BloodSkill, DeferredItem<SkillItem>> registerSkills() {
        Map<BloodSkill, DeferredItem<SkillItem>> skills = new EnumMap<>(BloodSkill.class);
        for (BloodSkill skill : BloodSkill.values()) {
            skills.put(skill, ITEMS.registerItem(skill.getName() + "_skill", properties -> new SkillItem(skill, properties),
                    properties -> properties.stacksTo(1).rarity(skill.rarity()).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)));
        }
        return skills;
    }
}

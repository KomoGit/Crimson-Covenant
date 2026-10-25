package com.zestudio.crimsoncovenant.registry;

import java.util.EnumMap;
import java.util.Map;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.item.BloodBottleItem;
import com.zestudio.crimsoncovenant.item.BloodSpearItem;
import com.zestudio.crimsoncovenant.entity.Gargoyle;
import com.zestudio.crimsoncovenant.item.EphemeralSwordItem;
import com.zestudio.crimsoncovenant.item.GargoyleStatueItem;
import com.zestudio.crimsoncovenant.item.SilverArrowItem;
import com.zestudio.crimsoncovenant.skill.AncientTomeItem;
import com.zestudio.crimsoncovenant.skill.BloodSkill;
import com.zestudio.crimsoncovenant.skill.SkillItem;
import com.zestudio.crimsoncovenant.item.GhostBottleItem;
import com.zestudio.crimsoncovenant.block.SilveredBottleBlock;
import com.zestudio.crimsoncovenant.item.IronMaidenItem;
import com.zestudio.crimsoncovenant.curse.Curse;
import com.zestudio.crimsoncovenant.curse.CursedTomeItem;
import com.zestudio.crimsoncovenant.item.WhipItem;
import com.zestudio.crimsoncovenant.guide.GuideBookItem;
import com.zestudio.crimsoncovenant.guide.GuideBooks;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// All of the mod's items are registered here. Every item registered here shows up in the mod's creative tab
// (apart from the blood orb).
public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CrimsonCovenant.MODID);

    /// Dropped when a player kills a vampire or a hunter: the vampires' currency, spent with the Ancient Butler and at
    /// the Blood Altar
    public static final DeferredItem<Item> TOKEN_OF_BLOOD = ITEMS.registerSimpleItem("token_of_blood", properties -> properties.rarity(Rarity.UNCOMMON));
    /// Dropped when a player kills a Master Vampire or a Master Hunter: spent, with Tokens of Blood, to upgrade a
    /// blood skill at the Blood Altar
    public static final DeferredItem<Item> TOKEN_OF_MASTER = ITEMS.registerSimpleItem("token_of_master", properties -> properties.rarity(Rarity.RARE));
    /// Dropped by Ghosts when Holy Water banishes them
    public static final DeferredItem<Item> ECTOPLASM = ITEMS.registerSimpleItem("ectoplasm", properties -> properties.rarity(Rarity.UNCOMMON));

    // ---- Silver: ore -> raw silver -> ingot <-> nuggets -> dust (brewed into Holy Water) ----
    public static final DeferredItem<Item> RAW_SILVER = ITEMS.registerSimpleItem("raw_silver");
    public static final DeferredItem<Item> SILVER_INGOT = ITEMS.registerSimpleItem("silver_ingot");
    public static final DeferredItem<Item> SILVER_NUGGET = ITEMS.registerSimpleItem("silver_nugget");
    public static final DeferredItem<Item> SILVER_DUST = ITEMS.registerSimpleItem("silver_dust");
    /// Minted from silver ingots: the hunters' currency, spent with the Plague Doctor
    public static final DeferredItem<Item> SILVER_COIN = ITEMS.registerSimpleItem("silver_coin");
    /// Extra damage to undead and werewolves, and slows them
    public static final DeferredItem<SilverArrowItem> SILVER_ARROW = ITEMS.registerItem("silver_arrow", SilverArrowItem::new);
    /// Plaited leather tipped and studded with silver: a long reach, and a crack that drives foes back (see WhipItem)
    public static final DeferredItem<WhipItem> WHIP = ITEMS.registerItem("whip", WhipItem::new, WhipItem::properties);

    // ---- Blood skills: an Ancient Tome of each, and the skill the Ancient Translator makes from it ----
    public static final Map<BloodSkill, DeferredItem<AncientTomeItem>> TOMES = registerTomes();
    public static final Map<BloodSkill, DeferredItem<SkillItem>> SKILLS = registerSkills();
    /// What the Blood Parade's orb looks like in flight. Not obtainable.
    public static final DeferredItem<Item> BLOOD_ORB = ITEMS.registerSimpleItem("blood_orb");
    /// Conjured by the Blood Spear skill and thrown like a trident. Not obtainable otherwise.
    public static final DeferredItem<BloodSpearItem> BLOOD_SPEAR = ITEMS.registerItem("blood_spear", BloodSpearItem::new,
            properties -> properties.stacksTo(1).rarity(Rarity.RARE));
    /// Drunk to refill a vampire's blood bar. Sold by the Ancient Butler.
    public static final DeferredItem<BloodBottleItem> BLOOD_BOTTLE = ITEMS.registerItem("blood_bottle", BloodBottleItem::new,
            properties -> properties.component(DataComponents.CONSUMABLE, Consumables.defaultDrink().build()).usingConvertsTo(Items.GLASS_BOTTLE)
                    .craftRemainder(Items.GLASS_BOTTLE).stacksTo(16));
    /// From the Ephemeral Sword skill: as good as iron, unbreakable, and gone after a minute
    public static final DeferredItem<EphemeralSwordItem> EPHEMERAL_SWORD = ITEMS.registerItem("ephemeral_sword", EphemeralSwordItem::new,
            properties -> properties.sword(ToolMaterial.IRON, 3.0F, -2.4F).component(DataComponents.UNBREAKABLE, Unit.INSTANCE).rarity(Rarity.UNCOMMON));

    /// The burst of blood a Blood Charge drives the Crimson Shroud on with, drawn as it flies. Never held: the charge is
    /// part of the skill (see skill.BloodSkills.bloodCharge)
    public static final DeferredItem<Item> BLOOD_CHARGE = ITEMS.registerSimpleItem("blood_charge");

    // ---- Garlic: planted like a carrot, brewed into the Potion of Garlic, braided to hang up ----
    public static final DeferredItem<BlockItem> GARLIC = ITEMS.registerItem("garlic",
            properties -> new BlockItem(ModBlocks.GARLIC_CROP.get(), properties.useItemDescriptionPrefix()),
            properties -> properties.food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.3F).build())
                    .compostable(ContextIntProviders.COMPOSTABLE_MEDIUM));

    // ---- Block items ----
    public static final DeferredItem<BlockItem> SILVER_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.SILVER_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_SILVER_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.DEEPSLATE_SILVER_ORE);
    public static final DeferredItem<BlockItem> SILVER_BLOCK = ITEMS.registerSimpleBlockItem(ModBlocks.SILVER_BLOCK);
    public static final DeferredItem<BlockItem> GRAVESTONE = ITEMS.registerSimpleBlockItem(ModBlocks.GRAVESTONE);
    public static final DeferredItem<BlockItem> MOSSY_GRAVESTONE = ITEMS.registerSimpleBlockItem(ModBlocks.MOSSY_GRAVESTONE);
    public static final DeferredItem<BlockItem> CRACKED_GRAVESTONE = ITEMS.registerSimpleBlockItem(ModBlocks.CRACKED_GRAVESTONE);
    public static final DeferredItem<BlockItem> STONE_CROSS = ITEMS.registerSimpleBlockItem(ModBlocks.STONE_CROSS);
    public static final DeferredItem<BlockItem> DEEPSLATE_OBELISK = ITEMS.registerSimpleBlockItem(ModBlocks.DEEPSLATE_OBELISK);
    public static final DeferredItem<BlockItem> STONE_TOMB = ITEMS.registerSimpleBlockItem(ModBlocks.STONE_TOMB);
    public static final DeferredItem<BlockItem> HUNTERS_TABLE = ITEMS.registerSimpleBlockItem(ModBlocks.HUNTERS_TABLE);
    public static final DeferredItem<BlockItem> BLOOD_ALTAR = ITEMS.registerSimpleBlockItem(ModBlocks.BLOOD_ALTAR);
    public static final DeferredItem<BlockItem> ANCIENT_TRANSLATOR = ITEMS.registerSimpleBlockItem(ModBlocks.ANCIENT_TRANSLATOR);
    public static final DeferredItem<BlockItem> GARLIC_BRAID = ITEMS.registerSimpleBlockItem(ModBlocks.GARLIC_BRAID);
    public static final DeferredItem<BlockItem> BEAR_TRAP = ITEMS.registerSimpleBlockItem(ModBlocks.BEAR_TRAP);
    public static final DeferredItem<BlockItem> SILVER_SPIKES = ITEMS.registerSimpleBlockItem(ModBlocks.SILVER_SPIKES);
    public static final DeferredItem<BlockItem> RAT_TRAP = ITEMS.registerSimpleBlockItem(ModBlocks.RAT_TRAP);
    public static final DeferredItem<BlockItem> SILVERED_BOTTLE = ITEMS.registerSimpleBlockItem(ModBlocks.SILVERED_BOTTLE);
    public static final DeferredItem<BlockItem> COFFIN = ITEMS.registerSimpleBlockItem(ModBlocks.COFFIN, properties -> properties.stacksTo(1));
    public static final DeferredItem<BlockItem> BLOOD_THRONE = ITEMS.registerSimpleBlockItem(ModBlocks.BLOOD_THRONE);
    public static final DeferredItem<BlockItem> CURSEFORGE = ITEMS.registerSimpleBlockItem(ModBlocks.CURSEFORGE);
    /// The hunters' cursed tomes, one for each curse (see curse.Curse)
    public static final Map<Curse, DeferredItem<CursedTomeItem>> CURSED_TOMES = registerCursedTomes();
    public static final DeferredItem<IronMaidenItem> IRON_MAIDEN = ITEMS.registerItem("iron_maiden", properties -> new IronMaidenItem(ModBlocks.IRON_MAIDEN.get(), properties),
            properties -> properties.stacksTo(16).useBlockDescriptionPrefix());
    /// The same bottle with a ghost in it: places the bottle with its ghost
    public static final DeferredItem<GhostBottleItem> GHOST_IN_A_BOTTLE = ITEMS.registerItem("ghost_in_a_bottle",
            properties -> new GhostBottleItem(ModBlocks.SILVERED_BOTTLE.get(), properties),
            properties -> properties.stacksTo(16).component(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(SilveredBottleBlock.GHOST, true)));
    /// A ghost crammed into a plain glass bottle, in torment: a soul, as good as a Ghost in a Bottle (see guide.Souls)
    public static final DeferredItem<BlockItem> TORMENTED_SOUL = ITEMS.registerItem("tormented_soul",
            properties -> new BlockItem(ModBlocks.TORMENTED_SOUL.get(), properties), properties -> properties.stacksTo(16));
    /// The guide every player starts with, and the vampire's own book it becomes (see guide.GuideBooks)
    public static final DeferredItem<GuideBookItem> NIGHTS_LEDGER = ITEMS.registerItem("nights_ledger",
            properties -> new GuideBookItem(GuideBooks.Book.LEDGER, properties), properties -> properties.stacksTo(1));
    public static final DeferredItem<GuideBookItem> CODEX_SANGUINIS = ITEMS.registerItem("codex_sanguinis",
            properties -> new GuideBookItem(GuideBooks.Book.CODEX, properties), properties -> properties.stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<BlockItem> SILVER_NET_TRAP = ITEMS.registerSimpleBlockItem(ModBlocks.SILVER_NET_TRAP);
    /// Carved stone, waiting to be set down and woken: silver for the hunters', blood for the vampires'
    public static final DeferredItem<GargoyleStatueItem> CATHEDRAL_GARGOYLE_STATUE = ITEMS.registerItem("cathedral_gargoyle_statue",
            properties -> new GargoyleStatueItem(ModEntities.CATHEDRAL_GARGOYLE, Gargoyle.Side.HUNTER, properties),
            properties -> properties.stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<GargoyleStatueItem> BLOOD_GARGOYLE_STATUE = ITEMS.registerItem("blood_gargoyle_statue",
            properties -> new GargoyleStatueItem(ModEntities.BLOOD_GARGOYLE, Gargoyle.Side.VAMPIRE, properties),
            properties -> properties.stacksTo(1).rarity(Rarity.UNCOMMON));

    // Entity types are registered before items, so the hunter type is available here
    public static final DeferredItem<SpawnEggItem> HUNTER_SPAWN_EGG = ITEMS.registerItem("hunter_spawn_egg", SpawnEggItem::new,
            properties -> properties.spawnEgg(ModEntities.HUNTER.get()));
    public static final DeferredItem<SpawnEggItem> PLAGUE_DOCTOR_SPAWN_EGG = ITEMS.registerItem("plague_doctor_spawn_egg", SpawnEggItem::new,
            properties -> properties.spawnEgg(ModEntities.PLAGUE_DOCTOR.get()));
    public static final DeferredItem<SpawnEggItem> RAT_SPAWN_EGG = ITEMS.registerItem("rat_spawn_egg", SpawnEggItem::new,
            properties -> properties.spawnEgg(ModEntities.RAT.get()));
    public static final DeferredItem<SpawnEggItem> HUNTER_CHEMIST_SPAWN_EGG = ITEMS.registerItem("hunter_chemist_spawn_egg", SpawnEggItem::new,
            properties -> properties.spawnEgg(ModEntities.HUNTER_CHEMIST.get()));
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

    private static Map<Curse, DeferredItem<CursedTomeItem>> registerCursedTomes() {
        Map<Curse, DeferredItem<CursedTomeItem>> tomes = new EnumMap<>(Curse.class);
        for (Curse curse : Curse.values()) {
            tomes.put(curse, ITEMS.registerItem("cursed_tome_" + curse.getSerializedName(), properties -> new CursedTomeItem(curse, properties),
                    properties -> properties.stacksTo(1).rarity(curse == Curse.DRAGONSTRIKE ? Rarity.EPIC : Rarity.RARE)));
        }
        return tomes;
    }

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

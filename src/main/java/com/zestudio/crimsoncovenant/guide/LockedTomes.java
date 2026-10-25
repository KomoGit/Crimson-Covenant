package com.zestudio.crimsoncovenant.guide;

import java.util.Optional;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.skill.AncientTomeItem;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/// No Ancient Tome turns up in the wild (in a chest, or dropped by a creature) for anyone who hasn't unlocked its skill
/// in their Codex Sanguinis (see SkillLore): the chest's opener, or whoever made the kill. Run after every other loot
/// modifier (it has the lowest priority), so it catches the tomes they add too.
public class LockedTomes extends LootModifier {
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT_MODIFIERS = DeferredRegister.create(
            NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, CrimsonCovenant.MODID);
    public static final MapCodec<LockedTomes> CODEC = RecordCodecBuilder.mapCodec(instance -> codecStart(instance).apply(instance, LockedTomes::new));

    static {
        LOOT_MODIFIERS.register("locked_tomes", () -> CODEC);
    }

    public LockedTomes(Optional<Holder<LootItemCondition>> condition, int priority) {
        super(condition, priority);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        Identifier table = context.getQueriedLootTableId();
        if (table == null || !(table.getPath().startsWith("chests/") || table.getPath().startsWith("entities/"))) {
            return generatedLoot;
        }
        Player finder = context.getOptional(LootContextParams.THIS_ENTITY) instanceof Player opener ? opener : context.getOptional(LootContextParams.LAST_DAMAGE_PLAYER);
        generatedLoot.removeIf(stack -> stack.getItem() instanceof AncientTomeItem tome && (finder == null || !SkillLore.isUnlocked(finder, tome.skill())));
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}

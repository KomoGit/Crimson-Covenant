package com.zestudio.crimsoncovenant.guide;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.block.AncientTranslatorBlock;
import com.zestudio.crimsoncovenant.registry.ModAttachments;
import com.zestudio.crimsoncovenant.skill.BloodSkill;
import com.zestudio.crimsoncovenant.skill.SkillItem;
import com.zestudio.crimsoncovenant.vampirism.Vampirism;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/// What a vampire knows of the blood skills, kept in their Codex Sanguinis. A skill must be unlocked before its Ancient
/// Tome can be found or read: standing by an Ancient Translator with souls in bottles (see Souls), the vampire picks it
/// in the Codex and the translator takes the souls. Neo skills first; a few of them open the Unusual ones, and a few of
/// those the Ancient. Once its tome's been translated (or the vampire holds the skill itself), its page is filled in.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public final class SkillLore {
    /// How near an Ancient Translator the vampire must be to unlock a skill
    public static final int TRANSLATOR_RANGE = 6;
    /// Skills of the tier below that must be unlocked first
    public static final int TO_OPEN_NEXT_TIER = 2;

    public enum Outcome { UNLOCKED, NOT_A_VAMPIRE, ALREADY, TIER_SEALED, NO_TRANSLATOR, NOT_ENOUGH_SOULS }

    private SkillLore() {}

    /// Souls it takes to unlock a skill of this tier
    public static int soulCost(BloodSkill.Tier tier) {
        return switch (tier) {
            case NEO -> 1;
            case UNUSUAL -> 2;
            case ANCIENT -> 3;
        };
    }

    public static Set<BloodSkill> unlocked(Player player) {
        return toSet(player.getData(ModAttachments.UNLOCKED_SKILLS));
    }

    public static Set<BloodSkill> translated(Player player) {
        return toSet(player.getData(ModAttachments.TRANSLATED_SKILLS));
    }

    public static boolean isUnlocked(Player player, BloodSkill skill) {
        return player.getData(ModAttachments.UNLOCKED_SKILLS).contains(skill.getName());
    }

    public static boolean isTranslated(Player player, BloodSkill skill) {
        return player.getData(ModAttachments.TRANSLATED_SKILLS).contains(skill.getName());
    }

    /// Whether skills of this tier can be unlocked yet: Neo always; the others once enough of the tier below are
    public static boolean isTierOpen(Player player, BloodSkill.Tier tier) {
        BloodSkill.Tier below = switch (tier) {
            case NEO -> null;
            case UNUSUAL -> BloodSkill.Tier.NEO;
            case ANCIENT -> BloodSkill.Tier.UNUSUAL;
        };
        return below == null || unlocked(player).stream().filter(skill -> skill.tier() == below).count() >= TO_OPEN_NEXT_TIER;
    }

    public static boolean isNearTranslator(Player player) {
        BlockPos at = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-TRANSLATOR_RANGE, -2, -TRANSLATOR_RANGE), at.offset(TRANSLATOR_RANGE, 2, TRANSLATOR_RANGE))) {
            if (player.level().getBlockState(pos).getBlock() instanceof AncientTranslatorBlock) {
                return true;
            }
        }
        return false;
    }

    /// What unlocking the skill now would come to, without doing it
    public static Outcome check(Player player, BloodSkill skill) {
        if (!Vampirism.isVampire(player)) {
            return Outcome.NOT_A_VAMPIRE;
        }
        if (isUnlocked(player, skill)) {
            return Outcome.ALREADY;
        }
        if (!isTierOpen(player, skill.tier())) {
            return Outcome.TIER_SEALED;
        }
        if (!isNearTranslator(player)) {
            return Outcome.NO_TRANSLATOR;
        }
        if (Souls.count(player) < soulCost(skill.tier())) {
            return Outcome.NOT_ENOUGH_SOULS;
        }
        return Outcome.UNLOCKED;
    }

    /// The vampire picks the skill in the Codex: if they can, the translator nearby takes the souls, and it's unlocked
    public static Outcome unlock(ServerPlayer player, BloodSkill skill) {
        Outcome outcome = check(player, skill);
        if (outcome == Outcome.UNLOCKED && Souls.spend(player, soulCost(skill.tier()))) {
            add(player, ModAttachments.UNLOCKED_SKILLS.get(), skill);
            player.level().playSound(null, player.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.0F, 0.7F);
            player.level().playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F, 0.6F);
        }
        Component message = switch (outcome) {
            case UNLOCKED -> Component.translatable("message.crimsoncovenant.codex.unlocked", skill.displayName());
            case NOT_A_VAMPIRE -> Component.translatable("message.crimsoncovenant.codex.not_a_vampire");
            case ALREADY -> Component.translatable("message.crimsoncovenant.codex.already", skill.displayName());
            case TIER_SEALED -> Component.translatable("message.crimsoncovenant.codex.sealed", skill.tier().displayName());
            case NO_TRANSLATOR -> Component.translatable("message.crimsoncovenant.codex.no_translator");
            case NOT_ENOUGH_SOULS -> Component.translatable("message.crimsoncovenant.codex.souls", soulCost(skill.tier()), Souls.count(player));
        };
        player.sendOverlayMessage(message.copy().withStyle(outcome == Outcome.UNLOCKED ? ChatFormatting.DARK_RED : ChatFormatting.GRAY));
        return outcome;
    }

    /// Its tome's been translated (or the vampire has the skill in hand): its page in the Codex is filled in
    public static void markTranslated(Player player, BloodSkill skill) {
        if (!isTranslated(player, skill)) {
            add(player, ModAttachments.TRANSLATED_SKILLS.get(), skill);
            player.sendSystemMessage(Component.translatable("message.crimsoncovenant.codex.translated", skill.displayName()).withStyle(ChatFormatting.DARK_RED));
        }
    }

    // A skill turning up in a vampire's hands (bought, traded, or from before the Codex) fills in its page too
    @SubscribeEvent
    static void learnFromSkillsInHand(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 40 != 0 || !Vampirism.isVampire(player)) {
            return;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof SkillItem skill) {
                markTranslated(player, skill.skill());
            }
        }
    }

    private static void add(Player player, net.neoforged.neoforge.attachment.AttachmentType<List<String>> type, BloodSkill skill) {
        List<String> names = new ArrayList<>(player.getData(type));
        if (!names.contains(skill.getName())) {
            names.add(skill.getName());
            player.setData(type, List.copyOf(names));
        }
    }

    private static Set<BloodSkill> toSet(List<String> names) {
        Set<BloodSkill> skills = EnumSet.noneOf(BloodSkill.class);
        for (BloodSkill skill : BloodSkill.values()) {
            if (names.contains(skill.getName())) {
                skills.add(skill);
            }
        }
        return skills;
    }
}

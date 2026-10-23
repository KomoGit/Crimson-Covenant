package com.zestudio.crimsoncovenant.skill;

import java.util.ArrayList;
import java.util.List;

import com.zestudio.crimsoncovenant.registry.ModAttachments;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/// The skills a player wears: SLOTS slots, each holding one skill item and cast with its own key. Skills are put
/// on and taken off in the Blood Skills screen, or put on by using the skill item. They stay on through death.
public final class SkillSlots {
    public static final int SLOTS = 4;

    private SkillSlots() {}

    /// The worn skills, always SLOTS long (empty stacks for empty slots)
    public static List<ItemStack> get(Player player) {
        List<ItemStack> saved = player.getData(ModAttachments.EQUIPPED_SKILLS);
        List<ItemStack> slots = new ArrayList<>(SLOTS);
        for (int i = 0; i < SLOTS; i++) {
            slots.add(i < saved.size() ? saved.get(i) : ItemStack.EMPTY);
        }
        return slots;
    }

    public static ItemStack get(Player player, int slot) {
        return get(player).get(slot);
    }

    public static void set(Player player, int slot, ItemStack stack) {
        List<ItemStack> slots = get(player);
        slots.set(slot, stack.copy());
        player.setData(ModAttachments.EQUIPPED_SKILLS, List.copyOf(slots));
    }

    public static boolean isWearing(Player player, BloodSkill skill) {
        return get(player).stream().anyMatch(stack -> stack.is(skill.skillItem()));
    }

    /// Puts the skill on in the first free slot. False if it's already worn or every slot is taken.
    public static boolean equip(Player player, ItemStack stack) {
        if (!(stack.getItem() instanceof SkillItem skillItem) || isWearing(player, skillItem.skill())) {
            return false;
        }
        List<ItemStack> slots = get(player);
        for (int i = 0; i < SLOTS; i++) {
            if (slots.get(i).isEmpty()) {
                set(player, i, stack.split(1));
                return true;
            }
        }
        return false;
    }

    /// The player's skill slots as a container for the Blood Skills screen. Every change is saved straight back.
    public static class Container extends SimpleContainer {
        private final Player player;

        public Container(Player player) {
            super(SLOTS);
            this.player = player;
            List<ItemStack> slots = get(player);
            for (int i = 0; i < SLOTS; i++) {
                super.setItem(i, slots.get(i).copy());
            }
        }

        @Override
        public void setChanged() {
            super.setChanged();
            List<ItemStack> slots = new ArrayList<>(SLOTS);
            for (int i = 0; i < SLOTS; i++) {
                slots.add(this.getItem(i).copy());
            }
            this.player.setData(ModAttachments.EQUIPPED_SKILLS, List.copyOf(slots));
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}

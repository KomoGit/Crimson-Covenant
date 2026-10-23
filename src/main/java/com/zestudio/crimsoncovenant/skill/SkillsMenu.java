package com.zestudio.crimsoncovenant.skill;

import com.zestudio.crimsoncovenant.registry.ModMenus;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/// The Blood Skills screen: the player's skill slots above their inventory, laid out like a hopper's
public class SkillsMenu extends AbstractContainerMenu {
    public static final int SLOT_X = 53;
    public static final int SLOT_Y = 20;
    private final Container skills;

    // Client side: the real slots are synced from the server
    public SkillsMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(SkillSlots.SLOTS));
    }

    public SkillsMenu(int containerId, Inventory inventory, Container skills) {
        super(ModMenus.SKILLS.get(), containerId);
        this.skills = skills;
        checkContainerSize(skills, SkillSlots.SLOTS);
        for (int i = 0; i < SkillSlots.SLOTS; i++) {
            this.addSlot(new SkillSlot(skills, i, SLOT_X + i * 18, SLOT_Y));
        }
        this.addStandardInventorySlots(inventory, 8, 51);
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack clicked = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            clicked = stack.copy();
            if (slotIndex < SkillSlots.SLOTS) {
                if (!this.moveItemStackTo(stack, SkillSlots.SLOTS, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stack, 0, SkillSlots.SLOTS, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return clicked;
    }

    /// Takes one skill, and not one that's already worn in another slot
    private static class SkillSlot extends Slot {
        SkillSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (!(stack.getItem() instanceof SkillItem)) {
                return false;
            }
            for (int i = 0; i < this.container.getContainerSize(); i++) {
                if (i != this.getContainerSlot() && this.container.getItem(i).is(stack.getItem())) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}

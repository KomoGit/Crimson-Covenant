package com.zestudio.crimsoncovenant.curse;

import com.zestudio.crimsoncovenant.registry.ModBlocks;
import com.zestudio.crimsoncovenant.registry.ModDataComponents;
import com.zestudio.crimsoncovenant.registry.ModItems;
import com.zestudio.crimsoncovenant.registry.ModMenus;
import com.zestudio.crimsoncovenant.registry.ModTags;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/// The Curseforge at work: a cursed tome, the weapon to bear its curse, and a Ghost in a Bottle to carry the curse over.
/// The result is the weapon cursed (one level deeper, if it bore that curse already); the tome and the ghost are spent,
/// and the empty bottle comes back.
public class CurseforgeMenu extends ItemCombinerMenu {
    public static final int TOME_SLOT = 0;
    public static final int WEAPON_SLOT = 1;
    public static final int GHOST_SLOT = 2;
    public static final int RESULT_SLOT = 3;

    public CurseforgeMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, ContainerLevelAccess.NULL);
    }

    public CurseforgeMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(ModMenus.CURSEFORGE.get(), containerId, inventory, access, ItemCombinerMenuSlotDefinition.create()
                .withSlot(TOME_SLOT, 8, 48, stack -> stack.getItem() instanceof CursedTomeItem)
                .withSlot(WEAPON_SLOT, 26, 48, stack -> stack.is(ModTags.Items.CURSEABLE_MELEE) || stack.is(ModTags.Items.CURSEABLE_RANGED))
                .withSlot(GHOST_SLOT, 44, 48, stack -> stack.is(ModItems.GHOST_IN_A_BOTTLE.get()))
                .withResultSlot(RESULT_SLOT, 98, 48)
                .build());
    }

    /// The weapon with the tome's curse laid on it, or empty if it can't take it: the wrong kind of weapon, another
    /// curse already on it, or the curse as deep as it goes
    public static ItemStack forge(ItemStack weapon, ItemStack tome) {
        if (weapon.isEmpty() || !(tome.getItem() instanceof CursedTomeItem cursedTome) || !cursedTome.curse().fits(weapon)) {
            return ItemStack.EMPTY;
        }
        Curse curse = cursedTome.curse();
        WeaponCurse laid = WeaponCurse.of(weapon);
        if (laid != null && (laid.curse() != curse || laid.level() >= Curse.MAX_LEVEL)) {
            return ItemStack.EMPTY;
        }
        ItemStack cursed = weapon.copyWithCount(1);
        cursed.set(ModDataComponents.CURSE.get(), new WeaponCurse(curse, laid == null ? 1 : laid.level() + 1));
        return cursed;
    }

    @Override
    public void createResult() {
        ItemStack ghost = this.inputSlots.getItem(GHOST_SLOT);
        ItemStack result = ghost.is(ModItems.GHOST_IN_A_BOTTLE.get())
                ? forge(this.inputSlots.getItem(WEAPON_SLOT), this.inputSlots.getItem(TOME_SLOT))
                : ItemStack.EMPTY;
        this.resultSlots.setItem(0, result);
    }

    @Override
    protected boolean mayPickup(Player player, boolean hasItem) {
        return hasItem && !this.resultSlots.getItem(0).isEmpty();
    }

    @Override
    protected void onTake(Player player, ItemStack carried) {
        carried.onCraftedBy(player, carried.getCount());
        for (int slot : new int[] {TOME_SLOT, WEAPON_SLOT, GHOST_SLOT}) {
            ItemStack stack = this.inputSlots.getItem(slot);
            stack.shrink(1);
            this.inputSlots.setItem(slot, stack);
        }
        // The ghost goes into the curse; its bottle comes back
        player.getInventory().placeItemBackInInventory(new ItemStack(ModItems.SILVERED_BOTTLE.get()), Prediction.SERVER_ONLY);
        this.access.execute((level, pos) -> {
            level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.8F, 0.7F);
            level.playSound(null, pos, SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS, 1.0F, 0.8F);
        });
    }

    @Override
    protected boolean isValidBlock(BlockState state) {
        return state.is(ModBlocks.CURSEFORGE.get());
    }
}

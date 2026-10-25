package com.zestudio.crimsoncovenant.curse;

import com.zestudio.crimsoncovenant.registry.ModBlocks;
import com.zestudio.crimsoncovenant.registry.ModDataComponents;
import com.zestudio.crimsoncovenant.registry.ModItems;
import com.zestudio.crimsoncovenant.registry.ModMenus;
import com.zestudio.crimsoncovenant.registry.ModTags;
import com.zestudio.crimsoncovenant.guide.Souls;

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
import net.minecraft.world.inventory.DataSlot;

/// The Curseforge at work: a cursed tome, the weapon to bear its curse, and souls (Ghosts in a Bottle or Tormented
/// Souls) to carry the curse over: as many as it costs (see soulCost). The result is the weapon cursed (one level
/// deeper, if it bore that curse already); the tome and the souls are spent, and their empty bottles come back.
public class CurseforgeMenu extends ItemCombinerMenu {
    public static final int TOME_SLOT = 0;
    public static final int WEAPON_SLOT = 1;
    public static final int GHOST_SLOT = 2;
    public static final int RESULT_SLOT = 3;
    /// Already haunted, a weapon takes this much more to haunt deeper (rounded down)
    private static final double DEEPER = 1.25;

    /// The souls the haunting in hand will cost, synced to the screen; 0 when there's nothing to forge
    private final DataSlot cost = DataSlot.standalone();

    public CurseforgeMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, ContainerLevelAccess.NULL);
    }

    public CurseforgeMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(ModMenus.CURSEFORGE.get(), containerId, inventory, access, ItemCombinerMenuSlotDefinition.create()
                .withSlot(TOME_SLOT, 8, 48, stack -> stack.getItem() instanceof CursedTomeItem)
                .withSlot(WEAPON_SLOT, 26, 48, stack -> stack.is(ModTags.Items.CURSEABLE_MELEE) || stack.is(ModTags.Items.CURSEABLE_RANGED))
                .withSlot(GHOST_SLOT, 44, 48, Souls::isSoul)
                .withResultSlot(RESULT_SLOT, 98, 48)
                .build());
        this.addDataSlot(this.cost);
    }

    /// How many souls it takes to lay `curse` on `weapon`: the curse's own price (see Curse.souls), twice that if the
    /// weapon's enchanted, and a quarter more again (rounded down) if it's haunted already
    public static int soulCost(ItemStack weapon, Curse curse) {
        int souls = curse.souls();
        if (weapon.isEnchanted()) {
            souls *= 2;
        }
        if (WeaponCurse.of(weapon) != null) {
            souls = (int) Math.floor(souls * DEEPER);
        }
        return souls;
    }

    public int getCost() {
        return this.cost.get();
    }

    /// The weapon with the tome's curse laid on it, or empty if it can't take it: the wrong kind of weapon, another
    /// curse already on it, or the curse as deep as it goes
    public static ItemStack forge(ItemStack weapon, ItemStack tome) {
        // A haunted weapon wears out unmended: one that mends itself won't take a haunting
        if (weapon.isEmpty() || !(tome.getItem() instanceof CursedTomeItem cursedTome) || !cursedTome.curse().fits(weapon) || HauntedWear.mends(weapon)) {
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
        ItemStack weapon = this.inputSlots.getItem(WEAPON_SLOT);
        ItemStack tome = this.inputSlots.getItem(TOME_SLOT);
        ItemStack result = forge(weapon, tome);
        this.cost.set(result.isEmpty() ? 0 : soulCost(weapon, ((CursedTomeItem) tome.getItem()).curse()));
        // Shown, but not to be had, without the souls to pay for it
        this.resultSlots.setItem(0, Souls.isSoul(this.inputSlots.getItem(GHOST_SLOT)) ? result : ItemStack.EMPTY);
    }

    /// Whether the souls put in will pay for the haunting
    public boolean canAfford() {
        return this.cost.get() > 0 && this.inputSlots.getItem(GHOST_SLOT).getCount() >= this.cost.get();
    }

    @Override
    protected boolean mayPickup(Player player, boolean hasItem) {
        return hasItem && !this.resultSlots.getItem(0).isEmpty() && (this.canAfford() || player.hasInfiniteMaterials());
    }

    @Override
    protected void onTake(Player player, ItemStack carried) {
        carried.onCraftedBy(player, carried.getCount());
        ItemStack souls = this.inputSlots.getItem(GHOST_SLOT);
        int spent = Math.min(Math.max(this.cost.get(), 1), souls.getCount());
        ItemStack bottles = Souls.emptyBottle(souls).copyWithCount(spent);
        for (int slot : new int[] {TOME_SLOT, WEAPON_SLOT, GHOST_SLOT}) {
            ItemStack stack = this.inputSlots.getItem(slot);
            stack.shrink(slot == GHOST_SLOT ? spent : 1);
            this.inputSlots.setItem(slot, stack);
        }
        this.cost.set(0);
        // The ghosts go into the curse; their bottles come back
        player.getInventory().placeItemBackInInventory(bottles, Prediction.SERVER_ONLY);
        this.access.execute((level, pos) -> {
            level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.8F, 0.7F);
            level.playSound(null, pos, SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS, 1.0F, 0.8F);
            // The haunting goes into the weapon with a wail, and the old chains rattle
            level.playSound(null, pos, SoundEvents.AMBIENT_SOUL_SAND_VALLEY_ADDITIONS.value(), SoundSource.BLOCKS, 1.0F, 0.9F);
            level.playSound(null, pos, SoundEvents.CHAIN_HIT, SoundSource.BLOCKS, 0.8F, 0.5F);
        });
    }

    @Override
    protected boolean isValidBlock(BlockState state) {
        return state.is(ModBlocks.CURSEFORGE.get());
    }
}

package com.zestudio.crimsoncovenant.item;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zestudio.crimsoncovenant.registry.ModDataComponents;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleSmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.level.Level;

/// Silvering at the smithing table: a weapon (base) and a silver ingot (addition), no template needed. The weapon
/// comes back the same, enchantments and all, but silvered (see Silvering). A weapon can only be silvered once.
public class SilveringRecipe extends SimpleSmithingRecipe {
    public static final MapCodec<SilveringRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(
            i -> i.group(
                    Recipe.CommonInfo.MAP_CODEC.forGetter(o -> o.commonInfo),
                    Ingredient.CODEC.fieldOf("base").forGetter(o -> o.base),
                    Ingredient.CODEC.fieldOf("addition").forGetter(o -> o.addition))
                    .apply(i, SilveringRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, SilveringRecipe> STREAM_CODEC = StreamCodec.composite(
            Recipe.CommonInfo.STREAM_CODEC, o -> o.commonInfo,
            Ingredient.CONTENTS_STREAM_CODEC, o -> o.base,
            Ingredient.CONTENTS_STREAM_CODEC, o -> o.addition,
            SilveringRecipe::new);
    public static final RecipeSerializer<SilveringRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    private final Ingredient base;
    private final Ingredient addition;

    public SilveringRecipe(Recipe.CommonInfo commonInfo, Ingredient base, Ingredient addition) {
        super(commonInfo);
        this.base = base;
        this.addition = addition;
    }

    @Override
    public boolean matches(SmithingRecipeInput input, Level level) {
        return super.matches(input, level) && !Silvering.isSilvered(input.base());
    }

    @Override
    public ItemStack assemble(SmithingRecipeInput input) {
        ItemStack silvered = input.base().copyWithCount(1);
        silvered.set(ModDataComponents.SILVERED.get(), true);
        return silvered;
    }

    @Override
    public Optional<Ingredient> templateIngredient() {
        return Optional.empty();
    }

    @Override
    public Ingredient baseIngredient() {
        return this.base;
    }

    @Override
    public Optional<Ingredient> additionIngredient() {
        return Optional.of(this.addition);
    }

    @Override
    public RecipeSerializer<SilveringRecipe> getSerializer() {
        return SERIALIZER;
    }

    @Override
    protected PlacementInfo createPlacementInfo() {
        return PlacementInfo.createFromOptionals(List.of(Optional.empty(), Optional.of(this.base), Optional.of(this.addition)));
    }

    @Override
    public List<RecipeDisplay> display() {
        SlotDisplay base = this.base.display();
        return List.of(new SmithingRecipeDisplay(SlotDisplay.Empty.INSTANCE, base, this.addition.display(), base,
                new SlotDisplay.ItemSlotDisplay(Items.SMITHING_TABLE)));
    }
}

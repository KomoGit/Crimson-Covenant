package com.zestudio.crimsoncovenant.item;

import java.util.function.Consumer;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.entity.BloodSpear;
import com.zestudio.crimsoncovenant.entity.WhipLash;
import com.zestudio.crimsoncovenant.registry.ModDataComponents;
import com.zestudio.crimsoncovenant.curse.Grapple;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/// The whip: plaited werewolf hide, studded and tipped with silver. It reaches twice as far as an arm, cracks out in a lash
/// with every swing (see WhipLash), cutting through everything along it, and drives whatever it strikes back a few
/// blocks, the way the Blood Spear does: into a wall, and it's slammed against it. The silver in it bites anything weak to
/// silver as a silvered weapon does (see Silvering). Mended with leather. Right-click and its lash catches the first
/// creature it reaches and yanks it in; hooked round a block instead, with Wraith's Tether laid on it, it hauls its
/// wielder in (see curse.Grapple).
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public class WhipItem extends Item {
    public static final int DURABILITY = 320;
    /// Blocks of extra reach: 6 in all, against an arm's 3
    public static final double EXTRA_REACH = 3.0;
    /// The drive: about 3 blocks back, unless a wall stops it first
    public static final double DRIVE_SPEED = 0.8;
    public static final int DRIVE_TICKS = 4;
    public static final float SLAM_DAMAGE = 3.0F;
    /// How far the swing must have come back round (the attack indicator) before the whip will crack again
    public static final float READY = 0.9F;
    /// How soon after a right-click the whip can be right-clicked again
    public static final int PULL_COOLDOWN = 20;

    public WhipItem(Item.Properties properties) {
        super(properties);
    }

    /// A whip's properties: its durability, mending, blow, swing, reach, and its silver
    public static Item.Properties properties(Item.Properties properties) {
        return properties.durability(DURABILITY).repairable(Items.LEATHER).component(DataComponents.WEAPON, new Weapon(1))
                .component(ModDataComponents.SILVERED.get(), true)
                .attributes(ItemAttributeModifiers.builder()
                        .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 4.0, AttributeModifier.Operation.ADD_VALUE),
                                EquipmentSlotGroup.MAINHAND)
                        .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, -2.8, AttributeModifier.Operation.ADD_VALUE),
                                EquipmentSlotGroup.MAINHAND)
                        .add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(CrimsonCovenant.id("whip.reach"), EXTRA_REACH,
                                AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                        .build());
    }

    public static boolean isWhip(ItemStack stack) {
        return stack.getItem() instanceof WhipItem;
    }

    /// A blow with the whip drives its victim back, away from whoever struck it; not while it's reeling from a slam
    /// (the slam itself is the wielder's blow, and would only drive it back into the same wall)
    @SubscribeEvent
    static void crackBack(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        ItemStack weapon = event.getSource().getWeaponItem();
        if (weapon == null || !isWhip(weapon) || !(event.getSource().getEntity() instanceof LivingEntity attacker)
                || event.getSource().getDirectEntity() != attacker || attacker == victim || !victim.isAlive() || !(victim.level() instanceof ServerLevel)
                || BloodSpear.isStaggered(victim)) {
            return;
        }
        Vec3 away = victim.position().subtract(attacker.position()).horizontal();
        if (away.lengthSqr() < 1.0E-4) {
            return;
        }
        BloodSpear.drive(victim, away.normalize().scale(DRIVE_SPEED), DRIVE_TICKS, attacker, SLAM_DAMAGE, false);
    }

    /// Whether the player's whip can crack now: swung back round, and their last lash drawn in
    public static boolean isReady(Player player) {
        return isWhip(player.getMainHandItem()) && player.getAttackStrengthScale(0.5F) >= READY && !WhipLash.isLashing(player);
    }

    /// The swing itself: the lash cracks out, and lands the blow (sent by the player's client on the attack key, see
    /// client.ModKeyMappings)
    public static void crack(ServerPlayer player) {
        if (isReady(player)) {
            WhipLash.crack(player);
        }
    }

    // Right-click: a yank, or a grapple
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack whip = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND || WhipLash.isLashing(player) || Grapple.isGrappling(player)) {
            return InteractionResult.PASS;
        }
        player.getCooldowns().addCooldown(whip, PULL_COOLDOWN);
        if (level instanceof ServerLevel server && !Grapple.hook(server, player, whip)) {
            WhipLash.crack(player, WhipLash.Kind.PULL, null, WhipLash.LASH_TICKS);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.crimsoncovenant.whip.hint").withStyle(ChatFormatting.GRAY));
    }
}

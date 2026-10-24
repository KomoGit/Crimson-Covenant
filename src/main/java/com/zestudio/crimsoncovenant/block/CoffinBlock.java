package com.zestudio.crimsoncovenant.block;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.entity.Hunter;
import com.zestudio.crimsoncovenant.entity.Werewolf;
import com.zestudio.crimsoncovenant.vampirism.Vampirism;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.clock.ClockTimeMarkers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.ClockAdjustment;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;

/// A coffin: six-sided, of polished ebony with brass trim and handles, lined with quilted red velvet and a satin
/// pillow, two blocks long like a bed and slept in like one. Its lid closes over whoever sleeps inside. Unlike
/// a bed it doesn't care what time it is: sleep in it by day and you wake at nightfall (if everyone else is asleep
/// too), and lying in it keeps the sun off a vampire. It sets your spawn as a bed does.
///
/// It's the only place a vampire can sleep: once turned, a player can't use a bed at all. A vampire who sleeps the
/// night (or day) through in a coffin wakes having fed on the life of any villagers held captive in Iron Maidens
/// around it (see IronMaidenBlock).
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public class CoffinBlock extends AbstractBedBlock {
    /// Sleep any time; set your spawn; never explodes, whatever the dimension
    public static final BedRule COFFIN_RULE = new BedRule(BedRule.Rule.ALWAYS, BedRule.Rule.ALWAYS, false, false, Optional.empty());
    /// The sleeper lies on the velvet, inside the walls
    private static final double SLEEP_HEIGHT = 3.0 / 16.0;
    // The six-sided outline, head end to foot: (from, to, half the width), in sixteenths along the coffin's length
    private static final double[][] OUTLINE = {{0, 2, 4.5}, {2, 4, 5.5}, {4, 8, 6.5}, {8, 16, 7.0}, {16, 21, 6.5}, {21, 26, 5.5}, {26, 30, 4.5}, {30, 32, 4.0}};
    private static final double TOP = 10.0;
    private static final double LID_TOP = 11.5;
    // Drawn for a head end facing north
    private static final Map<Direction, VoxelShape> HEAD_SHAPES = Shapes.rotateHorizontal(outline(true, TOP));
    private static final Map<Direction, VoxelShape> FOOT_SHAPES = Shapes.rotateHorizontal(outline(false, TOP));
    private static final Map<Direction, VoxelShape> HEAD_CLOSED = Shapes.rotateHorizontal(outline(true, LID_TOP));
    private static final Map<Direction, VoxelShape> FOOT_CLOSED = Shapes.rotateHorizontal(outline(false, LID_TOP));

    private static VoxelShape outline(boolean head, double height) {
        VoxelShape shape = Shapes.empty();
        for (double[] segment : OUTLINE) {
            if ((segment[0] < 16) == head) {
                double offset = head ? 0 : 16;
                shape = Shapes.or(shape, Block.box(8 - segment[2], 0, segment[0] - offset, 8 + segment[2], height, segment[1] - offset));
            }
        }
        return shape;
    }

    public CoffinBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // The lid closes over whoever's sleeping inside
        boolean closed = state.getValue(OCCUPIED);
        boolean head = state.getValue(PART) == BedPart.HEAD;
        return (head ? closed ? HEAD_CLOSED : HEAD_SHAPES : closed ? FOOT_CLOSED : FOOT_SHAPES).get(state.getValue(FACING));
    }

    @Override
    public BedRule getBedRule(Level level, BlockPos pos) {
        return COFFIN_RULE;
    }

    // Never asked, since the coffin has a rule of its own
    @Override
    protected EnvironmentAttribute<BedRule> getBedEnvironmentAttribute() {
        return EnvironmentAttributes.BED_RULE;
    }

    @Override
    protected InteractionResult destroyOnUse(BlockState state, Level level, BlockPos pos, Player player) {
        return InteractionResult.PASS;
    }

    @Override
    protected void destroyOnLeave(Level level, BlockPos pos) {
    }

    @Override
    public OptionalDouble getSleepHeight(BlockState state, Level level, BlockPos pos) {
        return state.is(this) ? OptionalDouble.of(SLEEP_HEIGHT) : OptionalDouble.empty();
    }

    /// Whether the sleeper is lying in a coffin
    public static boolean isInCoffin(LivingEntity sleeper) {
        return sleeper.isSleeping() && sleeper.getSleepingPos().map(pos -> sleeper.level().getBlockState(pos).getBlock() instanceof CoffinBlock).orElse(false);
    }

    /// Whether a vampire can rest here: only in a coffin
    public static boolean barsVampires(BlockState state) {
        return state.getBlock() instanceof AbstractBedBlock && !(state.getBlock() instanceof CoffinBlock);
    }

    // ---- Vampires sleep in coffins, and only coffins ----

    @SubscribeEvent
    static void vampiresShunBeds(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (player.isSecondaryUseActive() || !Vampirism.isVampire(player) || !barsVampires(event.getLevel().getBlockState(event.getPos()))) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        if (!event.getLevel().isClientSide()) {
            player.sendOverlayMessage(Component.translatable("message.crimsoncovenant.vampire_needs_coffin").withStyle(ChatFormatting.DARK_RED));
        }
    }

    // In case anything else puts a vampire to bed
    @SubscribeEvent
    static void vampiresCantSleepInBeds(CanPlayerSleepEvent event) {
        if (Vampirism.isVampire(event.getEntity()) && barsVampires(event.getLevel().getBlockState(event.getPos()))) {
            event.setProblem(new Player.BedSleepingProblem(Component.translatable("message.crimsoncovenant.vampire_needs_coffin")));
        }
    }

    /// The creatures of the night are a vampire's kin: vampires, ghosts, bats and the like prowling about don't keep one
    /// from resting in a coffin, as they'd keep a living sleeper from their bed. Only what's out for the vampire's
    /// blood does: a monster with them as its target, a werewolf, or a hunter.
    @SubscribeEvent
    static void kinLetVampiresRest(CanPlayerSleepEvent event) {
        Player player = event.getEntity();
        if (!Vampirism.isVampire(player) || !(event.getState().getBlock() instanceof CoffinBlock) || player.isCreative()) {
            return;
        }
        if (isThreatened(event.getLevel(), player, event.getPos())) {
            event.setProblem(Player.BedSleepingProblem.NOT_SAFE);
        } else if (event.getProblem() == Player.BedSleepingProblem.NOT_SAFE) {
            event.setProblem(null);
        }
    }

    /// Whether anything near the coffin at `pos` would come for the vampire in it
    public static boolean isThreatened(Level level, Player vampire, BlockPos pos) {
        AABB around = new AABB(pos).inflate(8.0, 5.0, 8.0);
        return !level.getEntitiesOfClass(Mob.class, around, mob -> mob.isAlive()
                && (mob instanceof Hunter || mob instanceof Werewolf || (mob instanceof Enemy && mob.getTarget() == vampire))).isEmpty();
    }

    /// Sleeping the day away (in coffins: nothing else lets you) wakes you at nightfall, not the next morning
    @SubscribeEvent
    static void wakeAtNightfall(SleepFinishedTimeEvent event) {
        if (event.getLevel() instanceof ServerLevel level && !level.isDarkOutside()) {
            event.setAdjustment(new ClockAdjustment.Marker(ClockTimeMarkers.NIGHT));
        }
    }
}

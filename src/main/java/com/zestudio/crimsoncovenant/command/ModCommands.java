package com.zestudio.crimsoncovenant.command;

import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.hunter.HunterGear;
import com.zestudio.crimsoncovenant.hunter.HunterRanks;
import com.zestudio.crimsoncovenant.vampirism.VampireAge;
import com.zestudio.crimsoncovenant.vampirism.Vampirism;
import com.zestudio.crimsoncovenant.world.BloodMoon;
import com.zestudio.crimsoncovenant.world.HunterPartySpawner;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/// Operator commands, mostly for testing:
/// - `/crimsoncovenant vampirism <players> (infect|turn|cure)`
/// - `/crimsoncovenant vampirism <players> age <days>`: sets how long vampires have been vampires (0-30 days)
/// - `/crimsoncovenant hunters spawn_now`: spawns a hunter party nearby the way the night spawner would
/// - `/crimsoncovenant hunter <players> rank (none|initiate|tracker|slayer|warden|grand_hunter)`: sets their hunter rank
///   outright, no Silver Coin, tokens or silver asked
/// - `/crimsoncovenant hunter <players> (promote|demote)`: one rank up or down
/// - `/crimsoncovenant hunter <players> get`: tells you their ranks
/// - `/crimsoncovenant bloodmoon (start|stop)`: starts or ends a blood moon right away
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public final class ModCommands {
    private ModCommands() {}

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal(CrimsonCovenant.MODID)
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("vampirism")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(action("infect", player -> Vampirism.infect(player, Vampirism.INFECTION_DURATION)))
                                .then(action("turn", Vampirism::turn))
                                .then(action("cure", Vampirism::cure))
                                .then(Commands.literal("age").then(Commands.argument("days", IntegerArgumentType.integer(0, VampireAge.MAX_DAYS))
                                        .executes(ModCommands::setVampireAge)))))
                .then(Commands.literal("bloodmoon")
                        .then(Commands.literal("start").executes(context -> {
                            BloodMoon.start(context.getSource().getServer().overworld());
                            return 1;
                        }))
                        .then(Commands.literal("stop").executes(context -> {
                            BloodMoon.stop(context.getSource().getServer().overworld());
                            return 1;
                        })))
                .then(Commands.literal("hunters")
                        .then(Commands.literal("spawn_now").executes(context -> spawnHuntersNow(context.getSource()))))
                .then(Commands.literal("hunter")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(rankCommand())
                                .then(Commands.literal("promote").executes(context -> changeRank(context, player -> HunterRanks.get(player) + 1)))
                                .then(Commands.literal("demote").executes(context -> changeRank(context, player -> HunterRanks.get(player) - 1)))
                                .then(Commands.literal("get").executes(ModCommands::getRanks)))));
    }

    // One literal per rank, so they tab-complete
    private static LiteralArgumentBuilder<CommandSourceStack> rankCommand() {
        LiteralArgumentBuilder<CommandSourceStack> rank = Commands.literal("rank");
        for (int r = HunterRanks.NONE; r <= HunterRanks.MAX_RANK; r++) {
            int chosen = r;
            rank.then(Commands.literal(HunterRanks.id(r)).executes(context -> changeRank(context, player -> chosen)));
        }
        return rank;
    }

    private static int changeRank(CommandContext<CommandSourceStack> context, ToIntFunction<ServerPlayer> newRank) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "targets");
        for (ServerPlayer player : players) {
            HunterRanks.set(player, newRank.applyAsInt(player));
            int rank = HunterRanks.get(player);
            player.sendSystemMessage(Component.translatable("commands.crimsoncovenant.hunter.now", HunterRanks.name(rank)).withStyle(ChatFormatting.GOLD));
            if (rank > HunterRanks.NONE) {
                player.sendSystemMessage(Component.translatable("message.crimsoncovenant.hunter.unlocked", HunterGear.unlockedAt(rank)).withStyle(ChatFormatting.GRAY));
            }
            if (Vampirism.isTainted(player)) {
                context.getSource().sendSystemMessage(Component.translatable("commands.crimsoncovenant.hunter.tainted", player.getDisplayName()).withStyle(ChatFormatting.GRAY));
            }
        }
        if (players.size() == 1) {
            ServerPlayer player = players.iterator().next();
            context.getSource().sendSuccess(() -> Component.translatable("commands.crimsoncovenant.hunter.set.single", player.getDisplayName(), HunterRanks.name(HunterRanks.get(player))), true);
        } else {
            context.getSource().sendSuccess(() -> Component.translatable("commands.crimsoncovenant.hunter.set.multiple", players.size()), true);
        }
        return players.size();
    }

    private static int getRanks(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "targets");
        for (ServerPlayer player : players) {
            context.getSource().sendSuccess(() -> Component.translatable("commands.crimsoncovenant.hunter.get", player.getDisplayName(), HunterRanks.name(HunterRanks.get(player))), false);
        }
        return players.size();
    }

    // Sets the vampires' age (see VampireAge), so its tiers can be tried out without waiting
    private static int setVampireAge(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int days = IntegerArgumentType.getInteger(context, "days");
        int count = 0;
        for (ServerPlayer player : EntityArgument.getPlayers(context, "targets")) {
            if (Vampirism.isVampire(player)) {
                VampireAge.setDays(player, days);
                VampireAge.tick(player);
                count++;
            }
        }
        int aged = count;
        context.getSource().sendSuccess(() -> Component.translatable("commands.crimsoncovenant.vampirism.age", aged, days), true);
        return count;
    }

    // Runs the natural party spawner once near the command's position, skipping the night/timer/chance checks
    private static int spawnHuntersNow(CommandSourceStack source) {
        BlockPos pos = HunterPartySpawner.spawnPartyNear(source.getLevel(), BlockPos.containing(source.getPosition()), source.getLevel().getRandom());
        if (pos == null) {
            source.sendFailure(Component.translatable("commands.crimsoncovenant.hunters.spawn_now.failed"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.crimsoncovenant.hunters.spawn_now.success", pos.getX(), pos.getY(), pos.getZ()), true);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> action(String name, Consumer<ServerPlayer> action) {
        return Commands.literal(name).executes(context -> {
            Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "targets");
            players.forEach(action);
            context.getSource().sendSuccess(() -> Component.translatable("commands.crimsoncovenant.vampirism." + name, players.size()), true);
            return players.size();
        });
    }
}

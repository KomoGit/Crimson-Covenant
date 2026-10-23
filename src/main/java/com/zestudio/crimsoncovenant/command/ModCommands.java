package com.zestudio.crimsoncovenant.command;

import java.util.Collection;
import java.util.function.Consumer;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.vampirism.Vampirism;
import com.zestudio.crimsoncovenant.world.BloodMoon;
import com.zestudio.crimsoncovenant.world.HunterPartySpawner;

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
/// - `/crimsoncovenant hunters spawn_now`: spawns a hunter party nearby the way the night spawner would
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
                                .then(action("cure", Vampirism::cure))))
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
                        .then(Commands.literal("spawn_now").executes(context -> spawnHuntersNow(context.getSource())))));
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

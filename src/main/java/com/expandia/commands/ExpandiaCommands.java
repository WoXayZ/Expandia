package com.expandia.commands;

import com.expandia.ExpandiaConstants;
import com.expandia.config.ExpandiaConfigManager;
import com.expandia.progression.ExpandiaProgressionManager;
import com.expandia.progression.ExpandiaProgressionSync;
import com.expandia.worldgen.ExpandiaWorldGenInjector;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = ExpandiaConstants.MOD_ID)
public final class ExpandiaCommands {

    private ExpandiaCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("expandia")
                .then(Commands.literal("islands")
                        .executes(ExpandiaCommands::islands))
                .then(Commands.literal("unlock")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                .executes(ExpandiaCommands::unlock)))
                .then(Commands.literal("reset")
                        .requires(source -> source.hasPermission(2))
                        .executes(ExpandiaCommands::reset))
                .then(Commands.literal("reload")
                        .requires(source -> source.hasPermission(2))
                        .executes(ExpandiaCommands::reload));

        event.getDispatcher().register(root);
    }

    private static int islands(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        int overworld = ExpandiaProgressionManager.getOverworldIslandCount(server);
        int nether = ExpandiaProgressionManager.getNetherIslandCount(server);
        int radius = ExpandiaProgressionManager.getIslandRadius();
        ctx.getSource().sendSuccess(() -> Component.translatable(
                ExpandiaConstants.Lang.COMMAND_ISLANDS, overworld, nether, radius), false);
        return overworld + nether;
    }

    private static int unlock(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        int count = IntegerArgumentType.getInteger(ctx, "count");
        for (int i = 0; i < count; i++) {
            ExpandiaProgressionManager.grantIsland(server, ctx.getSource().getPlayer());
        }
        int totalOverworld = ExpandiaProgressionManager.getOverworldIslandCount(server);
        int totalNether = ExpandiaProgressionManager.getNetherIslandCount(server);
        ctx.getSource().sendSuccess(() -> Component.translatable(
                ExpandiaConstants.Lang.COMMAND_UNLOCK, count, totalOverworld, totalNether), true);
        return totalOverworld + totalNether;
    }

    private static int reset(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        ExpandiaProgressionManager.reset(server);
        ctx.getSource().sendSuccess(() -> Component.translatable(
                ExpandiaConstants.Lang.COMMAND_RESET,
                ExpandiaProgressionManager.getOverworldIslandCount(server),
                ExpandiaProgressionManager.getNetherIslandCount(server)), true);
        return 1;
    }

    private static int reload(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        ExpandiaConfigManager.reload();
        ExpandiaProgressionManager.syncRuntimeState(server);
        ExpandiaWorldGenInjector.inject(server);
        ExpandiaProgressionSync.sendToAll(server);
        ctx.getSource().sendSuccess(() -> Component.translatable(
                ExpandiaConstants.Lang.COMMAND_RELOAD,
                ExpandiaProgressionManager.getOverworldIslandCount(server),
                ExpandiaProgressionManager.getNetherIslandCount(server)), true);
        return 1;
    }
}

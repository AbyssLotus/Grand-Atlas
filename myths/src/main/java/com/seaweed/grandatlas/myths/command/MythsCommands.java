package com.seaweed.grandatlas.myths.command;

import com.seaweed.grandatlas.myths.labyrinth.Labyrinth;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * The add-on's commands (ops / cheats on):
 * <pre>
 * /myths labyrinth   go into the Labyrinth (remembers where you were)
 * /myths leave       go back to where you came from
 * </pre>
 * Registered as a GAME-bus listener: the game rebuilds its command list every time a world starts.
 */
public final class MythsCommands {

    private MythsCommands() {}

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("myths")
                        // Each .then(...) wraps its WHOLE child, including its .requires and .executes.
                        .then(Commands.literal("labyrinth")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    ServerPlayer player = source.getPlayerOrException();
                                    if (!Labyrinth.enter(player)) {
                                        source.sendFailure(Component.literal("The Labyrinth dimension isn't loaded."));
                                        return 0;
                                    }
                                    return 1;
                                }))
                        .then(Commands.literal("leave")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> {
                                    Labyrinth.leave(context.getSource().getPlayerOrException());
                                    return 1;
                                }))
        );
    }
}

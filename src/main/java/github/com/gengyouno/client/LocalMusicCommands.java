package github.com.gengyouno.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

public final class LocalMusicCommands {
    private LocalMusicCommands() {
    }

    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("music")
                .then(Commands.literal("play")
                        .executes(context -> play(context, null))
                        .then(Commands.argument("track", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(LocalMusicLibrary.trackIds(), builder))
                                .executes(context -> play(context, StringArgumentType.getString(context, "track")))))
                .then(Commands.literal("stop")
                        .executes(LocalMusicCommands::stop)));
    }

    private static int play(CommandContext<CommandSourceStack> context, String requestedId) {
        LocalMusicTrack track = requestedId == null
                ? LocalMusicLibrary.firstTrack().orElse(null)
                : LocalMusicLibrary.findTrack(requestedId).orElse(null);

        if (track == null) {
            context.getSource().sendFailure(Component.literal(requestedId == null
                    ? "No music tracks found in musiclibraries JSON files"
                    : "Unknown music track: " + requestedId));
            return 0;
        }

        LocalMusicPlayer.play(track);
        context.getSource().sendSuccess(() -> Component.literal("Queued music: " + track.displayName()), false);
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> context) {
        LocalMusicPlayer.stop();
        context.getSource().sendSuccess(() -> Component.literal("Stopping local music"), false);
        return 1;
    }
}

package github.com.gengyouno.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import github.com.gengyouno.Localmusicmod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class LocalMusicServerCommands {
    private LocalMusicServerCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("music")
                .then(Commands.literal("give")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .then(Commands.literal("music_book")
                                        .executes(LocalMusicServerCommands::giveMusicBook)))));
    }

    private static int giveMusicBook(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int given = 0;
        for (Entity entity : EntityArgument.getEntities(context, "targets")) {
            if (entity instanceof ServerPlayer player) {
                ItemStack stack = Localmusicmod.MUSIC_BOOK.get().getDefaultInstance();
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
                given++;
            }
        }

        if (given == 0) {
            context.getSource().sendFailure(Component.literal("No selected entities can receive a music book"));
            return 0;
        }

        int total = given;
        context.getSource().sendSuccess(() -> Component.literal("Gave music_book to " + total + " player(s)"), true);
        return given;
    }
}

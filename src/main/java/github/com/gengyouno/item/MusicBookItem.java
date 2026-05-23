package github.com.gengyouno.item;

import java.lang.reflect.Method;
import java.util.function.Consumer;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

public class MusicBookItem extends Item {
    public MusicBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            openClientScreen();
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.localmusicmod.music_book.tooltip"));
    }

    private static void openClientScreen() {
        try {
            Class<?> screenClass = Class.forName("github.com.gengyouno.client.LocalMusicBookScreen");
            Method open = screenClass.getMethod("open");
            open.invoke(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not open local music book", exception);
        }
    }
}

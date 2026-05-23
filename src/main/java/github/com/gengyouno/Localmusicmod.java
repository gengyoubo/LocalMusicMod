package github.com.gengyouno;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import github.com.gengyouno.item.MusicBookItem;
import github.com.gengyouno.network.LocalMusicNetworking;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(Localmusicmod.MODID)
public class Localmusicmod {
    public static final String MODID = "localmusicmod";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredItem<MusicBookItem> MUSIC_BOOK = ITEMS.registerItem("music_book", MusicBookItem::new,
            properties -> properties.stacksTo(1).rarity(Rarity.UNCOMMON));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> LOCAL_MUSIC_TAB = CREATIVE_MODE_TABS.register("local_music",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.localmusicmod.local_music"))
                    .icon(() -> MUSIC_BOOK.get().getDefaultInstance())
                    .displayItems((parameters, output) -> output.accept(MUSIC_BOOK.get()))
                    .build());

    public Localmusicmod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(LocalMusicNetworking::register);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Local music command mod loaded");
    }
}

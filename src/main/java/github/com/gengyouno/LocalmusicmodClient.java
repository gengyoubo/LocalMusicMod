package github.com.gengyouno;

import github.com.gengyouno.client.LocalMusicCommands;
import github.com.gengyouno.client.LocalMusicPlayer;
import github.com.gengyouno.client.PauseAudioController;
import github.com.gengyouno.client.UploadDirectory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = Localmusicmod.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Localmusicmod.MODID, value = Dist.CLIENT)
public class LocalmusicmodClient {
    public LocalmusicmodClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        Localmusicmod.LOGGER.info("Local music client loaded");
        UploadDirectory.ensureExists();
    }

    @SubscribeEvent
    static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        LocalMusicCommands.register(event);
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        PauseAudioController.update();
        LocalMusicPlayer.suppressVanillaMusic();
        LocalMusicPlayer.updateVolume();
    }
}

package github.com.gengyouno.client;

import github.com.gengyouno.Localmusicmod;
import github.com.gengyouno.network.MusicPlayPayload;
import github.com.gengyouno.network.MusicStopPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class LocalMusicClientNetworking {
    private LocalMusicClientNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(Localmusicmod.MODID).versioned("1");
        registrar.playToClient(MusicPlayPayload.TYPE, MusicPlayPayload.STREAM_CODEC,
                LocalMusicClientNetworking::handlePlayBroadcast);
        registrar.playToClient(MusicStopPayload.TYPE, MusicStopPayload.STREAM_CODEC,
                LocalMusicClientNetworking::handleStopBroadcast);
    }

    public static void requestPlay(LocalMusicTrack track) {
        String url = track.url() == null ? "" : track.url();
        if (url.isBlank()) {
            tell(Component.literal("This track has no remote URL, so only you can hear it."));
            LocalMusicPlayer.play(track);
            return;
        }

        ClientPacketDistributor.sendToServer(new MusicPlayPayload(track.id(), track.displayName(), url, track.volume(), track.loop()));
    }

    public static void requestStop() {
        ClientPacketDistributor.sendToServer(MusicStopPayload.INSTANCE);
    }

    private static void handlePlayBroadcast(MusicPlayPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> LocalMusicPlayer.play(new LocalMusicTrack(
                payload.id(),
                payload.title(),
                null,
                payload.url(),
                payload.volume(),
                payload.loop()
        )));
    }

    private static void handleStopBroadcast(MusicStopPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(LocalMusicPlayer::stop);
    }

    private static void tell(Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(message);
        }
    }
}

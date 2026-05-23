package github.com.gengyouno.network;

import github.com.gengyouno.Localmusicmod;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class LocalMusicNetworking {
    private LocalMusicNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(Localmusicmod.MODID).versioned("1");
        registrar.playToServer(MusicPlayPayload.TYPE, MusicPlayPayload.STREAM_CODEC,
                LocalMusicNetworking::handlePlayRequest);
        registrar.playToServer(MusicStopPayload.TYPE, MusicStopPayload.STREAM_CODEC,
                LocalMusicNetworking::handleStopRequest);
    }

    private static void handlePlayRequest(MusicPlayPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> PacketDistributor.sendToAllPlayers(payload));
    }

    private static void handleStopRequest(MusicStopPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> PacketDistributor.sendToAllPlayers(MusicStopPayload.INSTANCE));
    }
}

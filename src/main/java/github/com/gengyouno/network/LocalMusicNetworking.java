package github.com.gengyouno.network;

import github.com.gengyouno.Localmusicmod;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.lang.reflect.InvocationTargetException;

public final class LocalMusicNetworking {
    private LocalMusicNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(Localmusicmod.MODID).versioned("1");
        registrar.playBidirectional(MusicPlayPayload.TYPE, MusicPlayPayload.STREAM_CODEC,
                LocalMusicNetworking::handlePlayRequest,
                LocalMusicNetworking::handlePlayBroadcast);
        registrar.playBidirectional(MusicStopPayload.TYPE, MusicStopPayload.STREAM_CODEC,
                LocalMusicNetworking::handleStopRequest,
                LocalMusicNetworking::handleStopBroadcast);
    }

    private static void handlePlayRequest(MusicPlayPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> PacketDistributor.sendToAllPlayers(payload));
    }

    private static void handleStopRequest(MusicStopPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> PacketDistributor.sendToAllPlayers(MusicStopPayload.INSTANCE));
    }

    private static void handlePlayBroadcast(MusicPlayPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> invokeClient("playBroadcast", new Class<?>[]{MusicPlayPayload.class}, payload));
    }

    private static void handleStopBroadcast(MusicStopPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> invokeClient("stopBroadcast", new Class<?>[0]));
    }

    private static void invokeClient(String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            Class<?> clientNetworking = Class.forName("github.com.gengyouno.client.LocalMusicClientNetworking");
            clientNetworking.getMethod(methodName, parameterTypes).invoke(null, args);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            Localmusicmod.LOGGER.warn("Failed to handle local music client payload", exception);
        }
    }
}

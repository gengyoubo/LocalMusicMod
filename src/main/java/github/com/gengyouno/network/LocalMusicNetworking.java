package github.com.gengyouno.network;

import github.com.gengyouno.Localmusicmod;
import github.com.gengyouno.server.UploadedMusicServer;
import net.minecraft.server.level.ServerPlayer;
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
        registrar.playToServer(MusicUploadStartPayload.TYPE, MusicUploadStartPayload.STREAM_CODEC,
                LocalMusicNetworking::handleUploadStart);
        registrar.playToServer(MusicUploadChunkPayload.TYPE, MusicUploadChunkPayload.STREAM_CODEC,
                LocalMusicNetworking::handleUploadChunk);
        registrar.playToServer(MusicUploadFinishPayload.TYPE, MusicUploadFinishPayload.STREAM_CODEC,
                LocalMusicNetworking::handleUploadFinish);
        registrar.playToServer(MusicServerTrackPlayRequestPayload.TYPE, MusicServerTrackPlayRequestPayload.STREAM_CODEC,
                LocalMusicNetworking::handleServerTrackPlayRequest);
        registrar.playToClient(MusicServerTrackDataStartPayload.TYPE, MusicServerTrackDataStartPayload.STREAM_CODEC,
                LocalMusicNetworking::handleServerTrackDataStart);
        registrar.playToClient(MusicServerTrackDataChunkPayload.TYPE, MusicServerTrackDataChunkPayload.STREAM_CODEC,
                LocalMusicNetworking::handleServerTrackDataChunk);
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

    private static void handleUploadStart(MusicUploadStartPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                UploadedMusicServer.startUpload(player, payload);
            }
        });
    }

    private static void handleUploadChunk(MusicUploadChunkPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                UploadedMusicServer.acceptChunk(player, payload);
            }
        });
    }

    private static void handleUploadFinish(MusicUploadFinishPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                UploadedMusicServer.finishUpload(player, payload);
            }
        });
    }

    private static void handleServerTrackPlayRequest(MusicServerTrackPlayRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                UploadedMusicServer.playUploadedTrack(player, payload);
            }
        });
    }

    private static void handleServerTrackDataStart(MusicServerTrackDataStartPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> invokeClient("serverTrackDataStart", new Class<?>[]{MusicServerTrackDataStartPayload.class}, payload));
    }

    private static void handleServerTrackDataChunk(MusicServerTrackDataChunkPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> invokeClient("serverTrackDataChunk", new Class<?>[]{MusicServerTrackDataChunkPayload.class}, payload));
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

package github.com.gengyouno.client;

import github.com.gengyouno.network.MusicPlayPayload;
import github.com.gengyouno.network.MusicServerTrackDataChunkPayload;
import github.com.gengyouno.network.MusicServerTrackDataStartPayload;
import github.com.gengyouno.network.MusicServerTrackPlayRequestPayload;
import github.com.gengyouno.network.MusicStopPayload;
import github.com.gengyouno.network.MusicUploadChunkPayload;
import github.com.gengyouno.network.MusicUploadFinishPayload;
import github.com.gengyouno.network.MusicUploadStartPayload;
import github.com.gengyouno.server.UploadedMusicServer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public final class LocalMusicClientNetworking {
    private static final Map<String, DownloadSession> DOWNLOADS = new HashMap<>();

    private LocalMusicClientNetworking() {
    }

    public static void requestPlay(LocalMusicTrack track) {
        String url = track.url() == null ? "" : track.url();
        if (url.isBlank()) {
            tell(Component.literal("This track has no remote URL."));
            return;
        }

        ClientPacketDistributor.sendToServer(new MusicPlayPayload(track.id(), track.displayName(), url, track.volume(), track.loop()));
    }

    public static void requestStop() {
        ClientPacketDistributor.sendToServer(MusicStopPayload.INSTANCE);
    }

    public static void requestServerTrackPlay(String id) {
        ClientPacketDistributor.sendToServer(new MusicServerTrackPlayRequestPayload(id));
    }

    public static void uploadTrack(String id, String fileName) {
        Path uploads = UploadDirectory.ensureExists();
        Path file = uploads.resolve(fileName).normalize();

        try {
            if (!file.startsWith(uploads.normalize()) || !Files.isRegularFile(file)) {
                tell(Component.literal("Put the .ogg file in " + uploads));
                return;
            }
            if (!fileName.toLowerCase().endsWith(".ogg")) {
                tell(Component.literal("Only .ogg uploads are supported"));
                return;
            }

            byte[] data = Files.readAllBytes(file);
            if (data.length <= 0 || data.length > UploadedMusicServer.MAX_UPLOAD_BYTES) {
                tell(Component.literal("Upload size must be between 1 byte and 50 MB"));
                return;
            }

            String title = stripExtension(file.getFileName().toString());
            int totalChunks = Math.max(1, (data.length + UploadedMusicServer.CHUNK_SIZE - 1) / UploadedMusicServer.CHUNK_SIZE);
            ClientPacketDistributor.sendToServer(new MusicUploadStartPayload(id, title, file.getFileName().toString(), data.length, totalChunks));
            for (int index = 0; index < totalChunks; index++) {
                int start = index * UploadedMusicServer.CHUNK_SIZE;
                int end = Math.min(data.length, start + UploadedMusicServer.CHUNK_SIZE);
                ClientPacketDistributor.sendToServer(new MusicUploadChunkPayload(id, index, totalChunks, Arrays.copyOfRange(data, start, end)));
            }
            ClientPacketDistributor.sendToServer(new MusicUploadFinishPayload(id));
            tell(Component.literal("Upload sent: " + id));
        } catch (IOException exception) {
            tell(Component.literal("Could not upload music: " + exception.getMessage()));
        }
    }

    public static void playBroadcast(MusicPlayPayload payload) {
        LocalMusicPlayer.play(new LocalMusicTrack(
                payload.id(),
                payload.title(),
                payload.url(),
                payload.volume(),
                payload.loop()
        ));
    }

    public static void stopBroadcast() {
        LocalMusicPlayer.stop();
    }

    public static void serverTrackDataStart(MusicServerTrackDataStartPayload payload) {
        DOWNLOADS.put(payload.id(), new DownloadSession(payload));
        tell(Component.literal("Receiving uploaded music: " + payload.title()));
    }

    public static void serverTrackDataChunk(MusicServerTrackDataChunkPayload payload) {
        DownloadSession session = DOWNLOADS.get(payload.id());
        if (session == null || payload.index() < 0 || payload.index() >= session.totalChunks || payload.totalChunks() != session.totalChunks) {
            return;
        }

        session.chunks[payload.index()] = Arrays.copyOf(payload.data(), payload.data().length);
        if (session.isComplete()) {
            DOWNLOADS.remove(payload.id());
            try {
                LocalMusicPlayer.playUploaded(session.id, session.title, session.fileName, session.join(), session.volume, session.loop);
            } catch (IOException exception) {
                tell(Component.literal("Could not receive uploaded music: " + exception.getMessage()));
            }
        }
    }

    private static void tell(Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(message);
        }
    }

    private static String stripExtension(String value) {
        int dot = value.lastIndexOf('.');
        return dot <= 0 ? value : value.substring(0, dot);
    }

    private static final class DownloadSession {
        private final String id;
        private final String title;
        private final String fileName;
        private final int totalSize;
        private final int totalChunks;
        private final float volume;
        private final boolean loop;
        private final byte[][] chunks;

        private DownloadSession(MusicServerTrackDataStartPayload payload) {
            this.id = payload.id();
            this.title = payload.title();
            this.fileName = payload.fileName();
            this.totalSize = payload.totalSize();
            this.totalChunks = payload.totalChunks();
            this.volume = payload.volume();
            this.loop = payload.loop();
            this.chunks = new byte[payload.totalChunks()][];
        }

        private boolean isComplete() {
            for (byte[] chunk : chunks) {
                if (chunk == null) {
                    return false;
                }
            }
            return true;
        }

        private byte[] join() throws IOException {
            ByteArrayOutputStream output = new ByteArrayOutputStream(totalSize);
            for (byte[] chunk : chunks) {
                output.write(chunk);
            }
            return output.toByteArray();
        }
    }
}

package github.com.gengyouno.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import github.com.gengyouno.Localmusicmod;
import github.com.gengyouno.network.MusicServerTrackDataChunkPayload;
import github.com.gengyouno.network.MusicServerTrackDataStartPayload;
import github.com.gengyouno.network.MusicServerTrackPlayRequestPayload;
import github.com.gengyouno.network.MusicUploadChunkPayload;
import github.com.gengyouno.network.MusicUploadFinishPayload;
import github.com.gengyouno.network.MusicUploadStartPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class UploadedMusicServer {
    public static final int CHUNK_SIZE = 32 * 1024;
    public static final int MAX_UPLOAD_BYTES = 50 * 1024 * 1024;

    private static final Map<UploadKey, UploadSession> UPLOADS = new HashMap<>();

    private UploadedMusicServer() {
    }

    public static void startUpload(ServerPlayer player, MusicUploadStartPayload payload) {
        String id = normalizeId(payload.id());
        if (id.isBlank() || !payload.fileName().toLowerCase().endsWith(".ogg")) {
            tell(player, "Only .ogg uploads with an English id are supported");
            return;
        }
        if (payload.totalSize() <= 0 || payload.totalSize() > MAX_UPLOAD_BYTES || payload.totalChunks() <= 0) {
            tell(player, "Upload is too large or invalid. Max size is 50 MB");
            return;
        }

        UploadSession session = new UploadSession(id, safeTitle(payload.title(), id), id + ".ogg", payload.totalSize(), payload.totalChunks());
        UPLOADS.put(new UploadKey(player.getUUID(), id), session);
        tell(player, "Uploading music: " + id);
    }

    public static void acceptChunk(ServerPlayer player, MusicUploadChunkPayload payload) {
        String id = normalizeId(payload.id());
        UploadSession session = UPLOADS.get(new UploadKey(player.getUUID(), id));
        if (session == null || payload.index() < 0 || payload.index() >= session.totalChunks || payload.totalChunks() != session.totalChunks) {
            return;
        }
        if (payload.data().length > MusicUploadChunkPayload.MAX_CHUNK_BYTES) {
            UPLOADS.remove(new UploadKey(player.getUUID(), id));
            tell(player, "Upload failed: chunk is too large");
            return;
        }
        session.chunks[payload.index()] = Arrays.copyOf(payload.data(), payload.data().length);
    }

    public static void finishUpload(ServerPlayer player, MusicUploadFinishPayload payload) {
        String id = normalizeId(payload.id());
        UploadKey key = new UploadKey(player.getUUID(), id);
        UploadSession session = UPLOADS.remove(key);
        if (session == null || !session.isComplete()) {
            tell(player, "Upload failed: missing chunks for " + id);
            return;
        }

        try {
            byte[] data = session.join();
            if (data.length != session.totalSize || data.length > MAX_UPLOAD_BYTES) {
                tell(player, "Upload failed: size mismatch");
                return;
            }

            Path directory = musicDirectory(player.level().getServer());
            Files.createDirectories(directory);
            Files.write(directory.resolve(session.fileName), data);
            writeMetadata(directory.resolve(session.id + ".json"), session);
            tell(player, "Uploaded music: " + session.id);
        } catch (IOException exception) {
            Localmusicmod.LOGGER.warn("Could not save uploaded music {}", id, exception);
            tell(player, "Upload failed: " + exception.getMessage());
        }
    }

    public static void playUploadedTrack(ServerPlayer requester, MusicServerTrackPlayRequestPayload payload) {
        String id = normalizeId(payload.id());
        try {
            StoredTrack track = loadTrack(requester.level().getServer(), id);
            if (track == null) {
                tell(requester, "Unknown uploaded music: " + payload.id());
                return;
            }

            broadcastTrack(track);
            requester.level().getServer().getPlayerList().broadcastSystemMessage(Component.literal("Playing uploaded music: " + track.title), false);
        } catch (IOException exception) {
            Localmusicmod.LOGGER.warn("Could not play uploaded music {}", id, exception);
            tell(requester, "Could not play uploaded music: " + exception.getMessage());
        }
    }

    private static void broadcastTrack(StoredTrack track) {
        int totalChunks = Math.max(1, (track.data.length + CHUNK_SIZE - 1) / CHUNK_SIZE);
        PacketDistributor.sendToAllPlayers(new MusicServerTrackDataStartPayload(
                track.id, track.title, track.fileName, track.data.length, totalChunks, track.volume, track.loop
        ));

        for (int index = 0; index < totalChunks; index++) {
            int start = index * CHUNK_SIZE;
            int end = Math.min(track.data.length, start + CHUNK_SIZE);
            PacketDistributor.sendToAllPlayers(new MusicServerTrackDataChunkPayload(
                    track.id, index, totalChunks, Arrays.copyOfRange(track.data, start, end)
            ));
        }
    }

    private static StoredTrack loadTrack(MinecraftServer server, String id) throws IOException {
        if (id.isBlank()) {
            return null;
        }

        Path directory = musicDirectory(server);
        Path audio = directory.resolve(id + ".ogg");
        Path metadata = directory.resolve(id + ".json");
        if (!Files.isRegularFile(audio)) {
            return null;
        }

        String title = id;
        float volume = 1.0F;
        boolean loop = false;
        if (Files.isRegularFile(metadata)) {
            try (Reader reader = Files.newBufferedReader(metadata, StandardCharsets.UTF_8)) {
                JsonObject object = JsonParser.parseReader(reader).getAsJsonObject();
                title = getString(object, "title", title);
                volume = object.has("volume") ? object.get("volume").getAsFloat() : volume;
                loop = object.has("loop") && object.get("loop").getAsBoolean();
            }
        }

        return new StoredTrack(id, title, id + ".ogg", Files.readAllBytes(audio), Math.max(0.0F, volume), loop);
    }

    private static void writeMetadata(Path path, UploadSession session) throws IOException {
        JsonObject object = new JsonObject();
        object.addProperty("id", session.id);
        object.addProperty("title", session.title);
        object.addProperty("file", session.fileName);
        object.addProperty("volume", 1.0F);
        object.addProperty("loop", false);
        Files.writeString(path, object.toString() + "\n", StandardCharsets.UTF_8);
    }

    private static Path musicDirectory(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("localmusicmod").resolve("uploaded_music");
    }

    private static String normalizeId(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase()
                .replaceAll("[^a-z0-9_.-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }

    private static String safeTitle(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String getString(JsonObject object, String name, String fallback) {
        return object.has(name) && object.get(name).isJsonPrimitive() ? object.get(name).getAsString() : fallback;
    }

    private static void tell(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message));
    }

    private record UploadKey(UUID playerId, String musicId) {
    }

    private static final class UploadSession {
        private final String id;
        private final String title;
        private final String fileName;
        private final int totalSize;
        private final int totalChunks;
        private final byte[][] chunks;

        private UploadSession(String id, String title, String fileName, int totalSize, int totalChunks) {
            this.id = id;
            this.title = title;
            this.fileName = fileName;
            this.totalSize = totalSize;
            this.totalChunks = totalChunks;
            this.chunks = new byte[totalChunks][];
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

    private record StoredTrack(String id, String title, String fileName, byte[] data, float volume, boolean loop) {
    }
}

package github.com.gengyouno.network;

import github.com.gengyouno.Localmusicmod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MusicServerTrackDataChunkPayload(String id, int index, int totalChunks, byte[] data) implements CustomPacketPayload {
    public static final int MAX_CHUNK_BYTES = 64 * 1024;
    public static final CustomPacketPayload.Type<MusicServerTrackDataChunkPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Localmusicmod.MODID, "music_server_track_data_chunk"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MusicServerTrackDataChunkPayload> STREAM_CODEC =
            CustomPacketPayload.codec(MusicServerTrackDataChunkPayload::write, MusicServerTrackDataChunkPayload::new);

    private MusicServerTrackDataChunkPayload(FriendlyByteBuf input) {
        this(input.readUtf(128), input.readVarInt(), input.readVarInt(), input.readByteArray(MAX_CHUNK_BYTES));
    }

    private void write(FriendlyByteBuf output) {
        output.writeUtf(id, 128);
        output.writeVarInt(index);
        output.writeVarInt(totalChunks);
        output.writeByteArray(data);
    }

    @Override
    public CustomPacketPayload.Type<MusicServerTrackDataChunkPayload> type() {
        return TYPE;
    }
}

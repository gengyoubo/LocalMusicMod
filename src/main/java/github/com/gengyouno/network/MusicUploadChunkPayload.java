package github.com.gengyouno.network;

import github.com.gengyouno.Localmusicmod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MusicUploadChunkPayload(String id, int index, int totalChunks, byte[] data) implements CustomPacketPayload {
    public static final int MAX_CHUNK_BYTES = 64 * 1024;
    public static final CustomPacketPayload.Type<MusicUploadChunkPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Localmusicmod.MODID, "music_upload_chunk"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MusicUploadChunkPayload> STREAM_CODEC =
            CustomPacketPayload.codec(MusicUploadChunkPayload::write, MusicUploadChunkPayload::new);

    private MusicUploadChunkPayload(FriendlyByteBuf input) {
        this(input.readUtf(128), input.readVarInt(), input.readVarInt(), input.readByteArray(MAX_CHUNK_BYTES));
    }

    private void write(FriendlyByteBuf output) {
        output.writeUtf(id, 128);
        output.writeVarInt(index);
        output.writeVarInt(totalChunks);
        output.writeByteArray(data);
    }

    @Override
    public CustomPacketPayload.Type<MusicUploadChunkPayload> type() {
        return TYPE;
    }
}

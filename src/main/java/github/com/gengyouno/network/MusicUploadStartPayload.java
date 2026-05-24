package github.com.gengyouno.network;

import github.com.gengyouno.Localmusicmod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MusicUploadStartPayload(String id, String title, String fileName, int totalSize, int totalChunks) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MusicUploadStartPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Localmusicmod.MODID, "music_upload_start"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MusicUploadStartPayload> STREAM_CODEC =
            CustomPacketPayload.codec(MusicUploadStartPayload::write, MusicUploadStartPayload::new);

    private MusicUploadStartPayload(FriendlyByteBuf input) {
        this(input.readUtf(128), input.readUtf(512), input.readUtf(256), input.readVarInt(), input.readVarInt());
    }

    private void write(FriendlyByteBuf output) {
        output.writeUtf(id, 128);
        output.writeUtf(title, 512);
        output.writeUtf(fileName, 256);
        output.writeVarInt(totalSize);
        output.writeVarInt(totalChunks);
    }

    @Override
    public CustomPacketPayload.Type<MusicUploadStartPayload> type() {
        return TYPE;
    }
}

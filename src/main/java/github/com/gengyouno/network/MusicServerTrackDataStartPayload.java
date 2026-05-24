package github.com.gengyouno.network;

import github.com.gengyouno.Localmusicmod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MusicServerTrackDataStartPayload(String id, String title, String fileName, int totalSize, int totalChunks, float volume, boolean loop) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MusicServerTrackDataStartPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Localmusicmod.MODID, "music_server_track_data_start"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MusicServerTrackDataStartPayload> STREAM_CODEC =
            CustomPacketPayload.codec(MusicServerTrackDataStartPayload::write, MusicServerTrackDataStartPayload::new);

    private MusicServerTrackDataStartPayload(FriendlyByteBuf input) {
        this(input.readUtf(128), input.readUtf(512), input.readUtf(256), input.readVarInt(), input.readVarInt(), input.readFloat(), input.readBoolean());
    }

    private void write(FriendlyByteBuf output) {
        output.writeUtf(id, 128);
        output.writeUtf(title, 512);
        output.writeUtf(fileName, 256);
        output.writeVarInt(totalSize);
        output.writeVarInt(totalChunks);
        output.writeFloat(volume);
        output.writeBoolean(loop);
    }

    @Override
    public CustomPacketPayload.Type<MusicServerTrackDataStartPayload> type() {
        return TYPE;
    }
}

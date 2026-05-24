package github.com.gengyouno.network;

import github.com.gengyouno.Localmusicmod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MusicServerTrackPlayRequestPayload(String id) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MusicServerTrackPlayRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Localmusicmod.MODID, "music_server_track_play"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MusicServerTrackPlayRequestPayload> STREAM_CODEC =
            CustomPacketPayload.codec(MusicServerTrackPlayRequestPayload::write, MusicServerTrackPlayRequestPayload::new);

    private MusicServerTrackPlayRequestPayload(FriendlyByteBuf input) {
        this(input.readUtf(128));
    }

    private void write(FriendlyByteBuf output) {
        output.writeUtf(id, 128);
    }

    @Override
    public CustomPacketPayload.Type<MusicServerTrackPlayRequestPayload> type() {
        return TYPE;
    }
}

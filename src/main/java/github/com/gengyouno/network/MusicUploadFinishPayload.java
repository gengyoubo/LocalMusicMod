package github.com.gengyouno.network;

import github.com.gengyouno.Localmusicmod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MusicUploadFinishPayload(String id) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MusicUploadFinishPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Localmusicmod.MODID, "music_upload_finish"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MusicUploadFinishPayload> STREAM_CODEC =
            CustomPacketPayload.codec(MusicUploadFinishPayload::write, MusicUploadFinishPayload::new);

    private MusicUploadFinishPayload(FriendlyByteBuf input) {
        this(input.readUtf(128));
    }

    private void write(FriendlyByteBuf output) {
        output.writeUtf(id, 128);
    }

    @Override
    public CustomPacketPayload.Type<MusicUploadFinishPayload> type() {
        return TYPE;
    }
}

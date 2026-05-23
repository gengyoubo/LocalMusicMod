package github.com.gengyouno.network;

import github.com.gengyouno.Localmusicmod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MusicPlayPayload(String id, String title, String url, float volume, boolean loop) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MusicPlayPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Localmusicmod.MODID, "music_play"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MusicPlayPayload> STREAM_CODEC =
            CustomPacketPayload.codec(MusicPlayPayload::write, MusicPlayPayload::new);

    private MusicPlayPayload(FriendlyByteBuf input) {
        this(input.readUtf(128), input.readUtf(512), input.readUtf(2048), input.readFloat(), input.readBoolean());
    }

    private void write(FriendlyByteBuf output) {
        output.writeUtf(id, 128);
        output.writeUtf(title, 512);
        output.writeUtf(url, 2048);
        output.writeFloat(volume);
        output.writeBoolean(loop);
    }

    @Override
    public CustomPacketPayload.Type<MusicPlayPayload> type() {
        return TYPE;
    }
}

package github.com.gengyouno.network;

import github.com.gengyouno.Localmusicmod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MusicStopPayload() implements CustomPacketPayload {
    public static final MusicStopPayload INSTANCE = new MusicStopPayload();
    public static final CustomPacketPayload.Type<MusicStopPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Localmusicmod.MODID, "music_stop"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MusicStopPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public CustomPacketPayload.Type<MusicStopPayload> type() {
        return TYPE;
    }
}

package github.com.gengyouno.client;

import github.com.gengyouno.network.MusicPlayPayload;
import github.com.gengyouno.network.MusicStopPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public final class LocalMusicClientNetworking {
    private LocalMusicClientNetworking() {
    }

    public static void requestPlay(LocalMusicTrack track) {
        String url = track.url() == null ? "" : track.url();
        if (url.isBlank()) {
            tell(Component.literal("This track has no remote URL, so only you can hear it."));
            LocalMusicPlayer.play(track);
            return;
        }

        ClientPacketDistributor.sendToServer(new MusicPlayPayload(track.id(), track.displayName(), url, track.volume(), track.loop()));
    }

    public static void requestStop() {
        ClientPacketDistributor.sendToServer(MusicStopPayload.INSTANCE);
    }

    public static void playBroadcast(MusicPlayPayload payload) {
        LocalMusicPlayer.play(new LocalMusicTrack(
                payload.id(),
                payload.title(),
                null,
                payload.url(),
                payload.volume(),
                payload.loop()
        ));
    }

    public static void stopBroadcast() {
        LocalMusicPlayer.stop();
    }

    private static void tell(Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(message);
        }
    }
}

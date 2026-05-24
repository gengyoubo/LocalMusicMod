package github.com.gengyouno.client;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;

public final class PauseAudioController {
    private static boolean pausedVanillaSounds;

    private PauseAudioController() {
    }

    public static void update() {
        Minecraft minecraft = Minecraft.getInstance();
        boolean paused = minecraft.isPaused();

        LocalMusicPlayer.setGamePaused(paused);

        if (paused && !pausedVanillaSounds) {
            minecraft.getSoundManager().pauseAllExcept(SoundSource.UI);
            pausedVanillaSounds = true;
            return;
        }

        if (!paused && pausedVanillaSounds) {
            minecraft.getSoundManager().resume();
            pausedVanillaSounds = false;
        }
    }
}

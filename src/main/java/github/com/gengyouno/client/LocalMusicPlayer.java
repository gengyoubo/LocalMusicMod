package github.com.gengyouno.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;

import github.com.gengyouno.Config;
import github.com.gengyouno.Localmusicmod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;

public final class LocalMusicPlayer {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final ExecutorService AUDIO_WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "LocalMusicMod-Audio");
        thread.setDaemon(true);
        return thread;
    });

    private static Clip currentClip;
    private static LocalMusicTrack currentTrack;
    private static int playToken;
    private static boolean pausedForGame;
    private static boolean wasRunningWhenPaused;

    private LocalMusicPlayer() {
    }

    public static synchronized boolean isPlaying() {
        return currentClip != null && currentClip.isRunning();
    }

    public static void play(LocalMusicTrack track) {
        int token;
        synchronized (LocalMusicPlayer.class) {
            stopLocked();
            currentTrack = track;
            token = ++playToken;
        }

        stopVanillaMusic();
        tell(Component.literal("Loading music: " + track.displayName()));
        CompletableFuture.supplyAsync(() -> loadClip(track), AUDIO_WORKER)
                .whenComplete((clip, throwable) -> Minecraft.getInstance().execute(() -> {
                    if (throwable != null) {
                        synchronized (LocalMusicPlayer.class) {
                            if (token == playToken) {
                                currentTrack = null;
                            }
                        }
                        Localmusicmod.LOGGER.warn("Could not play music {}", track.id(), throwable);
                        tell(Component.literal("Could not play music: " + throwable.getMessage()));
                        return;
                    }

                    synchronized (LocalMusicPlayer.class) {
                        if (token != playToken) {
                            clip.close();
                            return;
                        }

                        currentClip = clip;
                        applyVolumeLocked();
                        clip.setFramePosition(0);
                        if (pausedForGame) {
                            wasRunningWhenPaused = true;
                        } else if (track.loop()) {
                            clip.loop(Clip.LOOP_CONTINUOUSLY);
                        } else {
                            clip.start();
                        }
                    }
                    tell(Component.literal("Now playing: " + track.displayName()));
                }));
    }

    public static void playUploaded(String id, String title, String fileName, byte[] data, float volume, boolean loop) {
        LocalMusicTrack track = new LocalMusicTrack(id, title, "uploaded://" + id + "/" + fileName, volume, loop);
        int token;
        synchronized (LocalMusicPlayer.class) {
            stopLocked();
            currentTrack = track;
            token = ++playToken;
        }

        stopVanillaMusic();
        tell(Component.literal("Loading uploaded music: " + track.displayName()));
        CompletableFuture.supplyAsync(() -> loadClipFromBytes(fileName, data), AUDIO_WORKER)
                .whenComplete((clip, throwable) -> Minecraft.getInstance().execute(() -> {
                    if (throwable != null) {
                        synchronized (LocalMusicPlayer.class) {
                            if (token == playToken) {
                                currentTrack = null;
                            }
                        }
                        Localmusicmod.LOGGER.warn("Could not play uploaded music {}", id, throwable);
                        tell(Component.literal("Could not play uploaded music: " + throwable.getMessage()));
                        return;
                    }

                    synchronized (LocalMusicPlayer.class) {
                        if (token != playToken) {
                            clip.close();
                            return;
                        }

                        currentClip = clip;
                        applyVolumeLocked();
                        clip.setFramePosition(0);
                        if (pausedForGame) {
                            wasRunningWhenPaused = true;
                        } else if (loop) {
                            clip.loop(Clip.LOOP_CONTINUOUSLY);
                        } else {
                            clip.start();
                        }
                    }
                    tell(Component.literal("Now playing uploaded music: " + track.displayName()));
                }));
    }

    public static void stop() {
        synchronized (LocalMusicPlayer.class) {
            playToken++;
            stopLocked();
            wasRunningWhenPaused = false;
        }
        tell(Component.literal("Music stopped"));
    }

    public static synchronized void updateVolume() {
        applyVolumeLocked();
    }

    public static void suppressVanillaMusic() {
        if (hasActiveTrack()) {
            stopVanillaMusic();
        }
    }

    public static synchronized void setGamePaused(boolean paused) {
        if (pausedForGame == paused) {
            return;
        }

        pausedForGame = paused;
        if (currentClip == null) {
            wasRunningWhenPaused = false;
            return;
        }

        if (paused) {
            wasRunningWhenPaused = currentClip.isRunning();
            if (wasRunningWhenPaused) {
                currentClip.stop();
            }
            return;
        }

        if (wasRunningWhenPaused) {
            if (currentTrack != null && currentTrack.loop()) {
                currentClip.loop(Clip.LOOP_CONTINUOUSLY);
            } else {
                currentClip.start();
            }
        }
        wasRunningWhenPaused = false;
    }

    private static synchronized boolean hasActiveTrack() {
        return currentTrack != null || currentClip != null;
    }

    private static void stopVanillaMusic() {
        Minecraft.getInstance().getMusicManager().stopPlaying();
    }

    private static Clip loadClip(LocalMusicTrack track) {
        try {
            byte[] bytes = readTrackBytes(track);
            DecodedAudio audio = decode(track, bytes);
            return openClip(track, audio);
        } catch (Exception exception) {
            throw new IllegalStateException(exception.getMessage(), exception);
        }
    }

    private static Clip loadClipFromBytes(String fileName, byte[] bytes) {
        try {
            DecodedAudio audio = decode(fileName, bytes);
            return openClip(null, audio);
        } catch (Exception exception) {
            throw new IllegalStateException(exception.getMessage(), exception);
        }
    }

    private static Clip openClip(LocalMusicTrack track, DecodedAudio audio) throws Exception {
            Clip clip = AudioSystem.getClip();
            clip.open(audio.format(), audio.data(), 0, audio.data().length);
            clip.addLineListener(event -> {
                if (event.getType() != LineEvent.Type.STOP) {
                    return;
                }

                synchronized (LocalMusicPlayer.class) {
                    boolean looping = currentTrack != null && currentTrack.loop();
                    if (currentClip == clip && !looping && clip.getFramePosition() >= clip.getFrameLength()) {
                        clip.close();
                        currentClip = null;
                        currentTrack = null;
                    }
                }
            });
            return clip;
    }

    private static byte[] readTrackBytes(LocalMusicTrack track) throws IOException, InterruptedException {
        if (track.url() == null || track.url().isBlank()) {
            throw new IOException("Track has no remote URL");
        }

        if (!Config.ENABLE_GITHUB_URLS.getAsBoolean()) {
            throw new IOException("Remote music URLs are disabled in the config");
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(track.url()))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "LocalMusicMod")
                .GET()
                .build();
        HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode() + " for " + track.url());
        }
        return response.body();
    }

    private static DecodedAudio decode(LocalMusicTrack track, byte[] bytes) throws Exception {
        return decode(URI.create(track.url()).getPath(), bytes);
    }

    private static DecodedAudio decode(String name, byte[] bytes) throws Exception {
        String lowerName = name.toLowerCase(Locale.ROOT);

        if (lowerName.endsWith(".ogg")) {
            return decodeOgg(bytes);
        }
        if (lowerName.endsWith(".wav")) {
            return decodeWithJavaSound(bytes);
        }

        throw new IOException("Unsupported audio format. Use .ogg or .wav");
    }

    private static DecodedAudio decodeOgg(byte[] bytes) throws IOException {
        try (JOrbisAudioStream stream = new JOrbisAudioStream(new ByteArrayInputStream(bytes))) {
            AudioFormat source = stream.getFormat();
            ByteArrayOutputStream output = new ByteArrayOutputStream();

            while (stream.readChunk(sample -> {
                int value = Math.max(-32768, Math.min(32767, (int) (sample * 32767.0F)));
                output.write(value & 0xFF);
                output.write(value >> 8 & 0xFF);
            })) {
            }

            AudioFormat format = new AudioFormat(source.getSampleRate(), 16, source.getChannels(), true, false);
            return new DecodedAudio(format, output.toByteArray());
        }
    }

    private static DecodedAudio decodeWithJavaSound(byte[] bytes) throws Exception {
        try (AudioInputStream input = AudioSystem.getAudioInputStream(new ByteArrayInputStream(bytes))) {
            AudioFormat source = input.getFormat();
            AudioFormat decoded = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    source.getSampleRate(),
                    16,
                    source.getChannels(),
                    source.getChannels() * Short.BYTES,
                    source.getSampleRate(),
                    false
            );

            try (AudioInputStream pcm = AudioSystem.getAudioInputStream(decoded, input)) {
                return new DecodedAudio(decoded, pcm.readAllBytes());
            }
        }
    }

    private static void stopLocked() {
        if (currentClip != null) {
            currentClip.stop();
            currentClip.close();
            currentClip = null;
        }
        currentTrack = null;
        wasRunningWhenPaused = false;
    }

    private static void applyVolumeLocked() {
        if (currentClip == null || !currentClip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        float master = minecraft.options.getSoundSourceVolume(SoundSource.MASTER);
        float music = minecraft.options.getSoundSourceVolume(SoundSource.MUSIC);
        float trackVolume = currentTrack == null ? 1.0F : currentTrack.volume();
        float volume = Math.max(0.0F, Math.min(1.0F, master * music * trackVolume));

        FloatControl gain = (FloatControl) currentClip.getControl(FloatControl.Type.MASTER_GAIN);
        if (volume <= 0.0F) {
            gain.setValue(gain.getMinimum());
        } else {
            float decibels = (float) (20.0D * Math.log10(volume));
            gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), decibels)));
        }
    }

    private static void tell(Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(message);
        }
    }

    private record DecodedAudio(AudioFormat format, byte[] data) {
    }
}

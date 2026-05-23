package github.com.gengyouno.client;

import java.nio.file.Path;

public record LocalMusicTrack(
        String id,
        String title,
        Path file,
        String url,
        float volume,
        boolean loop
) {
    public String displayName() {
        return title == null || title.isBlank() ? id : title;
    }
}

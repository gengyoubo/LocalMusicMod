package github.com.gengyouno.client;

public record LocalMusicTrack(
        String id,
        String title,
        String url,
        float volume,
        boolean loop
) {
    public String displayName() {
        return title == null || title.isBlank() ? id : title;
    }
}

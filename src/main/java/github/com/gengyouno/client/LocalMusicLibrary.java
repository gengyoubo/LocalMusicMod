package github.com.gengyouno.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import github.com.gengyouno.Config;
import github.com.gengyouno.Localmusicmod;

public final class LocalMusicLibrary {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private LocalMusicLibrary() {
    }

    public static List<LocalMusicTrack> loadTracks() {
        List<LocalMusicTrack> tracks = new ArrayList<>();
        Set<String> visitedLibraries = new HashSet<>();

        String defaultLibraryUrl = normalizeLibraryUrl(Config.DEFAULT_LIBRARY_URL.get());
        if (Config.ENABLE_GITHUB_URLS.getAsBoolean() && defaultLibraryUrl != null && !defaultLibraryUrl.isBlank()) {
            loadRemoteJson(defaultLibraryUrl, tracks, visitedLibraries);
        }

        return tracks.stream()
                .sorted(Comparator.comparing(LocalMusicTrack::id))
                .toList();
    }

    public static Optional<LocalMusicTrack> firstTrack() {
        return loadTracks().stream().findFirst();
    }

    public static Optional<LocalMusicTrack> findTrack(String id) {
        return loadTracks().stream()
                .filter(track -> track.id().equalsIgnoreCase(id))
                .findFirst();
    }

    public static List<String> trackIds() {
        return loadTracks().stream().map(LocalMusicTrack::id).toList();
    }

    private static void loadRemoteJson(String url, List<LocalMusicTrack> tracks, Set<String> visitedLibraries) {
        if (!visitedLibraries.add(url)) {
            return;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "LocalMusicMod")
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("HTTP " + response.statusCode());
            }
            loadJson(JsonParser.parseString(response.body()), URI.create(url), tracks, visitedLibraries);
        } catch (Exception exception) {
            Localmusicmod.LOGGER.warn("Could not load remote music library {}. Check config/localmusicmod-common.toml if this URL is outdated.", url, exception);
        }
    }

    private static void loadJson(JsonElement element, URI baseUri, List<LocalMusicTrack> tracks, Set<String> visitedLibraries) {
        if (element == null || element.isJsonNull()) {
            return;
        }

        if (element.isJsonArray()) {
            addTracks(element.getAsJsonArray(), baseUri, tracks);
            return;
        }

        if (!element.isJsonObject()) {
            return;
        }

        JsonObject object = element.getAsJsonObject();
        if (object.has("libraries") && object.get("libraries").isJsonArray()) {
            for (JsonElement library : object.getAsJsonArray("libraries")) {
                if (!library.isJsonPrimitive()) {
                    continue;
                }

                String location = library.getAsString();
                if (isHttpUrl(location)) {
                    loadRemoteJson(location, tracks, visitedLibraries);
                } else {
                    loadRemoteJson(resolveRemoteSibling(baseUri, location), tracks, visitedLibraries);
                }
            }
        }

        if (object.has("tracks") && object.get("tracks").isJsonArray()) {
            addTracks(object.getAsJsonArray("tracks"), baseUri, tracks);
        } else if (object.has("file") || object.has("path") || object.has("url")) {
            addTrack(object, baseUri, tracks);
        }
    }

    private static void addTracks(JsonArray array, URI baseUri, List<LocalMusicTrack> tracks) {
        for (JsonElement element : array) {
            if (element.isJsonObject()) {
                addTrack(element.getAsJsonObject(), baseUri, tracks);
            }
        }
    }

    private static void addTrack(JsonObject object, URI baseUri, List<LocalMusicTrack> tracks) {
        String id = getString(object, "id", null);
        String title = getString(object, "title", id);
        String url = getString(object, "url", null);
        String fileName = getString(object, "file", getString(object, "path", null));
        float volume = getFloat(object, "volume", 1.0F);
        boolean loop = getBoolean(object, "loop", false);

        if ((url == null || url.isBlank()) && fileName != null && !fileName.isBlank()) {
            url = resolveRemoteSibling(baseUri, preferRemoteOgg(fileName));
        } else if (url != null && !url.isBlank() && !isHttpUrl(url)) {
            url = resolveRemoteSibling(baseUri, url);
        }

        if ((id == null || id.isBlank()) && title != null && !title.isBlank()) {
            id = normalizeId(title);
        }
        if ((id == null || id.isBlank()) && url != null) {
            id = stripExtension(lastUrlPathSegment(url));
        }

        id = normalizeId(id);
        if (id.isBlank()) {
            id = generatedId(title, fileName, url);
        }

        if (id.isBlank() || url == null || url.isBlank() || !isHttpUrl(url)) {
            return;
        }

        tracks.add(new LocalMusicTrack(id, title, url, Math.max(0.0F, volume), loop));
    }

    private static boolean isHttpUrl(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }

    private static String normalizeLibraryUrl(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }

        String normalized = url
                .replace("githubusercontent.com/gengyoubo/LocalMusicMode/main/", "githubusercontent.com/gengyoubo/LocalMusicMod/master/")
                .replace("githubusercontent.com/gengyoubo/LocalMusicMode/master/", "githubusercontent.com/gengyoubo/LocalMusicMod/master/")
                .replace("githubusercontent.com/gengyoubo/LocalMusicMod/main/", "githubusercontent.com/gengyoubo/LocalMusicMod/master/");
        if (!normalized.equals(url)) {
            Localmusicmod.LOGGER.info("Using migrated music library URL {}", normalized);
        }
        return normalized;
    }

    private static String resolveRemoteSibling(URI baseUri, String relativePath) {
        String basePath = baseUri.getPath();
        int slash = basePath.lastIndexOf('/');
        String directory = slash < 0 ? "/" : basePath.substring(0, slash + 1);
        String encodedPath = directory + encodePath(relativePath);
        return URI.create(baseUri.getScheme() + "://" + baseUri.getAuthority() + encodedPath).toString();
    }

    private static String encodePath(String path) {
        return java.util.Arrays.stream(path.replace('\\', '/').split("/", -1))
                .map(part -> java.net.URLEncoder.encode(part, StandardCharsets.UTF_8).replace("+", "%20"))
                .collect(java.util.stream.Collectors.joining("/"));
    }

    private static String preferRemoteOgg(String fileName) {
        return fileName.toLowerCase(Locale.ROOT).endsWith(".wav")
                ? fileName.substring(0, fileName.length() - 4) + ".ogg"
                : fileName;
    }

    private static String getString(JsonObject object, String name, String fallback) {
        JsonElement element = object.get(name);
        return element == null || !element.isJsonPrimitive() ? fallback : element.getAsString();
    }

    private static float getFloat(JsonObject object, String name, float fallback) {
        JsonElement element = object.get(name);
        return element == null || !element.isJsonPrimitive() ? fallback : element.getAsFloat();
    }

    private static boolean getBoolean(JsonObject object, String name, boolean fallback) {
        JsonElement element = object.get(name);
        return element == null || !element.isJsonPrimitive() ? fallback : element.getAsBoolean();
    }

    private static String normalizeId(String value) {
        if (value == null) {
            return "";
        }

        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_\\-.]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }

    private static String generatedId(String title, String fileName, String url) {
        String source = firstPresent(title, fileName, url);
        return source.isBlank() ? "" : "track_" + Integer.toHexString(source.hashCode());
    }

    private static String firstPresent(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String stripExtension(String value) {
        int dot = value.lastIndexOf('.');
        return dot <= 0 ? value : value.substring(0, dot);
    }

    private static String lastUrlPathSegment(String url) {
        String path = URI.create(url).getPath();
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }
}

package github.com.gengyouno.client;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

import github.com.gengyouno.Localmusicmod;
import net.minecraft.client.Minecraft;

public final class LocalMusicLibrary {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private LocalMusicLibrary() {
    }

    public static List<LocalMusicTrack> loadTracks() {
        Path root = findLibraryRoot();
        if (!Files.isDirectory(root)) {
            return List.of();
        }

        List<LocalMusicTrack> tracks = new ArrayList<>();
        Set<String> visitedLibraries = new HashSet<>();

        try (var paths = Files.walk(root)) {
            List<Path> jsonFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(LocalMusicLibrary::isJsonFile)
                    .sorted()
                    .toList();

            for (Path jsonFile : jsonFiles) {
                loadLocalJson(jsonFile, tracks, visitedLibraries);
            }
        } catch (IOException exception) {
            Localmusicmod.LOGGER.warn("Could not scan local music library {}", root, exception);
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

    private static void loadLocalJson(Path jsonFile, List<LocalMusicTrack> tracks, Set<String> visitedLibraries) {
        String key = jsonFile.toAbsolutePath().normalize().toString();
        if (!visitedLibraries.add(key)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(jsonFile, StandardCharsets.UTF_8)) {
            loadJson(JsonParser.parseReader(reader), jsonFile.getParent(), tracks, visitedLibraries);
        } catch (Exception exception) {
            Localmusicmod.LOGGER.warn("Could not load music library {}", jsonFile, exception);
        }
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
            String body = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
            loadJson(JsonParser.parseString(body), null, tracks, visitedLibraries);
        } catch (Exception exception) {
            Localmusicmod.LOGGER.warn("Could not load remote music library {}", url, exception);
        }
    }

    private static void loadJson(JsonElement element, Path baseDirectory, List<LocalMusicTrack> tracks, Set<String> visitedLibraries) {
        if (element == null || element.isJsonNull()) {
            return;
        }

        if (element.isJsonArray()) {
            addTracks(element.getAsJsonArray(), baseDirectory, tracks);
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
                } else if (baseDirectory != null) {
                    loadLocalJson(baseDirectory.resolve(location).normalize(), tracks, visitedLibraries);
                }
            }
        }

        if (object.has("tracks") && object.get("tracks").isJsonArray()) {
            addTracks(object.getAsJsonArray("tracks"), baseDirectory, tracks);
        } else if (object.has("file") || object.has("path") || object.has("url")) {
            addTrack(object, baseDirectory, tracks);
        }
    }

    private static void addTracks(JsonArray array, Path baseDirectory, List<LocalMusicTrack> tracks) {
        for (JsonElement element : array) {
            if (element.isJsonObject()) {
                addTrack(element.getAsJsonObject(), baseDirectory, tracks);
            }
        }
    }

    private static void addTrack(JsonObject object, Path baseDirectory, List<LocalMusicTrack> tracks) {
        String id = getString(object, "id", null);
        String title = getString(object, "title", id);
        String url = getString(object, "url", null);
        String fileName = getString(object, "file", getString(object, "path", null));
        float volume = getFloat(object, "volume", 1.0F);
        boolean loop = getBoolean(object, "loop", false);

        Path file = null;
        if (fileName != null && !fileName.isBlank()) {
            Path raw = Path.of(fileName);
            file = raw.isAbsolute() || baseDirectory == null ? raw.normalize() : baseDirectory.resolve(raw).normalize();
        }

        if ((id == null || id.isBlank()) && title != null && !title.isBlank()) {
            id = normalizeId(title);
        }
        if ((id == null || id.isBlank()) && file != null) {
            id = stripExtension(file.getFileName().toString());
        }
        if ((id == null || id.isBlank()) && url != null) {
            id = stripExtension(Path.of(URI.create(url).getPath()).getFileName().toString());
        }

        id = normalizeId(id);
        if (id.isBlank()) {
            id = generatedId(title, fileName, url);
        }

        if (id.isBlank() || (file == null && (url == null || url.isBlank()))) {
            return;
        }

        tracks.add(new LocalMusicTrack(id, title, file, url, Math.max(0.0F, volume), loop));
    }

    private static Path findLibraryRoot() {
        String configured = System.getProperty("localmusicmod.libraryDir");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("LOCALMUSICMOD_LIBRARY_DIR");
        }
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }

        List<Path> starts = new ArrayList<>();
        starts.add(Path.of("").toAbsolutePath());
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameDirectory != null) {
            starts.add(minecraft.gameDirectory.toPath().toAbsolutePath());
        }

        for (Path start : starts) {
            Path current = start.normalize();
            for (int i = 0; i < 6 && current != null; i++) {
                Path candidate = current.resolve("musiclibraries");
                if (Files.isDirectory(candidate)) {
                    return candidate;
                }
                current = current.getParent();
            }
        }

        return starts.getFirst().resolve("musiclibraries").normalize();
    }

    private static boolean isJsonFile(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json");
    }

    private static boolean isHttpUrl(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
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
}

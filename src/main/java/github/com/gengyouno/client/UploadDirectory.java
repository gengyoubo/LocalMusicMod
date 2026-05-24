package github.com.gengyouno.client;

import github.com.gengyouno.Localmusicmod;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class UploadDirectory {
    private UploadDirectory() {
    }

    public static Path path() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("localmusicmod").resolve("uploads").normalize();
    }

    public static Path ensureExists() {
        Path uploads = path();
        try {
            Files.createDirectories(uploads);
        } catch (IOException exception) {
            Localmusicmod.LOGGER.warn("Could not create local music upload directory {}", uploads, exception);
        }
        return uploads;
    }
}

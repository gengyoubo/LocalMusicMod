package github.com.gengyouno;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_GITHUB_URLS = BUILDER
            .comment("Whether /music may play tracks from http(s) URLs, for example GitHub raw links.")
            .define("enableGithubUrls", true);

    public static final ModConfigSpec.ConfigValue<String> DEFAULT_LIBRARY_URL = BUILDER
            .comment("Default remote library.json URL. Leave blank to only use local musiclibraries JSON files.")
            .define("defaultLibraryUrl", "https://raw.githubusercontent.com/gengyoubo/LocalMusicMod/master/musiclibraries/library.json");

    static final ModConfigSpec SPEC = BUILDER.build();
}

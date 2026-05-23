package github.com.gengyouno;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_GITHUB_URLS = BUILDER
            .comment("Whether /music may play tracks from http(s) URLs, for example GitHub raw links.")
            .define("enableGithubUrls", true);

    static final ModConfigSpec SPEC = BUILDER.build();
}

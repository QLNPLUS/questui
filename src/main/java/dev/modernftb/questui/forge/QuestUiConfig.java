package dev.modernftb.questui.forge;

import net.minecraftforge.common.ForgeConfigSpec;

public final class QuestUiConfig {
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final ForgeConfigSpec.BooleanValue CHAPTER_HIT_TEST_DEBUG;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("debug");
        CHAPTER_HIT_TEST_DEBUG = builder
                .comment("Log mouse coordinates, AUI hit targets, and chapter toggle bounds while diagnosing clicks.")
                .define("chapterHitTest", false);
        builder.pop();
        CLIENT_SPEC = builder.build();
    }

    private QuestUiConfig() {
    }
}

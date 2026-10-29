package dev.modernftb.questui.forge;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(value="questui")
public final class QuestUiMod {
    public static final String MOD_ID = "questui";
    public static final Logger LOGGER = LoggerFactory.getLogger((String)"questui");

    public QuestUiMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, QuestUiConfig.CLIENT_SPEC);
        LOGGER.info("Apricity Quests loaded; client UI integration is enabled when FTB Quests is present.");
    }
}


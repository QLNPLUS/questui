package dev.modernftb.questui.common.model;

import dev.modernftb.questui.common.model.QuestMapNodeSnapshot;
import dev.modernftb.questui.common.model.QuestSnapshot;
import java.util.List;

public record ChapterSnapshot(long id, String title, String subtitle, int defaultMinWidth, double defaultQuestSize, String defaultQuestShape, boolean alwaysInvisible, boolean requireSequentialTasks, boolean defaultRepeatable, boolean consumeItems, boolean hideQuestDetailsUntilStartable, boolean hideQuestUntilDepsComplete, boolean hideQuestUntilDepsVisible, String progressionMode, List<QuestSnapshot> quests, List<QuestMapNodeSnapshot> mapNodes) {
    public ChapterSnapshot {
        title = title == null || title.isBlank() ? "Untitled chapter" : title;
        subtitle = subtitle == null ? "" : subtitle;
        defaultMinWidth = Math.max(0, defaultMinWidth);
        defaultQuestSize = Math.max(1.0, defaultQuestSize);
        defaultQuestShape = defaultQuestShape == null || defaultQuestShape.isBlank() ? "default" : defaultQuestShape;
        progressionMode = progressionMode == null ? "" : progressionMode;
        quests = List.copyOf(quests);
        mapNodes = List.copyOf(mapNodes);
    }
}


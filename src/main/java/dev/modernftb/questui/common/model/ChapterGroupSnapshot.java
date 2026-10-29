package dev.modernftb.questui.common.model;

import dev.modernftb.questui.common.model.ChapterSnapshot;
import java.util.List;

public record ChapterGroupSnapshot(long id, String title, boolean defaultGroup, boolean collapsed, List<ChapterSnapshot> chapters) {
    public ChapterGroupSnapshot {
        title = title == null || title.isBlank() ? "Chapters" : title;
        chapters = List.copyOf(chapters);
    }
}


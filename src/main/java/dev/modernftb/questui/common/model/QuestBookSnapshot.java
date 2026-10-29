package dev.modernftb.questui.common.model;

import dev.modernftb.questui.common.model.ChapterGroupSnapshot;
import dev.modernftb.questui.common.model.ChapterSnapshot;
import java.util.List;

public record QuestBookSnapshot(String title, List<ChapterGroupSnapshot> groups) {
    public QuestBookSnapshot {
        title = title == null || title.isBlank() ? "Quest Book" : title;
        groups = List.copyOf(groups);
    }

    public List<ChapterSnapshot> chapters() {
        return this.groups.stream().flatMap(group -> group.chapters().stream()).toList();
    }
}


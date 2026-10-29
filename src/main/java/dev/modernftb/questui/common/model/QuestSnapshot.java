package dev.modernftb.questui.common.model;

import dev.modernftb.questui.common.model.RewardSnapshot;
import dev.modernftb.questui.common.model.TaskSnapshot;
import java.util.List;

public record QuestSnapshot(long id, String title, String subtitle, String description, int progressPercent, boolean started, boolean canStart, boolean completed, boolean detailsHidden, List<Long> dependencies, boolean hideDependencyLines, boolean hideDependentLines, List<TaskSnapshot> tasks, List<RewardSnapshot> rewards) {
    public QuestSnapshot {
        title = title == null || title.isBlank() ? "Untitled quest" : title;
        subtitle = subtitle == null ? "" : subtitle;
        description = description == null ? "" : description;
        progressPercent = Math.max(0, Math.min(100, progressPercent));
        dependencies = List.copyOf(dependencies);
        tasks = List.copyOf(tasks);
        rewards = List.copyOf(rewards);
    }
}


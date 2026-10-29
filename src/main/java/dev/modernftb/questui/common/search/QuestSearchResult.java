package dev.modernftb.questui.common.search;

import java.util.Objects;

public record QuestSearchResult(long chapterId, long questId, String title, String matchDetail, MatchKind kind) {
    public enum MatchKind {
        CHAPTER,
        QUEST,
        TASK,
        REWARD
    }

    public QuestSearchResult {
        title = Objects.requireNonNullElse(title, "");
        matchDetail = Objects.requireNonNullElse(matchDetail, "");
        kind = Objects.requireNonNull(kind);
    }
}

package dev.modernftb.questui.common.search;

public record QuestSearchOptions(boolean chapterNames, boolean questNames,
                                 boolean tasksAndRewards, boolean crossChapter) {
    public static QuestSearchOptions defaults() {
        return new QuestSearchOptions(true, true, true, false);
    }
}

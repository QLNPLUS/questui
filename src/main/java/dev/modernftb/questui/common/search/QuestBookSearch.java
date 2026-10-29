package dev.modernftb.questui.common.search;

import dev.modernftb.questui.common.model.ChapterSnapshot;
import dev.modernftb.questui.common.model.QuestSnapshot;
import dev.modernftb.questui.common.model.RewardSnapshot;
import dev.modernftb.questui.common.model.TaskSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class QuestBookSearch {
    public static final int MAX_RESULTS = 120;

    /**
     * @param truncated true when the scan stopped at its time budget instead of covering the whole
     *                  book, so callers can say so instead of presenting partial results as final.
     */
    public record Outcome(List<QuestSearchResult> results, boolean truncated) {
    }

    private QuestBookSearch() {
    }

    /**
     * Scans a pre-built {@link QuestSearchIndex} within a time budget.
     *
     * @param deadlineNanos an absolute {@link System#nanoTime()} instant. The scan returns what it has
     *                      found once the budget runs out, so one oversized quest book can never pin
     *                      the search thread long enough to look like a freeze.
     */
    public static Outcome search(QuestSearchIndex index, long currentChapterId, String query,
                                 QuestSearchOptions options, long deadlineNanos) {
        if (index == null || Thread.currentThread().isInterrupted()) {
            return new Outcome(List.of(), false);
        }
        String needle = normalize(query);
        if (needle.isEmpty()) {
            return new Outcome(List.of(), false);
        }
        QuestSearchOptions selectedOptions = options == null ? QuestSearchOptions.defaults() : options;
        List<QuestSearchResult> results = new ArrayList<>();
        for (QuestSearchIndex.ChapterEntry chapter : index.chapters()) {
            if (QuestBookSearch.outOfBudget(deadlineNanos)) {
                return new Outcome(List.copyOf(results), true);
            }
            if (!selectedOptions.crossChapter() && chapter.chapterId() != currentChapterId) {
                continue;
            }
            String chapterTitle = chapter.title().display();
            if (selectedOptions.chapterNames()
                    && (chapter.title().key().contains(needle) || chapter.subtitle().key().contains(needle))) {
                results.add(new QuestSearchResult(chapter.chapterId(), 0L, chapterTitle, "章节",
                        QuestSearchResult.MatchKind.CHAPTER));
                if (results.size() >= MAX_RESULTS) {
                    return new Outcome(List.copyOf(results), false);
                }
            }
            for (QuestSearchIndex.QuestEntry quest : chapter.quests()) {
                if (QuestBookSearch.outOfBudget(deadlineNanos)) {
                    return new Outcome(List.copyOf(results), true);
                }
                if (selectedOptions.questNames()) {
                    boolean nameMatch = quest.title().key().contains(needle)
                            || quest.subtitle().key().contains(needle);
                    boolean descriptionMatch = quest.description().key().contains(needle);
                    if (nameMatch || descriptionMatch) {
                        results.add(new QuestSearchResult(chapter.chapterId(), quest.questId(),
                                quest.title().display(),
                                chapterTitle + (nameMatch ? " · 任务" : " · 说明"),
                                QuestSearchResult.MatchKind.QUEST));
                        if (results.size() >= MAX_RESULTS) {
                            return new Outcome(List.copyOf(results), false);
                        }
                    }
                }
                if (selectedOptions.tasksAndRewards()) {
                    QuestBookSearch.addTaskMatches(results, chapter.chapterId(), chapterTitle, quest, needle);
                    if (results.size() >= MAX_RESULTS) {
                        return new Outcome(List.copyOf(results), false);
                    }
                    QuestBookSearch.addRewardMatches(results, chapter.chapterId(), chapterTitle, quest, needle);
                    if (results.size() >= MAX_RESULTS) {
                        return new Outcome(List.copyOf(results), false);
                    }
                }
            }
        }
        return new Outcome(List.copyOf(results), false);
    }

    public static boolean matchesQuest(QuestSnapshot quest, String query, QuestSearchOptions options) {
        String needle = normalize(query);
        if (needle.isEmpty()) {
            return true;
        }
        if (quest == null) {
            return false;
        }
        QuestSearchOptions selectedOptions = options == null ? QuestSearchOptions.defaults() : options;
        if (selectedOptions.questNames()
                && (contains(quest.title(), needle) || contains(quest.subtitle(), needle)
                || contains(quest.description(), needle))) {
            return true;
        }
        if (selectedOptions.tasksAndRewards()) {
            return quest.tasks().stream().anyMatch(task -> contains(task.title(), needle))
                    || quest.rewards().stream().anyMatch(reward -> contains(reward.title(), needle));
        }
        return false;
    }

    public static boolean matchesChapter(ChapterSnapshot chapter, String query, QuestSearchOptions options) {
        String needle = normalize(query);
        if (chapter == null || needle.isEmpty()) {
            return false;
        }
        QuestSearchOptions selectedOptions = options == null ? QuestSearchOptions.defaults() : options;
        return selectedOptions.chapterNames()
                && (contains(chapter.title(), needle) || contains(chapter.subtitle(), needle));
    }

    private static void addTaskMatches(List<QuestSearchResult> results, long chapterId, String chapterTitle,
                                       QuestSearchIndex.QuestEntry quest, String needle) {
        for (QuestSearchIndex.Text task : quest.tasks()) {
            if (results.size() >= MAX_RESULTS) {
                return;
            }
            if (task.key().contains(needle)) {
                results.add(new QuestSearchResult(chapterId, quest.questId(),
                        quest.title().display(),
                        chapterTitle + " · 目标：" + task.display(),
                        QuestSearchResult.MatchKind.TASK));
            }
        }
    }

    private static void addRewardMatches(List<QuestSearchResult> results, long chapterId, String chapterTitle,
                                         QuestSearchIndex.QuestEntry quest, String needle) {
        for (QuestSearchIndex.Text reward : quest.rewards()) {
            if (results.size() >= MAX_RESULTS) {
                return;
            }
            if (reward.key().contains(needle)) {
                results.add(new QuestSearchResult(chapterId, quest.questId(),
                        quest.title().display(),
                        chapterTitle + " · 奖励：" + reward.display(),
                        QuestSearchResult.MatchKind.REWARD));
            }
        }
    }

    private static boolean outOfBudget(long deadlineNanos) {
        return Thread.currentThread().isInterrupted() || System.nanoTime() >= deadlineNanos;
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}

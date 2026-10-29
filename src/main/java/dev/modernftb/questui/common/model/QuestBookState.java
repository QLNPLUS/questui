package dev.modernftb.questui.common.model;

import dev.modernftb.questui.common.model.ChapterGroupSnapshot;
import dev.modernftb.questui.common.model.ChapterSnapshot;
import dev.modernftb.questui.common.model.QuestBookSnapshot;
import dev.modernftb.questui.common.model.QuestSnapshot;
import dev.modernftb.questui.common.search.QuestBookSearch;
import dev.modernftb.questui.common.search.QuestSearchOptions;
import dev.modernftb.questui.common.search.QuestSearchResult;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class QuestBookState {
    private QuestBookSnapshot snapshot;
    private long selectedChapterId;
    private long selectedQuestId;
    private String searchQuery = "";
    private QuestSearchOptions searchOptions = QuestSearchOptions.defaults();
    private List<QuestSearchResult> cachedSearchResults;
    private Set<Long> cachedVisibleQuestIds = Set.of();
    private QuestBookSnapshot cachedSearchSnapshot;
    private QuestSearchOptions cachedSearchOptions;
    private String cachedSearchQuery;
    private long cachedSearchChapterId;
    private boolean searchPending;

    public QuestBookState(QuestBookSnapshot snapshot) {
        this.replaceSnapshot(snapshot);
    }

    public QuestBookSnapshot snapshot() {
        return this.snapshot;
    }

    public long selectedChapterId() {
        return this.selectedChapterId;
    }

    public long selectedQuestId() {
        return this.selectedQuestId;
    }

    public String searchQuery() {
        return this.searchQuery;
    }

    public void setSearchQuery(String query) {
        String next = query == null ? "" : query.trim();
        if (!next.equals(this.searchQuery)) {
            this.searchQuery = next;
            this.invalidateSearchCache();
        }
    }

    public QuestSearchOptions searchOptions() {
        return this.searchOptions;
    }

    public void setSearchOptions(QuestSearchOptions options) {
        QuestSearchOptions next = options == null ? QuestSearchOptions.defaults() : options;
        if (!next.equals(this.searchOptions)) {
            this.searchOptions = next;
            this.invalidateSearchCache();
        }
    }

    public boolean searchPending() {
        return this.searchPending;
    }

    public void setSearchPending(boolean pending) {
        this.searchPending = pending;
    }

    public void setSearchResults(List<QuestSearchResult> results) {
        this.cachedSearchResults = List.copyOf(results == null ? List.of() : results);
        this.cachedSearchSnapshot = this.snapshot;
        this.cachedSearchQuery = this.searchQuery;
        this.cachedSearchOptions = this.searchOptions;
        this.cachedSearchChapterId = this.selectedChapterId;
        this.cachedVisibleQuestIds = this.cachedSearchResults.stream()
                .filter(result -> result.chapterId() == this.selectedChapterId && result.questId() != 0L)
                .map(QuestSearchResult::questId).collect(Collectors.toUnmodifiableSet());
        this.searchPending = false;
    }

    public List<QuestSearchResult> searchResults() {
        return this.hasCurrentSearchResults() ? this.cachedSearchResults : List.of();
    }

    private boolean hasCurrentSearchResults() {
        return this.cachedSearchResults != null && this.cachedSearchSnapshot == this.snapshot
                && this.cachedSearchChapterId == this.selectedChapterId
                && this.searchQuery.equals(this.cachedSearchQuery)
                && this.searchOptions.equals(this.cachedSearchOptions);
    }

    private void invalidateSearchCache() {
        this.cachedSearchResults = null;
        this.cachedVisibleQuestIds = Set.of();
        this.cachedSearchSnapshot = null;
        this.cachedSearchQuery = null;
        this.cachedSearchOptions = null;
        this.searchPending = false;
    }

    public void replaceSnapshot(QuestBookSnapshot next) {
        long previousChapter = this.selectedChapterId;
        long previousQuest = this.selectedQuestId;
        boolean snapshotChanged = this.snapshot != next;
        this.snapshot = next;
        ChapterSnapshot chapter = this.findChapter(previousChapter).orElseGet(() -> this.snapshot.chapters().stream().filter(candidate -> !candidate.quests().isEmpty()).findFirst().orElseGet(() -> this.snapshot.chapters().stream().findFirst().orElse(null)));
        this.selectedChapterId = chapter == null ? 0L : chapter.id();
        this.selectedQuestId = chapter == null || chapter.quests().stream().noneMatch(quest -> quest.id() == previousQuest) ? 0L : previousQuest;
        if (snapshotChanged || previousChapter != this.selectedChapterId) {
            this.invalidateSearchCache();
        }
    }

    public void replaceSelectedQuest(QuestSnapshot quest) {
        if (quest == null || this.selectedQuestId != quest.id()) {
            return;
        }
        List<ChapterGroupSnapshot> groups = this.snapshot.groups().stream().map(group -> {
            List<ChapterSnapshot> chapters = group.chapters().stream().map(chapter -> {
                if (chapter.id() != this.selectedChapterId) {
                    return chapter;
                }
                List<QuestSnapshot> quests = chapter.quests().stream().map(existing -> existing.id() == quest.id() ? quest : existing).toList();
                return new ChapterSnapshot(chapter.id(), chapter.title(), chapter.subtitle(), chapter.defaultMinWidth(), chapter.defaultQuestSize(), chapter.defaultQuestShape(), chapter.alwaysInvisible(), chapter.requireSequentialTasks(), chapter.defaultRepeatable(), chapter.consumeItems(), chapter.hideQuestDetailsUntilStartable(), chapter.hideQuestUntilDepsComplete(), chapter.hideQuestUntilDepsVisible(), chapter.progressionMode(), quests, chapter.mapNodes());
            }).toList();
            return new ChapterGroupSnapshot(group.id(), group.title(), group.defaultGroup(), group.collapsed(), chapters);
        }).toList();
        this.snapshot = new QuestBookSnapshot(this.snapshot.title(), groups);
        this.invalidateSearchCache();
    }

    public void selectChapter(long chapterId) {
        if (this.findChapter(chapterId).isEmpty()) {
            return;
        }
        this.selectedChapterId = chapterId;
        this.selectedQuestId = 0L;
        this.invalidateSearchCache();
    }

    public void selectQuest(long questId) {
        ChapterSnapshot chapter = this.selectedChapter().orElse(null);
        if (chapter != null && chapter.quests().stream().anyMatch(quest -> quest.id() == questId)) {
            this.selectedQuestId = questId;
        }
    }

    public void clearSelectedQuest() {
        this.selectedQuestId = 0L;
    }

    public Optional<ChapterSnapshot> selectedChapter() {
        return this.findChapter(this.selectedChapterId);
    }

    public Optional<QuestSnapshot> selectedQuest() {
        return this.selectedChapter().flatMap(chapter -> chapter.quests().stream().filter(quest -> quest.id() == this.selectedQuestId).findFirst());
    }

    public List<QuestSnapshot> visibleQuests() {
        ChapterSnapshot chapter = this.selectedChapter().orElse(null);
        if (chapter == null) {
            return List.of();
        }
        if (QuestBookSearch.matchesChapter(chapter, this.searchQuery, this.searchOptions)) {
            return chapter.quests();
        }
        if (this.searchQuery.isBlank()) {
            return chapter.quests();
        }
        if (this.searchPending) {
            return List.of();
        }
        if (this.hasCurrentSearchResults()) {
            return chapter.quests().stream().filter(quest -> this.cachedVisibleQuestIds.contains(quest.id())).toList();
        }
        return List.of();
    }

    private Optional<ChapterSnapshot> findChapter(long chapterId) {
        return this.snapshot.chapters().stream().filter(chapter -> chapter.id() == chapterId).findFirst();
    }


}


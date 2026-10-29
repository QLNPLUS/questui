package dev.modernftb.questui.common.search;

import dev.modernftb.questui.common.model.ChapterGroupSnapshot;
import dev.modernftb.questui.common.model.ChapterSnapshot;
import dev.modernftb.questui.common.model.QuestBookSnapshot;
import dev.modernftb.questui.common.model.QuestSnapshot;
import dev.modernftb.questui.common.model.RewardSnapshot;
import dev.modernftb.questui.common.model.TaskSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pre-normalized search text for one {@link QuestBookSnapshot}.
 *
 * Building this once per snapshot keeps a keystroke from re-lowercasing every quest title,
 * description, task and reward in the pack, which is what made long searches stall the client.
 */
public final class QuestSearchIndex {
    /** A displayable string paired with the lower-cased form used for matching. */
    public record Text(String display, String key) {
        static Text of(String value) {
            String safe = value == null ? "" : value;
            return new Text(safe, safe.trim().toLowerCase(Locale.ROOT));
        }
    }

    public record QuestEntry(long questId, Text title, Text subtitle, Text description,
                             Text[] tasks, Text[] rewards) {
    }

    public record ChapterEntry(long chapterId, Text title, Text subtitle, List<QuestEntry> quests) {
    }

    private final QuestBookSnapshot snapshot;
    private final List<ChapterEntry> chapters;

    private QuestSearchIndex(QuestBookSnapshot snapshot, List<ChapterEntry> chapters) {
        this.snapshot = snapshot;
        this.chapters = chapters;
    }

    public static QuestSearchIndex build(QuestBookSnapshot book) {
        if (book == null) {
            return new QuestSearchIndex(null, List.of());
        }
        List<ChapterEntry> entries = new ArrayList<>();
        for (ChapterGroupSnapshot group : book.groups()) {
            for (ChapterSnapshot chapter : group.chapters()) {
                List<QuestEntry> quests = new ArrayList<>(chapter.quests().size());
                for (QuestSnapshot quest : chapter.quests()) {
                    quests.add(new QuestEntry(quest.id(),
                            Text.of(quest.title()),
                            Text.of(quest.subtitle()),
                            Text.of(quest.description()),
                            texts(quest.tasks().stream().map(TaskSnapshot::title).toList()),
                            texts(quest.rewards().stream().map(RewardSnapshot::title).toList())));
                }
                entries.add(new ChapterEntry(chapter.id(),
                        Text.of(chapter.title()),
                        Text.of(chapter.subtitle()),
                        List.copyOf(quests)));
            }
        }
        return new QuestSearchIndex(book, List.copyOf(entries));
    }

    /** The snapshot this index was derived from; identity-compared before reuse. */
    public QuestBookSnapshot snapshot() {
        return this.snapshot;
    }

    public List<ChapterEntry> chapters() {
        return this.chapters;
    }

    private static Text[] texts(List<String> values) {
        Text[] texts = new Text[values.size()];
        for (int i = 0; i < texts.length; ++i) {
            texts[i] = Text.of(values.get(i));
        }
        return texts;
    }
}

package dev.modernftb.questui.forge.client;

import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.ChapterGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestLink;
import dev.ftb.mods.ftbquests.quest.QuestObject;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbquests.quest.reward.RewardClaimType;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.modernftb.questui.common.model.ChapterGroupSnapshot;
import dev.modernftb.questui.common.model.ChapterSnapshot;
import dev.modernftb.questui.common.model.QuestBookSnapshot;
import dev.modernftb.questui.common.model.QuestMapNodeSnapshot;
import dev.modernftb.questui.common.model.QuestSnapshot;
import dev.modernftb.questui.common.model.RewardSnapshot;
import dev.modernftb.questui.common.model.TaskSnapshot;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;

final class QuestBookSnapshotFactory {
    private QuestBookSnapshotFactory() {
    }

    static QuestBookSnapshot capture(ClientQuestFile file) {
        TeamData teamData = file.selfTeamData;
        ArrayList<ChapterGroupSnapshot> groups = new ArrayList<ChapterGroupSnapshot>();
        for (ChapterGroup group : file.getChapterGroups()) {
            if (!file.canEdit() && !group.isVisible(teamData)) continue;
            ArrayList<ChapterSnapshot> chapters = new ArrayList<ChapterSnapshot>();
            for (Chapter chapter : group.getChapters()) {
                if (!file.canEdit() && (!chapter.isVisible(teamData) || chapter.isAlwaysInvisible())) continue;
                chapters.add(QuestBookSnapshotFactory.snapshotChapter(file, teamData, chapter));
            }
            if (chapters.isEmpty() && !file.canEdit()) continue;
            groups.add(new ChapterGroupSnapshot(group.getId(), group.getTitle().getString(), group.isDefaultGroup(), group.isGuiCollapsed(), chapters));
        }
        return new QuestBookSnapshot(file.getTitle().getString(), groups);
    }

    static QuestSnapshot captureQuest(ClientQuestFile file, Quest quest) {
        if (file == null || file.selfTeamData == null || quest == null) {
            return null;
        }
        return QuestBookSnapshotFactory.snapshotQuest(file, file.selfTeamData, quest);
    }

    private static ChapterSnapshot snapshotChapter(ClientQuestFile file, TeamData teamData, Chapter chapter) {
        HashSet<Long> seenQuestIds = new HashSet<Long>();
        ArrayList<QuestSnapshot> quests = new ArrayList<QuestSnapshot>();
        ArrayList<QuestMapNodeSnapshot> nodes = new ArrayList<QuestMapNodeSnapshot>();
        for (Quest quest : chapter.getQuests()) {
            QuestBookSnapshotFactory.addQuest(file, teamData, quest, seenQuestIds, quests);
            nodes.add(new QuestMapNodeSnapshot(quest.getId(), quest.getId(), quest.getX(), quest.getY(), quest.getWidth(), quest.getHeight(), quest.getIconScale(), quest.getShape(), false, file.canEdit() || quest.isVisible(teamData)));
        }
        for (QuestLink link : chapter.getQuestLinks()) {
            Quest quest = link.getQuest().orElse(null);
            if (quest == null) continue;
            QuestBookSnapshotFactory.addQuest(file, teamData, quest, seenQuestIds, quests);
            nodes.add(new QuestMapNodeSnapshot(link.getId(), quest.getId(), link.getX(), link.getY(), link.getWidth(), link.getHeight(), quest.getIconScale(), link.getShape(), true, file.canEdit() || link.isVisible(teamData) && quest.isVisible(teamData)));
        }
        String subtitle = chapter.getRawSubtitle().stream().map(String::trim).filter(line -> !line.isBlank()).collect(Collectors.joining("\n"));
        String progressionMode = chapter.getProgressionMode() == null ? "" : chapter.getProgressionMode().name();
        return new ChapterSnapshot(chapter.getId(), chapter.getTitle().getString(), subtitle, chapter.getDefaultMinWidth(), chapter.getDefaultQuestSize(), chapter.getDefaultQuestShape(), chapter.isAlwaysInvisible(), chapter.isRequireSequentialTasks(), chapter.isDefaultRepeatable(), chapter.consumeItems(), chapter.hideQuestDetailsUntilStartable(), chapter.hideQuestUntilDepsComplete(), chapter.isHideQuestUntilDepsVisible(), progressionMode, quests, nodes);
    }

    private static boolean addQuest(ClientQuestFile file, TeamData teamData, Quest quest, Set<Long> seenQuestIds, List<QuestSnapshot> result) {
        if (!file.canEdit() && !quest.isVisible(teamData)) {
            return false;
        }
        if (seenQuestIds.add(quest.getId())) {
            result.add(QuestBookSnapshotFactory.snapshotQuest(file, teamData, quest));
        }
        return true;
    }

    private static QuestSnapshot snapshotQuest(ClientQuestFile file, TeamData teamData, Quest quest) {
        ArrayList<TaskSnapshot> tasks = new ArrayList<TaskSnapshot>();
        boolean sequentialTasks = !file.canEdit() && quest.getRequireSequentialTasks();
        for (Task task : quest.getTasks()) {
            boolean completed = teamData.isCompleted((QuestObject)task);
            tasks.add(QuestBookSnapshotFactory.snapshotTask(teamData, quest, task));
            if (!sequentialTasks || completed) continue;
            break;
        }
        UUID playerId = Minecraft.m_91087_().f_91074_ == null ? null : Minecraft.m_91087_().f_91074_.m_20148_();
        List<RewardSnapshot> rewards = quest.getRewards().stream().map(reward -> QuestBookSnapshotFactory.snapshotReward(teamData, playerId, reward)).toList();
        String description = quest.getDescription().stream().map(component -> component.getString()).filter(line -> !line.isBlank()).collect(Collectors.joining("\n"));
        List<Long> dependencies = quest.streamDependencies().filter(Quest.class::isInstance).map(Quest.class::cast).map(QuestObjectBase::getId).toList();
        boolean canStart = teamData.canStartTasks(quest);
        return new QuestSnapshot(quest.getId(), quest.getTitle().getString(), quest.getSubtitle().getString(), description,
                teamData.getRelativeProgress((QuestObject)quest), teamData.isStarted((QuestObject)quest), canStart,
                teamData.isCompleted((QuestObject)quest),
                (quest.hideDetailsUntilStartable() || quest.getChapter().hideQuestDetailsUntilStartable()) && !canStart,
                dependencies, quest.shouldHideDependencyLines(), quest.shouldHideDependentLines(), tasks, rewards);
    }

    private static TaskSnapshot snapshotTask(TeamData teamData, Quest quest, Task task) {
        boolean canClick = task.isValid() && teamData.canStartTasks(quest) && !teamData.isCompleted((QuestObject)task);
        return new TaskSnapshot(task.getId(), task.getTitle().getString(), task.getType().getTypeId().toString(), task.getType().getDisplayName().getString(), teamData.getProgress(task), task.getMaxProgress(), teamData.isCompleted((QuestObject)task), canClick);
    }

    private static RewardSnapshot snapshotReward(TeamData teamData, UUID playerId, Reward reward) {
        if (playerId == null) {
            return new RewardSnapshot(reward.getId(), reward.getTitle().getString(), false, false);
        }
        RewardClaimType claimType = teamData.getClaimType(playerId, reward);
        boolean claimed = claimType.isClaimed();
        boolean claimable = claimType.canClaim() && !teamData.isRewardBlocked(reward);
        return new RewardSnapshot(reward.getId(), reward.getTitle().getString(), claimed, claimable);
    }
}


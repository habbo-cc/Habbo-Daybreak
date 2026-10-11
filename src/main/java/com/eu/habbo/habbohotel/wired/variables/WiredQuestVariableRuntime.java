package com.eu.habbo.habbohotel.wired.variables;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.quests.Quest;
import com.eu.habbo.habbohotel.quests.QuestManager;
import com.eu.habbo.habbohotel.quests.QuestUserProgress;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredVariableType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Read-only July Quest and Quest Chain variable values backed by Arcturus'
 * authoritative quest progress.
 */
public final class WiredQuestVariableRuntime {
    private WiredQuestVariableRuntime() {
    }

    public static boolean isQuestBacked(WiredVariableDefinition definition) {
        return definition != null
                && (definition.type() == WiredVariableType.QUEST
                || definition.type() == WiredVariableType.QUEST_CHAIN);
    }

    public static WiredVariableValue read(Room room, WiredVariableDefinition definition,
                                          WiredVariableHolder holder) {
        if (room == null || !isQuestBacked(definition) || holder == null
                || holder.scope() != WiredVariableHolder.Scope.USER) {
            return null;
        }
        Habbo habbo = room.getHabbo(holder.stableId());
        if (habbo == null) {
            return null;
        }
        String configuredCode = configuredCode(room, definition.definitionItemId());
        if (configuredCode == null) {
            return null;
        }

        ProgressValue progress = definition.type() == WiredVariableType.QUEST
                ? questProgress(habbo, configuredCode)
                : questChainProgress(habbo, configuredCode);
        if (progress == null) {
            return null;
        }
        return new WiredVariableValue(definition.variableId(), holder, progress.value,
                progress.createdAtMs, progress.updatedAtMs, progress.revision);
    }

    public static List<WiredVariableValue> values(Room room, WiredVariableDefinition definition) {
        if (room == null || !isQuestBacked(definition)) {
            return List.of();
        }
        List<WiredVariableValue> values = new ArrayList<>();
        for (Habbo habbo : room.getHabbos()) {
            WiredVariableValue value = read(room, definition,
                    WiredVariableHolder.user(habbo.getHabboInfo().getId()));
            if (value != null) {
                values.add(value);
            }
        }
        values.sort(Comparator.comparing(WiredVariableValue::holder));
        return List.copyOf(values);
    }

    private static String configuredCode(Room room, int definitionItemId) {
        if (!(room.getRoomSpecialTypes().getVariable(definitionItemId)
                instanceof com.eu.habbo.habbohotel.items.interactions.wired.variables.WiredVariableQuestBase quest)) {
            return null;
        }
        return quest.configuredCode();
    }

    private static ProgressValue questProgress(Habbo habbo, String code) {
        Quest quest = findQuest(code);
        if (quest == null) {
            return null;
        }
        QuestUserProgress progress;
        synchronized (habbo.getHabboStats().getQuestProgress()) {
            progress = habbo.getHabboStats().getQuestProgress(quest);
        }
        int value = progress == null ? 0 : Math.max(0, progress.getCompletedSteps());
        long completedAt = progress == null ? 0L
                : Math.max(0L, progress.getCompletedAt() * 1000L);
        return new ProgressValue(value, 0L, completedAt, value);
    }

    private static ProgressValue questChainProgress(Habbo habbo, String code) {
        List<Quest> quests = findQuestChain(code);
        if (quests.isEmpty()) {
            return null;
        }
        int completed = 0;
        long latestCompletedAt = 0L;
        synchronized (habbo.getHabboStats().getQuestProgress()) {
            for (Quest quest : quests) {
                QuestUserProgress progress = habbo.getHabboStats().getQuestProgress(quest);
                if (progress != null && progress.isCompleted(quest)) {
                    completed++;
                    latestCompletedAt = Math.max(latestCompletedAt,
                            Math.max(0L, progress.getCompletedAt() * 1000L));
                }
            }
        }
        return new ProgressValue(completed, 0L, latestCompletedAt, completed);
    }

    private static Quest findQuest(String code) {
        QuestManager manager = Emulator.getGameEnvironment().getQuestManager();
        if (manager == null || code == null) {
            return null;
        }
        try {
            Quest byId = manager.getQuest(Integer.parseInt(code));
            if (byId != null) {
                return byId;
            }
        } catch (NumberFormatException ignored) {
        }
        for (Quest quest : manager.getQuests()) {
            if (code.equals(quest.getLocalizationCode())) {
                return quest;
            }
        }
        return null;
    }

    private static List<Quest> findQuestChain(String code) {
        QuestManager manager = Emulator.getGameEnvironment().getQuestManager();
        if (manager == null || code == null) {
            return List.of();
        }
        List<Quest> byCampaign = manager.getQuestsByCampaign(code);
        if (!byCampaign.isEmpty()) {
            return byCampaign;
        }
        return manager.getQuests().stream()
                .filter(quest -> code.equals(quest.getChainCode()))
                .sorted(Comparator.comparingInt(Quest::getSortOrder)
                        .thenComparingInt(Quest::getId))
                .toList();
    }

    private record ProgressValue(int value, long createdAtMs,
                                 long updatedAtMs, long revision) {
    }
}

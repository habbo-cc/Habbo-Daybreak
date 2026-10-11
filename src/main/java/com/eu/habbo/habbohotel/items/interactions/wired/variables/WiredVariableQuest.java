package com.eu.habbo.habbohotel.items.interactions.wired.variables;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.wired.WiredVariableType;

import java.sql.ResultSet;
import java.sql.SQLException;

/** July variable code 5: read-only progress for one quest code per user. */
public final class WiredVariableQuest extends WiredVariableQuestBase {
    public WiredVariableQuest(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredVariableQuest(int id, int userId, Item item, String extra,
                              int limitedStack, int limitedSells) {
        super(id, userId, item, extra, limitedStack, limitedSells);
    }

    @Override
    public WiredVariableType getType() {
        return WiredVariableType.QUEST;
    }
}

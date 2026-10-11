package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredTransactionOutcome;
import java.sql.ResultSet;
import java.sql.SQLException;

/** July AIR parameterless transaction-completed trigger 25. */
public final class WiredTriggerTransactionCompleted extends WiredTriggerTransactionBase {
    public WiredTriggerTransactionCompleted(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredTriggerTransactionCompleted(
            int id,
            int userId,
            Item item,
            String extradata,
            int limitedStack,
            int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredTriggerType getType() {
        return WiredTriggerType.TRANSACTION_COMPLETED;
    }

    @Override
    WiredEvent.Type eventType() {
        return WiredEvent.Type.TRANSACTION_COMPLETED;
    }

    @Override
    boolean accepts(WiredTransactionOutcome outcome) {
        return outcome.completed();
    }
}

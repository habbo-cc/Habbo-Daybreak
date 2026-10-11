package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.habbohotel.items.Item;

import java.sql.ResultSet;
import java.sql.SQLException;

/** Marker interaction for July credit-storage chests. */
public class InteractionCoinChest extends InteractionFurniChest {
    public InteractionCoinChest(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionCoinChest(int id, int userId, Item item, String extradata,
            int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }
}

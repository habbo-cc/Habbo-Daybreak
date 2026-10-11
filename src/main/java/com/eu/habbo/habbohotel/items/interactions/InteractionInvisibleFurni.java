package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.habbohotel.items.Item;

import java.sql.ResultSet;
import java.sql.SQLException;

/** Shared interaction identity for July's invisible block, click, bed and chair tiles. */
public class InteractionInvisibleFurni extends InteractionDefault {
    public InteractionInvisibleFurni(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionInvisibleFurni(int id, int userId, Item item, String extradata,
                                     int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }
}

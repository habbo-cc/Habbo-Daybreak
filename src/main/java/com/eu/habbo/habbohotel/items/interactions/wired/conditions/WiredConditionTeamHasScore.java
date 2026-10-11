package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.games.GamePlayer;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredConditionTeamHasScore extends WiredConditionConfigBase {
    public static final WiredConditionType type = WiredConditionType.TEAM_HAS_SCORE;

    public WiredConditionTeamHasScore(ResultSet set, Item baseItem) throws SQLException { super(set, baseItem); }
    public WiredConditionTeamHasScore(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) { super(id, userId, item, extradata, limitedStack, limitedSells); }

    @Override public WiredConditionType getType() { return type; }

    @Override
    public boolean evaluate(WiredContext ctx) {
        RoomUnit actor = ctx.actor().orElse(null);
        Habbo habbo = actor != null ? ctx.room().getHabbo(actor) : null;
        GamePlayer player = habbo != null ? habbo.getHabboInfo().getGamePlayer() : null;
        if (player == null) {
            return false;
        }

        int team = this.intParams.length > 0 ? this.intParams[0] : 0;
        int score = this.intParams.length > 1 ? this.intParams[1] : 0;
        int operator = this.intParams.length > 2 ? this.intParams[2] : 0;
        if (team != 0 && player.getTeamColor().type != team) {
            return false;
        }
        return compare(player.getScore(), score, operator);
    }
}

package com.eu.habbo.habbohotel.items.interactions.wired.selectors;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.games.Game;
import com.eu.habbo.habbohotel.games.GameTeam;
import com.eu.habbo.habbohotel.games.GameTeamColors;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredSelectorType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredTargets;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredSelectorUsersInTeam extends WiredSelectorConfigBase {
    public WiredSelectorUsersInTeam(ResultSet set, Item baseItem) throws SQLException { super(set, baseItem); }
    public WiredSelectorUsersInTeam(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) { super(id, userId, item, extradata, limitedStack, limitedSells); }
    @Override public WiredSelectorType getType() { return WiredSelectorType.USERS_IN_TEAM; }
    @Override public WiredTargets resolve(Room room, WiredContext ctx) {
        WiredTargets targets = targets();
        int team = this.intParams.length > 0 ? this.intParams[0] : 0;
        for (RoomUnit unit : allRoomUnits(room)) {
            Habbo habbo = room.getHabbo(unit);
            if (habbo == null) {
                continue;
            }
            GameTeam matched = null;
            for (Game game : room.getGames()) {
                matched = game.getTeamForHabbo(habbo);
                if (matched != null) {
                    break;
                }
            }
            if (matched != null && (team == 0 || matched.teamColor == GameTeamColors.fromType(team))) {
                targets.addUser(unit);
            }
        }
        return targets;
    }
}

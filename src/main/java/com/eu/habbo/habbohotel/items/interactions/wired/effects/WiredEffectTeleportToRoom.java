package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionRoomLinker;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * TELEPORT_TO_ROOM (44): sends the sourced users to the room holding the pair
 * partner of the picked Wired Room Linker (wf_room_linker, paired like classic
 * teleports via items_teleports). Users arrive on the partner linker's tile.
 */
public class WiredEffectTeleportToRoom extends WiredEffectConfigBase {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredEffectTeleportToRoom.class);

    public static final WiredEffectType type = WiredEffectType.TELEPORT_TO_ROOM;

    public WiredEffectTeleportToRoom(ResultSet set, Item baseItem) throws SQLException { super(set, baseItem); }
    public WiredEffectTeleportToRoom(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) { super(id, userId, item, extradata, limitedStack, limitedSells); }

    @Override public WiredEffectType getType() { return type; }
    @Override public boolean requiresTriggeringUser() { return true; }
    @Override protected boolean supportsFurniPicking() { return true; }
    @Override protected boolean supportsUserPicking() { return true; }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        if (!super.saveData(settings, gameClient)) {
            return false;
        }
        for (HabboItem item : this.items) {
            if (!(item instanceof InteractionRoomLinker)) {
                throw new WiredSaveException("Can only select wired room linkers!");
            }
        }
        return true;
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null) {
            return;
        }

        HabboItem linker = null;
        for (HabboItem item : resolveFurniSource(ctx, this.furniSourceTypes, 0, this.items, null)) {
            if (item instanceof InteractionRoomLinker && item.getRoomId() == room.getId()) {
                linker = item;
                break;
            }
        }
        if (linker == null) {
            return;
        }

        final int linkerId = linker.getId();
        final List<Habbo> travellers = new ArrayList<>();
        for (RoomUnit unit : resolveUserSource(ctx, this.userSourceTypes, 0)) {
            Habbo habbo = room.getHabbo(unit);
            if (habbo != null) { // bots/pets cannot change rooms
                travellers.add(habbo);
            }
        }
        if (travellers.isEmpty()) {
            return;
        }

        // Room transition off the wired/room cycle, like the classic teleport runnables.
        Emulator.getThreading().run(() -> {
            int[] partner = findPartner(linkerId);
            if (partner == null || partner[1] <= 0 || partner[1] == room.getId()) {
                return;
            }

            Room targetRoom = Emulator.getGameEnvironment().getRoomManager().loadRoom(partner[1]);
            if (targetRoom == null) {
                return;
            }
            if (targetRoom.isPreLoaded()) {
                targetRoom.loadData();
            }

            HabboItem targetLinker = targetRoom.getHabboItemByDatabaseId(partner[0]);
            RoomTile arrival = null;
            if (targetLinker != null && targetRoom.getLayout() != null) {
                arrival = targetRoom.getLayout().getTile(targetLinker.getX(), targetLinker.getY());
            }

            for (Habbo habbo : travellers) {
                if (habbo.getHabboInfo().getCurrentRoom() != room) {
                    continue; // already left
                }
                if (arrival != null) {
                    habbo.getRoomUnit().setLocation(arrival);
                    habbo.getRoomUnit().getPath().clear();
                    habbo.getRoomUnit().removeStatus(RoomUnitStatus.MOVE);
                    habbo.getRoomUnit().setZ(arrival.getStackHeight());
                    habbo.getRoomUnit().setPreviousLocationZ(arrival.getStackHeight());
                }
                room.removeHabbo(habbo, false);
                Emulator.getGameEnvironment().getRoomManager().enterRoom(habbo, targetRoom.getId(), "", true, arrival);
            }
        }, 0);
    }

    /** Returns {partnerItemId, partnerRoomId} for the linker's items_teleports pair, or null. */
    private static int[] findPartner(int linkerId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT i.id, i.room_id FROM items_teleports t INNER JOIN items i ON (i.id = t.teleport_one_id OR i.id = t.teleport_two_id) WHERE (t.teleport_one_id = ? OR t.teleport_two_id = ?) AND i.id != ? LIMIT 1")) {
            statement.setInt(1, linkerId);
            statement.setInt(2, linkerId);
            statement.setInt(3, linkerId);
            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    return new int[] { set.getInt("id"), set.getInt("room_id") };
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
        return null;
    }
}

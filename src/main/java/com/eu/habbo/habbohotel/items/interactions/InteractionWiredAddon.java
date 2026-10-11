package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredCategoryType;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.api.IWiredAddon;
import com.eu.habbo.habbohotel.wired.core.WiredAuthorizationService;
import com.eu.habbo.habbohotel.wired.core.WiredFeatureCapabilityGuard;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.wired.OpenMessageComposer;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Editor and persistence foundation for active Wired 2.0 add-ons.  Concrete
 * add-ons are consumed by the room stack index and {@code WiredEngine}; the
 * furniture itself is never invoked as a standalone wired effect.
 */
public abstract class InteractionWiredAddon extends InteractionWired implements IWiredAddon {
    protected InteractionWiredAddon(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    protected InteractionWiredAddon(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    public abstract boolean saveData(WiredSettingsV2 settings);

    @Override
    protected WiredCategoryType getWiredCategory() {
        return WiredCategoryType.ADDON;
    }

    @Override
    protected int getWiredTypeCode() {
        return getType().code;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        serializeWiredDataV2(message, room);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return true;
    }

    @Override
    public boolean isWalkable() {
        return true;
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (client != null
                && room != null
                && WiredFeatureCapabilityGuard.isEditorReady(client, room, this)
                && WiredAuthorizationService.isAuthorized(
                WiredAuthorizationService.Operation.VIEW_EDITOR,
                client,
                room,
                this,
                WiredCategoryType.ADDON)) {
            client.sendResponse(new OpenMessageComposer(this));
            activateBox(room);
        }
    }
}

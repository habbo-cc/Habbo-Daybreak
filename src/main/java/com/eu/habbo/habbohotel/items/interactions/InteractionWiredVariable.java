package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredCategoryType;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.api.IWiredVariable;
import com.eu.habbo.habbohotel.wired.core.WiredAuthorizationService;
import com.eu.habbo.habbohotel.wired.core.WiredCapabilityService;
import com.eu.habbo.habbohotel.wired.variables.WiredCrossRoomAliasRepository;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableDefinition;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.wired.OpenMessageComposer;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * AIR-compatible editor/runtime foundation for Wired 2.0 variables.
 * Each concrete variable type owns its verified storage and lifecycle semantics;
 * unfinished types remain unavailable through capability negotiation.
 */
public abstract class InteractionWiredVariable extends InteractionWired implements IWiredVariable {
    protected InteractionWiredVariable(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    protected InteractionWiredVariable(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    public abstract boolean saveData(WiredSettingsV2 settings);

    /** Binds persistent typed definitions after a room loads or editor save. */
    public boolean bindManager(WiredVariableManager manager) {
        return false;
    }

    /**
     * Keeps the durable cross-room lookup index aligned with the definition
     * accepted by the room manager. Alias types override this to retain their
     * source edge.
     */
    public boolean syncDefinitionRegistry(WiredVariableDefinition definition) {
        return WiredCrossRoomAliasRepository.instance().syncSourceDefinition(definition);
    }

    @Override
    protected WiredCategoryType getWiredCategory() {
        return WiredCategoryType.VARIABLE;
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
                && room.getRoomSpecialTypes().getWiredVariableManager() != null
                && room.getRoomSpecialTypes().getWiredVariableManager().isOperational()
                && client.getWiredCapabilityState().supportsRoom(
                        WiredCapabilityService.CAPABILITY_VARIABLES, room.getId())
                && WiredAuthorizationService.isAuthorized(
                WiredAuthorizationService.Operation.VIEW_EDITOR,
                client,
                room,
                this,
                WiredCategoryType.VARIABLE)) {
            client.sendResponse(new OpenMessageComposer(this));
            activateBox(room);
        }
    }
}

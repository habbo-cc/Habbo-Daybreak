package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

/** July floor-furniture chest using standard MapStuffData room serialization. */
public class InteractionFurniChest extends InteractionDefault {
    public InteractionFurniChest(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionFurniChest(int id, int userId, Item item, String extradata,
            int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void serializeExtradata(ServerMessage message) {
        Map<String, String> data = Emulator.getGameEnvironment()
                .getChestManager().furnitureData(this);
        message.appendInt(1 + (this.isLimited() ? 256 : 0));
        message.appendInt(data.size());
        for (Map.Entry<String, String> entry : data.entrySet()) {
            message.appendString(entry.getKey());
            message.appendString(entry.getValue());
        }
        if (this.isLimited()) {
            message.appendInt(this.getLimitedSells());
            message.appendInt(this.getLimitedStack());
        }
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) {
        if (objects != null && objects.length >= 2
                && objects[1] instanceof WiredEffectType
                && objects[1] == WiredEffectType.TOGGLE_STATE) {
            Emulator.getGameEnvironment().getChestManager().toggleStateFromWired(room, this);
            return;
        }
        Emulator.getGameEnvironment().getChestManager().open(client, room, this);
    }
}

package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredMovementAddonRuntime;
import com.eu.habbo.messages.outgoing.rooms.items.WiredMovementsMessageComposer;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredEffectSetFurniAltitude extends WiredEffectConfigBase {
    public static final WiredEffectType type = WiredEffectType.SET_FURNI_ALTITUDE;
    private static final int OPERATOR_INCREASE = 0;
    private static final int OPERATOR_DECREASE = 1;
    private static final int OPERATOR_SET = 2;
    private static final double MIN_ALTITUDE = 0.0;
    private static final double MAX_ALTITUDE = 80.0;

    public WiredEffectSetFurniAltitude(ResultSet set, Item baseItem) throws SQLException { super(set, baseItem); }
    public WiredEffectSetFurniAltitude(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) { super(id, userId, item, extradata, limitedStack, limitedSells); }

    @Override public WiredEffectType getType() { return type; }
    @Override protected boolean supportsFurniPickingWhenEmpty() { return true; }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        int amount = this.intParams.length > 0 ? this.intParams[0] : 0;
        int operator = this.intParams.length > 1 ? this.intParams[1] : 0;
        for (HabboItem item : WiredMovementAddonRuntime.furniTargets(ctx, sourceItems(ctx))) {
            RoomTile tile = room.getLayout().getTile(item.getX(), item.getY());
            if (tile == null) continue;

            double oldZ = item.getZ();
            double amountAsHeight = clamp(amount / 100.0, MIN_ALTITUDE, MAX_ALTITUDE);
            double floorHeight = Math.max(MIN_ALTITUDE, tile.getZ());
            double target;

            // July's UI writes [altitudeInHundredths, operator]:
            // 0 = Increase, 1 = Decrease, 2 = Set value.
            switch (operator) {
                case OPERATOR_INCREASE:
                    target = oldZ + amountAsHeight;
                    break;
                case OPERATOR_DECREASE:
                    target = oldZ - amountAsHeight;
                    break;
                case OPERATOR_SET:
                    target = amountAsHeight;
                    break;
                default:
                    // A malformed stored value must not turn into an unexpected decrease.
                    target = oldZ;
                    break;
            }

            // Furniture altitude is absolute; it may never sit below its room tile's floor.
            target = clamp(target, floorHeight, Math.max(MAX_ALTITUDE, floorHeight));
            if (Double.compare(oldZ, target) == 0) continue;

            item.setZ(target);
            // Wired 2.0: stream a smooth WiredMovements altitude slide (same tile, fromZ -> toZ) instead of the legacy roller hop.
            WiredMovementAddonRuntime.moved(ctx, room, item, tile, oldZ, tile);

            // Movement Physics may deliberately retain the previous altitude. Only persist/update
            // occupancy when the runtime mutation actually changed the item's final height.
            if (Double.compare(oldZ, item.getZ()) != 0) {
                item.needsUpdate(true);
                Emulator.getThreading().run(item);
                room.updateTiles(room.getLayout().getTilesAt(tile, item.getBaseItem().getWidth(),
                        item.getBaseItem().getLength(), item.getRotation()));
            }
        }
    }

    private static double clamp(double value, double minimum, double maximum) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return minimum;
        return Math.max(minimum, Math.min(maximum, value));
    }
}

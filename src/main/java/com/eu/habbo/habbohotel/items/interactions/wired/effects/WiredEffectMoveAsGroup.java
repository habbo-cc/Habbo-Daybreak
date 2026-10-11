package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.FurnitureMovementError;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredMovementAddonRuntime;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public class WiredEffectMoveAsGroup extends WiredEffectConfigBase {
    public static final WiredEffectType type = WiredEffectType.MOVE_AS_GROUP;

    public WiredEffectMoveAsGroup(ResultSet set, Item baseItem) throws SQLException { super(set, baseItem); }
    public WiredEffectMoveAsGroup(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) { super(id, userId, item, extradata, limitedStack, limitedSells); }

    @Override public WiredEffectType getType() { return type; }
    @Override protected boolean supportsFurniPickingWhenEmpty() { return true; }
    @Override protected int getFurniSourceSlotCount() { return 2; }
    @Override protected int getUserSourceSlotCount() { return 1; }

    @Override
    protected int[] getAllowedFurniSourcesForSlot(int slot) {
        if (slot == 0) {
            return new int[] {
                    FURNI_SOURCE_PICKED_1,
                    FURNI_SOURCE_TRIGGERING_ITEM,
                    FURNI_SOURCE_SELECTOR,
                    FURNI_SOURCE_SIGNAL
            };
        }
        return new int[] {
                FURNI_SOURCE_TRIGGERING_ITEM,
                FURNI_SOURCE_PICKED_2,
                FURNI_SOURCE_SELECTOR,
                FURNI_SOURCE_SIGNAL
        };
    }

    @Override
    protected int[] getAllowedUserSourcesForSlot(int slot) {
        return new int[] {
                USER_SOURCE_TRIGGERING_USER,
                USER_SOURCE_SELECTOR,
                USER_SOURCE_SIGNAL
        };
    }

    @Override
    protected int getDefaultFurniSourceForSlot(int slot) {
        return slot == 0 ? FURNI_SOURCE_PICKED_1 : FURNI_SOURCE_TRIGGERING_ITEM;
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        List<HabboItem> movingItems = new ArrayList<>(
                WiredMovementAddonRuntime.furniTargets(ctx, sourceItems(ctx, 0)));
        if (movingItems.isEmpty()) {
            return;
        }

        RoomTile target = resolveTarget(ctx);
        if (target == null) {
            return;
        }

        int dx = this.intParams.length > 1 ? this.intParams[1] : 0;
        int dy = this.intParams.length > 2 ? this.intParams[2] : 0;
        RoomTile offsetTarget = room.getLayout().getTile(
                (short) (target.x + dx),
                (short) (target.y + dy));
        if (offsetTarget == null) {
            return;
        }

        HabboItem anchor = movingItems.stream()
                .min(Comparator.comparingInt(HabboItem::getY)
                        .thenComparingInt(HabboItem::getX))
                .orElse(null);
        if (anchor == null) {
            return;
        }

        List<GroupMove> moves = new ArrayList<>();
        for (HabboItem item : movingItems) {
            RoomTile itemTarget = room.getLayout().getTile(
                    (short) (offsetTarget.x + item.getX() - anchor.getX()),
                    (short) (offsetTarget.y + item.getY() - anchor.getY()));
            if (itemTarget == null) {
                return;
            }
            moves.add(new GroupMove(item, itemTarget));
        }

        for (GroupMove move : moves) {
            RoomTile from = room.getLayout().getTile(move.item.getX(), move.item.getY());
            double fromZ = move.item.getZ();
            if (from != null && WiredMovementAddonRuntime.move(
                    ctx, room, move.item, move.target, move.item.getRotation(), false, false)
                    == FurnitureMovementError.NONE) {
                WiredMovementAddonRuntime.moved(ctx, room, move.item, from, fromZ, move.target);
            }
        }
    }

    private RoomTile resolveTarget(WiredContext ctx) {
        boolean useUserSource = this.intParams.length == 0 || this.intParams[0] == 1;
        if (useUserSource) {
            RoomUnit user = sourceUsers(ctx).stream().findFirst().orElse(null);
            return user != null ? user.getCurrentLocation() : null;
        }

        Collection<HabboItem> targets = sourceItems(ctx, 1);
        HabboItem target = targets.stream().findFirst().orElse(null);
        if (target == null) {
            return null;
        }
        return ctx.room().getLayout().getTile(target.getX(), target.getY());
    }

    private static final class GroupMove {
        private final HabboItem item;
        private final RoomTile target;

        private GroupMove(HabboItem item, RoomTile target) {
            this.item = item;
            this.target = target;
        }
    }
}

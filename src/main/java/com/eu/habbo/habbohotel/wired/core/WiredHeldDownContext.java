package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;

/** July context values captured from one completed AIR pointer press/release. */
public record WiredHeldDownContext(
        int durationTicks,
        int originType,
        int originId,
        int originX,
        int originY,
        boolean originValid,
        int releaseType,
        int releaseId,
        int releaseX,
        int releaseY) {

    public static final int TYPE_EMPTY = 0;
    public static final int TYPE_FURNI = 1;
    public static final int TYPE_TILE = 2;
    public static final int TYPE_USER = 3;
    public static final int MAX_DURATION_TICKS = 1_728_000;

    public WiredHeldDownContext {
        durationTicks = Math.max(0, Math.min(MAX_DURATION_TICKS, durationTicks));
        originType = normalizeType(originType);
        releaseType = normalizeType(releaseType);
        originId = Math.max(0, originId);
        releaseId = Math.max(0, releaseId);
    }

    public static WiredHeldDownContext furni(HabboItem item, int durationTicks) {
        if (item == null) {
            return null;
        }
        return same(durationTicks, TYPE_FURNI, item.getId(), item.getX(), item.getY());
    }

    public static WiredHeldDownContext user(RoomUnit unit, int durationTicks) {
        if (unit == null || unit.getCurrentLocation() == null) {
            return null;
        }
        return same(durationTicks, TYPE_USER, unit.getId(),
                unit.getCurrentLocation().x, unit.getCurrentLocation().y);
    }

    private static WiredHeldDownContext same(
            int durationTicks, int type, int id, int x, int y) {
        return new WiredHeldDownContext(durationTicks, type, id, x, y, true,
                type, id, x, y);
    }

    private static int normalizeType(int type) {
        return type >= TYPE_FURNI && type <= TYPE_USER ? type : TYPE_EMPTY;
    }

    public void seed(WiredContextVariableStore values) {
        if (values == null) {
            return;
        }
        values.set("@held_down", 1);
        values.set("@held_down.total_duration_ticks", durationTicks);
        values.set("@held_down.origin_type", originType);
        values.set("@held_down.origin_id", originId);
        values.set("@held_down.origin_x", originX);
        values.set("@held_down.origin_y", originY);
        values.set("@held_down.origin_valid", originValid ? 1 : 0);
        values.set("@held_down.release_type", releaseType);
        values.set("@held_down.release_id", releaseId);
        values.set("@held_down.release_x", releaseX);
        values.set("@held_down.release_y", releaseY);
    }
}

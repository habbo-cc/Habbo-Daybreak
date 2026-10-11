package com.eu.habbo.habbohotel.wired;

import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.users.DanceType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;

/**
 * Official May 2026 Wired user action codes used by trigger 16 and the
 * matching action condition/selector family.
 */
public final class WiredUserAction {
    public static final int WAVE = 0;
    public static final int BLOW = 1;
    public static final int LAUGH = 2;
    public static final int RESPECT = 3;
    public static final int AWAKE = 4;
    public static final int SLEEP = 5;
    public static final int SIT = 6;
    public static final int STAND = 7;
    public static final int LAY = 8;
    public static final int SIGN = 10;
    public static final int DANCE = 11;
    public static final int SIXTY_SEVEN = 67;

    private WiredUserAction() {
    }

    public static int fromAvatarExpression(int action) {
        switch (action) {
            case 0:
                return AWAKE;
            case 1:
                return WAVE;
            case 2:
                return BLOW;
            case 3:
                return LAUGH;
            case 5:
                return SLEEP;
            case 7:
                return RESPECT;
            case 67:
                return SIXTY_SEVEN;
            default:
                return action;
        }
    }

    public static String signExtra(int signId) {
        return String.valueOf(signId);
    }

    public static String danceExtra(int danceId) {
        return "dance " + danceId;
    }

    /**
     * Matches the action event currently being handled, or a room-unit state that
     * remains observable after the event (idle, posture, sign and dance).
     */
    public static boolean matches(RoomUnit unit, WiredContext context, int actionCode, String requiredExtra) {
        if (unit == null || context == null) {
            return false;
        }

        String normalizedExtra = requiredExtra == null ? "" : requiredExtra;
        if (context.eventType() == WiredEvent.Type.USER_PERFORMS_ACTION
                && context.actor().map(actor -> actor.getId() == unit.getId()).orElse(false)
                && context.event().getScore() == actionCode
                && extraMatches(normalizedExtra, context.text().orElse(""))) {
            return true;
        }

        switch (actionCode) {
            case WAVE:
                return unit.hasStatus(RoomUnitStatus.WAVE);
            case AWAKE:
                return !unit.isIdle();
            case SLEEP:
                return unit.isIdle();
            case SIT:
                return unit.hasStatus(RoomUnitStatus.SIT) || unit.hasStatus(RoomUnitStatus.SIT_IN);
            case STAND:
                return !unit.hasStatus(RoomUnitStatus.SIT)
                        && !unit.hasStatus(RoomUnitStatus.SIT_IN)
                        && !unit.hasStatus(RoomUnitStatus.LAY)
                        && !unit.hasStatus(RoomUnitStatus.LAY_IN);
            case LAY:
                return unit.hasStatus(RoomUnitStatus.LAY) || unit.hasStatus(RoomUnitStatus.LAY_IN);
            case SIGN:
                return unit.hasStatus(RoomUnitStatus.SIGN)
                        && extraMatches(normalizedExtra, unit.getStatus(RoomUnitStatus.SIGN));
            case DANCE:
                DanceType dance = unit.getDanceType();
                return dance != null
                        && dance != DanceType.NONE
                        && extraMatches(normalizedExtra, danceExtra(dance.getType()));
            case BLOW:
            case LAUGH:
            case RESPECT:
            case SIXTY_SEVEN:
            default:
                return false;
        }
    }

    private static boolean extraMatches(String requiredExtra, String actualExtra) {
        return requiredExtra.isEmpty() || requiredExtra.equals(actualExtra == null ? "" : actualExtra);
    }
}

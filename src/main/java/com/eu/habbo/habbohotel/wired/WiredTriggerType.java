package com.eu.habbo.habbohotel.wired;

public enum WiredTriggerType {
    SAY_SOMETHING(0),
    WALKS_ON_FURNI(1),
    WALKS_OFF_FURNI(2),
    AT_GIVEN_TIME(3),
    STATE_CHANGED(4),
    PERIODICALLY(6),
    ENTER_ROOM(7),
    GAME_STARTS(8),
    GAME_ENDS(9),
    SCORE_ACHIEVED(10),
    COLLISION(11),
    PERIODICALLY_LONG(12),
    BOT_REACHED_STF(13),
    BOT_REACHED_AVTR(14),
    // May/July Wired 2.0 trigger codes.
    CLOCK_REACH_TIME(15),
    USER_PERFORMS_ACTION(16),
    RECEIVE_SIGNAL(17),
    CLICK_FURNI(18),
    PERIOD_SHORT(19),
    STUFF_STATE(20),
    /** July AIR wf_trg_click_tile. */
    CLICK_TILE(21),
    /** July AIR Core Variables trigger. */
    VARIABLE_CHANGED(22),
    LEAVE_ROOM(23),
    CLICK_USER(24),
    TRANSACTION_COMPLETED(25),
    TRANSACTION_FAILED(26),
    SAY_COMMAND(0),
    IDLES(11),
    UNIDLES(11),
    CUSTOM(13),
    STARTS_DANCING(11),
    STOPS_DANCING(11);

    public final int code;

    WiredTriggerType(int code) {
        this.code = code;
    }
}

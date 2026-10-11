package com.eu.habbo.habbohotel.games.snowstorm;

/** Frozen original-Shockwave SnowStorm v1 operation codes and collection limits. */
public final class SnowStormProtocol {
    public static final int VERSION = 4;
    public static final int MAX_LIVE_INSTANCES = 100;
    public static final int MAX_FINISHED_ROWS = 15;
    public static final int MAX_INSTANCE_ROWS = MAX_LIVE_INSTANCES + MAX_FINISHED_ROWS;
    public static final int MAX_OBJECTS = 337;
    public static final int MAX_EVENTS_PER_SUBTURN = 128;
    public static final int MAX_EVENTS_PER_TURN = 512;

    public static final int OP_GET_LOUNGE = 0;
    public static final int OP_CREATE = 1;
    public static final int OP_DELETE = 2;
    public static final int OP_INITIATE_JOIN = 3;
    public static final int OP_JOIN = 4;
    public static final int OP_LEAVE = 5;
    public static final int OP_KICK = 6;
    public static final int OP_WATCH = 7;
    public static final int OP_START = 8;
    public static final int OP_GAME_EVENT = 9;
    public static final int OP_FULL_STATUS = 10;
    public static final int OP_FINISHED_RESULT = 11;
    public static final int OP_ADD_BOT = 12;
    public static final int OP_REMOVE_BOT = 13;

    private SnowStormProtocol() {}
}

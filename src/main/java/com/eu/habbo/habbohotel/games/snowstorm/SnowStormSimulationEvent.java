package com.eu.habbo.habbohotel.games.snowstorm;

/**
 * Typed events emitted by the authoritative SnowStorm simulation.
 *
 * <p>The protocol IDs are stable wire values, not enum ordinals. Packet encoding and consumers
 * use the named record fields, so changing an event shape is a compile-time operation.
 */
public sealed interface SnowStormSimulationEvent permits
        SnowStormSimulationEvent.RemoveObject,
        SnowStormSimulationEvent.AvatarMove,
        SnowStormSimulationEvent.AvatarThrow,
        SnowStormSimulationEvent.LaunchSnowball,
        SnowStormSimulationEvent.Hit,
        SnowStormSimulationEvent.Stun,
        SnowStormSimulationEvent.ActivityState,
        SnowStormSimulationEvent.SnowballCount,
        SnowStormSimulationEvent.MachineRestock,
        SnowStormSimulationEvent.MachineTransfer,
        SnowStormSimulationEvent.PlayerScore,
        SnowStormSimulationEvent.SnowballMiss {

    Type type();

    record RemoveObject(int objectId) implements SnowStormSimulationEvent {
        public RemoveObject { requirePositive(objectId, "objectId"); }
        @Override public Type type() { return Type.REMOVE_OBJECT; }
    }

    record AvatarMove(int objectId, int worldX, int worldY, int targetWorldX, int targetWorldY,
                      int goalTileX, int goalTileY, int direction) implements SnowStormSimulationEvent {
        public AvatarMove {
            requirePositive(objectId, "objectId");
            requireUnsignedByte(direction, "direction");
        }
        @Override public Type type() { return Type.AVATAR_MOVE; }
    }

    record AvatarThrow(int objectId, int trajectory, int targetWorldX, int targetWorldY)
            implements SnowStormSimulationEvent {
        public AvatarThrow {
            requirePositive(objectId, "objectId");
            requireUnsignedByte(trajectory, "trajectory");
        }
        @Override public Type type() { return Type.AVATAR_THROW; }
    }

    record LaunchSnowball(int objectId, int throwerObjectId, int worldX, int worldY, int worldZ,
                          int direction, int trajectory, int ttl, int parabolaOffset)
            implements SnowStormSimulationEvent {
        public LaunchSnowball {
            requirePositive(objectId, "objectId");
            requirePositive(throwerObjectId, "throwerObjectId");
            requireUnsignedShort(direction, "direction");
            requireUnsignedByte(trajectory, "trajectory");
            requireUnsignedShort(ttl, "ttl");
            requireUnsignedShort(parabolaOffset, "parabolaOffset");
        }
        @Override public Type type() { return Type.LAUNCH_SNOWBALL; }
    }

    record Hit(int throwerObjectId, int targetObjectId, int impactWorldX, int impactWorldY,
               int impactWorldZ, int direction, int health) implements SnowStormSimulationEvent {
        public Hit {
            requirePositive(throwerObjectId, "throwerObjectId");
            requirePositive(targetObjectId, "targetObjectId");
            requireUnsignedByte(direction, "direction");
            requireUnsignedByte(health, "health");
        }
        @Override public Type type() { return Type.HIT; }
    }

    record Stun(int throwerObjectId, int targetObjectId, int direction, int fallPose)
            implements SnowStormSimulationEvent {
        public Stun {
            requirePositive(throwerObjectId, "throwerObjectId");
            requirePositive(targetObjectId, "targetObjectId");
            requireUnsignedByte(direction, "direction");
            requireUnsignedByte(fallPose, "fallPose");
        }
        @Override public Type type() { return Type.STUN; }
    }

    record ActivityState(int objectId, int activity, int timer, int health)
            implements SnowStormSimulationEvent {
        public ActivityState {
            requirePositive(objectId, "objectId");
            requireUnsignedByte(activity, "activity");
            requireUnsignedShort(timer, "timer");
            requireUnsignedByte(health, "health");
        }
        @Override public Type type() { return Type.ACTIVITY_STATE; }
    }

    record SnowballCount(int objectId, int count) implements SnowStormSimulationEvent {
        public SnowballCount {
            requirePositive(objectId, "objectId");
            requireUnsignedByte(count, "count");
        }
        @Override public Type type() { return Type.SNOWBALL_COUNT; }
    }

    record MachineRestock(int machineObjectId, int count) implements SnowStormSimulationEvent {
        public MachineRestock {
            requirePositive(machineObjectId, "machineObjectId");
            requireUnsignedByte(count, "count");
        }
        @Override public Type type() { return Type.MACHINE_RESTOCK; }
    }

    record MachineTransfer(int machineObjectId, int playerObjectId, int machineCount, int playerCount)
            implements SnowStormSimulationEvent {
        public MachineTransfer {
            requirePositive(machineObjectId, "machineObjectId");
            requirePositive(playerObjectId, "playerObjectId");
            requireUnsignedByte(machineCount, "machineCount");
            requireUnsignedByte(playerCount, "playerCount");
        }
        @Override public Type type() { return Type.MACHINE_TRANSFER; }
    }

    record PlayerScore(int objectId, int score) implements SnowStormSimulationEvent {
        public PlayerScore { requirePositive(objectId, "objectId"); }
        @Override public Type type() { return Type.PLAYER_SCORE; }
    }

    record SnowballMiss(int objectId) implements SnowStormSimulationEvent {
        public SnowballMiss { requirePositive(objectId, "objectId"); }
        @Override public Type type() { return Type.SNOWBALL_MISS; }
    }

    enum Type {
        REMOVE_OBJECT(1),
        AVATAR_MOVE(2),
        AVATAR_THROW(3),
        LAUNCH_SNOWBALL(4),
        HIT(5),
        STUN(6),
        ACTIVITY_STATE(7),
        SNOWBALL_COUNT(8),
        MACHINE_RESTOCK(9),
        MACHINE_TRANSFER(10),
        PLAYER_SCORE(11),
        SNOWBALL_MISS(12);

        private final int id;

        Type(int id) { this.id = id; }
        public int id() { return id; }
    }

    private static void requirePositive(int value, String field) {
        if (value <= 0) throw new IllegalArgumentException(field + " must be positive");
    }

    private static void requireUnsignedByte(int value, String field) {
        if (value < 0 || value > 0xFF) throw new IllegalArgumentException(field + " must fit a byte");
    }

    private static void requireUnsignedShort(int value, String field) {
        if (value < 0 || value > 0xFFFF) throw new IllegalArgumentException(field + " must fit a short");
    }
}

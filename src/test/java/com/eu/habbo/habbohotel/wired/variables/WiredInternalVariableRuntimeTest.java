package com.eu.habbo.habbohotel.wired.variables;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUserRotation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WiredInternalVariableRuntimeTest {
    @Test
    void definitionsAreImmutableTargetScopedAndExposeWritePolicy() {
        assertTrue(WiredInternalVariableRuntime.matches(
                "internal:1:@position.x", WiredInternalVariableRuntime.TARGET_USER));
        assertFalse(WiredInternalVariableRuntime.matches(
                "internal:1:@position.x", WiredInternalVariableRuntime.TARGET_FURNI));
        assertTrue(WiredInternalVariableRuntime.definition(
                "internal:1:@position.x", WiredInternalVariableRuntime.TARGET_USER).writable());
        assertFalse(WiredInternalVariableRuntime.definition(
                "internal:1:@user_id", WiredInternalVariableRuntime.TARGET_USER).writable());
        assertTrue(WiredInternalVariableRuntime.definition(
                "internal:0:@projectile.animation.tiles_travelled",
                WiredInternalVariableRuntime.TARGET_FURNI).runtimeObservable());
        assertTrue(WiredInternalVariableRuntime.definition(
                "internal:0:@projectile.animation.is_travelling",
                WiredInternalVariableRuntime.TARGET_FURNI).runtimeObservable());
        assertFalse(WiredInternalVariableRuntime.definition(
                "internal:0:@projectile.animation.position.x",
                WiredInternalVariableRuntime.TARGET_FURNI).runtimeObservable());
        assertNull(WiredInternalVariableRuntime.definition(
                "@missing", WiredInternalVariableRuntime.TARGET_USER));
        assertThrows(UnsupportedOperationException.class,
                () -> WiredInternalVariableRuntime.definitions().clear());
    }

    @Test
    void inspectionUsesOpaqueIdsAndDropsUnknownProviderValues() {
        Map<String, Integer> providerValues = new LinkedHashMap<>();
        providerValues.put("@position.x", 7);
        providerValues.put("@user_id", 42);
        providerValues.put("@not_official", 99);

        Map<String, Integer> result = WiredInternalVariableRuntime.inspectionValues(
                WiredInternalVariableRuntime.TARGET_USER, providerValues);

        assertEquals(7, result.get("internal:1:@position.x"));
        assertEquals(42, result.get("internal:1:@user_id"));
        assertFalse(result.containsKey("@not_official"));
        assertThrows(UnsupportedOperationException.class,
                () -> result.put("internal:1:@position.y", 3));
    }

    @Test
    void directionAltitudeHandItemAndEffectWritesEnforceBounds() {
        Room room = mock(Room.class);
        RoomUnit unit = activeUnit(room, new RoomTile(
                (short) 1, (short) 1, (short) 0, RoomTileState.OPEN, true));

        assertTrue(WiredInternalVariableRuntime.writeUnit(
                room, unit, "@direction", 7));
        assertEquals(RoomUserRotation.NORTH_WEST, unit.getBodyRotation());
        assertFalse(WiredInternalVariableRuntime.writeUnit(
                room, unit, "@direction", 8));

        assertTrue(WiredInternalVariableRuntime.writeUnit(
                room, unit, "@altitude", 175));
        assertEquals(1.75D, unit.getZ());

        assertTrue(WiredInternalVariableRuntime.writeUnit(
                room, unit, "@handitem", 10000));
        verify(room).giveHandItem(unit, 10000);
        assertFalse(WiredInternalVariableRuntime.writeUnit(
                room, unit, "@handitem", 10001));
        verify(room, never()).giveHandItem(unit, 10001);

        assertTrue(WiredInternalVariableRuntime.writeUnit(
                room, unit, "@effect", 100000));
        verify(room).giveEffect(unit, 100000, Integer.MAX_VALUE);
        assertFalse(WiredInternalVariableRuntime.writeUnit(
                room, unit, "@effect", 100001));
        verify(room, never()).giveEffect(unit, 100001, Integer.MAX_VALUE);
    }

    @Test
    void positionWriteMovesToValidatedTileAndRefreshesBothLocations() {
        Room room = mock(Room.class);
        RoomLayout layout = mock(RoomLayout.class);
        RoomTile source = new RoomTile(
                (short) 1, (short) 4, (short) 0, RoomTileState.OPEN, true);
        RoomTile destination = new RoomTile(
                (short) 2, (short) 4, (short) 0, RoomTileState.OPEN, true);
        destination.setStackHeight(2.5D);
        when(room.getLayout()).thenReturn(layout);
        when(layout.getTile((short) 2, (short) 4)).thenReturn(destination);
        RoomUnit unit = activeUnit(room, source);

        assertTrue(WiredInternalVariableRuntime.writeUnit(
                room, unit, "@position.x", 2));

        assertEquals(destination, unit.getCurrentLocation());
        assertEquals(source, unit.getPreviousLocation());
        assertEquals(2.5D, unit.getZ());
        verify(room).updateHabbosAt((short) 1, (short) 4);
        verify(room).updateBotsAt((short) 1, (short) 4);
        verify(room).updateHabbosAt((short) 2, (short) 4);
        verify(room).updateBotsAt((short) 2, (short) 4);
    }

    @Test
    void invalidPositionAndDetachedUnitsFailClosedWithoutRoomMutation() {
        Room room = mock(Room.class);
        RoomLayout layout = mock(RoomLayout.class);
        when(room.getLayout()).thenReturn(layout);
        RoomUnit unit = activeUnit(room, new RoomTile(
                (short) 1, (short) 1, (short) 0, RoomTileState.OPEN, true));

        assertFalse(WiredInternalVariableRuntime.writeUnit(
                room, unit, "@position.x", -1));
        assertFalse(WiredInternalVariableRuntime.writeUnit(
                room, unit, "@position.y", Short.MAX_VALUE));
        assertFalse(WiredInternalVariableRuntime.writeUnit(
                room, unit, "@unknown", 1));
        unit.setInRoom(false);
        assertFalse(WiredInternalVariableRuntime.writeUnit(
                room, unit, "@direction", 0));
        verify(room, never()).updateHabbosAt((short) 1, (short) 1);
        verify(room, never()).updateBotsAt((short) 1, (short) 1);
    }

    private static RoomUnit activeUnit(Room room, RoomTile location) {
        RoomUnit unit = new RoomUnit();
        unit.setRoom(room);
        unit.setLocation(location);
        unit.setZ(location.getStackHeight());
        unit.setInRoom(true);
        return unit;
    }
}

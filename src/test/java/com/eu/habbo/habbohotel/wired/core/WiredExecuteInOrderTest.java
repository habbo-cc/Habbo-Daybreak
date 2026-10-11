package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredSelector;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.WiredSelectorType;
import com.eu.habbo.habbohotel.wired.api.IWiredEffect;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import gnu.trove.set.hash.THashSet;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WiredExecuteInOrderTest {
    @Test
    void physicalOrderEmulationIsStableByZThenDatabaseItemId() {
        DummyEffect high = effect(30, 2.0);
        DummyEffect sameZLaterId = effect(20, 1.0);
        DummyEffect sameZEarlierId = effect(10, 1.0);
        THashSet<InteractionWiredEffect> unordered = new THashSet<>();
        unordered.add(high);
        unordered.add(sameZLaterId);
        unordered.add(sameZEarlierId);

        assertEquals(List.of(sameZEarlierId, sameZLaterId, high),
                RoomWiredStackIndex.collectEffects(unordered));
    }

    @Test
    void selectorDependencyOrderIsStableByZThenDatabaseItemId() {
        DummySelector high = selector(30, 2.0);
        DummySelector sameZLaterId = selector(20, 1.0);
        DummySelector sameZEarlierId = selector(10, 1.0);
        THashSet<InteractionWiredSelector> unordered = new THashSet<>();
        unordered.add(high);
        unordered.add(sameZLaterId);
        unordered.add(sameZEarlierId);

        assertEquals(List.of(sameZEarlierId, sameZLaterId, high),
                RoomWiredStackIndex.collectSelectors(unordered));
    }

    @Test
    void code17PreservesResolvedOrderWhileOrdinaryStacksStillShuffle() {
        IWiredEffect first = ctx -> { };
        IWiredEffect second = ctx -> { };
        IWiredEffect third = ctx -> { };
        List<IWiredEffect> resolved = List.of(first, second, third);

        assertEquals(resolved, WiredEngine.resolveExecutionOrder(resolved, true, new Random(1)));

        List<IWiredEffect> ordinary = WiredEngine.resolveExecutionOrder(resolved, false, new ZeroRandom());
        assertNotEquals(resolved, ordinary);
        assertEquals(List.of(second, third, first), ordinary);
        assertEquals(List.of(first, second, third), resolved); // input/cached stack is never mutated
    }

    @Test
    void delayedSnapshotRejectsSourceOnlyEditsAndFurnitureMoves() {
        DummyEffect effect = effect(40, 1.0);
        WiredEngine.DelayedEffectSnapshot original = WiredEngine.DelayedEffectSnapshot.capture(effect);
        assertTrue(original.configurationMatches());

        effect.setWiredSourceTypes(new int[] {200}, new int[0]);
        assertFalse(original.configurationMatches());

        WiredEngine.DelayedEffectSnapshot afterSourceEdit =
                WiredEngine.DelayedEffectSnapshot.capture(effect);
        assertTrue(afterSourceEdit.configurationMatches());
        effect.setX((short) 7);
        assertFalse(afterSourceEdit.configurationMatches());
    }

    @Test
    void delayedActorValidationDropsStaleOptionalActorsAndCancelsRequiredOnes() {
        RoomUnit staleActor = new RoomUnit();

        WiredEngine.DelayedActorValidation optional =
                WiredEngine.validateDelayedActor(staleActor, null, false);
        assertTrue(optional.valid());
        assertNull(optional.actor());

        WiredEngine.DelayedActorValidation required =
                WiredEngine.validateDelayedActor(staleActor, null, true);
        assertFalse(required.valid());
        assertNull(required.actor());

        staleActor.setInRoom(true);
        WiredEngine.DelayedActorValidation current =
                WiredEngine.validateDelayedActor(staleActor, null, true);
        assertTrue(current.valid());
        assertEquals(staleActor, current.actor());
    }

    private static DummyEffect effect(int id, double z) {
        DummyEffect effect = new DummyEffect(id);
        effect.setZ(z);
        return effect;
    }

    private static DummySelector selector(int id, double z) {
        DummySelector selector = new DummySelector(id);
        selector.setZ(z);
        return selector;
    }

    private static final class ZeroRandom extends Random {
        @Override
        public int nextInt(int bound) {
            return 0;
        }
    }

    private static final class DummyEffect extends InteractionWiredEffect {
        private DummyEffect(int id) {
            super(id, 1, null, "0", 0, 0);
        }

        @Override public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException { return false; }
        @Override public WiredEffectType getType() { return WiredEffectType.TOGGLE_STATE; }
        @Override public void execute(WiredContext ctx) { }
        @Override public String getWiredData() { return ""; }
        @Override public void serializeWiredData(ServerMessage message, Room room) { }
        @Override public void loadWiredData(ResultSet set, Room room) throws SQLException { }
        @Override public void onPickUp() { }
        @Override protected boolean supportsFurniPicking() { return true; }
    }

    private static final class DummySelector extends InteractionWiredSelector {
        private DummySelector(int id) {
            super(id, 1, null, "0", 0, 0);
        }

        @Override public WiredSelectorType getType() { return WiredSelectorType.REMOTE_SELECTOR; }
        @Override public boolean saveData(WiredSettingsV2 settings) { return false; }
        @Override public WiredTargets resolve(Room room, WiredContext ctx) { return new WiredTargets(); }
        @Override public String getWiredData() { return ""; }
        @Override public void loadWiredData(ResultSet set, Room room) throws SQLException { }
        @Override public void onPickUp() { }
    }
}

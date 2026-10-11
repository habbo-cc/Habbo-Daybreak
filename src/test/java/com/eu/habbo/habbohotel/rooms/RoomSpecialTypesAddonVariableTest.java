package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredAddon;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.wired.WiredAddonType;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class RoomSpecialTypesAddonVariableTest {
    @Test
    void addonAndVariableIndexesFollowAddMoveRemoveLifecycle() {
        RoomSpecialTypes types = new RoomSpecialTypes();
        DummyAddon addon = new DummyAddon(11);
        DummyVariable variable = new DummyVariable(12);
        addon.setX((short) 2);
        addon.setY((short) 3);
        variable.setX((short) 2);
        variable.setY((short) 3);

        types.addAddon(addon);
        types.addVariable(variable);
        assertSame(addon, types.getAddon(11));
        assertSame(variable, types.getVariable(12));
        assertEquals(1, types.getAddons(WiredAddonType.EXECUTION_LIMIT).size());
        assertEquals(1, types.getVariables(WiredVariableType.USER).size());
        assertEquals(1, types.getAddons(2, 3).size());
        assertEquals(1, types.getVariables(2, 3).size());

        addon.setX((short) 4);
        addon.setY((short) 5);
        variable.setX((short) 4);
        variable.setY((short) 5);
        types.updateAddonLocation(addon, 2, 3);
        types.updateVariableLocation(variable, 2, 3);
        assertEquals(0, types.getAddons(2, 3).size());
        assertEquals(0, types.getVariables(2, 3).size());
        assertEquals(1, types.getAddons(4, 5).size());
        assertEquals(1, types.getVariables(4, 5).size());

        types.removeAddon(addon);
        types.removeVariable(variable);
        assertNull(types.getAddon(11));
        assertNull(types.getVariable(12));
        assertEquals(0, types.getAddons().size());
        assertEquals(0, types.getVariables().size());
        assertEquals(0, types.getAddons(4, 5).size());
        assertEquals(0, types.getVariables(4, 5).size());
    }

    @Test
    void sameIdReplacementRemovesTheOldInstanceFromEveryIndex() {
        RoomSpecialTypes types = new RoomSpecialTypes();
        DummyAddon oldAddon = new DummyAddon(21);
        DummyAddon newAddon = new DummyAddon(21);
        DummyVariable oldVariable = new DummyVariable(22);
        DummyVariable newVariable = new DummyVariable(22);
        oldAddon.setX((short) 1);
        oldAddon.setY((short) 2);
        newAddon.setX((short) 5);
        newAddon.setY((short) 6);
        oldVariable.setX((short) 1);
        oldVariable.setY((short) 2);
        newVariable.setX((short) 5);
        newVariable.setY((short) 6);

        types.addAddon(oldAddon);
        types.addVariable(oldVariable);
        types.addAddon(newAddon);
        types.addVariable(newVariable);

        assertSame(newAddon, types.getAddon(21));
        assertSame(newVariable, types.getVariable(22));
        assertEquals(0, types.getAddons(1, 2).size());
        assertEquals(0, types.getVariables(1, 2).size());
        assertEquals(1, types.getAddons(5, 6).size());
        assertEquals(1, types.getVariables(5, 6).size());
        assertEquals(1, types.getAddons(WiredAddonType.EXECUTION_LIMIT).size());
        assertEquals(1, types.getVariables(WiredVariableType.USER).size());

        // Removing a stale handle must not unregister the replacement.
        types.removeAddon(oldAddon);
        types.removeVariable(oldVariable);
        assertSame(newAddon, types.getAddon(21));
        assertSame(newVariable, types.getVariable(22));
        assertEquals(1, types.getAddons(5, 6).size());
        assertEquals(1, types.getVariables(5, 6).size());
    }

    @Test
    void staleMoveCallbacksCannotResurrectReplacedInstances() {
        RoomSpecialTypes types = new RoomSpecialTypes();
        DummyAddon oldAddon = new DummyAddon(31);
        DummyAddon newAddon = new DummyAddon(31);
        DummyVariable oldVariable = new DummyVariable(32);
        DummyVariable newVariable = new DummyVariable(32);
        oldAddon.setX((short) 1);
        oldAddon.setY((short) 1);
        oldVariable.setX((short) 1);
        oldVariable.setY((short) 1);
        newAddon.setX((short) 2);
        newAddon.setY((short) 2);
        newVariable.setX((short) 2);
        newVariable.setY((short) 2);

        types.addAddon(oldAddon);
        types.addVariable(oldVariable);
        types.addAddon(newAddon);
        types.addVariable(newVariable);

        oldAddon.setX((short) 9);
        oldAddon.setY((short) 9);
        oldVariable.setX((short) 9);
        oldVariable.setY((short) 9);
        types.updateAddonLocation(oldAddon, 1, 1);
        types.updateVariableLocation(oldVariable, 1, 1);

        assertSame(newAddon, types.getAddon(31));
        assertSame(newVariable, types.getVariable(32));
        assertEquals(0, types.getAddons(9, 9).size());
        assertEquals(0, types.getVariables(9, 9).size());
        assertEquals(1, types.getAddons(2, 2).size());
        assertEquals(1, types.getVariables(2, 2).size());
    }

    @Test
    void sameInstanceReregistrationRemovesItsPreviousSpatialMembership() {
        RoomSpecialTypes types = new RoomSpecialTypes();
        DummyAddon addon = new DummyAddon(41);
        DummyVariable variable = new DummyVariable(42);
        addon.setX((short) 3);
        addon.setY((short) 4);
        variable.setX((short) 3);
        variable.setY((short) 4);
        types.addAddon(addon);
        types.addVariable(variable);

        addon.setX((short) 7);
        addon.setY((short) 8);
        variable.setX((short) 7);
        variable.setY((short) 8);
        types.addAddon(addon);
        types.addVariable(variable);

        assertEquals(0, types.getAddons(3, 4).size());
        assertEquals(0, types.getVariables(3, 4).size());
        assertEquals(1, types.getAddons(7, 8).size());
        assertEquals(1, types.getVariables(7, 8).size());
        assertEquals(1, types.getAddons().size());
        assertEquals(1, types.getVariables().size());
    }

    private static final class DummyAddon extends InteractionWiredAddon {
        private DummyAddon(int id) { super(id, 1, null, "0", 0, 0); }
        @Override public WiredAddonType getType() { return WiredAddonType.EXECUTION_LIMIT; }
        @Override public boolean saveData(WiredSettingsV2 settings) { return false; }
        @Override public String getWiredData() { return ""; }
        @Override public void loadWiredData(ResultSet set, Room room) throws SQLException { }
        @Override public void onPickUp() { }
    }

    private static final class DummyVariable extends InteractionWiredVariable {
        private DummyVariable(int id) { super(id, 1, null, "0", 0, 0); }
        @Override public WiredVariableType getType() { return WiredVariableType.USER; }
        @Override public boolean saveData(WiredSettingsV2 settings) { return false; }
        @Override public String getWiredData() { return ""; }
        @Override public void loadWiredData(ResultSet set, Room room) throws SQLException { }
        @Override public void onPickUp() { }
    }
}

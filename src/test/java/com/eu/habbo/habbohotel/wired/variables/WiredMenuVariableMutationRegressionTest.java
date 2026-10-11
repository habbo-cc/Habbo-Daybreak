package com.eu.habbo.habbohotel.wired.variables;

import com.eu.habbo.habbohotel.items.interactions.wired.variables.WiredVariableReference;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static com.eu.habbo.habbohotel.wired.variables.WiredAliasRegressionFixture.settings;
import static org.junit.jupiter.api.Assertions.*;

class WiredMenuVariableMutationRegressionTest {
    @Test
    void valuelessCrossRoomReferenceCreatesAndDeletesOnlyAuthoritativePresence() {
        try (var fixture = new WiredAliasRegressionFixture()) {
            var origin = fixture.room(99);
            var sourceRoom = fixture.room(100);
            var source = fixture.user(sourceRoom, 60, false, 11);
            String sourceId = "room:" + source.getId();
            var reference = new WiredVariableReference(61, 1, null, "0", 0, 0);
            reference.setRoomId(99);
            origin.types().addVariable(reference);
            assertTrue(reference.saveData(settings(new int[]{0}, "flag_reference", sourceId)));
            origin.types().refreshVariableDefinition(reference);
            var holder = WiredVariableHolder.user(77);
            var events = new ArrayList<WiredVariableMutation>();
            var metadata = WiredVariableMetadata.fromDefinition(origin.room(), origin.manager().definition(reference.variableId()));
            assertFalse(metadata.hasValue());
            assertFalse(metadata.canWriteValue());
            assertTrue(metadata.canCreateAndDelete());
            assertEquals(WiredVariableManager.MutationResult.CREATED,
                    WiredVariableMenuMutation.apply(origin.room(), holder, reference.variableId(), 999, 1, true, events::add));
            assertEquals(0, sourceRoom.manager().get(sourceId, holder).value());
            assertNull(origin.manager().get(reference.variableId(), holder));
            assertEquals(WiredVariableManager.MutationResult.INVALID,
                    WiredVariableMenuMutation.apply(origin.room(), holder, reference.variableId(), 1, 0, true, events::add));
            assertEquals(WiredVariableManager.MutationResult.REMOVED,
                    WiredVariableMenuMutation.apply(origin.room(), holder, reference.variableId(), 0, 2, true, events::add));
            assertNull(sourceRoom.manager().get(sourceId, holder));
            assertNull(origin.manager().get(reference.variableId(), holder));
            assertEquals(List.of(WiredVariableMutation.Kind.CREATED, WiredVariableMutation.Kind.DELETED),
                    events.stream().map(WiredVariableMutation::kind).toList());
        }
    }

    @Test
    void actualCrossRoomReferenceUpdatesSourceNeverShadowAndReadOnlyPolicyTracksReopen() throws Exception {
        try (var fixture = new WiredAliasRegressionFixture()) {
            var origin = fixture.room(99);
            var sourceRoom = fixture.room(100);
            var source = fixture.user(sourceRoom, 50, true, 11);
            String sourceId = "room:" + source.getId();
            var reference = new WiredVariableReference(51, 1, null, "0", 0, 0);
            reference.setRoomId(99);
            origin.types().addVariable(reference);
            assertTrue(reference.saveData(settings(new int[]{0}, "shared_reference", sourceId)));
            origin.types().refreshVariableDefinition(reference);
            var holder = WiredVariableHolder.user(77);
            var events = new ArrayList<WiredVariableMutation>();
            var metadata = WiredVariableMetadata.fromDefinition(origin.room(), origin.manager().definition(reference.variableId()));
            assertTrue(metadata.canWriteValue());
            assertTrue(metadata.canCreateAndDelete());
            assertEquals(WiredVariableManager.MutationResult.CREATED,
                    WiredVariableMenuMutation.apply(origin.room(), holder, reference.variableId(), 12, 1, true, events::add));
            assertEquals(12, sourceRoom.manager().get(sourceId, holder).value());
            assertNull(origin.manager().get(reference.variableId(), holder));
            assertEquals(WiredVariableManager.MutationResult.CHANGED,
                    WiredVariableMenuMutation.apply(origin.room(), holder, reference.variableId(), 20, 0, true, events::add));
            assertEquals(20, WiredVariableAliasOperations.read(origin.room(), reference.variableId(), holder));
            assertEquals(List.of(WiredVariableMutation.Kind.CREATED, WiredVariableMutation.Kind.VALUE_CHANGED),
                    events.stream().map(WiredVariableMutation::kind).toList());
            assertTrue(events.stream().allMatch(event -> event.variableId().equals(reference.variableId())
                    && event.changeOrigin() == WiredVariableMutation.CHANGE_ORIGIN_CREATOR_TOOL));

            assertTrue(reference.saveData(settings(new int[]{1}, "shared_reference", sourceId)));
            origin.types().refreshVariableDefinition(reference);
            var reopened = new WiredVariableReference(51, 1, null, "0", 0, 0);
            reopened.setRoomId(99);
            reopened.loadWiredData(null, origin.room());
            origin.types().addVariable(reopened);
            assertTrue(reopened.readOnly());
            var readOnly = WiredVariableMetadata.fromDefinition(origin.room(), origin.manager().definition(reopened.variableId()));
            assertFalse(readOnly.canWriteValue());
            assertFalse(readOnly.canCreateAndDelete());
            for (int operation = 0; operation <= 2; operation++) {
                assertEquals(WiredVariableManager.MutationResult.INVALID,
                        WiredVariableMenuMutation.apply(origin.room(), holder, reopened.variableId(), 99, operation, true, events::add));
            }
            assertEquals(20, sourceRoom.manager().get(sourceId, holder).value());
            assertNull(origin.manager().get(reference.variableId(), holder));
            assertEquals(2, events.size());
        }
    }
}

package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.items.interactions.wired.WiredCategoryType;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WiredAuthorizationServiceTest {
    @Test
    void deniesMissingAuthenticationRoomOrItemValidation() {
        assertFalse(allowed(
                WiredAuthorizationService.Operation.EDIT_CONFIGURATION,
                facts(false, true, true, true, false, false, true, true, WiredCategoryType.EFFECT)));
        assertFalse(allowed(
                WiredAuthorizationService.Operation.EDIT_CONFIGURATION,
                facts(true, false, true, true, false, false, true, true, WiredCategoryType.EFFECT)));
        assertFalse(allowed(
                WiredAuthorizationService.Operation.EDIT_CONFIGURATION,
                facts(true, true, false, true, false, false, true, true, WiredCategoryType.EFFECT)));
        assertFalse(allowed(
                WiredAuthorizationService.Operation.EDIT_CONFIGURATION,
                facts(true, true, true, true, false, false, false, true, WiredCategoryType.EFFECT)));
        assertFalse(allowed(
                WiredAuthorizationService.Operation.EDIT_CONFIGURATION,
                facts(true, true, true, true, false, false, true, false, WiredCategoryType.EFFECT)));
    }

    @Test
    void roomRightsPreserveExistingEditorSaveAndSnapshotAccess() {
        WiredAuthorizationService.Facts facts =
                facts(true, true, true, true, false, false, true, true, WiredCategoryType.EFFECT);

        assertTrue(allowed(WiredAuthorizationService.Operation.VIEW_EDITOR, facts));
        assertTrue(allowed(WiredAuthorizationService.Operation.EDIT_CONFIGURATION, facts));
        assertTrue(allowed(WiredAuthorizationService.Operation.APPLY_SNAPSHOT, facts));
    }

    @Test
    void moveRotatePreservesSelectorViewAndConfigurationButNotSnapshotAccess() {
        WiredAuthorizationService.Facts selectorFacts =
                facts(true, true, true, false, true, false, true, true, WiredCategoryType.SELECTOR);
        WiredAuthorizationService.Facts effectFacts =
                facts(true, true, true, false, true, false, true, true, WiredCategoryType.EFFECT);

        assertTrue(allowed(WiredAuthorizationService.Operation.VIEW_EDITOR, selectorFacts));
        assertFalse(allowed(WiredAuthorizationService.Operation.VIEW_EDITOR, effectFacts));
        assertTrue(allowed(WiredAuthorizationService.Operation.ACK_EDITOR, selectorFacts));
        assertTrue(allowed(WiredAuthorizationService.Operation.ACK_EDITOR, effectFacts));
        assertTrue(allowed(WiredAuthorizationService.Operation.EDIT_CONFIGURATION, effectFacts));
        assertFalse(allowed(WiredAuthorizationService.Operation.APPLY_SNAPSHOT, effectFacts));
    }

    @Test
    void snapshotStillRequiresUserToBeInRoom() {
        WiredAuthorizationService.Facts facts =
                facts(true, true, false, true, false, false, true, true, WiredCategoryType.CONDITION);

        assertFalse(allowed(WiredAuthorizationService.Operation.APPLY_SNAPSHOT, facts));
    }

    @Test
    void superWiredDoesNotGrantEditorAuthorization() {
        WiredAuthorizationService.Facts facts =
                facts(true, true, true, false, false, true, true, true, WiredCategoryType.EFFECT);

        assertFalse(allowed(WiredAuthorizationService.Operation.VIEW_EDITOR, facts));
        assertFalse(allowed(WiredAuthorizationService.Operation.EDIT_CONFIGURATION, facts));
        assertFalse(allowed(WiredAuthorizationService.Operation.APPLY_SNAPSHOT, facts));
    }

    @Test
    void operationsWithoutLivePolicyRemainFailClosed() {
        WiredAuthorizationService.Facts privilegedFacts =
                facts(true, true, true, true, true, true, true, true, WiredCategoryType.EFFECT);
        EnumSet<WiredAuthorizationService.Operation> futureOperations = EnumSet.of(
                WiredAuthorizationService.Operation.INSPECT,
                WiredAuthorizationService.Operation.VARIABLE_CRUD,
                WiredAuthorizationService.Operation.ENVIRONMENT_EDIT,
                WiredAuthorizationService.Operation.VIEW_LOGS,
                WiredAuthorizationService.Operation.POLICY_EDIT);

        for (WiredAuthorizationService.Operation operation : futureOperations) {
            assertFalse(allowed(operation, privilegedFacts), operation + " must remain closed");
        }
    }

    private boolean allowed(
            WiredAuthorizationService.Operation operation,
            WiredAuthorizationService.Facts facts) {
        return WiredAuthorizationService.isAuthorized(operation, facts);
    }

    private WiredAuthorizationService.Facts facts(
            boolean authenticated,
            boolean exactCurrentRoom,
            boolean inRoom,
            boolean hasRoomRights,
            boolean hasMoveRotatePermission,
            boolean hasSuperWiredPermission,
            boolean itemInRoom,
            boolean itemCategoryMatches,
            WiredCategoryType category) {
        return new WiredAuthorizationService.Facts(
                authenticated,
                exactCurrentRoom,
                inRoom,
                hasRoomRights,
                hasMoveRotatePermission,
                hasSuperWiredPermission,
                itemInRoom,
                itemCategoryMatches,
                category);
    }
}

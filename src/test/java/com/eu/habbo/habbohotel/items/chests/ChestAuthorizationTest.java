package com.eu.habbo.habbohotel.items.chests;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChestAuthorizationTest {
    @Test
    void rejectsStaleOrUnauthenticatedRoomStateForEveryOperation() {
        for (ChestAuthorization.Operation operation : ChestAuthorization.Operation.values()) {
            assertFalse(ChestAuthorization.allowed(operation,
                    facts(false, true, true, true, true, true,
                            true, true, true, true, true)));
            assertFalse(ChestAuthorization.allowed(operation,
                    facts(true, false, true, true, true, true,
                            true, true, true, true, true)));
            assertFalse(ChestAuthorization.allowed(operation,
                    facts(true, true, false, true, true, true,
                            true, true, true, true, true)));
        }
    }

    @Test
    void openAndDonateHonorPublicFlagsWithoutGrantingMutationRights() {
        ChestAuthorization.Facts visitor = facts(true, true, true,
                false, false, false, false, true,
                true, false, false);

        assertTrue(ChestAuthorization.allowed(ChestAuthorization.Operation.OPEN, visitor));
        assertTrue(ChestAuthorization.allowed(ChestAuthorization.Operation.DONATE, visitor));
        assertFalse(ChestAuthorization.allowed(ChestAuthorization.Operation.WITHDRAW, visitor));
        assertFalse(ChestAuthorization.allowed(ChestAuthorization.Operation.EDIT, visitor));
        assertFalse(ChestAuthorization.allowed(ChestAuthorization.Operation.LOCK, visitor));
    }

    @Test
    void chestOwnerCanUnlockAndWithdrawEvenWhileLocked() {
        ChestAuthorization.Facts owner = facts(true, true, true,
                true, false, false, false, false,
                false, true, true);

        assertTrue(ChestAuthorization.allowed(ChestAuthorization.Operation.UNLOCK, owner));
        assertTrue(ChestAuthorization.allowed(ChestAuthorization.Operation.WITHDRAW, owner));
        assertTrue(ChestAuthorization.allowed(ChestAuthorization.Operation.EDIT, owner));
        assertFalse(ChestAuthorization.allowed(ChestAuthorization.Operation.WIRED_USE, owner));
    }

    @Test
    void roomOwnerMayLockButOnlyChestOwnerMayUnlockOrEdit() {
        ChestAuthorization.Facts roomOwner = facts(true, true, true,
                false, true, true, true, true,
                false, true, true);

        assertTrue(ChestAuthorization.allowed(ChestAuthorization.Operation.LOCK, roomOwner));
        assertFalse(ChestAuthorization.allowed(ChestAuthorization.Operation.UNLOCK, roomOwner));
        assertFalse(ChestAuthorization.allowed(ChestAuthorization.Operation.EDIT, roomOwner));
    }

    @Test
    void wiredUseRequiresUpgradeModifyPermissionAndUnlockedState() {
        assertTrue(ChestAuthorization.allowed(ChestAuthorization.Operation.WIRED_USE,
                facts(true, true, true, false, false, false, true, false,
                        false, false, true)));
        assertFalse(ChestAuthorization.allowed(ChestAuthorization.Operation.WIRED_USE,
                facts(true, true, true, false, false, false, true, false,
                        false, true, true)));
        assertFalse(ChestAuthorization.allowed(ChestAuthorization.Operation.WIRED_USE,
                facts(true, true, true, false, false, false, false, true,
                        false, false, true)));
        assertFalse(ChestAuthorization.allowed(ChestAuthorization.Operation.WIRED_USE,
                facts(true, true, true, false, false, false, true, false,
                        false, false, false)));
    }

    private ChestAuthorization.Facts facts(
            boolean authenticated,
            boolean sameRoom,
            boolean chestInRoom,
            boolean chestOwner,
            boolean roomOwner,
            boolean canReadWired,
            boolean canModifyWired,
            boolean everyoneCanOpen,
            boolean everyoneCanDonate,
            boolean locked,
            boolean wiredEnabled) {
        return new ChestAuthorization.Facts(authenticated, sameRoom, chestInRoom,
                chestOwner, roomOwner, canReadWired, canModifyWired,
                everyoneCanOpen, everyoneCanDonate, locked, wiredEnabled);
    }
}

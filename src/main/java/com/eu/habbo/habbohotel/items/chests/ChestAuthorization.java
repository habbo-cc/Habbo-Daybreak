package com.eu.habbo.habbohotel.items.chests;

/** Pure server-side chest permission policy matching July's wrapper checks. */
public final class ChestAuthorization {
    public enum Operation {
        OPEN,
        DONATE,
        WITHDRAW,
        EDIT,
        LOCK,
        UNLOCK,
        VIEW_LOGS,
        WIRED_USE
    }

    public record Facts(
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
    }

    private ChestAuthorization() {
    }

    public static boolean allowed(Operation operation, Facts facts) {
        if (operation == null || facts == null || !facts.authenticated()
                || !facts.sameRoom() || !facts.chestInRoom()) {
            return false;
        }
        boolean canRead = facts.chestOwner() || facts.canReadWired();
        boolean canEdit = facts.chestOwner() || facts.canModifyWired();
        return switch (operation) {
            case OPEN -> facts.everyoneCanOpen() || canRead;
            case DONATE -> facts.everyoneCanDonate()
                    || (canEdit && (!facts.locked() || facts.chestOwner()));
            case WITHDRAW -> canEdit && (!facts.locked() || facts.chestOwner());
            case EDIT -> facts.chestOwner();
            case LOCK -> facts.chestOwner() || facts.roomOwner();
            case UNLOCK -> facts.chestOwner();
            case VIEW_LOGS -> canRead;
            case WIRED_USE -> facts.wiredEnabled() && canEdit && !facts.locked();
        };
    }
}

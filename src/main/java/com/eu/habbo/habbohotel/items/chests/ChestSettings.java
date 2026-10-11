package com.eu.habbo.habbohotel.items.chests;

/** Immutable, wire-shaped July chest settings. */
public record ChestSettings(
        boolean everyoneCanOpen,
        boolean everyoneCanDonate,
        String name,
        String description,
        int stateControlMode,
        int previewMode,
        int previewAmount,
        int capacity,
        int capacityLevel,
        boolean wiredEnabled,
        boolean locked,
        boolean autoLock,
        int notifyMode,
        boolean notifyFull,
        boolean notifyDonation,
        boolean notifyWithdraw,
        boolean notifyEmpty,
        boolean notifyWiredTransaction,
        long revision) {

    public ChestSettings {
        name = sanitize(name, 30);
        description = sanitize(description, 200);
        stateControlMode = clamp(stateControlMode, 0, 3);
        previewMode = clamp(previewMode, 0, 7);
        previewAmount = clamp(previewAmount, 1, 4);
        capacity = Math.max(0, capacity);
        capacityLevel = Math.max(0, capacityLevel);
        notifyMode = clamp(notifyMode, 0, 1);
        revision = Math.max(0, revision);
    }

    public static ChestSettings defaults(int capacity) {
        return new ChestSettings(true, false, "", "", 0, 0, 1,
                Math.max(0, capacity), 0, false, true, false, 0,
                false, false, false, false, false, 0);
    }

    public ChestSettings withGeneral(String name, String description,
            boolean everyoneCanOpen, boolean everyoneCanDonate,
            int stateControlMode, int previewMode, int previewAmount,
            boolean wiredEnabled) {
        return new ChestSettings(everyoneCanOpen, everyoneCanDonate, name, description,
                stateControlMode, previewMode, previewAmount, this.capacity,
                this.capacityLevel, this.wiredEnabled || wiredEnabled, this.locked,
                this.autoLock, this.notifyMode, this.notifyFull, this.notifyDonation,
                this.notifyWithdraw, this.notifyEmpty, this.notifyWiredTransaction,
                this.revision + 1);
    }

    private static String sanitize(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String clean = value.replace('\u0000', ' ').trim();
        return clean.length() <= maxLength ? clean : clean.substring(0, maxLength);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

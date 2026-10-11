package com.eu.habbo.habbohotel.wired.core;

/**
 * Synchronous result of a click-user Wired dispatch. Only stacks that pass
 * selectors/conditions and plugin cancellation contribute to this result.
 */
public final class WiredClickUserOutcome {
    private boolean stackExecuted;
    private boolean openMenu = true;
    private boolean rotate = true;

    void record(boolean blockMenuOpen, boolean doNotRotate) {
        this.stackExecuted = true;
        if (blockMenuOpen) {
            this.openMenu = false;
        }
        if (doNotRotate) {
            this.rotate = false;
        }
    }

    public boolean stackExecuted() {
        return this.stackExecuted;
    }

    public boolean openMenu() {
        return this.openMenu;
    }

    public boolean rotate() {
        return this.rotate;
    }
}

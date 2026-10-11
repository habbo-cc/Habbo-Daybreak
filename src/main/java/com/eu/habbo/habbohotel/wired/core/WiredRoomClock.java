package com.eu.habbo.habbohotel.wired.core;

/**
 * Per-room Wired clock.
 * <p>
 * Controlled by the CONTROL_CLOCK effect (start/stop/reset), read by the
 * CLOCK_REACH_TIME trigger and CLOCK_TIME_MATCHES condition. The clock advances
 * lazily from wall time while running, so no per-tick bookkeeping is needed for
 * readers; only the trigger polls it from the 50ms tick service.
 * </p>
 */
public class WiredRoomClock {
    private boolean running = false;
    private long runStartMs = 0;
    private int baseSeconds = 0;

    public synchronized void start() {
        if (!this.running) {
            this.running = true;
            this.runStartMs = System.currentTimeMillis();
        }
    }

    public synchronized void stop() {
        if (this.running) {
            this.baseSeconds = this.getTotalSeconds();
            this.running = false;
        }
    }

    public synchronized void reset() {
        this.baseSeconds = 0;
        this.runStartMs = System.currentTimeMillis();
    }

    public synchronized boolean isRunning() {
        return this.running;
    }

    public synchronized int getTotalSeconds() {
        if (!this.running) {
            return this.baseSeconds;
        }
        return this.baseSeconds + (int) ((System.currentTimeMillis() - this.runStartMs) / 1000L);
    }

    public synchronized int getTotalHalfSeconds() {
        if (!this.running) {
            return this.baseSeconds * 2;
        }
        return (this.baseSeconds * 2) + (int) ((System.currentTimeMillis() - this.runStartMs) / 500L);
    }
}

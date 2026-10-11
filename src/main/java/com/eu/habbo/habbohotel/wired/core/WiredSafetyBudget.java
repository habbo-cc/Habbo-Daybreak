package com.eu.habbo.habbohotel.wired.core;

import java.util.UUID;

/**
 * Shared, thread-safe resource budget for one root Wired execution.
 * Child stacks, signals, remote selectors and delayed work must retain the
 * same instance so that creating a new {@link WiredState} cannot reset limits.
 */
public final class WiredSafetyBudget {
    public enum PathKind {
        STACK,
        TRIGGER_STACK,
        SIGNAL,
        REMOTE_SELECTOR
    }

    public interface PathLease extends AutoCloseable {
        @Override
        void close();
    }

    private final UUID runId = UUID.randomUUID();
    private final int maxTotalSteps;
    private final int maxSignalDepth;
    private final int maxRemoteDepth;
    private final int maxTriggerStackDepth;
    private final int maxFanOut;
    private final int maxTargets;
    private int totalSteps;
    private int totalFanOut;
    private int totalTargets;

    public WiredSafetyBudget(
            int maxTotalSteps,
            int maxSignalDepth,
            int maxRemoteDepth,
            int maxTriggerStackDepth,
            int maxFanOut,
            int maxTargets) {
        this.maxTotalSteps = requirePositive("maxTotalSteps", maxTotalSteps);
        this.maxSignalDepth = requirePositive("maxSignalDepth", maxSignalDepth);
        this.maxRemoteDepth = requirePositive("maxRemoteDepth", maxRemoteDepth);
        this.maxTriggerStackDepth = requirePositive("maxTriggerStackDepth", maxTriggerStackDepth);
        this.maxFanOut = requirePositive("maxFanOut", maxFanOut);
        this.maxTargets = requirePositive("maxTargets", maxTargets);
    }

    public static WiredSafetyBudget forSteps(int maxTotalSteps) {
        return new WiredSafetyBudget(maxTotalSteps, 10, 10, 10, 1_000, 1_000);
    }

    private static int requirePositive(String name, int value) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    public UUID runId() {
        return this.runId;
    }

    public synchronized void step() {
        if (this.totalSteps >= this.maxTotalSteps) {
            throw limit("total steps", this.maxTotalSteps);
        }
        this.totalSteps++;
    }

    public synchronized boolean canStep() {
        return this.totalSteps < this.maxTotalSteps;
    }

    public synchronized int totalSteps() {
        return this.totalSteps;
    }

    public synchronized int remainingSteps() {
        return Math.max(0, this.maxTotalSteps - this.totalSteps);
    }

    public synchronized void consumeFanOut(long amount) {
        this.totalFanOut = consume("fan-out", this.totalFanOut, amount, this.maxFanOut);
    }

    public synchronized void consumeTargets(long amount) {
        this.totalTargets = consume("targets", this.totalTargets, amount, this.maxTargets);
    }

    public synchronized int totalFanOut() {
        return this.totalFanOut;
    }

    public synchronized int totalTargets() {
        return this.totalTargets;
    }

    public synchronized boolean aggregateLimitReached() {
        return this.totalSteps >= this.maxTotalSteps
                || this.totalFanOut >= this.maxFanOut
                || this.totalTargets >= this.maxTargets;
    }

    private int consume(String name, int current, long amount, int maximum) {
        if (amount < 0 || amount > maximum - current) {
            throw limit(name, maximum);
        }
        return current + (int) amount;
    }

    int maxDepth(PathKind kind) {
        switch (kind) {
            case SIGNAL -> {
                return this.maxSignalDepth;
            }
            case REMOTE_SELECTOR -> {
                return this.maxRemoteDepth;
            }
            case TRIGGER_STACK -> {
                return this.maxTriggerStackDepth;
            }
            case STACK -> {
                return Integer.MAX_VALUE;
            }
        }
        throw new IllegalArgumentException("Unknown path kind: " + kind);
    }

    WiredLimitException limit(String name, int maximum) {
        return new WiredLimitException("Wired execution exceeded " + name + " limit: "
                + maximum + " (runId: " + this.runId + ')');
    }
}

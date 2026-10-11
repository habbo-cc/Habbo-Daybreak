package com.eu.habbo.habbohotel.wired.core;

import java.util.HashSet;
import java.util.Set;

/** Branch-local ancestry for cycle and nesting checks. */
final class WiredExecutionPath {
    private record PathKey(WiredSafetyBudget.PathKind kind, long stableId) {
    }

    private final WiredSafetyBudget budget;
    private final Set<PathKey> activePath;
    private int signalDepth;
    private int remoteDepth;
    private int triggerStackDepth;

    WiredExecutionPath(WiredSafetyBudget budget) {
        this(budget, new HashSet<>(), 0, 0, 0);
    }

    private WiredExecutionPath(
            WiredSafetyBudget budget,
            Set<PathKey> activePath,
            int signalDepth,
            int remoteDepth,
            int triggerStackDepth) {
        this.budget = budget;
        this.activePath = activePath;
        this.signalDepth = signalDepth;
        this.remoteDepth = remoteDepth;
        this.triggerStackDepth = triggerStackDepth;
    }

    WiredExecutionPath fork() {
        return new WiredExecutionPath(
                this.budget,
                new HashSet<>(this.activePath),
                this.signalDepth,
                this.remoteDepth,
                this.triggerStackDepth);
    }

    WiredSafetyBudget.PathLease enter(WiredSafetyBudget.PathKind kind, long stableId) {
        if (kind == null) {
            throw new IllegalArgumentException("kind cannot be null");
        }

        PathKey key = new PathKey(kind, stableId);
        if (!this.activePath.add(key)) {
            throw new WiredLimitException("Wired execution cycle detected for " + kind
                    + ':' + stableId + " (runId: " + this.budget.runId() + ')');
        }

        try {
            incrementDepth(kind);
        } catch (RuntimeException exception) {
            this.activePath.remove(key);
            throw exception;
        }
        return new Lease(this, key);
    }

    private void incrementDepth(WiredSafetyBudget.PathKind kind) {
        switch (kind) {
            case SIGNAL -> this.signalDepth = increment("signal depth", this.signalDepth, kind);
            case REMOTE_SELECTOR -> this.remoteDepth = increment("remote-selector depth", this.remoteDepth, kind);
            case TRIGGER_STACK -> this.triggerStackDepth = increment("trigger-stack depth", this.triggerStackDepth, kind);
            case STACK -> {
            }
        }
    }

    private int increment(String name, int current, WiredSafetyBudget.PathKind kind) {
        int maximum = this.budget.maxDepth(kind);
        if (current >= maximum) {
            throw this.budget.limit(name, maximum);
        }
        return current + 1;
    }

    private void leave(PathKey key) {
        if (!this.activePath.remove(key)) {
            return;
        }
        switch (key.kind()) {
            case SIGNAL -> this.signalDepth--;
            case REMOTE_SELECTOR -> this.remoteDepth--;
            case TRIGGER_STACK -> this.triggerStackDepth--;
            case STACK -> {
            }
        }
    }

    private static final class Lease implements WiredSafetyBudget.PathLease {
        private WiredExecutionPath owner;
        private final PathKey key;

        private Lease(WiredExecutionPath owner, PathKey key) {
            this.owner = owner;
            this.key = key;
        }

        @Override
        public void close() {
            WiredExecutionPath currentOwner = this.owner;
            this.owner = null;
            if (currentOwner != null) {
                currentOwner.leave(this.key);
            }
        }
    }
}

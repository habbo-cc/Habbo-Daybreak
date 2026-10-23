package com.eu.habbo.habbohotel.wired.core;

import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;

/** Admission boundary for one initialization per loaded/empty-room epoch. */
public final class WiredRoomInitialization {
    private static final long EMPTY_TIMEOUT = 30_000_000_000L;
    private static final long NOT_EMPTY = Long.MIN_VALUE;
    private final LongSupplier clock;
    private volatile boolean initialized;
    private volatile long emptySince = NOT_EMPTY;
    private volatile long epoch;
    private volatile long initializingEpoch;
    private volatile Thread owner;
    private CompletableFuture<Void> inProgress;

    public WiredRoomInitialization() { this(System::nanoTime); }
    WiredRoomInitialization(LongSupplier clock) { this.clock = clock; }

    public boolean allowsDispatch() {
        Thread currentOwner = owner;
        if (currentOwner == Thread.currentThread()) return initializingEpoch == epoch;
        return currentOwner == null && initialized && !expired() && owner == null;
    }

    private boolean expired() {
        long since = emptySince;
        return since != NOT_EMPTY && clock.getAsLong() - since > EMPTY_TIMEOUT;
    }

    /** Run callbacks without holding the lifecycle monitor or room locks. */
    public boolean beforeEntry(Runnable initialize) {
        for (;;) {
            CompletableFuture<Void> future;
            long claimedEpoch = -1;
            boolean claimed = false;
            synchronized (this) {
                if (owner == Thread.currentThread()) return initializingEpoch == epoch;
                if (inProgress != null) {
                    future = inProgress;
                } else if (initialized && !expired()) {
                    emptySince = NOT_EMPTY;
                    return true;
                } else {
                    future = new CompletableFuture<>();
                    inProgress = future;
                    owner = Thread.currentThread();
                    initializingEpoch = epoch;
                    claimedEpoch = epoch;
                    initialized = false;
                    emptySince = NOT_EMPTY;
                    claimed = true;
                }
            }
            if (!claimed) {
                future.join();
                continue;
            }
            try {
                initialize.run();
            } catch (RuntimeException | Error failure) {
                finish(future, claimedEpoch, failure);
                throw failure;
            }
            return finish(future, claimedEpoch, null);
        }
    }

    private boolean finish(CompletableFuture<Void> future, long claimedEpoch, Throwable failure) {
        boolean current;
        synchronized (this) {
            current = epoch == claimedEpoch;
            initialized = current && failure == null;
            owner = null;
            inProgress = null;
        }
        if (failure == null) future.complete(null);
        else future.completeExceptionally(failure);
        return current && failure == null;
    }

    public synchronized void becameEmpty() {
        // Duplicate leave notifications must not restart the empty-room timer.
        if (emptySince == NOT_EMPTY) emptySince = clock.getAsLong();
    }

    public synchronized void reset() {
        epoch++;
        initialized = false;
        emptySince = NOT_EMPTY;
    }
}

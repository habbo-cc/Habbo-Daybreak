package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.api.WiredStack;
import com.eu.habbo.habbohotel.wired.menu.WiredRoomMonitor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rolling per-room Wired execution usage shown by July's Wired Menu.
 *
 * <p>The scaled usage window and per-stack baseline are adapted from Seth's
 * Arcturus Wired implementation. Runtime errors remain owned by
 * {@code WiredRoomMonitor}; this class only meters accepted work.</p>
 */
public final class WiredUsageTracker {
    private static final int DEFAULT_USAGE_LIMIT = 8_750;
    private static final int DEFAULT_WINDOW_MS = 1_000;
    private static final int DEFAULT_BASELINE_INTERVAL_MS = 1_000;
    private static final int DEFAULT_DELAYED_EVENTS_LIMIT = 500;
    private static final int USAGE_SCALE = 20;
    private static final int STACK_BASELINE_COST = 25;
    private static final int DEPTH_COST = USAGE_SCALE / 4;

    private final Map<Integer, RoomUsageWindow> rooms = new ConcurrentHashMap<>();

    public int getCurrentUsage(Room room) {
        if (room == null) {
            return 0;
        }
        RoomUsageWindow window = this.rooms.get(room.getId());
        return window == null ? 0
                : display(window.current(System.currentTimeMillis(), windowMs()));
    }

    public int getUsageLimit() {
        return Math.max(1,
                Emulator.getConfig().getInt("wired.max_usage", DEFAULT_USAGE_LIMIT));
    }

    public boolean isHeavy(Room room) {
        if (room == null) {
            return false;
        }
        RoomUsageWindow window = this.rooms.get(room.getId());
        return window != null
                && window.current(System.currentTimeMillis(), windowMs())
                >= scaled(this.getUsageLimit()) / 2;
    }

    /**
     * Charges each candidate stack at most once per baseline interval. This
     * keeps repeaters visible in the meter without charging every nested field
     * operation twice.
     */
    public boolean tryConsumeStack(Room room, WiredStack stack, int recursionDepth) {
        if (room == null) {
            return false;
        }
        int cost = STACK_BASELINE_COST
                + Math.max(0, recursionDepth) * DEPTH_COST;
        RoomUsageWindow window =
                this.rooms.computeIfAbsent(room.getId(), ignored -> new RoomUsageWindow());
        UsageResult result = window.tryConsume(stackKey(stack), cost,
                scaled(this.getUsageLimit()), windowMs(), baselineIntervalMs());
        if (result.becameHeavy()) {
            WiredRoomMonitor.markedAsHeavy(room);
        }
        return result.accepted();
    }

    public boolean tryQueueDelayed(Room room) {
        if (room == null) {
            return false;
        }
        RoomUsageWindow window =
                this.rooms.computeIfAbsent(room.getId(), ignored -> new RoomUsageWindow());
        if (!window.tryQueueDelayed(delayedEventsLimit())) {
            WiredRoomMonitor.delayedEventsCap(room);
            return false;
        }
        return true;
    }

    public void completeDelayed(Room room) {
        if (room == null) {
            return;
        }
        RoomUsageWindow window = this.rooms.get(room.getId());
        if (window != null) {
            window.completeDelayed();
        }
    }

    public void clear(Room room) {
        if (room != null) {
            this.rooms.remove(room.getId());
        }
    }

    public void clearAll() {
        this.rooms.clear();
    }

    private static int windowMs() {
        return Math.max(100,
                Emulator.getConfig().getInt("wired.usage.window.ms", DEFAULT_WINDOW_MS));
    }

    private static int baselineIntervalMs() {
        return Math.max(100, Emulator.getConfig().getInt(
                "wired.usage.stack_baseline.interval.ms",
                DEFAULT_BASELINE_INTERVAL_MS));
    }

    private static int delayedEventsLimit() {
        return Math.max(1, Emulator.getConfig().getInt(
                "wired.delayed.events.max", DEFAULT_DELAYED_EVENTS_LIMIT));
    }

    private static int scaled(int value) {
        if (value > Integer.MAX_VALUE / USAGE_SCALE) {
            return Integer.MAX_VALUE;
        }
        return value * USAGE_SCALE;
    }

    private static int display(int value) {
        return (value + USAGE_SCALE - 1) / USAGE_SCALE;
    }

    private static String stackKey(WiredStack stack) {
        return stack == null || stack.triggerItem() == null
                ? "stack:null" : "stack:" + stack.triggerItem().getId();
    }

    private static final class RoomUsageWindow {
        private final Deque<UsageEvent> events = new ArrayDeque<>();
        private final Map<String, Long> charges = new ConcurrentHashMap<>();
        private int usage;
        private int delayedEvents;

        private synchronized int current(long now, int windowMs) {
            this.prune(now, windowMs);
            return this.usage;
        }

        private synchronized UsageResult tryConsume(
                String key, int cost, int limit, int windowMs, int intervalMs) {
            long now = System.currentTimeMillis();
            this.prune(now, windowMs);
            Long previous = this.charges.get(key);
            if (previous != null && now - previous < intervalMs) {
                return new UsageResult(true, false);
            }
            if (cost > limit - this.usage) {
                return new UsageResult(false, false);
            }
            boolean wasHeavy = this.usage >= limit / 2;
            this.events.addLast(new UsageEvent(now, cost));
            this.usage += cost;
            this.charges.put(key, now);
            return new UsageResult(true, !wasHeavy && this.usage >= limit / 2);
        }

        private synchronized boolean tryQueueDelayed(int limit) {
            if (this.delayedEvents >= limit) {
                return false;
            }
            this.delayedEvents++;
            return true;
        }

        private synchronized void completeDelayed() {
            if (this.delayedEvents > 0) {
                this.delayedEvents--;
            }
        }

        private void prune(long now, int windowMs) {
            while (!this.events.isEmpty()
                    && now - this.events.peekFirst().timestamp > windowMs) {
                this.usage -= this.events.removeFirst().cost;
            }
            long cutoff = now - Math.max(windowMs * 2L,
                    baselineIntervalMs() * 2L);
            this.charges.entrySet().removeIf(entry -> entry.getValue() < cutoff);
            if (this.usage < 0) {
                this.usage = 0;
            }
        }
    }

    private record UsageEvent(long timestamp, int cost) {
    }

    private record UsageResult(boolean accepted, boolean becameHeavy) {
    }
}

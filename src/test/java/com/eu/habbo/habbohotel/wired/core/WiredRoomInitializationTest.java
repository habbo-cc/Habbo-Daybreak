package com.eu.habbo.habbohotel.wired.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WiredRoomInitializationTest {
    @Test
    void coldPreloadIsSilentAndFirstAdmissionInitializesBeforeOtherEvents() {
        WiredRoomInitialization state = new WiredRoomInitialization();
        List<String> events = new ArrayList<>();
        assertFalse(state.allowsDispatch());
        assertTrue(state.beforeEntry(() -> {
            assertFalse(Thread.holdsLock(state));
            assertTrue(state.allowsDispatch());
            events.add("init");
            assertTrue(state.beforeEntry(() -> fail("nested admission cannot initialize again")));
            events.add("nested effect");
        }));
        events.add("enter");
        assertTrue(state.beforeEntry(() -> fail("a second entrant cannot initialize again")));
        assertEquals(List.of("init", "nested effect", "enter"), events);
        assertTrue(state.allowsDispatch());
    }

    @Test
    void thirtySecondsIsWarmButStrictlyMoreRequiresInitializationEvenWithoutUnloading() {
        AtomicLong now = new AtomicLong();
        WiredRoomInitialization state = new WiredRoomInitialization(now::get);
        AtomicInteger resets = new AtomicInteger();
        state.beforeEntry(resets::incrementAndGet);
        state.becameEmpty();
        now.set(30_000_000_000L);
        assertTrue(state.allowsDispatch());
        state.beforeEntry(resets::incrementAndGet);
        assertEquals(1, resets.get());
        state.becameEmpty();
        now.addAndGet(30_000_000_001L);
        assertFalse(state.allowsDispatch());
        state.beforeEntry(resets::incrementAndGet);
        assertEquals(2, resets.get());
        assertTrue(state.allowsDispatch());
    }

    @Test
    void duplicateEmptyNotificationsDoNotPostponeInitialization() {
        AtomicLong now = new AtomicLong();
        WiredRoomInitialization state = new WiredRoomInitialization(now::get);
        state.beforeEntry(() -> {});
        state.becameEmpty();
        now.set(20_000_000_000L);
        state.becameEmpty();
        now.set(30_000_000_001L);
        assertFalse(state.allowsDispatch());
    }

    @Test
    void disposeOrReloadEstablishesANewEpochWithoutWaitingThirtySeconds() {
        WiredRoomInitialization state = new WiredRoomInitialization();
        AtomicInteger resets = new AtomicInteger();
        state.beforeEntry(resets::incrementAndGet);
        state.reset();
        assertFalse(state.allowsDispatch());
        state.beforeEntry(resets::incrementAndGet);
        assertEquals(2, resets.get());
    }

    @Test
    void resetInsideCallbackCannotMarkADisposedEpochInitialized() {
        WiredRoomInitialization state = new WiredRoomInitialization();
        assertFalse(state.beforeEntry(() -> {
            state.reset();
            assertFalse(state.beforeEntry(() -> fail("stale reentry must not join its own future")));
        }));
        assertFalse(state.allowsDispatch());
        assertTrue(state.beforeEntry(() -> {}));
        assertTrue(state.allowsDispatch());
    }

    @Test
    void failureReleasesTheAdmissionAndAllowsARetry() {
        WiredRoomInitialization state = new WiredRoomInitialization();
        assertThrows(IllegalStateException.class, () -> state.beforeEntry(() -> { throw new IllegalStateException("test"); }));
        assertFalse(state.allowsDispatch());
        assertTrue(state.beforeEntry(() -> {}));
    }

    @Test
    void concurrentFirstAdmissionsInitializeOnceAndOtherThreadsCannotDispatchEarly() throws Exception {
        WiredRoomInitialization state = new WiredRoomInitialization();
        AtomicInteger count = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(8);
        try {
            var first = pool.submit(() -> state.beforeEntry(() -> {
                count.incrementAndGet();
                started.countDown();
                try { assertTrue(finish.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { throw new RuntimeException(e); }
            }));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertFalse(state.allowsDispatch());
            var peers = new ArrayList<java.util.concurrent.Future<Boolean>>();
            for (int i = 0; i < 6; i++) peers.add(pool.submit(() -> state.beforeEntry(count::incrementAndGet)));
            finish.countDown();
            assertTrue(first.get(5, TimeUnit.SECONDS));
            for (var peer : peers) assertTrue(peer.get(5, TimeUnit.SECONDS));
            assertEquals(1, count.get());
        } finally {
            finish.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}

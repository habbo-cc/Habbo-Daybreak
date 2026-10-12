package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.threading.ThreadPooling;
import com.eu.habbo.database.Database;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoomLoadingConcurrencyTest {
    private ThreadPooling threading;
    private Object previousThreading;

    @BeforeEach
    void setUp() throws Exception {
        previousThreading = field(Emulator.class, "threading").get(null);
        threading = new ThreadPooling(16);
        field(Emulator.class, "threading").set(null, threading);
    }

    @AfterEach
    void tearDown() throws Exception {
        threading.shutDown();
        field(Emulator.class, "threading").set(null, previousThreading);
    }

    @Test
    void sixteenRoomEntriesDoNotStarveTheirLoadersOrNextGamePacket() throws Exception {
        CountDownLatch entriesStarted = new CountDownLatch(16);
        CountDownLatch loaderStarted = new CountDownLatch(4);
        CountDownLatch releaseLoaders = new CountDownLatch(1);
        AtomicInteger loads = new AtomicInteger();
        List<Future<?>> entries = new ArrayList<>();
        try {
            for (int index = 0; index < 16; index++) {
                Room room = room(() -> {
                    assertTrue(Thread.currentThread().getName().startsWith("RoomLoad"));
                    loaderStarted.countDown();
                    await(releaseLoaders);
                    loads.incrementAndGet();
                });
                entries.add(threading.getService().submit(() -> {
                    room.startBackgroundLoad();
                    entriesStarted.countDown();
                    room.waitForLoad();
                }));
            }
            assertTrue(entriesStarted.await(3, TimeUnit.SECONDS));
            assertTrue(loaderStarted.await(3, TimeUnit.SECONDS));
            // Every game worker is now occupied by entry. This packet can only
            // run once room loaders complete without depending on that pool.
            Future<?> nextPacket = threading.getService().submit(() -> {});
            releaseLoaders.countDown();
            for (Future<?> entry : entries) {
                entry.get(3, TimeUnit.SECONDS);
            }
            nextPacket.get(3, TimeUnit.SECONDS);
            assertEquals(16, loads.get());
        } finally {
            releaseLoaders.countDown();
        }
    }

    @Test
    void simultaneousEntriesShareOneLoadAndWaitUntilItFinishes() throws Exception {
        CountDownLatch loaderStarted = new CountDownLatch(1);
        CountDownLatch releaseLoader = new CountDownLatch(1);
        CountDownLatch entriesStarted = new CountDownLatch(16);
        AtomicInteger loads = new AtomicInteger();
        Room room = room(() -> {
            loads.incrementAndGet();
            loaderStarted.countDown();
            await(releaseLoader);
        });
        List<Future<?>> entries = new ArrayList<>();
        try {
            room.startBackgroundLoad();
            assertTrue(loaderStarted.await(3, TimeUnit.SECONDS));
            for (int index = 0; index < 16; index++) {
                entries.add(threading.getService().submit(() -> {
                    entriesStarted.countDown();
                    room.loadData();
                }));
            }
            assertTrue(entriesStarted.await(3, TimeUnit.SECONDS));
            assertTrue(room.isLoadingInProgress());
            assertFalse(room.isLoaded());
            releaseLoader.countDown();
            for (Future<?> entry : entries) {
                entry.get(3, TimeUnit.SECONDS);
            }
            assertTrue(room.isLoaded());
            assertFalse(room.isLoadingInProgress());
            assertEquals(1, loads.get());
        } finally {
            releaseLoader.countDown();
        }
    }

    @Test
    void synchronousLoadAlsoRunsOutsideTheGameExecutor() throws Exception {
        Room room = room(() -> assertTrue(Thread.currentThread().getName().startsWith("RoomLoad")));
        threading.getService().submit(room::loadData).get(3, TimeUnit.SECONDS);
        assertTrue(room.isLoaded());
    }

    @Test
    void loadFailurePropagatesAndLeavesTheRoomRetryable() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        Room room = room(() -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("load failed");
            }
        });
        CompletionException error = assertThrows(CompletionException.class, room::loadData);
        assertInstanceOf(IllegalStateException.class, error.getCause());
        assertFalse(room.isLoaded());
        assertFalse(room.isLoadingInProgress());
        assertTrue(room.isPreLoaded());
        room.loadData();
        assertTrue(room.isLoaded());
        assertEquals(2, attempts.get());
    }

    @Test
    void fullLoaderQueueRejectsWithoutMarkingRoomLoadingForever() throws Exception {
        threading.shutDown();
        threading = new ThreadPooling(1);
        field(Emulator.class, "threading").set(null, threading);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            CompletableFuture<Void> active = threading.submitRoomLoad(() -> {
                started.countDown();
                await(release);
            });
            assertTrue(started.await(3, TimeUnit.SECONDS));
            List<CompletableFuture<Void>> queued = new ArrayList<>();
            for (int index = 0; index < 128; index++) {
                queued.add(threading.submitRoomLoad(() -> {}));
            }
            Room room = room(() -> {});
            assertThrows(RejectedExecutionException.class, room::startBackgroundLoad);
            assertFalse(room.isLoadingInProgress());
            assertTrue(room.isPreLoaded());
            release.countDown();
            active.get(3, TimeUnit.SECONDS);
            CompletableFuture.allOf(queued.toArray(CompletableFuture[]::new)).get(3, TimeUnit.SECONDS);
            room.loadData();
            assertTrue(room.isLoaded());
        } finally {
            release.countDown();
        }
    }

    @Test
    void shutdownCompletesQueuedAndRunningLoadFuturesExceptionally() throws Exception {
        threading.shutDown();
        threading = new ThreadPooling(1);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> active = threading.submitRoomLoad(() -> {
            started.countDown();
            await(release);
        });
        try {
            assertTrue(started.await(3, TimeUnit.SECONDS));
            CompletableFuture<Void> queued = threading.submitRoomLoad(() -> {});
            threading.shutDown();
            assertThrows(CompletionException.class, queued::join);
            assertThrows(CompletionException.class, active::join);
            assertThrows(RejectedExecutionException.class, () -> threading.submitRoomLoad(() -> {}));
        } finally {
            release.countDown();
        }
    }

    @Test
    void coldRoomLookupClosesMetadataConnectionBeforeWaitingForLoad() throws Exception {
        Database database = mock(Database.class);
        HikariDataSource source = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        when(database.getDataSource()).thenReturn(source);
        when(source.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true, false);
        RoomManager manager = mock(RoomManager.class, CALLS_REAL_METHODS);
        field(RoomManager.class, "activeRooms").set(manager, new ConcurrentHashMap<Integer, Room>());
        try (var emulator = mockStatic(Emulator.class);
             var rooms = mockConstruction(Room.class, (room, context) -> {
                 when(room.getId()).thenReturn(42);
                 doAnswer(invocation -> {
                     verify(rows).close();
                     verify(statement).close();
                     verify(connection).close();
                     return null;
                 }).when(room).loadData();
             })) {
            emulator.when(Emulator::getDatabase).thenReturn(database);
            Room loaded = manager.loadRoom(42, true);
            assertSame(rooms.constructed().getFirst(), loaded);
            verify(loaded).loadData();
        }
    }

    private static Room room(Runnable body) throws Exception {
        // Exercise real Room admission/wait/state methods with a controlled
        // loader body. Database/layout/gameplay are outside this concurrency test.
        Room room = mock(Room.class, CALLS_REAL_METHODS);
        field(Room.class, "loadLock").set(room, new Object());
        field(Room.class, "preLoaded").setBoolean(room, true);
        doAnswer(invocation -> {
            field(Room.class, "preLoaded").setBoolean(room, false);
            body.run();
            field(Room.class, "loaded").setBoolean(room, true);
            return null;
        }).when(room).loadDataInternal();
        return room;
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("test loader was never released");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }
}

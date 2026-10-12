package com.eu.habbo.threading;

import com.eu.habbo.Emulator;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

public class ThreadPooling {

    private static final Logger LOGGER = LoggerFactory.getLogger(ThreadPooling.class);

    public final int threads;
    private final ScheduledExecutorService scheduledPool;
    private final ThreadPoolExecutor roomLoadPool;
    private final Set<CompletableFuture<Void>> roomLoads = ConcurrentHashMap.newKeySet();
    private volatile boolean canAdd;

    public ThreadPooling(Integer threads) {
        this.threads = threads;
        this.scheduledPool = new HabboExecutorService(this.threads, new DefaultThreadFactory("HabExec"));
        // Room entry can occupy every HabExec worker while waiting for these
        // tasks. Loaders must never depend on a free worker in that pool.
        int roomThreads = Math.max(1, Math.min(4, this.threads));
        this.roomLoadPool = new ThreadPoolExecutor(roomThreads, roomThreads, 0,
                TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(128),
                new DefaultThreadFactory("RoomLoad"), new ThreadPoolExecutor.AbortPolicy());
        this.canAdd = true;
        LOGGER.info("Thread Pool -> Loaded!");
    }

    public ScheduledFuture<?> run(Runnable run) {
        try {
            if (this.canAdd) {
                return this.run(run, 0);
            } else {
                if (Emulator.isShuttingDown) {
                    run.run();
                }
            }
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }

        return null;
    }

    public ScheduledFuture<?> run(Runnable run, long delay) {
        try {
            if (this.canAdd) {
                return this.scheduledPool.schedule(() -> {
                    try {
                        run.run();
                    } catch (Exception e) {
                        LOGGER.error("Caught exception", e);
                    }
                }, delay, TimeUnit.MILLISECONDS);
            }
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }

        return null;
    }

    public void shutDown() {
        this.canAdd = false;
        this.roomLoadPool.shutdownNow();
        // shutdownNow removes queued tasks without running them; explicitly
        // release the room-entry callers waiting on their completion futures.
        for (CompletableFuture<Void> load : this.roomLoads) {
            load.completeExceptionally(new RejectedExecutionException("Room loading is shutting down"));
        }
        this.roomLoads.clear();
        this.scheduledPool.shutdownNow();

        LOGGER.info("Threading -> Disposed!");
    }

    public void setCanAdd(boolean canAdd) {
        this.canAdd = canAdd;
    }

    public ScheduledExecutorService getService() {
        return this.scheduledPool;
    }

    public CompletableFuture<Void> submitRoomLoad(Runnable load) {
        if (!this.canAdd) {
            throw new RejectedExecutionException("Room loading is shutting down");
        }
        CompletableFuture<Void> result = new CompletableFuture<>();
        this.roomLoads.add(result);
        try {
            this.roomLoadPool.execute(() -> {
                try {
                    if (!result.isDone()) {
                        load.run();
                        result.complete(null);
                    }
                } catch (Throwable error) {
                    result.completeExceptionally(error);
                } finally {
                    this.roomLoads.remove(result);
                }
            });
        } catch (RejectedExecutionException error) {
            this.roomLoads.remove(result);
            throw error;
        }
        return result;
    }


}

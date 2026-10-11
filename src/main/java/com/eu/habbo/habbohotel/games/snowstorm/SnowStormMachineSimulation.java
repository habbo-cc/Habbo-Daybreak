package com.eu.habbo.habbohotel.games.snowstorm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Deterministic 60 ms-clock replacement for Havana's independent machine scheduler jobs. */
public final class SnowStormMachineSimulation {
    private SnowStormMachineSimulation() {
    }

    public static Result advance(
            State state,
            long elapsedMs,
            Set<Integer> eligibleObjectIds,
            Map<Integer, Integer> playerAmmo,
            int maximumStock,
            int refillIntervalMs,
            int transferIntervalMs) {
        Map<Integer, Integer> ammo = new LinkedHashMap<>(playerAmmo);
        List<Event> events = new ArrayList<>(2);
        int stock = state.stock();
        long nextRefillMs = state.nextRefillMs();
        long nextTransferMs = state.nextTransferMs();
        if (eligibleObjectIds.isEmpty()) {
            return new Result(new State(stock, elapsedMs, elapsedMs), ammo, events);
        }

        if (elapsedMs >= nextRefillMs) {
            if (stock < maximumStock) {
                stock++;
                events.add(Event.restock(stock));
            }
            nextRefillMs += refillIntervalMs;
        }
        if (elapsedMs >= nextTransferMs) {
            Integer winner = eligibleObjectIds.stream()
                    .sorted(Comparator.naturalOrder())
                    .filter(objectId -> ammo.getOrDefault(objectId, 0) < SnowStormAvatarActivity.MAX_AMMO)
                    .findFirst()
                    .orElse(null);
            if (winner != null && stock > 0) {
                stock--;
                int playerCount = ammo.getOrDefault(winner, 0) + 1;
                ammo.put(winner, playerCount);
                events.add(Event.transfer(winner, stock, playerCount));
            }
            nextTransferMs += transferIntervalMs;
        }
        return new Result(new State(stock, nextRefillMs, nextTransferMs), ammo, events);
    }

    public record State(int stock, long nextRefillMs, long nextTransferMs) {
        public State {
            if (stock < 0 || nextRefillMs < 0 || nextTransferMs < 0) {
                throw new IllegalArgumentException("Invalid SnowStorm machine state");
            }
        }

        public static State initial(int stock) {
            return new State(stock, 0, 0);
        }
    }

    public enum EventType { RESTOCK, TRANSFER }

    public record Event(EventType type, int playerObjectId, int machineCount, int playerCount) {
        static Event restock(int machineCount) {
            return new Event(EventType.RESTOCK, -1, machineCount, -1);
        }

        static Event transfer(int playerObjectId, int machineCount, int playerCount) {
            return new Event(EventType.TRANSFER, playerObjectId, machineCount, playerCount);
        }
    }

    public record Result(State state, Map<Integer, Integer> playerAmmo, List<Event> events) {
        public Result {
            playerAmmo = Map.copyOf(playerAmmo);
            events = List.copyOf(events);
        }
    }
}

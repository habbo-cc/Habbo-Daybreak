package com.eu.habbo.habbohotel.games.snowstorm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnowStormMovementAndMachineTest {
    @Test
    void sharedGridPathfinderRoutesAroundACompleteDirectBarrier() {
        SnowStormMapDefinition map = map(List.of(
                ".....",
                ".xxx.",
                "....."));
        SnowStormTile start = new SnowStormTile(0, 1);
        SnowStormTile target = new SnowStormTile(4, 1);

        var path = SnowStormMovementPlanner.findPath(map, start, target, Set.of());

        assertFalse(path.isEmpty());
        assertEquals(target.x(), path.get(path.size() - 1).x());
        assertEquals(target.y(), path.get(path.size() - 1).y());
        assertTrue(path.stream().noneMatch(step -> !map.isWalkable(
                new SnowStormTile(step.x(), step.y()))));
    }

    @Test
    void liveReservationsAreTreatedAsOccupiedGridTiles() {
        SnowStormMapDefinition map = map(List.of("...", "...", "..."));
        SnowStormTile start = new SnowStormTile(0, 1);
        SnowStormTile target = new SnowStormTile(2, 1);

        var path = SnowStormMovementPlanner.findPath(
                map, start, target, Set.of(new SnowStormTile(1, 1)));

        assertFalse(path.isEmpty());
        assertFalse(path.stream().anyMatch(step -> step.x() == 1 && step.y() == 1));
        assertEquals(target.x(), path.get(path.size() - 1).x());
        assertEquals(target.y(), path.get(path.size() - 1).y());
    }

    @Test
    void matchesAllDeterministicMachineCadenceAndContentionFixtures() throws IOException {
        for (JsonElement element : fixture().getAsJsonArray("machineVectors")) {
            JsonObject vector = element.getAsJsonObject();
            JsonArray collectors = vector.getAsJsonArray("collectors");
            Map<Integer, Integer> ammo = new HashMap<>();
            for (JsonElement collectorElement : collectors) {
                JsonObject collector = collectorElement.getAsJsonObject();
                ammo.put(collector.get("objectId").getAsInt(), collector.get("ammo").getAsInt());
            }
            SnowStormMachineSimulation.State state = SnowStormMachineSimulation.State.initial(0);
            List<ObservedEvent> events = new ArrayList<>();
            int duration = switch (vector.get("name").getAsString()) {
                case "sustained-collection" -> 150;
                case "no-collector-no-refill" -> 100;
                default -> 0;
            };
            for (int subturn = 0; subturn <= duration; subturn++) {
                Set<Integer> eligible = new HashSet<>();
                for (JsonElement collectorElement : collectors) {
                    JsonObject collector = collectorElement.getAsJsonObject();
                    if (collector.get("enterSubturn").getAsInt() <= subturn
                            && subturn < collector.get("leaveSubturn").getAsInt()) {
                        eligible.add(collector.get("objectId").getAsInt());
                    }
                }
                SnowStormMachineSimulation.Result result = SnowStormMachineSimulation.advance(
                        state, subturn * 60L, eligible, ammo, 5, 3000, 1000);
                state = result.state();
                ammo = new HashMap<>(result.playerAmmo());
                for (SnowStormMachineSimulation.Event event : result.events()) {
                    events.add(new ObservedEvent(subturn, event));
                }
            }
            JsonObject expected = vector.getAsJsonObject("result");
            assertEquals(expected.get("finalMachineCount").getAsInt(), state.stock(), vector.get("name").getAsString());
            for (Map.Entry<String, JsonElement> entry : expected.getAsJsonObject("finalPlayerCounts").entrySet()) {
                assertEquals(entry.getValue().getAsInt(), ammo.get(Integer.parseInt(entry.getKey())));
            }
            assertEquals(expected.getAsJsonArray("events").size(), events.size());
            for (int index = 0; index < events.size(); index++) {
                JsonObject expectedEvent = expected.getAsJsonArray("events").get(index).getAsJsonObject();
                ObservedEvent actual = events.get(index);
                assertEquals(expectedEvent.get("subturn").getAsInt(), actual.subturn());
                if (expectedEvent.get("event").getAsString().equals("restock")) {
                    assertEquals(SnowStormMachineSimulation.EventType.RESTOCK, actual.event().type());
                } else {
                    assertEquals(SnowStormMachineSimulation.EventType.TRANSFER, actual.event().type());
                    assertEquals(expectedEvent.get("playerObjectId").getAsInt(), actual.event().playerObjectId());
                    assertEquals(expectedEvent.get("playerCount").getAsInt(), actual.event().playerCount());
                }
                assertEquals(expectedEvent.get("machineCount").getAsInt(), actual.event().machineCount());
            }
        }
    }

    private static JsonObject fixture() throws IOException {
        Path path = Path.of("tools", "snowstorm", "fixtures", "simulation_fixtures.json");
        try (var reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static SnowStormMapDefinition map(List<String> collisions) {
        int width = collisions.get(0).length();
        List<String> floor = collisions.stream().map(row -> ".".repeat(width)).toList();
        return new SnowStormMapDefinition(1, 1, width, collisions.size(), 3200,
                0, 0, 0, 0, 0, "test", List.of(), floor, collisions,
                List.of(), List.of(), List.of(), 0, "test");
    }

    private record ObservedEvent(int subturn, SnowStormMachineSimulation.Event event) {
    }
}

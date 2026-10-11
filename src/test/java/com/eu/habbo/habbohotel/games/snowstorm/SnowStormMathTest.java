package com.eu.habbo.habbohotel.games.snowstorm;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnowStormMathTest {
    @Test
    void checksumIncludesObjectIdentityAndType() {
        List<Integer> values = List.of(6400, 3200, 4000, 90, 0, -1, 7, 6);
        int baseline = SnowStormMath.checksum(5,
                List.of(new SnowStormMath.ChecksumObject(1, 2, values)));
        assertNotEquals(baseline, SnowStormMath.checksum(5,
                List.of(new SnowStormMath.ChecksumObject(1, 3, values))));
        assertNotEquals(baseline, SnowStormMath.checksum(5,
                List.of(new SnowStormMath.ChecksumObject(2, 2, values))));
    }

    @Test
    void matchesEverySeedContributionAndChecksumFixture() throws IOException {
        JsonObject fixture = fixture("core_math_fixtures.json");
        for (JsonElement element : fixture.getAsJsonArray("seedVectors")) {
            JsonObject vector = element.getAsJsonObject();
            assertEquals(vector.get("output").getAsInt(),
                    SnowStormMath.iterateSeed(vector.get("input").getAsInt()));
        }
        for (JsonElement element : fixture.getAsJsonArray("contributionVectors")) {
            JsonObject vector = element.getAsJsonObject();
            assertEquals(vector.get("contribution").getAsInt(),
                    SnowStormMath.checksumContribution(integers(vector, "values")),
                    vector.get("name").getAsString());
        }
        for (JsonElement element : fixture.getAsJsonArray("checksumVectors")) {
            JsonObject vector = element.getAsJsonObject();
            List<SnowStormMath.ChecksumObject> objects = new ArrayList<>();
            for (JsonElement objectElement : vector.getAsJsonArray("objects")) {
                JsonObject object = objectElement.getAsJsonObject();
                objects.add(new SnowStormMath.ChecksumObject(
                        objectType(object.get("objectType").getAsString()),
                        object.get("objectId").getAsInt(), integers(object, "values")));
            }
            assertEquals(vector.get("checksum").getAsInt(),
                    SnowStormMath.checksum(vector.get("turnNumber").getAsInt(), objects),
                    vector.get("name").getAsString());
        }
    }

    @Test
    void matchesCoordinateDirectionAndMovementFixtures() throws IOException {
        JsonObject fixture = fixture("simulation_fixtures.json");
        for (JsonElement element : fixture.getAsJsonArray("coordinateVectors")) {
            JsonObject vector = element.getAsJsonObject();
            var actual = SnowStormMath.worldToTile(vector.get("world").getAsInt());
            if (vector.get("rejected").getAsBoolean()) {
                assertTrue(actual.isEmpty());
            } else {
                assertTrue(actual.isPresent());
                assertEquals(vector.get("tile").getAsInt(), actual.getAsInt());
            }
        }
        for (JsonElement element : fixture.getAsJsonArray("directionVectors")) {
            JsonObject vector = element.getAsJsonObject();
            SnowStormTile delta = SnowStormMath.directionDelta(vector.get("direction").getAsInt());
            assertEquals(vector.get("deltaX").getAsInt(), delta.x());
            assertEquals(vector.get("deltaY").getAsInt(), delta.y());
        }
        for (JsonElement element : fixture.getAsJsonArray("movementVectors")) {
            JsonObject vector = element.getAsJsonObject();
            int x = vector.getAsJsonArray("start").get(0).getAsInt();
            int y = vector.getAsJsonArray("start").get(1).getAsInt();
            int targetX = vector.getAsJsonArray("target").get(0).getAsInt();
            int targetY = vector.getAsJsonArray("target").get(1).getAsInt();
            for (JsonElement pathElement : vector.getAsJsonArray("path")) {
                JsonObject expected = pathElement.getAsJsonObject();
                x = SnowStormMath.stepAxis(x, targetX);
                y = SnowStormMath.stepAxis(y, targetY);
                assertEquals(expected.get("worldX").getAsInt(), x, vector.get("name").getAsString());
                assertEquals(expected.get("worldY").getAsInt(), y, vector.get("name").getAsString());
            }
            assertEquals(targetX, x);
            assertEquals(targetY, y);
        }
    }

    @Test
    void rejectsOnlyCoordinatesBelowTheOriginalHalfTileBoundary() {
        assertTrue(SnowStormMath.worldToTile(-1601).isEmpty());
        assertFalse(SnowStormMath.worldToTile(-1600).isEmpty());
    }

    private static JsonObject fixture(String name) throws IOException {
        Path path = Path.of("tools", "snowstorm", "fixtures", name);
        try (var reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static List<Integer> integers(JsonObject value, String name) {
        List<Integer> result = new ArrayList<>();
        for (JsonElement element : value.getAsJsonArray(name)) {
            result.add(element.getAsInt());
        }
        return List.copyOf(result);
    }

    private static int objectType(String name) {
        return switch (name) {
            case "avatar" -> 0;
            case "snowball" -> 1;
            case "machine" -> 2;
            default -> throw new IllegalArgumentException("Unknown SnowStorm fixture object type " + name);
        };
    }
}

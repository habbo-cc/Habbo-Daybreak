package com.eu.habbo.habbohotel.games.snowstorm;

import com.eu.habbo.habbohotel.games.snowstorm.data.SnowStormGeneratedRuntimeTables;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SnowStormProjectileMathTest {
    @Test
    void generatedTablesRetainTheFrozenLingoHashesAndDimensions() throws IOException {
        JsonObject sources = fixture().getAsJsonObject("sources");
        assertEquals(sources.get("velocityTableLingoSha256").getAsString(),
                SnowStormGeneratedRuntimeTables.VELOCITY_LINGO_SHA256);
        assertEquals(sources.get("squareRootLingoSha256").getAsString(),
                SnowStormGeneratedRuntimeTables.SQUARE_ROOT_LINGO_SHA256);
        assertEquals(sources.get("componentToAngleLingoSha256").getAsString(),
                SnowStormGeneratedRuntimeTables.COMPONENT_ANGLE_LINGO_SHA256);
        assertEquals(360, SnowStormGeneratedRuntimeTables.velocityTableSize());
        assertEquals(256, SnowStormGeneratedRuntimeTables.squareRootTableSize());
        assertEquals(256, SnowStormGeneratedRuntimeTables.componentAngleTableSize());
    }

    @Test
    void matchesAllQuickShortAndLongProjectilePaths() throws IOException {
        JsonObject vectors = fixture().getAsJsonObject("projectileVectors");
        assertCases(vectors.getAsJsonArray("cases"));
        assertCases(vectors.getAsJsonArray("obstacleBoundaryCases"));
    }

    @Test
    void matchesEveryStrictCollisionBoundary() throws IOException {
        JsonObject boundaries = fixture().getAsJsonObject("collisionBoundaries");
        for (JsonElement element : boundaries.getAsJsonArray("horizontal")) {
            JsonObject vector = element.getAsJsonObject();
            assertEquals(vector.get("collides").getAsBoolean(),
                    SnowStormProjectileMath.circlesCollide(vector.get("distance").getAsInt()));
        }
        for (JsonElement element : boundaries.getAsJsonArray("vertical")) {
            JsonObject vector = element.getAsJsonObject();
            assertEquals(vector.get("collides").getAsBoolean(),
                    SnowStormProjectileMath.collidesWithAvatarHeight(vector.get("snowballZ").getAsInt()));
        }
        for (JsonElement element : boundaries.getAsJsonArray("ground")) {
            JsonObject vector = element.getAsJsonObject();
            assertEquals(vector.get("collides").getAsBoolean(),
                    SnowStormProjectileMath.collidesWithOccupiedHeight(
                            vector.get("snowballZ").getAsInt(), vector.get("occupiedHeight").getAsInt()));
        }
    }

    private static void assertCases(JsonArray cases) {
        for (JsonElement element : cases) {
            JsonObject vector = element.getAsJsonObject();
            JsonArray start = vector.getAsJsonArray("start");
            JsonArray target = vector.getAsJsonArray("target");
            SnowStormProjectileMath.State state = SnowStormProjectileMath.launch(
                    SnowStormTrajectory.fromId(vector.get("trajectory").getAsInt()),
                    start.get(0).getAsInt(), start.get(1).getAsInt(),
                    target.get(0).getAsInt(), target.get(1).getAsInt());
            String name = vector.get("name").getAsString();
            assertEquals(vector.get("direction").getAsInt(), state.direction(), name + " direction");
            assertEquals(vector.get("initialTtl").getAsInt(), state.initialTtl(), name + " TTL");
            assertEquals(vector.get("parabolaOffset").getAsInt(), state.parabolaOffset(), name + " offset");
            for (JsonElement pathElement : vector.getAsJsonArray("path")) {
                JsonObject expected = pathElement.getAsJsonObject();
                SnowStormProjectileMath.Projection projection = SnowStormProjectileMath.project(state);
                SnowStormProjectileMath.Step step = SnowStormProjectileMath.resolve(
                        state, projection, expected.get("occupiedHeight").getAsInt());
                state = step.state();
                int subturn = expected.get("subturn").getAsInt();
                assertEquals(expected.get("worldX").getAsInt(), state.worldX(), name + " x at " + subturn);
                assertEquals(expected.get("worldY").getAsInt(), state.worldY(), name + " y at " + subturn);
                assertEquals(expected.get("worldZ").getAsInt(), state.worldZ(), name + " z at " + subturn);
                assertEquals(expected.get("ttl").getAsInt(), state.ttl(), name + " TTL at " + subturn);
                assertEquals(expected.get("removed").getAsBoolean(), state.removed(), name + " removal at " + subturn);
            }
        }
    }

    private static JsonObject fixture() throws IOException {
        Path path = Path.of("tools", "snowstorm", "fixtures", "simulation_fixtures.json");
        try (var reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}

package com.eu.habbo.habbohotel.games.snowstorm;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SnowStormCombatTest {
    @Test
    void matchesFfaEnemyFriendlyAndKnockoutScoringVectors() throws IOException {
        JsonObject fixture;
        try (var reader = Files.newBufferedReader(
                Path.of("tools", "snowstorm", "fixtures", "simulation_fixtures.json"))) {
            fixture = JsonParser.parseReader(reader).getAsJsonObject();
        }
        for (JsonElement element : fixture.getAsJsonArray("scoringVectors")) {
            JsonObject vector = element.getAsJsonObject();
            if (vector.get("sameAvatar").getAsBoolean()) continue;
            int health = vector.has("knockoutScore") ? 1 : 4;
            SnowStormCombat.Resolution result = SnowStormCombat.resolve(
                    vector.get("teamCount").getAsInt(),
                    vector.get("sameTeam").getAsBoolean(),
                    health);
            assertEquals(vector.get("damage").getAsInt(), result.damage(), vector.get("name").getAsString());
            assertEquals(vector.get("hitScore").getAsInt(), result.hitScore(), vector.get("name").getAsString());
            assertEquals(vector.has("knockoutScore") ? vector.get("knockoutScore").getAsInt() : 0,
                    result.knockoutScore(), vector.get("name").getAsString());
        }
    }
}

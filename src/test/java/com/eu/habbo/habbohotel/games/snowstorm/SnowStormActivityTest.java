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

class SnowStormActivityTest {
    @Test
    void matchesTheFrozenActionMatrix() throws IOException {
        for (JsonElement element : stateMachine().getAsJsonArray("actionMatrix")) {
            JsonObject vector = element.getAsJsonObject();
            SnowStormActivityState state = SnowStormActivityState.fromId(vector.get("state").getAsInt());
            assertEquals(vector.get("walk").getAsBoolean(), state.canWalk());
            assertEquals(vector.get("throw").getAsBoolean(), state.canThrow());
            assertEquals(vector.get("make").getAsBoolean(), state.canMake());
            assertEquals(vector.get("avatarCollision").getAsBoolean(), state.avatarCollision());
        }
    }

    @Test
    void matchesCreateCancelStunAndInvincibilityTimelines() throws IOException {
        JsonObject stateMachine = stateMachine();
        SnowStormAvatarActivity.State creating = new SnowStormAvatarActivity.State(
                SnowStormActivityState.CREATING, 20, 4, 2);
        for (JsonElement element : stateMachine.getAsJsonArray("creatingCompletes")) {
            creating = SnowStormAvatarActivity.tick(creating);
            assertState(element.getAsJsonObject(), creating);
        }

        JsonObject cancellation = stateMachine.getAsJsonObject("creatingCancelledByMove");
        JsonObject before = cancellation.getAsJsonObject("before");
        SnowStormAvatarActivity.State cancelled = SnowStormAvatarActivity.cancelCreatingForWalk(
                new SnowStormAvatarActivity.State(
                        SnowStormActivityState.fromId(before.get("activityState").getAsInt()),
                        before.get("activityTimer").getAsInt(), 4, before.get("ammo").getAsInt()));
        JsonObject after = cancellation.getAsJsonObject("after");
        assertEquals(after.get("activityState").getAsInt(), cancelled.activity().id());
        assertEquals(after.get("activityTimer").getAsInt(), cancelled.timer());
        assertEquals(after.get("ammo").getAsInt(), cancelled.ammo());

        JsonObject knockout = stateMachine.getAsJsonObject("knockoutLifecycle");
        JsonObject initial = knockout.getAsJsonObject("onKnockout");
        SnowStormAvatarActivity.State state = SnowStormAvatarActivity.knockOut(
                new SnowStormAvatarActivity.State(SnowStormActivityState.NORMAL, 0, 1, 3));
        assertState(initial, state);
        for (JsonElement element : knockout.getAsJsonArray("timeline")) {
            state = SnowStormAvatarActivity.tick(state);
            assertState(element.getAsJsonObject(), state);
        }
    }

    @Test
    void matchesGameClockThrowCooldown() throws IOException {
        JsonObject cooldown = stateMachine().getAsJsonObject("throwCooldown");
        List<Integer> accepted = new ArrayList<>();
        long last = -1;
        for (JsonElement element : cooldown.getAsJsonArray("attemptSubturns")) {
            int subturn = element.getAsInt();
            if (SnowStormAvatarActivity.acceptsThrowAt(subturn, last)) {
                accepted.add(subturn);
                last = subturn;
            }
        }
        List<Integer> expected = new ArrayList<>();
        cooldown.getAsJsonArray("acceptedSubturns").forEach(value -> expected.add(value.getAsInt()));
        assertEquals(expected, accepted);
    }

    private static void assertState(JsonObject expected, SnowStormAvatarActivity.State actual) {
        assertEquals(expected.get("activityState").getAsInt(), actual.activity().id());
        assertEquals(expected.get("activityTimer").getAsInt(), actual.timer());
        assertEquals(expected.get("health").getAsInt(), actual.health());
        assertEquals(expected.get("ammo").getAsInt(), actual.ammo());
    }

    private static JsonObject stateMachine() throws IOException {
        Path path = Path.of("tools", "snowstorm", "fixtures", "simulation_fixtures.json");
        try (var reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("stateMachine");
        }
    }
}

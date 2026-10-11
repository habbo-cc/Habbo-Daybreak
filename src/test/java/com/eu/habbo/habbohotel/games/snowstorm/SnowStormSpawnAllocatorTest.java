package com.eu.habbo.habbohotel.games.snowstorm;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SnowStormSpawnAllocatorTest {
    @Test
    void matchesEveryFrozenTwelvePlayerFixture() throws IOException {
        SnowStormMapCatalog catalog = SnowStormMapCatalog.loadGenerated();
        Path fixturePath = Path.of("tools", "snowstorm", "fixtures", "spawn_allocation_fixtures.json");
        JsonObject fixtureRoot;
        try (var reader = Files.newBufferedReader(fixturePath)) {
            fixtureRoot = JsonParser.parseReader(reader).getAsJsonObject();
        }

        for (JsonElement element : fixtureRoot.getAsJsonArray("fixtures")) {
            JsonObject fixture = element.getAsJsonObject();
            int mapId = fixture.get("mapId").getAsInt();
            int seed = (int) fixture.get("seed").getAsLong();
            SnowStormSpawnAllocator.Allocation allocation = SnowStormSpawnAllocator.allocate(
                    catalog.get(mapId), 1, SnowStormSpawnAllocator.MAXIMUM_PLAYERS, seed);

            assertEquals(fixture.get("attempt").getAsInt(), allocation.attempt(), "map " + mapId + " attempt");
            assertEquals(fixture.get("candidateCount").getAsInt(), allocation.candidateCount(),
                    "map " + mapId + " candidate count");
            assertEquals(fixture.getAsJsonArray("positions").size(), allocation.positions().size());
            for (int index = 0; index < allocation.positions().size(); index++) {
                JsonObject expected = fixture.getAsJsonArray("positions").get(index).getAsJsonObject();
                SnowStormSpawnAllocator.Candidate actual = allocation.positions().get(index);
                assertEquals(expected.get("clusterId").getAsInt(), actual.clusterId(),
                        "map " + mapId + " cluster at " + index);
                assertEquals(expected.get("x").getAsInt(), actual.x(), "map " + mapId + " x at " + index);
                assertEquals(expected.get("y").getAsInt(), actual.y(), "map " + mapId + " y at " + index);
                assertEquals(expected.get("minimumSeparation").getAsInt(), actual.minimumSeparation(),
                        "map " + mapId + " separation at " + index);
            }
        }
    }

    @Test
    void failsCleanlyWhenNoLegalCandidatesExist() {
        SnowStormMapDefinition source = SnowStormMapCatalog.loadGenerated().get(1);
        SnowStormMapDefinition blocked = new SnowStormMapDefinition(
                source.mapId(), source.revision(), source.width(), source.height(), source.worldUnitsPerTile(),
                source.tileOriginX(), source.tileOriginY(), source.worldOriginX(), source.worldOriginY(),
                source.worldOriginZ(), source.baseRoomModel(), source.backgroundMembers(), source.floorRows(),
                Collections.nCopies(source.height(), "x".repeat(source.width())), source.items(), source.machines(),
                source.spawnClusters(), source.checksum(), source.sha256());

        assertThrows(IllegalStateException.class,
                () -> SnowStormSpawnAllocator.allocate(blocked, 1, 12, 1));
    }

    @Test
    void supportsAllOriginalTeamCountsAtMaximumCapacity() {
        SnowStormMapCatalog catalog = SnowStormMapCatalog.loadGenerated();
        for (SnowStormMapDefinition map : catalog.maps()) {
            for (int teamCount = 1; teamCount <= 4; teamCount++) {
                assertEquals(12, SnowStormSpawnAllocator.allocate(
                        map, teamCount, 12, 0x53534E31 ^ map.checksum()).positions().size());
            }
        }
    }
}

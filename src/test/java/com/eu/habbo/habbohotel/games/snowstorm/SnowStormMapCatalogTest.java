package com.eu.habbo.habbohotel.games.snowstorm;

import com.eu.habbo.habbohotel.games.snowstorm.data.SnowStormGeneratedMapData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnowStormMapCatalogTest {
    @Test
    void loadsAndValidatesEveryGeneratedMap() {
        SnowStormMapCatalog catalog = SnowStormMapCatalog.loadGenerated();

        assertEquals(7, catalog.maps().size());
        assertEquals(Set.of(1, 2, 3, 4, 5, 6, 7),
                catalog.maps().stream().map(SnowStormMapDefinition::mapId).collect(Collectors.toSet()));
        assertEquals(List.of(88, 48, 163, 39, 335, 104, 51),
                catalog.maps().stream().map(map -> map.items().size()).toList());
        assertEquals(List.of(1, 1, 3, 0, 0, 0, 0),
                catalog.maps().stream().map(map -> map.machines().size()).toList());
        assertEquals(List.of(3, 3, 3, 1, 3, 5, 5),
                catalog.maps().stream().map(map -> map.spawnClusters().size()).toList());
        assertEquals(SnowStormGeneratedMapData.REVISION,
                catalog.maps().stream().mapToInt(SnowStormMapDefinition::revision).distinct().findFirst().orElseThrow());
    }

    @Test
    void keepsWalkingAndProjectileCollisionGeometryIndependent() {
        SnowStormMapDefinition map = SnowStormMapCatalog.loadGenerated().get(1);
        SnowStormMapItem tree = map.items().stream()
                .filter(item -> item.member().equals("sw_tree1"))
                .findFirst()
                .orElseThrow();

        assertEquals(3, tree.walkingHeight());
        assertEquals(4600, tree.projectileCollisionHeight());
        assertFalse(map.isWalkable(tree.footprint().getFirst()));
        assertEquals(4600, map.projectileCollisionHeight(tree.footprint().getFirst()));

        SnowStormMachineDefinition machine = map.machines().getFirst();
        assertTrue(map.isWalkable(machine.collectionTile()));
        assertEquals(2400, map.projectileCollisionHeight(machine.collisionTiles().getFirst()));
    }

    @Test
    void rejectsUnknownMapIds() {
        SnowStormMapCatalog catalog = SnowStormMapCatalog.loadGenerated();
        assertThrows(IllegalArgumentException.class, () -> catalog.get(0));
        assertThrows(IllegalArgumentException.class, () -> catalog.get(8));
    }
}

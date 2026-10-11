package com.eu.habbo.habbohotel.games.snowstorm;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SnowStormRepositoryTest {
    private static final Gson GSON = new Gson();

    @Test
    void acceptsACompleteRelationalProjectionOfTheGeneratedCatalog() {
        SnowStormMapCatalog catalog = SnowStormMapCatalog.loadGenerated();
        Snapshot snapshot = Snapshot.from(catalog);

        assertDoesNotThrow(() -> new SnowStormRepository().validate(connectionFor(snapshot), catalog));
    }

    @Test
    void rejectsAStaleMapChecksumAtStartup() {
        SnowStormMapCatalog catalog = SnowStormMapCatalog.loadGenerated();
        Snapshot snapshot = Snapshot.from(catalog);
        snapshot.maps.getFirst().put("checksum", catalog.get(1).checksum() + 1);

        SQLException exception = assertThrows(SQLException.class,
                () -> new SnowStormRepository().validate(connectionFor(snapshot), catalog));
        assertTrue(exception.getMessage().contains("checksum differs"));
    }

    private static Connection connectionFor(Snapshot snapshot) throws SQLException {
        Connection connection = mock(Connection.class);
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            List<Map<String, Object>> rows;
            if (sql.contains("FROM snowstorm_maps")) {
                rows = snapshot.maps;
            } else if (sql.contains("FROM snowstorm_map_items")) {
                rows = snapshot.items;
            } else if (sql.contains("FROM snowstorm_map_machines")) {
                rows = snapshot.machines;
            } else if (sql.contains("FROM snowstorm_map_spawns")) {
                rows = snapshot.spawns;
            } else {
                throw new AssertionError("Unexpected SnowStorm SQL: " + sql);
            }
            PreparedStatement statement = mock(PreparedStatement.class);
            ResultSet resultSet = resultSet(rows);
            when(statement.executeQuery()).thenReturn(resultSet);
            return statement;
        }).when(connection).prepareStatement(anyString());
        return connection;
    }

    private static ResultSet resultSet(List<Map<String, Object>> rows) throws SQLException {
        ResultSet result = mock(ResultSet.class);
        AtomicInteger index = new AtomicInteger(-1);
        when(result.next()).thenAnswer(ignored -> index.incrementAndGet() < rows.size());
        when(result.getInt(anyString())).thenAnswer(invocation ->
                ((Number) rows.get(index.get()).get(invocation.getArgument(0, String.class))).intValue());
        when(result.getString(anyString())).thenAnswer(invocation -> {
            Object value = rows.get(index.get()).get(invocation.getArgument(0, String.class));
            return value == null ? null : value.toString();
        });
        when(result.getBoolean(anyString())).thenAnswer(invocation -> {
            Object value = rows.get(index.get()).get(invocation.getArgument(0, String.class));
            return value instanceof Boolean bool ? bool : ((Number) value).intValue() != 0;
        });
        return result;
    }

    private record Snapshot(
            List<Map<String, Object>> maps,
            List<Map<String, Object>> items,
            List<Map<String, Object>> machines,
            List<Map<String, Object>> spawns) {

        static Snapshot from(SnowStormMapCatalog catalog) {
            List<Map<String, Object>> maps = new ArrayList<>();
            List<Map<String, Object>> items = new ArrayList<>();
            List<Map<String, Object>> machines = new ArrayList<>();
            List<Map<String, Object>> spawns = new ArrayList<>();
            for (SnowStormMapDefinition map : catalog.maps()) {
                maps.add(row(
                        "map_id", map.mapId(), "revision", map.revision(), "width", map.width(),
                        "height", map.height(), "world_units_per_tile", map.worldUnitsPerTile(),
                        "floor_map", String.join("|", map.floorRows()),
                        "collision_map", String.join("|", map.collisionRows()),
                        "checksum", map.checksum(), "sha256", map.sha256()));
                for (SnowStormMapItem item : map.items()) {
                    items.add(row(
                            "map_id", map.mapId(), "item_id", item.id(), "member_name", item.member(),
                            "asset_name", item.assetName(), "resource_id", item.resourceId(), "cast_id", item.castId(),
                            "tile_x", item.tileX(), "tile_y", item.tileY(), "world_x", item.worldX(),
                            "world_y", item.worldY(), "world_z", item.worldZ(), "direction", item.direction(),
                            "flip_x", item.flipX(), "source_loc_z", item.sourceLocZ(), "draw_order", item.drawOrder(),
                            "walking_height", item.walkingHeight(), "projectile_height", item.projectileCollisionHeight(),
                            "footprint_json", pointsJson(item.footprint()), "palette_id", item.paletteId(),
                            "palette_name", item.paletteName(), "registration_x", item.registrationX(),
                            "registration_y", item.registrationY()));
                }
                for (SnowStormMachineDefinition machine : map.machines()) {
                    machines.add(row(
                            "map_id", map.mapId(), "machine_id", machine.id(), "tile_x", machine.tileX(),
                            "tile_y", machine.tileY(), "world_x", machine.worldX(), "world_y", machine.worldY(),
                            "world_z", machine.worldZ(), "collection_tile_x", machine.collectionTileX(),
                            "collection_tile_y", machine.collectionTileY(), "direction", machine.direction(),
                            "collision_tiles_json", pointsJson(machine.collisionTiles()),
                            "collision_radius", machine.collisionRadius(), "collision_height", machine.collisionHeight(),
                            "initial_stock", machine.initialStock(), "maximum_stock", machine.maximumStock(),
                            "refill_interval_ms", machine.refillIntervalMs(),
                            "transfer_interval_ms", machine.transferIntervalMs()));
                }
                for (SnowStormSpawnCluster spawn : map.spawnClusters()) {
                    spawns.add(row(
                            "map_id", map.mapId(), "spawn_id", spawn.id(), "center_x", spawn.centerX(),
                            "center_y", spawn.centerY(), "radius", spawn.radius(),
                            "minimum_separation", spawn.minimumSeparation(),
                            "team_count_mask", spawn.applicableTeamCountMask(),
                            "fallback_rule", spawn.fallbackRule()));
                }
            }
            return new Snapshot(maps, items, machines, spawns);
        }
    }

    private static String pointsJson(List<SnowStormTile> points) {
        return GSON.toJson(points.stream().map(point -> List.of(point.x(), point.y())).toList());
    }

    private static Map<String, Object> row(Object... values) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            row.put((String) values[index], values[index + 1]);
        }
        return row;
    }
}

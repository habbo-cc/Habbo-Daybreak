package com.eu.habbo.habbohotel.games.snowstorm;

import com.eu.habbo.habbohotel.games.snowstorm.data.SnowStormGeneratedMapData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.CRC32;

/** Loads and proves the immutable seven-map runtime dataset generated from the r26 evidence. */
public final class SnowStormMapCatalog {
    public static final int MAP_COUNT = 7;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final String SPAWN_FALLBACK =
            "bounded-64-attempt-stable-farthest-legal-search-or-fail-start";
    private final Map<Integer, SnowStormMapDefinition> maps;

    private SnowStormMapCatalog(Map<Integer, SnowStormMapDefinition> maps) {
        this.maps = Map.copyOf(maps);
    }

    public static SnowStormMapCatalog loadGenerated() {
        JsonObject root = JsonParser.parseString(SnowStormGeneratedMapData.jsonData()).getAsJsonObject();
        require(integer(root, "schemaVersion") == SnowStormGeneratedMapData.SCHEMA_VERSION,
                "generated schema version differs from its Java header");
        require(integer(root, "revision") == SnowStormGeneratedMapData.REVISION,
                "generated revision differs from its Java header");
        require(integer(root, "canonicalChecksum") == SnowStormGeneratedMapData.CANONICAL_CHECKSUM,
                "generated canonical checksum differs from its Java header");
        require(string(root, "canonicalSha256").equals(SnowStormGeneratedMapData.CANONICAL_SHA256),
                "generated canonical SHA-256 differs from its Java header");

        JsonArray sourceMaps = array(root, "maps");
        require(sourceMaps.size() == MAP_COUNT, "expected exactly seven SnowStorm maps");
        Map<Integer, SnowStormMapDefinition> definitions = new LinkedHashMap<>();
        for (JsonElement sourceMap : sourceMaps) {
            JsonObject record = sourceMap.getAsJsonObject();
            validateMapHash(record);
            SnowStormMapDefinition definition = parseMap(record);
            require(definitions.put(definition.mapId(), definition) == null,
                    "duplicate map id " + definition.mapId());
        }
        require(definitions.keySet().equals(Set.of(1, 2, 3, 4, 5, 6, 7)),
                "map ids must be the complete range 1..7");
        validateCanonicalHash(root, sourceMaps);

        SnowStormMapCatalog catalog = new SnowStormMapCatalog(definitions);
        catalog.validateSpawnCapacity();
        return catalog;
    }

    public SnowStormMapDefinition get(int mapId) {
        SnowStormMapDefinition map = maps.get(mapId);
        if (map == null) {
            throw new IllegalArgumentException("Unknown SnowStorm map " + mapId);
        }
        return map;
    }

    public List<SnowStormMapDefinition> maps() {
        return maps.values().stream().sorted(Comparator.comparingInt(SnowStormMapDefinition::mapId)).toList();
    }

    private void validateSpawnCapacity() {
        for (SnowStormMapDefinition map : maps()) {
            int seed = 0x53534E31 ^ map.checksum();
            for (int teamCount = 1; teamCount <= 4; teamCount++) {
                SnowStormSpawnAllocator.allocate(
                        map, teamCount, SnowStormSpawnAllocator.MAXIMUM_PLAYERS, seed);
            }
        }
    }

    private static SnowStormMapDefinition parseMap(JsonObject record) {
        int mapId = integer(record, "mapId");
        int width = integer(record, "width");
        int height = integer(record, "height");
        int worldUnits = integer(record, "worldUnitsPerTile");
        require(mapId >= 1 && mapId <= MAP_COUNT, "map id is outside 1..7");
        require(integer(record, "revision") == SnowStormGeneratedMapData.REVISION,
                "map " + mapId + " has the wrong revision");
        require(width > 0 && height > 0, "map " + mapId + " has invalid dimensions");
        require(worldUnits == 3200, "map " + mapId + " must use 3200 world units per tile");

        List<String> floorRows = strings(record, "floorRows");
        List<String> collisionRows = strings(record, "collisionRows");
        validateRows(mapId, width, height, floorRows, collisionRows);

        JsonObject tileOrigin = object(record, "tileOrigin");
        JsonObject worldOrigin = object(record, "worldOrigin");
        List<SnowStormMapItem> items = parseItems(mapId, record, width, height, worldUnits, collisionRows);
        List<SnowStormMachineDefinition> machines =
                parseMachines(mapId, record, width, height, worldUnits, collisionRows);
        List<SnowStormSpawnCluster> spawns = parseSpawns(mapId, record, width, height);

        return new SnowStormMapDefinition(
                mapId,
                integer(record, "revision"),
                width,
                height,
                worldUnits,
                integer(tileOrigin, "x"),
                integer(tileOrigin, "y"),
                integer(worldOrigin, "x"),
                integer(worldOrigin, "y"),
                integer(worldOrigin, "z"),
                string(record, "baseRoomModel"),
                strings(record, "backgroundMembers"),
                floorRows,
                collisionRows,
                items,
                machines,
                spawns,
                integer(record, "checksum"),
                string(record, "sha256"));
    }

    private static List<SnowStormMapItem> parseItems(
            int mapId,
            JsonObject map,
            int width,
            int height,
            int worldUnits,
            List<String> collisionRows) {
        List<SnowStormMapItem> items = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Set<Integer> drawOrders = new TreeSet<>();
        for (JsonElement element : array(map, "items")) {
            JsonObject item = element.getAsJsonObject();
            String id = string(item, "id");
            require(ids.add(id), "map " + mapId + " has duplicate item " + id);
            int drawOrder = integer(item, "drawOrder");
            require(drawOrders.add(drawOrder), "map " + mapId + " has duplicate draw order " + drawOrder);
            int tileX = integer(item, "tileX");
            int tileY = integer(item, "tileY");
            require(inBounds(tileX, tileY, width, height), "map " + mapId + " item " + id + " is outside the map");
            require(integer(item, "worldX") == tileX * worldUnits
                            && integer(item, "worldY") == tileY * worldUnits,
                    "map " + mapId + " item " + id + " has inconsistent world coordinates");
            int walkingHeight = integer(item, "walkingHeight");
            int projectileHeight = integer(item, "projectileCollisionHeight");
            require(walkingHeight >= 0 && projectileHeight >= 0,
                    "map " + mapId + " item " + id + " has a negative collision height");
            List<SnowStormTile> footprint = points(item, "footprintTiles");
            require(!footprint.isEmpty(), "map " + mapId + " item " + id + " has no footprint");
            for (SnowStormTile tile : footprint) {
                require(inBounds(tile.x(), tile.y(), width, height),
                        "map " + mapId + " item " + id + " footprint is outside the map");
                if (bool(item, "blocksWalking")) {
                    require(collisionRows.get(tile.y()).charAt(tile.x()) == 'x',
                            "map " + mapId + " blocking item " + id + " is absent from collision rows");
                }
            }
            JsonObject asset = object(item, "castBitmap");
            JsonObject registration = object(asset, "registrationPoint");
            require(integer(asset, "resourceId") > 0 && integer(asset, "castId") > 0
                            && !string(asset, "asset").isBlank(),
                    "map " + mapId + " item " + id + " has incomplete r26 asset metadata");
            items.add(new SnowStormMapItem(
                    id,
                    string(item, "member"),
                    string(asset, "asset"),
                    integer(asset, "resourceId"),
                    integer(asset, "castId"),
                    tileX,
                    tileY,
                    integer(item, "worldX"),
                    integer(item, "worldY"),
                    integer(item, "worldZ"),
                    integer(item, "direction"),
                    bool(item, "flipX"),
                    integer(item, "sourceLocZ"),
                    drawOrder,
                    walkingHeight,
                    projectileHeight,
                    bool(item, "blocksWalking"),
                    footprint,
                    integer(asset, "paletteId"),
                    string(asset, "paletteName"),
                    integer(registration, "x"),
                    integer(registration, "y")));
        }
        require(drawOrders.equals(expectedSequence(items.size())),
                "map " + mapId + " draw orders are not a complete stable sequence");
        return List.copyOf(items);
    }

    private static List<SnowStormMachineDefinition> parseMachines(
            int mapId,
            JsonObject map,
            int width,
            int height,
            int worldUnits,
            List<String> collisionRows) {
        List<SnowStormMachineDefinition> machines = new ArrayList<>();
        Set<Integer> ids = new HashSet<>();
        for (JsonElement element : array(map, "machines")) {
            JsonObject machine = element.getAsJsonObject();
            int id = integer(machine, "id");
            require(ids.add(id), "map " + mapId + " has duplicate machine " + id);
            int tileX = integer(machine, "tileX");
            int tileY = integer(machine, "tileY");
            require(integer(machine, "worldX") == tileX * worldUnits
                            && integer(machine, "worldY") == tileY * worldUnits,
                    "map " + mapId + " machine " + id + " has inconsistent world coordinates");
            List<SnowStormTile> collisionTiles = points(machine, "collisionTiles");
            require(!collisionTiles.isEmpty(), "map " + mapId + " machine " + id + " has no collision footprint");
            for (SnowStormTile tile : collisionTiles) {
                require(inBounds(tile.x(), tile.y(), width, height)
                                && collisionRows.get(tile.y()).charAt(tile.x()) == 'x',
                        "map " + mapId + " machine " + id + " has an invalid collision tile");
            }
            int collectionX = integer(machine, "collectionTileX");
            int collectionY = integer(machine, "collectionTileY");
            require(inBounds(collectionX, collectionY, width, height)
                            && collisionRows.get(collectionY).charAt(collectionX) != 'x',
                    "map " + mapId + " machine " + id + " has an unusable collection tile");
            int initialStock = integer(machine, "initialStock");
            int maximumStock = integer(machine, "maximumStock");
            require(initialStock >= 0 && maximumStock > 0 && initialStock <= maximumStock,
                    "map " + mapId + " machine " + id + " has invalid stock bounds");
            require(integer(machine, "collisionRadius") > 0 && integer(machine, "collisionHeight") > 0,
                    "map " + mapId + " machine " + id + " has invalid independent collision geometry");
            require(integer(machine, "refillIntervalMs") > 0 && integer(machine, "transferIntervalMs") > 0,
                    "map " + mapId + " machine " + id + " has invalid cadence");
            machines.add(new SnowStormMachineDefinition(
                    id,
                    tileX,
                    tileY,
                    integer(machine, "worldX"),
                    integer(machine, "worldY"),
                    integer(machine, "worldZ"),
                    collectionX,
                    collectionY,
                    integer(machine, "direction"),
                    collisionTiles,
                    integer(machine, "collisionRadius"),
                    integer(machine, "collisionHeight"),
                    initialStock,
                    maximumStock,
                    integer(machine, "refillIntervalMs"),
                    integer(machine, "transferIntervalMs"),
                    bool(machine, "immediateRefillOnCollection"),
                    bool(machine, "immediateTransferOnCollection"),
                    string(machine, "arbitration")));
        }
        return List.copyOf(machines);
    }

    private static List<SnowStormSpawnCluster> parseSpawns(
            int mapId, JsonObject map, int width, int height) {
        List<SnowStormSpawnCluster> spawns = new ArrayList<>();
        Set<Integer> ids = new HashSet<>();
        for (JsonElement element : array(map, "spawnClusters")) {
            JsonObject spawn = element.getAsJsonObject();
            int id = integer(spawn, "id");
            require(ids.add(id), "map " + mapId + " has duplicate spawn cluster " + id);
            int centerX = integer(spawn, "centerX");
            int centerY = integer(spawn, "centerY");
            require(inBounds(centerX, centerY, width, height),
                    "map " + mapId + " spawn cluster " + id + " has an out-of-map centre");
            int radius = integer(spawn, "radius");
            int separation = integer(spawn, "minimumSeparation");
            require(radius >= 0 && separation >= 0,
                    "map " + mapId + " spawn cluster " + id + " has invalid geometry");
            int mask = 0;
            for (JsonElement count : array(spawn, "applicableTeamCounts")) {
                int teamCount = count.getAsInt();
                require(teamCount >= 1 && teamCount <= 4,
                        "map " + mapId + " spawn cluster " + id + " has invalid team applicability");
                mask |= 1 << (teamCount - 1);
            }
            require(mask != 0, "map " + mapId + " spawn cluster " + id + " applies to no team count");
            require(string(spawn, "fallback").equals(SPAWN_FALLBACK),
                    "map " + mapId + " spawn cluster " + id + " has an unbounded fallback rule");
            spawns.add(new SnowStormSpawnCluster(
                    id,
                    centerX,
                    centerY,
                    radius,
                    separation,
                    mask,
                    string(spawn, "teamAssignment"),
                    string(spawn, "fallback")));
        }
        require(!spawns.isEmpty(), "map " + mapId + " has no spawn clusters");
        return List.copyOf(spawns);
    }

    private static void validateRows(
            int mapId,
            int width,
            int height,
            List<String> floorRows,
            List<String> collisionRows) {
        require(floorRows.size() == height && collisionRows.size() == height,
                "map " + mapId + " row count differs from its height");
        for (int y = 0; y < height; y++) {
            String floor = floorRows.get(y);
            String collision = collisionRows.get(y);
            require(floor.length() == width && collision.length() == width,
                    "map " + mapId + " row " + y + " differs from its width");
            for (int x = 0; x < width; x++) {
                require((floor.charAt(x) == '0' || floor.charAt(x) == 'x')
                                && (collision.charAt(x) == '0' || collision.charAt(x) == 'x'),
                        "map " + mapId + " contains an invalid tile marker");
                require(floor.charAt(x) != 'x' || collision.charAt(x) == 'x',
                        "map " + mapId + " exposes an off-floor tile as walkable");
            }
        }
    }

    private static void validateMapHash(JsonObject map) {
        int expectedChecksum = integer(map, "checksum");
        String expectedSha = string(map, "sha256");
        JsonObject payload = map.deepCopy();
        payload.remove("checksum");
        payload.remove("sha256");
        byte[] canonical = canonicalJson(payload).getBytes(StandardCharsets.UTF_8);
        require(crc32(canonical) == expectedChecksum,
                "map " + integer(map, "mapId") + " checksum does not cover its runtime data");
        require(sha256(canonical).equals(expectedSha),
                "map " + integer(map, "mapId") + " SHA-256 does not cover its runtime data");
    }

    private static void validateCanonicalHash(JsonObject root, JsonArray maps) {
        JsonObject payload = new JsonObject();
        payload.addProperty("schemaVersion", integer(root, "schemaVersion"));
        payload.addProperty("revision", integer(root, "revision"));
        JsonArray strippedMaps = new JsonArray();
        for (JsonElement element : maps) {
            JsonObject map = element.getAsJsonObject().deepCopy();
            map.remove("checksum");
            map.remove("sha256");
            strippedMaps.add(map);
        }
        payload.add("maps", strippedMaps);
        byte[] canonical = canonicalJson(payload).getBytes(StandardCharsets.UTF_8);
        require(crc32(canonical) == SnowStormGeneratedMapData.CANONICAL_CHECKSUM,
                "canonical checksum does not cover all seven generated maps");
        require(sha256(canonical).equals(SnowStormGeneratedMapData.CANONICAL_SHA256),
                "canonical SHA-256 does not cover all seven generated maps");
    }

    static String canonicalJson(JsonElement element) {
        if (element.isJsonObject()) {
            StringBuilder result = new StringBuilder("{");
            boolean first = true;
            for (String key : element.getAsJsonObject().keySet().stream().sorted().toList()) {
                if (!first) {
                    result.append(',');
                }
                first = false;
                result.append(GSON.toJson(key)).append(':')
                        .append(canonicalJson(element.getAsJsonObject().get(key)));
            }
            return result.append('}').toString();
        }
        if (element.isJsonArray()) {
            StringBuilder result = new StringBuilder("[");
            for (int index = 0; index < element.getAsJsonArray().size(); index++) {
                if (index > 0) {
                    result.append(',');
                }
                result.append(canonicalJson(element.getAsJsonArray().get(index)));
            }
            return result.append(']').toString();
        }
        return GSON.toJson(element);
    }

    private static int crc32(byte[] value) {
        CRC32 crc = new CRC32();
        crc.update(value);
        return (int) crc.getValue();
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static Set<Integer> expectedSequence(int size) {
        Set<Integer> result = new TreeSet<>();
        for (int value = 0; value < size; value++) {
            result.add(value);
        }
        return result;
    }

    private static boolean inBounds(int x, int y, int width, int height) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    private static JsonObject object(JsonObject value, String name) {
        JsonElement element = value.get(name);
        require(element != null && element.isJsonObject(), "missing object " + name);
        return element.getAsJsonObject();
    }

    private static JsonArray array(JsonObject value, String name) {
        JsonElement element = value.get(name);
        require(element != null && element.isJsonArray(), "missing array " + name);
        return element.getAsJsonArray();
    }

    private static String string(JsonObject value, String name) {
        JsonElement element = value.get(name);
        require(element != null && element.isJsonPrimitive(), "missing string " + name);
        return element.getAsString();
    }

    private static int integer(JsonObject value, String name) {
        JsonElement element = value.get(name);
        require(element != null && element.isJsonPrimitive(), "missing integer " + name);
        return element.getAsInt();
    }

    private static boolean bool(JsonObject value, String name) {
        JsonElement element = value.get(name);
        require(element != null && element.isJsonPrimitive(), "missing boolean " + name);
        return element.getAsBoolean();
    }

    private static List<String> strings(JsonObject value, String name) {
        List<String> result = new ArrayList<>();
        for (JsonElement element : array(value, name)) {
            result.add(element.getAsString());
        }
        return List.copyOf(result);
    }

    private static List<SnowStormTile> points(JsonObject value, String name) {
        List<SnowStormTile> result = new ArrayList<>();
        for (JsonElement element : array(value, name)) {
            JsonArray point = element.getAsJsonArray();
            require(point.size() == 2, "invalid point in " + name);
            result.add(new SnowStormTile(point.get(0).getAsInt(), point.get(1).getAsInt()));
        }
        return List.copyOf(result);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Invalid SnowStorm map data: " + message);
        }
    }
}

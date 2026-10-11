#!/usr/bin/env python3
"""Generate SQL, AS3 and Java immutable map data from the canonical manifest."""

from __future__ import annotations

import argparse
import base64
import gzip
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parent
REPOSITORY = ROOT.parent.parent
MANIFEST = ROOT / "maps" / "snowstorm_maps.json"
GENERATED = ROOT / "generated"
SQL_FRAGMENT = GENERATED / "snowstorm_map_seed.sql"
AS3_OUTPUT = GENERATED / "SnowStormGeneratedMapData.as"
CLIENT_REPOSITORY = REPOSITORY.parent.parent / "client" / "habbo-client-clean"
CLIENT_AS3_OUTPUT = (
    CLIENT_REPOSITORY
    / "src" / "com" / "sulake" / "habbo" / "snowstorm" / "data"
    / "SnowStormGeneratedMapData.as"
)
JAVA_OUTPUT = (
    REPOSITORY
    / "src" / "main" / "java" / "com" / "eu" / "habbo" / "habbohotel"
    / "games" / "snowstorm" / "data" / "SnowStormGeneratedMapData.java"
)
STANDALONE_SQL = REPOSITORY / "sqlupdates" / "snowstorm.sql"
PRODUCTION_ONE_OFF = REPOSITORY / "sqlupdates" / "ngh_prod_post_wired2_one_off.sql"
BEGIN_MARKER = "-- BEGIN GENERATED SNOWSTORM MAP SEED"
END_MARKER = "-- END GENERATED SNOWSTORM MAP SEED"
INSTALL_BEGIN = "-- BEGIN GENERATED SNOWSTORM INSTALL"
INSTALL_END = "-- END GENERATED SNOWSTORM INSTALL"


def sql_text(value: object) -> str:
    return "'" + str(value).replace("\\", "\\\\").replace("'", "''") + "'"


def compact_json(value: object) -> str:
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False)


def value_rows(rows: list[list[object]]) -> str:
    return ",\n".join("  (" + ",".join(sql_text(value) if isinstance(value, str) else str(value) for value in row) + ")" for row in rows)


def runtime_manifest(source: dict[str, object]) -> dict[str, object]:
    maps: list[dict[str, object]] = []
    for record in source["maps"]:
        maps.append(
            {
                key: value
                for key, value in record.items()
                if key not in {"renderedReference", "reviewArtifacts"}
            }
        )
    return {
        "schemaVersion": source["schemaVersion"],
        "revision": source["revision"],
        "canonicalChecksum": source["canonicalChecksum"],
        "canonicalSha256": source["canonicalSha256"],
        "maps": maps,
    }


def generate_sql(source: dict[str, object]) -> str:
    maps = source["maps"]
    map_rows: list[list[object]] = []
    item_rows: list[list[object]] = []
    machine_rows: list[list[object]] = []
    spawn_rows: list[list[object]] = []
    for record in maps:
        map_id = int(record["mapId"])
        map_rows.append(
            [
                map_id,
                int(record["revision"]),
                int(record["width"]),
                int(record["height"]),
                int(record["worldUnitsPerTile"]),
                "|".join(record["floorRows"]),
                "|".join(record["collisionRows"]),
                int(record["checksum"]),
                record["sha256"],
                1,
            ]
        )
        for item in record["items"]:
            asset = item["castBitmap"]
            registration = asset["registrationPoint"]
            item_rows.append(
                [
                    map_id,
                    item["id"],
                    item["member"],
                    asset["asset"],
                    int(asset["resourceId"]),
                    int(asset["castId"]),
                    int(item["tileX"]),
                    int(item["tileY"]),
                    int(item["worldX"]),
                    int(item["worldY"]),
                    int(item["worldZ"]),
                    int(item["direction"]),
                    1 if item["flipX"] else 0,
                    int(item["sourceLocZ"]),
                    int(item["drawOrder"]),
                    int(item["walkingHeight"]),
                    int(item["projectileCollisionHeight"]),
                    compact_json(item["footprintTiles"]),
                    int(asset["paletteId"]),
                    asset["paletteName"],
                    int(registration["x"]),
                    int(registration["y"]),
                ]
            )
        for machine in record["machines"]:
            machine_rows.append(
                [
                    map_id,
                    int(machine["id"]),
                    int(machine["tileX"]),
                    int(machine["tileY"]),
                    int(machine["worldX"]),
                    int(machine["worldY"]),
                    int(machine["worldZ"]),
                    int(machine["collectionTileX"]),
                    int(machine["collectionTileY"]),
                    int(machine["direction"]),
                    compact_json(machine["collisionTiles"]),
                    int(machine["collisionRadius"]),
                    int(machine["collisionHeight"]),
                    int(machine["initialStock"]),
                    int(machine["maximumStock"]),
                    int(machine["refillIntervalMs"]),
                    int(machine["transferIntervalMs"]),
                ]
            )
        for spawn in record["spawnClusters"]:
            mask = sum(1 << (int(team_count) - 1) for team_count in spawn["applicableTeamCounts"])
            spawn_rows.append(
                [
                    map_id,
                    int(spawn["id"]),
                    int(spawn["centerX"]),
                    int(spawn["centerY"]),
                    int(spawn["radius"]),
                    int(spawn["minimumSeparation"]),
                    mask,
                    spawn["fallback"],
                ]
            )

    sections = [
        BEGIN_MARKER,
        "-- DO NOT EDIT: generated from tools/snowstorm/maps/snowstorm_maps.json.",
        (
            f"-- canonical revision={int(source['revision'])} checksum={int(source['canonicalChecksum'])} "
            f"sha256={source['canonicalSha256']} maps={len(map_rows)} items={len(item_rows)} "
            f"machines={len(machine_rows)} spawns={len(spawn_rows)}"
        ),
        "START TRANSACTION;",
        "",
        "INSERT INTO `snowstorm_maps`",
        "  (`map_id`,`revision`,`width`,`height`,`world_units_per_tile`,`floor_map`,`collision_map`,`checksum`,`sha256`,`enabled`)",
        "VALUES",
        value_rows(map_rows),
        "ON DUPLICATE KEY UPDATE",
        "  `revision`=VALUES(`revision`), `width`=VALUES(`width`), `height`=VALUES(`height`),",
        "  `world_units_per_tile`=VALUES(`world_units_per_tile`), `floor_map`=VALUES(`floor_map`),",
        "  `collision_map`=VALUES(`collision_map`), `checksum`=VALUES(`checksum`),",
        "  `sha256`=VALUES(`sha256`), `enabled`=1;",
        "",
        "DELETE FROM `snowstorm_map_items` WHERE `map_id` BETWEEN 1 AND 7;",
        "DELETE FROM `snowstorm_map_machines` WHERE `map_id` BETWEEN 1 AND 7;",
        "DELETE FROM `snowstorm_map_spawns` WHERE `map_id` BETWEEN 1 AND 7;",
        "",
        "INSERT INTO `snowstorm_map_items`",
        "  (`map_id`,`item_id`,`member_name`,`asset_name`,`resource_id`,`cast_id`,`tile_x`,`tile_y`,`world_x`,`world_y`,`world_z`,`direction`,`flip_x`,`source_loc_z`,`draw_order`,`walking_height`,`projectile_height`,`footprint_json`,`palette_id`,`palette_name`,`registration_x`,`registration_y`)",
        "VALUES",
        value_rows(item_rows) + ";",
        "",
    ]
    if machine_rows:
        sections.extend(
            [
                "INSERT INTO `snowstorm_map_machines`",
                "  (`map_id`,`machine_id`,`tile_x`,`tile_y`,`world_x`,`world_y`,`world_z`,`collection_tile_x`,`collection_tile_y`,`direction`,`collision_tiles_json`,`collision_radius`,`collision_height`,`initial_stock`,`maximum_stock`,`refill_interval_ms`,`transfer_interval_ms`)",
                "VALUES",
                value_rows(machine_rows) + ";",
                "",
            ]
        )
    sections.extend(
        [
            "INSERT INTO `snowstorm_map_spawns`",
            "  (`map_id`,`spawn_id`,`center_x`,`center_y`,`radius`,`minimum_separation`,`team_count_mask`,`fallback_rule`)",
            "VALUES",
            value_rows(spawn_rows) + ";",
            "",
            "COMMIT;",
            END_MARKER,
        ]
    )
    return "\n".join(sections) + "\n"


def as3_escape(value: str) -> str:
    return value.replace("\\", "\\\\").replace('"', '\\"')


def generate_as3(source: dict[str, object]) -> str:
    payload = compact_json(runtime_manifest(source))
    chunks = [payload[index : index + 24000] for index in range(0, len(payload), 24000)]
    constants = "\n".join(
        f'        private static const PART_{index}:String = "{as3_escape(chunk)}";'
        for index, chunk in enumerate(chunks)
    )
    join_expression = " +\n                ".join(f"PART_{index}" for index in range(len(chunks)))
    return f"""package com.sulake.habbo.snowstorm.data
{{
    public final class SnowStormGeneratedMapData
    {{
        public static const SCHEMA_VERSION:int = {int(source['schemaVersion'])};
        public static const REVISION:int = {int(source['revision'])};
        public static const CANONICAL_CHECKSUM:int = {int(source['canonicalChecksum'])};
        public static const CANONICAL_SHA256:String = \"{source['canonicalSha256']}\";

{constants}

        public static function get jsonData():String
        {{
            return {join_expression};
        }}
    }}
}}
"""


def java_escape(value: str) -> str:
    return value.replace("\\", "\\\\").replace('"', '\\"')


def generate_java(source: dict[str, object]) -> str:
    payload = compact_json(runtime_manifest(source)).encode("utf-8")
    encoded = base64.b64encode(gzip.compress(payload, mtime=0)).decode("ascii")
    chunks = [encoded[index : index + 12000] for index in range(0, len(encoded), 12000)]
    constants = ",\n".join(f'        "{java_escape(chunk)}"' for chunk in chunks)
    return f"""package com.eu.habbo.habbohotel.games.snowstorm.data;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;

/**
 * Immutable runtime map evidence generated from tools/snowstorm/maps/snowstorm_maps.json.
 * Do not edit by hand; run tools/snowstorm/generate_runtime_maps.py.
 */
public final class SnowStormGeneratedMapData {{
    public static final int SCHEMA_VERSION = {int(source['schemaVersion'])};
    public static final int REVISION = {int(source['revision'])};
    public static final int CANONICAL_CHECKSUM = {int(source['canonicalChecksum'])};
    public static final String CANONICAL_SHA256 = "{source['canonicalSha256']}";

    private static final String[] GZIP_BASE64_PARTS = {{
{constants}
    }};

    private SnowStormGeneratedMapData() {{
    }}

    public static String jsonData() {{
        return JsonHolder.VALUE;
    }}

    private static final class JsonHolder {{
        private static final String VALUE = decode();

        private static String decode() {{
            byte[] compressed = Base64.getDecoder().decode(String.join("", GZIP_BASE64_PARTS));
            try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(compressed))) {{
                return new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }} catch (IOException exception) {{
                throw new UncheckedIOException("Unable to decode generated SnowStorm maps", exception);
            }}
        }}
    }}
}}
"""


def replace_generated_block(sql: str, generated: str) -> str:
    begin = sql.find(BEGIN_MARKER)
    end = sql.find(END_MARKER)
    if begin < 0 or end < begin:
        raise RuntimeError("Standalone SQL is missing generated map markers")
    end += len(END_MARKER)
    return sql[:begin] + generated.rstrip("\n") + sql[end:]


def replace_or_append_install(sql: str, standalone: str) -> str:
    block = INSTALL_BEGIN + "\n" + standalone.rstrip("\n") + "\n" + INSTALL_END
    begin = sql.find(INSTALL_BEGIN)
    end = sql.find(INSTALL_END)
    if begin < 0 and end < 0:
        return sql.rstrip("\n") + "\n\n" + block + "\n"
    if begin < 0 or end < begin:
        raise RuntimeError("Production one-off contains an incomplete SnowStorm install block")
    end += len(INSTALL_END)
    return sql[:begin] + block + sql[end:]


def check_file(path: Path, expected: str) -> None:
    if not path.exists():
        raise RuntimeError(f"Missing generated output: {path}")
    if path.read_text(encoding="utf-8") != expected:
        raise RuntimeError(f"Generated output is stale: {path}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="verify outputs without rewriting them")
    args = parser.parse_args()
    source = json.loads(MANIFEST.read_text(encoding="utf-8"))
    if source.get("status") != "canonical-v1-evidence-frozen" or len(source.get("maps", [])) != 7:
        raise RuntimeError("Canonical seven-map manifest is not frozen")
    generated_sql = generate_sql(source)
    generated_as3 = generate_as3(source)
    generated_java = generate_java(source)
    standalone = STANDALONE_SQL.read_text(encoding="utf-8")
    expected_standalone = replace_generated_block(standalone, generated_sql)
    production = PRODUCTION_ONE_OFF.read_text(encoding="utf-8")
    expected_production = replace_or_append_install(production, expected_standalone)
    if args.check:
        check_file(SQL_FRAGMENT, generated_sql)
        check_file(AS3_OUTPUT, generated_as3)
        check_file(CLIENT_AS3_OUTPUT, generated_as3)
        check_file(JAVA_OUTPUT, generated_java)
        check_file(STANDALONE_SQL, expected_standalone)
        check_file(PRODUCTION_ONE_OFF, expected_production)
        print(
            f"PASS: {len(source['maps'])} maps, canonical checksum "
            f"{source['canonicalChecksum']}, SQL/AS3/Java outputs synchronized"
        )
        return

    GENERATED.mkdir(parents=True, exist_ok=True)
    SQL_FRAGMENT.write_text(generated_sql, encoding="utf-8", newline="\n")
    AS3_OUTPUT.write_text(generated_as3, encoding="utf-8", newline="\n")
    CLIENT_AS3_OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    CLIENT_AS3_OUTPUT.write_text(generated_as3, encoding="utf-8", newline="\n")
    JAVA_OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    JAVA_OUTPUT.write_text(generated_java, encoding="utf-8", newline="\n")
    STANDALONE_SQL.write_text(expected_standalone, encoding="utf-8", newline="\n")
    PRODUCTION_ONE_OFF.write_text(expected_production, encoding="utf-8", newline="\n")
    print(SQL_FRAGMENT.resolve())
    print(AS3_OUTPUT.resolve())
    print(CLIENT_AS3_OUTPUT.resolve())
    print(JAVA_OUTPUT.resolve())
    print(STANDALONE_SQL.resolve())
    print(PRODUCTION_ONE_OFF.resolve())


if __name__ == "__main__":
    main()

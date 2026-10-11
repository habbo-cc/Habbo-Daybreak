#!/usr/bin/env python3
"""Verify generated SnowStorm SQL/AS3 map payloads without touching a database."""

from __future__ import annotations

import json
import re

import generate_runtime_maps as generator


def fail(message: str) -> None:
    raise RuntimeError(message)


def main() -> None:
    source = json.loads(generator.MANIFEST.read_text(encoding="utf-8"))
    expected_sql = generator.generate_sql(source)
    actual_fragment = generator.SQL_FRAGMENT.read_text(encoding="utf-8")
    if actual_fragment != expected_sql:
        fail("Generated SQL fragment is stale")

    standalone = generator.STANDALONE_SQL.read_text(encoding="utf-8")
    begin = standalone.find(generator.BEGIN_MARKER)
    end = standalone.find(generator.END_MARKER)
    if begin < 0 or end < begin:
        fail("Standalone SQL seed markers are missing")
    end += len(generator.END_MARKER)
    if standalone[begin:end] != expected_sql.rstrip("\n"):
        fail("Standalone SQL does not contain the exact generated seed")
    if expected_sql.count("START TRANSACTION;") != 1 or expected_sql.count("COMMIT;") != 1:
        fail("Generated map replacement must have exactly one transaction")
    deletes = [expected_sql.find(f"DELETE FROM `{table}`") for table in (
        "snowstorm_map_items", "snowstorm_map_machines", "snowstorm_map_spawns"
    )]
    if any(index < 0 for index in deletes) or not all(index < expected_sql.find("COMMIT;") for index in deletes):
        fail("All replaceable child rows must be deleted inside the map transaction")

    production = generator.PRODUCTION_ONE_OFF.read_text(encoding="utf-8")
    install_begin = production.find(generator.INSTALL_BEGIN)
    install_end = production.find(generator.INSTALL_END)
    if install_begin < 0 or install_end < install_begin:
        fail("Production one-off SnowStorm install markers are missing")
    embedded_install = production[
        install_begin + len(generator.INSTALL_BEGIN) + 1 : install_end
    ].rstrip("\n")
    if embedded_install != standalone.rstrip("\n"):
        fail("Production one-off SnowStorm install is stale")

    as3 = generator.AS3_OUTPUT.read_text(encoding="utf-8")
    if not generator.CLIENT_AS3_OUTPUT.is_file():
        fail("Generated client AS3 map data is missing")
    client_as3 = generator.CLIENT_AS3_OUTPUT.read_text(encoding="utf-8")
    if client_as3 != as3:
        fail("Client AS3 map data differs from the verified generated artifact")
    part_matches = re.findall(r'private static const PART_(\d+):String = "((?:\\.|[^"\\])*)";', as3)
    if not part_matches:
        fail("Generated AS3 contains no JSON parts")
    part_matches.sort(key=lambda value: int(value[0]))
    if [int(index) for index, _ in part_matches] != list(range(len(part_matches))):
        fail("Generated AS3 JSON part indexes are not contiguous")
    decoded = "".join(json.loads('"' + encoded + '"') for _, encoded in part_matches)
    actual_runtime = json.loads(decoded)
    expected_runtime = generator.runtime_manifest(source)
    if actual_runtime != expected_runtime:
        fail("Generated AS3 runtime manifest differs from the canonical source")
    if int(actual_runtime["canonicalChecksum"]) != int(source["canonicalChecksum"]):
        fail("Generated AS3 canonical checksum mismatch")

    print(
        "PASS "
        f"maps={len(source['maps'])} "
        f"items={sum(len(record['items']) for record in source['maps'])} "
        f"machines={sum(len(record['machines']) for record in source['maps'])} "
        f"spawns={sum(len(record['spawnClusters']) for record in source['maps'])} "
        f"checksum={source['canonicalChecksum']} client=exact"
    )


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Generate bounded deterministic SnowStorm spawn-allocation fixtures.

The captured cluster fields are tile-space centre, Euclidean radius and Euclidean minimum
separation. The r26 game-info layouts cap every team configuration at twelve total players; this
allocator therefore proves a legal twelve-player arrangement for all seven canonical maps without
Havana's recursive random retry.
"""

from __future__ import annotations

import hashlib
import json
from math import isqrt
from pathlib import Path


ROOT = Path(__file__).resolve().parent
MAPS_PATH = ROOT / "maps/snowstorm_maps.json"
OUTPUT_PATH = ROOT / "fixtures/spawn_allocation_fixtures.json"
MAX_PLAYERS = 12
MAX_ATTEMPTS = 64
FIXTURE_SEED = 0x53534E31


def unsigned32(value: int) -> int:
    return value & 0xFFFFFFFF


def mix32(value: int) -> int:
    value = unsigned32(value ^ (value >> 16))
    value = unsigned32(value * 0x7FEB352D)
    value = unsigned32(value ^ (value >> 15))
    value = unsigned32(value * 0x846CA68B)
    return unsigned32(value ^ (value >> 16))


def candidate_priority(seed: int, attempt: int, candidate: dict[str, int]) -> int:
    value = unsigned32(seed ^ (attempt * 0x9E3779B9))
    value ^= unsigned32(candidate["clusterId"] * 0x85EBCA6B)
    value ^= unsigned32(candidate["x"] * 0xC2B2AE35)
    value ^= unsigned32(candidate["y"] * 0x27D4EB2F)
    return mix32(value)


def candidates_for(record: dict[str, object]) -> list[dict[str, int]]:
    result: list[dict[str, int]] = []
    seen: set[tuple[int, int]] = set()
    collision = record["collisionRows"]
    for cluster in sorted(record["spawnClusters"], key=lambda value: value["id"]):
        radius = int(cluster["radius"])
        for y in range(int(record["height"])):
            for x in range(int(record["width"])):
                if collision[y][x] == "x":
                    continue
                dx = x - int(cluster["centerX"])
                dy = y - int(cluster["centerY"])
                if isqrt(dx * dx + dy * dy) > radius:
                    continue
                # A tile appearing in more than one cluster is one physical candidate. Prefer the
                # lower cluster id, which is the stable source order used by the manifest.
                if (x, y) in seen:
                    continue
                seen.add((x, y))
                result.append(
                    {
                        "clusterId": int(cluster["id"]),
                        "x": x,
                        "y": y,
                        "minimumSeparation": int(cluster["minimumSeparation"]),
                    }
                )
    return result


def compatible(candidate: dict[str, int], selected: list[dict[str, int]]) -> bool:
    for other in selected:
        dx = candidate["x"] - other["x"]
        dy = candidate["y"] - other["y"]
        minimum = max(candidate["minimumSeparation"], other["minimumSeparation"])
        if isqrt(dx * dx + dy * dy) < minimum:
            return False
    return True


def allocate(record: dict[str, object], count: int, seed: int) -> tuple[int, list[dict[str, int]]]:
    candidates = candidates_for(record)
    best: list[dict[str, int]] = []
    for attempt in range(MAX_ATTEMPTS):
        ordered = sorted(
            candidates,
            key=lambda value: (
                candidate_priority(seed, attempt, value),
                -value["clusterId"],
                -value["y"],
                -value["x"],
            ),
            reverse=True,
        )
        selected: list[dict[str, int]] = []
        while len(selected) < count:
            eligible = [value for value in ordered if value not in selected and compatible(value, selected)]
            if not eligible:
                break
            if not selected:
                chosen = eligible[0]
            else:
                def rank(value: dict[str, int]) -> tuple[int, int, int, int, int]:
                    nearest = min(
                        (value["x"] - other["x"]) ** 2 + (value["y"] - other["y"]) ** 2
                        for other in selected
                    )
                    return (
                        nearest,
                        candidate_priority(seed, attempt, value),
                        -value["clusterId"],
                        -value["y"],
                        -value["x"],
                    )

                chosen = max(eligible, key=rank)
            selected.append(chosen)
        if len(selected) > len(best):
            best = selected
        if len(selected) == count:
            return attempt, selected
    raise ValueError(
        f"Map {record['mapId']} allocated only {len(best)}/{count} players in {MAX_ATTEMPTS} attempts"
    )


def canonical_bytes(value: object) -> bytes:
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode("utf-8")


def main() -> None:
    manifest = json.loads(MAPS_PATH.read_text(encoding="utf-8"))
    fixtures = []
    for record in manifest["maps"]:
        seed = unsigned32(FIXTURE_SEED ^ int(record["checksum"]))
        attempt, selected = allocate(record, MAX_PLAYERS, seed)
        fixtures.append(
            {
                "mapId": int(record["mapId"]),
                "mapChecksum": int(record["checksum"]),
                "seed": seed,
                "attempt": attempt,
                "candidateCount": len(candidates_for(record)),
                "positions": selected,
            }
        )
    output = {
        "schemaVersion": 1,
        "algorithm": {
            "metric": "floor-euclidean-tile-distance",
            "candidateOrder": "cluster-id then y then x; duplicate physical tiles keep lowest cluster id",
            "selection": "seeded first candidate, then farthest legal candidate with stable seeded ties",
            "separation": "maximum of the two source-cluster minimum separations",
            "maximumAttempts": MAX_ATTEMPTS,
            "maximumPlayers": MAX_PLAYERS,
        },
        "mapManifestSha256": hashlib.sha256(MAPS_PATH.read_bytes()).hexdigest().upper(),
        "fixtures": fixtures,
    }
    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT_PATH.write_text(json.dumps(output, indent=2, sort_keys=True) + "\n", encoding="utf-8", newline="\n")
    print(OUTPUT_PATH)
    for fixture in fixtures:
        print(
            f"map {fixture['mapId']}: candidates={fixture['candidateCount']} "
            f"attempt={fixture['attempt']} positions={len(fixture['positions'])}"
        )
    print(hashlib.sha256(OUTPUT_PATH.read_bytes()).hexdigest().upper())


if __name__ == "__main__":
    main()


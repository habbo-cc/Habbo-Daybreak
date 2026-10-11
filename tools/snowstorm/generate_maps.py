#!/usr/bin/env python3
"""Generate the canonical Shockwave SnowStorm map manifest and review overlays.

Havana's captured OBJECTS/spawn/machine files are treated as candidate records. The generator keeps
their raw depth values, replaces Havana's approximate collision heights with r26 variable-index
world heights, and validates the seven 50x50 floor masks before producing any runtime input.
"""

from __future__ import annotations

import hashlib
import json
import re
import shutil
import struct
import zlib
from pathlib import Path

from PIL import Image, ImageDraw


SERVER_ROOT = Path(__file__).resolve().parents[2]
HABBO_ROOT = Path("C:/habbo")
HAVANA_ROOT = HABBO_ROOT / "server/havana"
HAVANA_MAP_ROOT = HAVANA_ROOT / "tools/snowstorm_maps"
HAVANA_MANAGER = (
    HAVANA_ROOT
    / "Havana-Server/src/main/java/org/alexdev/havana/game/games/snowstorm/SnowStormMapsManager.java"
)
R26_PREVIEW_ROOT = HABBO_ROOT / "ccttoswf/output/snowstorm_r26/arena_previews"
R26_RENDERER = HABBO_ROOT / "ccttoswf/render_snowstorm_arenas.py"
R26_ROOM_CAST_ROOT = HABBO_ROOT / "ccttoswf/output/snowstorm_r26/hh_room_snowwar_arena"
R26_ROOM_INVENTORY = R26_ROOM_CAST_ROOT / "hh_room_snowwar_arena_inventory.json"
OUTPUT_ROOT = Path(__file__).resolve().parent / "maps"

WORLD_HEIGHTS = {
    "block_arch1": 2300,
    "block_arch2": 2300,
    "block_arch3": 2300,
    "block_basic": 2300,
    "block_basic2": 4600,
    "block_basic3": 6900,
    "block_small": 1150,
    "obst_duck": 2300,
    "obst_snowman": 4600,
    "sw_tree1": 4600,
    "sw_tree2": 4600,
    "sw_tree3": 4600,
    "sw_tree4": 4600,
    "sw_fence": 2500,
    "block_ice": 2300,
    "block_ice2": 4600,
    "block_arch1b": 6900,
    "block_arch2b": 6900,
    "block_arch3b": 6900,
}

# Logical item tiers recovered from Havana are used only for floor occupancy. They are deliberately
# separate from the r26 world-unit projectile heights above: arch2/arch2b and block_small can be
# walked through/under while still interacting with a low projectile.
WALKING_HEIGHTS = {
    "sw_tree1": 3,
    "sw_tree2": 3,
    "sw_tree3": 3,
    "sw_tree4": 3,
    "block_basic3": 3,
    "obst_snowman": 3,
    "block_arch1": 3,
    "block_arch3": 3,
    "block_arch1b": 3,
    "block_arch3b": 3,
    "block_basic2": 2,
    "block_ice2": 2,
    "block_basic": 1,
    "obst_duck": 1,
    "sw_fence": 1,
    "block_ice": 1,
}

BACKGROUND_NAMES = {"sw_backround2", "sw_backround3", "sw_backround6", "sw_backround7"}
MACHINE_ASSET_PREFIX = "hh_room_snowwar_arena_s_snowball_machine_"


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest().upper()


def load_cast_index() -> dict[str, dict[str, object]]:
    inventory = json.loads(R26_ROOM_INVENTORY.read_text(encoding="utf-8"))
    return {str(entry["asset"]): entry for entry in inventory["bitmaps"]}


def asset_record(asset: str, cast_index: dict[str, dict[str, object]]) -> dict[str, object]:
    if asset not in cast_index:
        raise ValueError(f"Missing r26 room-cast asset {asset}")
    entry = cast_index[asset]
    return {
        "asset": asset,
        "resourceId": int(entry["resourceId"]),
        "castId": int(entry["castId"]),
        "originalName": str(entry["originalName"]),
        "width": int(entry["width"]),
        "height": int(entry["height"]),
        "registrationPoint": {
            "x": int(entry["registrationPoint"]["x"]),
            "y": int(entry["registrationPoint"]["y"]),
        },
        "paletteId": int(entry["paletteId"]),
        "paletteName": str(entry["paletteName"]),
    }


def item_asset(member: str, direction: int, cast_index: dict[str, dict[str, object]]) -> dict[str, object]:
    prefix = "hh_room_snowwar_arena_"
    for asset in (
        f"{prefix}{member}_32_a_{direction}_0",
        f"{prefix}{member}_32_a_0_0",
    ):
        if asset in cast_index:
            return asset_record(asset, cast_index)
    raise ValueError(f"No r26 visual asset for {member} direction {direction}")


def machine_assets(cast_index: dict[str, dict[str, object]]) -> dict[str, object]:
    shadow = asset_record(MACHINE_ASSET_PREFIX + "sd", cast_index)
    stock = [asset_record(MACHINE_ASSET_PREFIX + f"a_0_0_0_0_{count}", cast_index) for count in range(6)]
    return {
        "shadow": shadow,
        "steadyOverlay": asset_record(MACHINE_ASSET_PREFIX + "b_0_0_0_0_0", cast_index),
        "refillOverlay": asset_record(MACHINE_ASSET_PREFIX + "b_0_0_0_0_1", cast_index),
        "stockFrames": stock,
        "stateComposition": "steady=A[0..5]+B0+shadow; refill=A[0..4]+B1+shadow",
    }


def java_string_expression(text: str) -> str:
    return "".join(re.findall(r'"([^"\\]*(?:\\.[^"\\]*)*)"', text)).replace("\\r", "\r")


def parse_heightmaps() -> tuple[dict[int, list[str]], dict[str, object]]:
    source = HAVANA_MANAGER.read_text(encoding="utf-8")
    maps: dict[int, list[str]] = {}
    for map_id in (1, 2, 3, 7):
        match = re.search(
            rf"if \(mapId == {map_id}\) \{{\s*return (.*?);\s*\}}",
            source,
            flags=re.DOTALL,
        )
        if not match:
            raise ValueError(f"Cannot recover heightmap {map_id}")
        maps[map_id] = [row for row in java_string_expression(match.group(1)).split("|") if row]

    tail = source[source.index("if (mapId == 7)") :]
    fallback_match = re.search(r"\n\s*return (\"x.*?);\s*\n\s*\}", tail, flags=re.DOTALL)
    if not fallback_match:
        raise ValueError("Cannot recover shared maps 4..6 heightmap")
    shared = [row for row in java_string_expression(fallback_match.group(1)).split("|") if row]
    maps[4] = list(shared)
    maps[5] = list(shared)
    maps[6] = list(shared)

    for map_id, rows in maps.items():
        if len(rows) != 50 or any(len(row) != 50 for row in rows):
            dimensions = sorted({len(row) for row in rows})
            raise ValueError(f"Map {map_id} is not 50x50: rows={len(rows)}, widths={dimensions}")
        if any(set(row) - set("xX0123456789") for row in rows):
            raise ValueError(f"Map {map_id} contains unsupported floor markers")

    provenance = {
        "source": HAVANA_MANAGER.as_posix(),
        "sourceSha256": sha256(HAVANA_MANAGER),
        "status": "derived-r26-correlated-and-reviewed",
        "distinctMasks": [1, 2, 3, 7],
        "sharedBaseMask": [4, 5, 6],
        "sharedBaseReason": (
            "OBJECTS captures for maps 4..6 contain no sw_backround member; their arena identity is "
            "formed by static obstacle records over the common snow field. Maps 1,2,3,7 each carry "
            "a distinct background member and distinct floor mask."
        ),
    }
    return maps, provenance


def parse_items(map_id: int, cast_index: dict[str, dict[str, object]]) -> list[dict[str, object]]:
    path = HAVANA_MAP_ROOT / f"arena_{map_id}.dat"
    result: list[dict[str, object]] = []
    seen: set[str] = set()
    for line_number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        raw = raw.strip()
        if not raw:
            continue
        parts = raw.split()
        if len(parts) != 6:
            raise ValueError(f"{path}:{line_number}: expected 6 fields")
        source_id, member, x_text, y_text, source_z_text, direction_text = parts
        if source_id in seen:
            raise ValueError(f"{path}:{line_number}: duplicate source id {source_id}")
        seen.add(source_id)
        if member not in WORLD_HEIGHTS and not member.startswith("sw_backround"):
            raise ValueError(f"{path}:{line_number}: unknown collision member {member}")
        projectile_height = WORLD_HEIGHTS.get(member, 0)
        walking_height = WALKING_HEIGHTS.get(member, 0)
        tile_x = int(x_text)
        tile_y = int(y_text)
        direction = int(direction_text) % 8
        result.append(
            {
                "id": source_id,
                "member": member,
                "sourceOrder": len(result),
                "tileX": tile_x,
                "tileY": tile_y,
                "worldX": tile_x * 3200,
                "worldY": tile_y * 3200,
                "worldZ": 0,
                # The fifth captured OBJECTS field is Director locZ, not physical altitude. The
                # calibrated r26 renderer reproduces archived frames by sorting (x+y, locZ, x),
                # while physical projectile collision comes from the r26 member-height table.
                "sourceLocZ": int(source_z_text),
                "visualSortOverride": int(source_z_text),
                "direction": direction,
                "flipX": False,
                "walkingHeight": walking_height,
                "projectileCollisionHeight": projectile_height,
                "collisionHeight": projectile_height,
                "blocksWalking": walking_height > 0,
                "footprintTiles": [[tile_x, tile_y]],
                "castBitmap": item_asset(member, direction, cast_index),
            }
        )
    backgrounds = [item for item in result if item["member"] in BACKGROUND_NAMES]
    props = [item for item in result if item["member"] not in BACKGROUND_NAMES]
    props.sort(
        key=lambda item: (
            int(item["tileX"]) + int(item["tileY"]),
            int(item["visualSortOverride"]),
            int(item["tileX"]),
            int(item["sourceOrder"]),
        )
    )
    for draw_order, item in enumerate(backgrounds + props):
        item["drawOrder"] = draw_order
        item["drawGroup"] = "background" if item in backgrounds else "prop"
    return result


def parse_spawns(map_id: int) -> list[dict[str, int]]:
    path = HAVANA_MAP_ROOT / f"arena_{map_id}_spawn_clusters.dat"
    chunks = [chunk.strip() for chunk in path.read_text(encoding="utf-8").split("|") if chunk.strip()]
    result = []
    for index, chunk in enumerate(chunks):
        fields = [int(value) for value in chunk.split()]
        if len(fields) != 4:
            raise ValueError(f"{path}: invalid spawn cluster {chunk!r}")
        x, y, radius, minimum = fields
        result.append(
            {
                "id": index + 1,
                "centerX": x,
                "centerY": y,
                "radius": radius,
                "minimumSeparation": minimum,
                "applicableTeamCounts": [1, 2, 3, 4],
                "teamAssignment": "shared-pool-with-global-separation",
                "fallback": "bounded-64-attempt-stable-farthest-legal-search-or-fail-start",
            }
        )
    return result


def parse_machines(map_id: int, cast_index: dict[str, dict[str, object]]) -> list[dict[str, object]]:
    path = HAVANA_MAP_ROOT / f"arena_{map_id}_snowmachines.dat"
    if not path.exists():
        return []
    result = []
    for index, raw in enumerate(path.read_text(encoding="utf-8").splitlines()):
        if not raw.strip():
            continue
        x, y = [int(value) for value in raw.split()]
        result.append(
            {
                "id": index + 1,
                "tileX": x,
                "tileY": y,
                "worldX": x * 3200,
                "worldY": y * 3200,
                "worldZ": 0,
                "direction": 0,
                "collectionTileX": x,
                "collectionTileY": y + 1,
                "collisionTiles": [[x, y], [x + 1, y], [x + 2, y]],
                "collisionRadius": 1200,
                "collisionHeight": 2400,
                "initialStock": 0,
                "maximumStock": 5,
                "refillIntervalMs": 3000,
                "transferIntervalMs": 1000,
                "immediateRefillOnCollection": True,
                "immediateTransferOnCollection": True,
                "arbitration": "ascending-machine-id-then-avatar-object-id",
                "visual": machine_assets(cast_index),
            }
        )
    return result


def signed_crc32(payload: bytes) -> int:
    return struct.unpack("!i", struct.pack("!I", zlib.crc32(payload) & 0xFFFFFFFF))[0]


def canonical_bytes(value: object) -> bytes:
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode("utf-8")


def make_collision_rows(
    floor_rows: list[str], items: list[dict[str, object]], machines: list[dict[str, object]]
) -> list[str]:
    grid = [["0" if char.lower() != "x" else "x" for char in row] for row in floor_rows]
    for item in items:
        if item["blocksWalking"]:
            grid[int(item["tileY"])][int(item["tileX"])] = "x"
    for machine in machines:
        for x, y in machine["collisionTiles"]:
            if 0 <= x < 50 and 0 <= y < 50:
                grid[y][x] = "x"
    return ["".join(row) for row in grid]


def render_overlay(map_record: dict[str, object], path: Path) -> None:
    scale = 10
    image = Image.new("RGBA", (50 * scale, 50 * scale), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    floor = map_record["floorRows"]
    collision = map_record["collisionRows"]
    for y in range(50):
        for x in range(50):
            box = (x * scale, y * scale, (x + 1) * scale - 1, (y + 1) * scale - 1)
            if floor[y][x].lower() == "x":
                draw.rectangle(box, fill=(25, 31, 38, 220))
            elif collision[y][x] == "x":
                draw.rectangle(box, fill=(205, 70, 63, 210))
            else:
                draw.rectangle(box, fill=(221, 247, 255, 170))
            draw.rectangle(box, outline=(90, 140, 155, 80))
    for machine in map_record["machines"]:
        x, y = machine["tileX"], machine["tileY"]
        draw.rectangle(
            (x * scale, y * scale, (x + 3) * scale - 1, (y + 1) * scale - 1),
            fill=(244, 162, 55, 230),
            outline=(100, 55, 0, 255),
            width=2,
        )
        cx, cy = machine["collectionTileX"], machine["collectionTileY"]
        draw.rectangle(
            (cx * scale, cy * scale, (cx + 1) * scale - 1, (cy + 1) * scale - 1),
            outline=(255, 145, 0, 255),
            width=2,
        )
    colors = [(0, 175, 255, 255), (255, 81, 54, 255), (255, 221, 54, 255), (237, 40, 255, 255)]
    for index, spawn in enumerate(map_record["spawnClusters"]):
        x, y, radius = spawn["centerX"], spawn["centerY"], spawn["radius"]
        draw.ellipse(
            ((x - radius) * scale, (y - radius) * scale, (x + radius + 1) * scale, (y + radius + 1) * scale),
            outline=colors[index % len(colors)],
            width=2,
        )
        draw.ellipse(
            (x * scale + 2, y * scale + 2, (x + 1) * scale - 3, (y + 1) * scale - 3),
            fill=colors[index % len(colors)],
        )
    image.save(path, optimize=False, compress_level=9)


def find_reference_preview(map_id: int) -> Path | None:
    exact = R26_PREVIEW_ROOT / f"arena_{map_id}.png"
    if exact.exists():
        return exact
    candidates = sorted(R26_PREVIEW_ROOT.glob(f"*{map_id}*.png"))
    return candidates[0] if candidates else None


def validate_map_record(record: dict[str, object]) -> None:
    map_id = int(record["mapId"])
    width = int(record["width"])
    height = int(record["height"])
    for field in ("floorRows", "collisionRows"):
        rows = record[field]
        if len(rows) != height or any(len(row) != width for row in rows):
            raise ValueError(f"Map {map_id} {field} is not an exact {width}x{height} rectangle")
    items = record["items"]
    item_ids = [item["id"] for item in items]
    if len(item_ids) != len(set(item_ids)):
        raise ValueError(f"Map {map_id} contains duplicate item IDs")
    draw_orders = sorted(int(item["drawOrder"]) for item in items)
    if draw_orders != list(range(len(items))):
        raise ValueError(f"Map {map_id} draw orders are not a complete stable sequence")
    for item in items:
        asset = item.get("castBitmap")
        if not isinstance(asset, dict) or not asset.get("asset") or not asset.get("resourceId"):
            raise ValueError(f"Map {map_id} item {item['id']} has no exact r26 cast bitmap")
        for tile_x, tile_y in item["footprintTiles"]:
            if not (0 <= tile_x < width and 0 <= tile_y < height):
                raise ValueError(f"Map {map_id} item {item['id']} footprint leaves the map")
    machine_ids = [int(machine["id"]) for machine in record["machines"]]
    if len(machine_ids) != len(set(machine_ids)):
        raise ValueError(f"Map {map_id} contains duplicate machine IDs")
    for machine in record["machines"]:
        visual = machine["visual"]
        if len(visual["stockFrames"]) != 6 or not visual["steadyOverlay"] or not visual["refillOverlay"]:
            raise ValueError(f"Map {map_id} machine {machine['id']} has incomplete visual states")
    if not record["spawnClusters"]:
        raise ValueError(f"Map {map_id} has no spawn cluster")


def main() -> None:
    heightmaps, heightmap_provenance = parse_heightmaps()
    cast_index = load_cast_index()
    OUTPUT_ROOT.mkdir(parents=True, exist_ok=True)
    records = []
    source_files = [HAVANA_MANAGER, R26_RENDERER, R26_ROOM_INVENTORY]
    for map_id in range(1, 8):
        item_path = HAVANA_MAP_ROOT / f"arena_{map_id}.dat"
        spawn_path = HAVANA_MAP_ROOT / f"arena_{map_id}_spawn_clusters.dat"
        source_files.extend([item_path, spawn_path])
        machine_path = HAVANA_MAP_ROOT / f"arena_{map_id}_snowmachines.dat"
        if machine_path.exists():
            source_files.append(machine_path)

        items = parse_items(map_id, cast_index)
        spawns = parse_spawns(map_id)
        machines = parse_machines(map_id, cast_index)
        rows = heightmaps[map_id]
        collision_rows = make_collision_rows(rows, items, machines)
        background_members = [item["member"] for item in items if str(item["member"]).startswith("sw_backround")]
        preview = find_reference_preview(map_id)
        if preview is None:
            raise ValueError(f"Map {map_id} has no reviewed r26 reference preview")
        source_files.append(preview)
        with Image.open(preview) as preview_image:
            rendered_reference = {
                "source": preview.as_posix(),
                "sha256": sha256(preview),
                "width": int(preview_image.width),
                "height": int(preview_image.height),
            }
        collision_objects = [
            {
                "id": f"item:{item['id']}",
                "kind": "item",
                "footprintTiles": item["footprintTiles"],
                "walkingHeight": item["walkingHeight"],
                "projectileCollisionHeight": item["projectileCollisionHeight"],
            }
            for item in items
            if int(item["walkingHeight"]) > 0 or int(item["projectileCollisionHeight"]) > 0
        ]
        collision_objects.extend(
            {
                "id": f"machine:{machine['id']}",
                "kind": "machine",
                "footprintTiles": machine["collisionTiles"],
                "walkingHeight": 1,
                "projectileCollisionHeight": machine["collisionHeight"],
                "collisionRadius": machine["collisionRadius"],
            }
            for machine in machines
        )
        record: dict[str, object] = {
            "mapId": map_id,
            "revision": 1,
            "width": 50,
            "height": 50,
            "worldUnitsPerTile": 3200,
            "tileOrigin": {"x": 0, "y": 0},
            "worldOrigin": {"x": 0, "y": 0, "z": 0},
            "baseRoomModel": "snowwar_arena_0",
            "backgroundMembers": background_members,
            "renderedReference": rendered_reference,
            "floorRows": rows,
            "collisionRows": collision_rows,
            "items": items,
            "collisionObjects": collision_objects,
            "machines": machines,
            "spawnClusters": spawns,
        }
        validate_map_record(record)
        checksum_payload = {
            key: value
            for key, value in record.items()
            if key not in {"checksum", "sha256", "renderedReference", "reviewArtifacts"}
        }
        record["checksum"] = signed_crc32(canonical_bytes(checksum_payload))
        record["sha256"] = hashlib.sha256(canonical_bytes(checksum_payload)).hexdigest().upper()
        records.append(record)

        map_dir = OUTPUT_ROOT / f"map_{map_id}"
        map_dir.mkdir(parents=True, exist_ok=True)
        (map_dir / "floor.txt").write_text("\n".join(rows) + "\n", encoding="ascii", newline="\n")
        (map_dir / "collision.txt").write_text(
            "\n".join(collision_rows) + "\n", encoding="ascii", newline="\n"
        )
        render_overlay(record, map_dir / "collision_overlay.png")
        shutil.copyfile(preview, map_dir / "reference_preview.png")
        record["reviewArtifacts"] = {
            "referencePreview": {
                "path": (map_dir / "reference_preview.png").as_posix(),
                "sha256": sha256(map_dir / "reference_preview.png"),
            },
            "collisionOverlay": {
                "path": (map_dir / "collision_overlay.png").as_posix(),
                "sha256": sha256(map_dir / "collision_overlay.png"),
            },
        }

    source_manifest = [
        {"path": path.as_posix(), "sha256": sha256(path), "bytes": path.stat().st_size}
        for path in sorted(set(source_files), key=lambda value: value.as_posix())
    ]
    output = {
        "schemaVersion": 1,
        "revision": 1,
        "status": "canonical-v1-evidence-frozen",
        "review": {
            "allSevenReferencePreviewsReviewed": True,
            "allSevenCollisionOverlaysReviewed": True,
            "machinePresence": {"1": 1, "2": 1, "3": 3, "4": 0, "5": 0, "6": 0, "7": 0},
        },
        "sourceRules": {
            "r26VariableIndex": "collision heights and machine maximum",
            "havanaCapturedData": "candidate OBJECTS, machine positions, spawn clusters and floor masks",
            "r26CalibratedPreviews": "visual placement, locZ ordering and machine-absence review fixtures",
            "laterAirSnowWar": "excluded",
        },
        "heightmapProvenance": heightmap_provenance,
        "sourceFiles": source_manifest,
        "maps": records,
    }
    canonical_manifest = {
        "schemaVersion": output["schemaVersion"],
        "revision": output["revision"],
        "maps": [
            {
                key: value
                for key, value in record.items()
                if key not in {"checksum", "sha256", "renderedReference", "reviewArtifacts"}
            }
            for record in records
        ],
    }
    output["canonicalChecksum"] = signed_crc32(canonical_bytes(canonical_manifest))
    output["canonicalSha256"] = hashlib.sha256(canonical_bytes(canonical_manifest)).hexdigest().upper()
    manifest_path = OUTPUT_ROOT / "snowstorm_maps.json"
    manifest_path.write_text(
        json.dumps(output, indent=2, sort_keys=True, ensure_ascii=False) + "\n",
        encoding="utf-8",
        newline="\n",
    )
    contact = Image.new("RGBA", (750, 1000), (28, 31, 34, 255))
    for index in range(7):
        overlay = Image.open(OUTPUT_ROOT / f"map_{index + 1}" / "collision_overlay.png").convert("RGBA")
        overlay.thumbnail((240, 240), Image.Resampling.NEAREST)
        x = 5 + (index % 3) * 250
        y = 5 + (index // 3) * 250
        contact.alpha_composite(overlay, (x, y))
        ImageDraw.Draw(contact).text((x + 4, y + 4), f"Map {index + 1}", fill=(0, 0, 0, 255))
    contact.save(OUTPUT_ROOT / "collision_contact_sheet.png", optimize=False, compress_level=9)
    print(manifest_path)
    for record in records:
        print(
            f"map {record['mapId']}: items={len(record['items'])} machines={len(record['machines'])} "
            f"spawns={len(record['spawnClusters'])} checksum={record['checksum']}"
        )


if __name__ == "__main__":
    main()

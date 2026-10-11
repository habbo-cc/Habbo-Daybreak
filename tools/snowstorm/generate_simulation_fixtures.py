#!/usr/bin/env python3
"""Generate deterministic r26/approved-deviation SnowStorm simulation fixtures."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parent
SHARED = ROOT / "evidence" / "shared_gamesystem_scripts.json"
OUTPUT = ROOT / "fixtures" / "simulation_fixtures.json"
REPOSITORY = ROOT.parent.parent
JAVA_OUTPUT = (
    REPOSITORY / "src" / "main" / "java" / "com" / "eu" / "habbo" / "habbohotel"
    / "games" / "snowstorm" / "data" / "SnowStormGeneratedRuntimeTables.java"
)
CLIENT_REPOSITORY = REPOSITORY.parent.parent / "client" / "habbo-client-clean"
AS3_OUTPUT = (
    CLIENT_REPOSITORY / "src" / "com" / "sulake" / "habbo" / "snowstorm" / "data"
    / "SnowStormGeneratedRuntimeTables.as"
)

TILE = 3200
SUBTURN_MOVEMENT = 640
SUBTURN_MS = 60
QUICK_TTL = 13
QUICK_VELOCITY = 2000
QUICK_HEIGHT = 4000
QUICK_DESCENT_POINT = 3
LOB_HEIGHT = 3000
LOB_VELOCITY = 2000
AVATAR_RADIUS = 1600
SNOWBALL_RADIUS = 400
PLAYER_HEIGHT = 5000
MACHINE_CAPACITY = 5
MACHINE_REFILL_MS = 3000
MACHINE_TRANSFER_MS = 1000

DIRECTION_DELTAS = (
    (0, -1),
    (1, -1),
    (1, 0),
    (1, 1),
    (0, 1),
    (-1, 1),
    (-1, 0),
    (-1, -1),
)


def director_div(numerator: int, denominator: int) -> int:
    """Director integer division truncates toward zero when both operands are integers."""
    if denominator == 0:
        raise ZeroDivisionError
    quotient = abs(numerator) // abs(denominator)
    return -quotient if (numerator < 0) != (denominator < 0) else quotient


def director_mod(value: int, divisor: int) -> int:
    return value - director_div(value, divisor) * divisor


def validate_360(value: int) -> int:
    if value > 359:
        value = director_mod(value, 360)
    elif value < 0:
        value = 360 + director_mod(value, 360)
    return value


def validate_8(value: int) -> int:
    if value > 7:
        value = director_mod(value, 8)
    elif value < 0:
        value = director_mod(8 + director_mod(value, 8), 8)
    return value


def direction_360_to_8(value: int) -> int:
    return validate_8(director_div(validate_360(value - 22), 45) + 1)


def parse_runtime_tables() -> tuple[list[int], list[int], list[int], list[int], dict[str, str]]:
    evidence = json.loads(SHARED.read_text(encoding="utf-8"))
    velocity_x: list[int] | None = None
    velocity_y: list[int] | None = None
    sqrt_table: list[int] | None = None
    angle_table: list[int] | None = None
    hashes: dict[str, str] = {}
    for cast in evidence["casts"]:
        for script in cast["scripts"]:
            name = script["memberName"]
            if name == "CVelocityTbl":
                arrays = re.findall(r"m_ar_ar_iTbl\.add\(\[([^\]]+)]\)", script["lingo"])
                if len(arrays) != 2:
                    raise RuntimeError("Expected two r26 velocity arrays")
                parsed = [[int(part.strip()) for part in values.split(",")] for values in arrays]
                if any(len(values) != 360 for values in parsed):
                    raise RuntimeError("Every r26 velocity array must contain 360 entries")
                velocity_x, velocity_y = parsed
                hashes["velocityTableLingoSha256"] = script["lingoSha256"]
            elif name == "Gamesystem Squareroot Class":
                match = re.search(r"table = \[([^\]]+)]", script["lingo"])
                if match is None:
                    raise RuntimeError("r26 square-root table missing")
                sqrt_table = [int(part.strip()) for part in match.group(1).split(",")]
                hashes["squareRootLingoSha256"] = script["lingoSha256"]
            elif name == "CComponentToAngle":
                match = re.search(r"m_componentToAngleArray = \[([^\]]+)]", script["lingo"])
                if match is None:
                    raise RuntimeError("r26 component-to-angle table missing")
                angle_table = [int(part.strip()) for part in match.group(1).split(",")]
                hashes["componentToAngleLingoSha256"] = script["lingoSha256"]
    if velocity_x is None or velocity_y is None or sqrt_table is None or angle_table is None:
        raise RuntimeError("One or more required r26 runtime tables were not found")
    if len(sqrt_table) != 256 or len(angle_table) != 256:
        raise RuntimeError("Expected 256-entry square-root and component-to-angle tables")
    return velocity_x, velocity_y, sqrt_table, angle_table, hashes


def fast_sqrt(value: int, table: list[int]) -> int:
    if value < 0:
        return -1
    branches = (
        (1073741824, 16777216, 256),
        (268435456, 4194304, 128),
        (67108864, 1048576, 64),
        (16777216, 262144, 32),
        (4194304, 65536, 16),
        (1048576, 16384, 8),
        (262144, 4096, 4),
        (65536, 1024, 2),
        (16384, 256, 1),
        (4096, 64, -2),
        (1024, 16, -4),
        (256, 4, -8),
        (0, 1, -16),
    )
    for threshold, divisor, scale in branches:
        if value >= threshold:
            result = table[director_div(value, divisor)]
            return result * scale if scale > 0 else director_div(result, -scale)
    raise AssertionError("unreachable")


def component_angle(x_component: int, y_component: int, table: list[int]) -> int:
    x = x_component
    y = y_component
    if abs(x) <= abs(y):
        if y == 0:
            y = 1
        scaled_x = x * 256
        index = min(abs(director_div(scaled_x, y)), 255)
        angle = table[index]
        if y < 0:
            return angle if scaled_x > 0 else 360 - angle
        return 180 - angle if scaled_x > 0 else 180 + angle
    if x == 0:
        x = 1
    scaled_y = y * 256
    index = min(abs(director_div(scaled_y, x)), 255)
    angle = table[index]
    if scaled_y < 0:
        return 90 - angle if x > 0 else 270 + angle
    return 90 + angle if x > 0 else 270 - angle


def world_to_tile(value: int) -> int | None:
    if value < -(TILE // 2):
        return None
    return director_div(value + TILE // 2, TILE)


def step_axis(current: int, target: int) -> int:
    delta = target - current
    if -SUBTURN_MOVEMENT < delta < SUBTURN_MOVEMENT:
        return target
    if delta < 0:
        return current - SUBTURN_MOVEMENT
    if delta > 0:
        return current + SUBTURN_MOVEMENT
    return current


def movement_path(start: tuple[int, int], target: tuple[int, int]) -> list[dict[str, int]]:
    x, y = start
    path: list[dict[str, int]] = []
    for subturn in range(1, 65):
        if (x, y) == target:
            break
        x = step_axis(x, target[0])
        y = step_axis(y, target[1])
        path.append({"subturn": subturn, "worldX": x, "worldY": y})
    return path


def neighbour_choice(angle: int, available: set[int]) -> dict[str, object]:
    candidates = (
        direction_360_to_8(angle),
        direction_360_to_8(validate_360(angle - 45)),
        direction_360_to_8(validate_360(angle + 45)),
    )
    selected = next((candidate for candidate in candidates if candidate in available), None)
    return {"angle": angle, "candidateDirections": list(candidates), "selectedDirection": selected}


def projectile_path(
    trajectory: int,
    start: tuple[int, int],
    target: tuple[int, int],
    velocity_x: list[int],
    velocity_y: list[int],
    sqrt_table: list[int],
    angle_table: list[int],
    occupied_heights: dict[int, int] | None = None,
) -> dict[str, object]:
    x, y = start
    delta_x = director_div(target[0] - x, 200)
    delta_y = director_div(target[1] - y, 200)
    direction = validate_360(component_angle(delta_x, delta_y, angle_table))
    if trajectory == 0:
        ttl = QUICK_TTL
        base_height = QUICK_HEIGHT
        multiplier = 4
    else:
        distance = fast_sqrt(delta_x * delta_x + delta_y * delta_y, sqrt_table) * 200
        ttl = director_div(distance, LOB_VELOCITY)
        base_height = LOB_HEIGHT
        multiplier = 10 if trajectory == 1 else 100
    initial_ttl = ttl
    offset = director_div(ttl, 2)
    path: list[dict[str, int]] = []
    removal_reason = "fixture-limit"
    occupied_heights = occupied_heights or {}
    for subturn in range(1, 513):
        ttl -= 1
        x += director_div(velocity_x[direction] * QUICK_VELOCITY, 255)
        y += director_div(velocity_y[direction] * QUICK_VELOCITY, 255)
        if trajectory == 0 and ttl > QUICK_DESCENT_POINT:
            temp = QUICK_DESCENT_POINT - offset
        else:
            temp = ttl - offset
        # Raw bytecode proves the cleaned Lingo's missing parentheses:
        # (offset^2 - temp^2) * multiplier + baseHeight.
        z = (offset * offset - temp * temp) * multiplier + base_height
        occupied_height = occupied_heights.get(subturn, 0)
        ground_collision = occupied_height > z
        removed = z < 1 or ground_collision
        path.append(
            {
                "subturn": subturn,
                "worldX": x,
                "worldY": y,
                "worldZ": z,
                "ttl": ttl,
                "occupiedHeight": occupied_height,
                "removed": removed,
            }
        )
        if removed:
            removal_reason = "below-ground" if z < 1 else "occupied-height"
            break
    return {
        "trajectory": trajectory,
        "start": list(start),
        "target": list(target),
        "direction": direction,
        "initialTtl": initial_ttl,
        "parabolaOffset": offset,
        "path": path,
        "removalReason": removal_reason,
    }


def circle_collision(distance: int) -> bool:
    return distance * distance < (AVATAR_RADIUS + SNOWBALL_RADIUS) ** 2


def avatar_height_collision(snowball_z: int) -> bool:
    return snowball_z < PLAYER_HEIGHT


def state_timeline(initial_state: int, timer: int, health: int, ammo: int, steps: int) -> list[dict[str, int]]:
    state = initial_state
    records: list[dict[str, int]] = []
    for subturn in range(1, steps + 1):
        if timer > 0:
            if timer == 1:
                if state == 1:
                    ammo = min(ammo + 1, 5)
                    state = 0
                elif state == 2:
                    health = 4
                    state = 3
                    timer = 60
                elif state == 3:
                    state = 0
            timer -= 1
        records.append(
            {"subturn": subturn, "activityState": state, "activityTimer": timer, "health": health, "ammo": ammo}
        )
    return records


def machine_timeline(
    collectors: list[dict[str, int]],
    duration_subturns: int,
    starting_stock: int = 0,
) -> dict[str, object]:
    """Model the approved Havana-visible cadence in deterministic 60 ms subturns."""
    stock = starting_stock
    ammo = {collector["objectId"]: collector["ammo"] for collector in collectors}
    events: list[dict[str, object]] = []
    next_refill_ms = 0
    next_transfer_ms = 0

    for subturn in range(duration_subturns + 1):
        elapsed_ms = subturn * SUBTURN_MS
        eligible = sorted(
            collector["objectId"]
            for collector in collectors
            if collector["enterSubturn"] <= subturn < collector["leaveSubturn"]
        )
        if not eligible:
            next_refill_ms = elapsed_ms
            next_transfer_ms = elapsed_ms
            continue

        if elapsed_ms >= next_refill_ms:
            if stock < MACHINE_CAPACITY:
                stock += 1
                events.append({"subturn": subturn, "event": "restock", "machineCount": stock})
            next_refill_ms += MACHINE_REFILL_MS

        if elapsed_ms >= next_transfer_ms:
            winner = next((object_id for object_id in eligible if ammo[object_id] < 5), None)
            if winner is not None and stock > 0:
                stock -= 1
                ammo[winner] += 1
                events.append(
                    {
                        "subturn": subturn,
                        "event": "transfer",
                        "playerObjectId": winner,
                        "machineCount": stock,
                        "playerCount": ammo[winner],
                    }
                )
            next_transfer_ms += MACHINE_TRANSFER_MS

    return {"events": events, "finalMachineCount": stock, "finalPlayerCounts": ammo}


def java_array(values: list[int]) -> str:
    lines = []
    for index in range(0, len(values), 24):
        lines.append("            " + ", ".join(str(value) for value in values[index : index + 24]))
    return ",\n".join(lines)


def as3_array(values: list[int]) -> str:
    lines = []
    for index in range(0, len(values), 24):
        lines.append("            " + ", ".join(str(value) for value in values[index : index + 24]))
    return ",\n".join(lines)


def generate_java(
    velocity_x: list[int],
    velocity_y: list[int],
    sqrt_table: list[int],
    angle_table: list[int],
    hashes: dict[str, str],
) -> str:
    return f"""package com.eu.habbo.habbohotel.games.snowstorm.data;

/** Exact integer lookup tables extracted from the frozen r26 hh_gamesys.cct Lingo. */
public final class SnowStormGeneratedRuntimeTables {{
    public static final String VELOCITY_LINGO_SHA256 = "{hashes['velocityTableLingoSha256']}";
    public static final String SQUARE_ROOT_LINGO_SHA256 = "{hashes['squareRootLingoSha256']}";
    public static final String COMPONENT_ANGLE_LINGO_SHA256 = "{hashes['componentToAngleLingoSha256']}";

    private static final int[] VELOCITY_X = {{
{java_array(velocity_x)}
    }};
    private static final int[] VELOCITY_Y = {{
{java_array(velocity_y)}
    }};
    private static final int[] SQUARE_ROOT = {{
{java_array(sqrt_table)}
    }};
    private static final int[] COMPONENT_ANGLE = {{
{java_array(angle_table)}
    }};

    private SnowStormGeneratedRuntimeTables() {{
    }}

    public static int velocityX(int direction) {{
        return VELOCITY_X[direction];
    }}

    public static int velocityY(int direction) {{
        return VELOCITY_Y[direction];
    }}

    public static int squareRoot(int index) {{
        return SQUARE_ROOT[index];
    }}

    public static int componentAngle(int index) {{
        return COMPONENT_ANGLE[index];
    }}

    public static int velocityTableSize() {{
        return VELOCITY_X.length;
    }}

    public static int squareRootTableSize() {{
        return SQUARE_ROOT.length;
    }}

    public static int componentAngleTableSize() {{
        return COMPONENT_ANGLE.length;
    }}
}}
"""


def generate_as3(
    velocity_x: list[int],
    velocity_y: list[int],
    sqrt_table: list[int],
    angle_table: list[int],
    hashes: dict[str, str],
) -> str:
    return f"""package com.sulake.habbo.snowstorm.data
{{
    public final class SnowStormGeneratedRuntimeTables
    {{
        public static const VELOCITY_LINGO_SHA256:String = "{hashes['velocityTableLingoSha256']}";
        public static const SQUARE_ROOT_LINGO_SHA256:String = "{hashes['squareRootLingoSha256']}";
        public static const COMPONENT_ANGLE_LINGO_SHA256:String = "{hashes['componentToAngleLingoSha256']}";

        private static const VELOCITY_X:Vector.<int> = Vector.<int>([
{as3_array(velocity_x)}
        ]);
        private static const VELOCITY_Y:Vector.<int> = Vector.<int>([
{as3_array(velocity_y)}
        ]);
        private static const SQUARE_ROOT:Vector.<int> = Vector.<int>([
{as3_array(sqrt_table)}
        ]);
        private static const COMPONENT_ANGLE:Vector.<int> = Vector.<int>([
{as3_array(angle_table)}
        ]);

        public static function velocityX(direction:int):int {{ return VELOCITY_X[direction]; }}
        public static function velocityY(direction:int):int {{ return VELOCITY_Y[direction]; }}
        public static function squareRoot(index:int):int {{ return SQUARE_ROOT[index]; }}
        public static function componentAngle(index:int):int {{ return COMPONENT_ANGLE[index]; }}
        public static function get velocityTableSize():int {{ return VELOCITY_X.length; }}
        public static function get squareRootTableSize():int {{ return SQUARE_ROOT.length; }}
        public static function get componentAngleTableSize():int {{ return COMPONENT_ANGLE.length; }}
    }}
}}
"""


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="verify fixtures/tables without rewriting")
    args = parser.parse_args()
    velocity_x, velocity_y, sqrt_table, angle_table, runtime_hashes = parse_runtime_tables()

    projectile_cases: list[dict[str, object]] = []
    trajectory_names = {0: "quick", 1: "short", 2: "long"}
    target_cases = (
        ("cardinal-one-tile", (0, 0), (3200, 0)),
        ("diagonal-one-tile", (0, 0), (3200, 3200)),
        ("minimum-range", (0, 0), (1, 0)),
        ("map-maximum-diagonal", (0, 0), (156800, 156800)),
    )
    for trajectory in range(3):
        for name, start, target in target_cases:
            projectile_cases.append(
                {
                    "name": f"{trajectory_names[trajectory]}-{name}",
                    **projectile_path(
                        trajectory, start, target, velocity_x, velocity_y, sqrt_table, angle_table
                    ),
                }
            )

    boundary_paths: list[dict[str, object]] = []
    for trajectory in range(3):
        baseline = projectile_path(
            trajectory, (0, 0), (12800, 0), velocity_x, velocity_y, sqrt_table, angle_table
        )
        probe_subturn = min(2, len(baseline["path"]))
        probe_height = baseline["path"][probe_subturn - 1]["worldZ"]
        boundary_paths.extend(
            [
                {
                    "name": f"{trajectory_names[trajectory]}-height-equality-survives",
                    **projectile_path(
                        trajectory,
                        (0, 0),
                        (12800, 0),
                        velocity_x,
                        velocity_y,
                        sqrt_table,
                        angle_table,
                        {probe_subturn: probe_height},
                    ),
                },
                {
                    "name": f"{trajectory_names[trajectory]}-height-plus-one-blocks",
                    **projectile_path(
                        trajectory,
                        (0, 0),
                        (12800, 0),
                        velocity_x,
                        velocity_y,
                        sqrt_table,
                        angle_table,
                        {probe_subturn: probe_height + 1},
                    ),
                },
            ]
        )

    world_values = (-1601, -1600, -1599, -1, 0, 1599, 1600, 4799, 4800)
    fixture = {
        "schemaVersion": 1,
        "sources": {
            "sharedRuntime": "hh_gamesys.cct",
            "snowstormScripts": [
                "Snowwar Avatar Gameobject Class",
                "Snowwar Snowball Gameobject Class",
                "Snowwar GameFieldManager Class",
            ],
            **runtime_hashes,
            "machineTiming": "Havana-visible timing approved for deterministic NGH implementation",
            "havanaComparison": {
                "accepted": ["300 ms turn with five subturn payloads", "machine stock/cadence intent"],
                "rejected": [
                    "wall-clock projectile tasks",
                    "landing-tile-only hit detection",
                    "velocity 3000",
                    "distance-based quick TTL and 22-tile cap",
                    "opposition-only physical collision",
                    "health restoration at knockout",
                ],
            },
        },
        "timing": {"turnMs": 300, "subturnsPerTurn": 5, "subturnMs": SUBTURN_MS},
        "coordinateVectors": [
            {"world": value, "tile": world_to_tile(value), "rejected": world_to_tile(value) is None}
            for value in world_values
        ],
        "directionVectors": [
            {"direction": direction, "deltaX": delta[0], "deltaY": delta[1]}
            for direction, delta in enumerate(DIRECTION_DELTAS)
        ],
        "neighbourChoiceVectors": [
            {"name": "direct-east", **neighbour_choice(90, {2})},
            {"name": "east-falls-back-ccw", **neighbour_choice(90, {1, 3})},
            {"name": "east-falls-back-cw", **neighbour_choice(90, {3})},
            {"name": "all-three-blocked", **neighbour_choice(90, {0, 4, 5, 6, 7})},
        ],
        "movementVectors": [
            {"name": "cardinal-five-subturn-tile", "start": [0, 0], "target": [3200, 0], "path": movement_path((0, 0), (3200, 0))},
            {"name": "diagonal-five-subturn-tile", "start": [0, 0], "target": [3200, 3200], "path": movement_path((0, 0), (3200, 3200))},
            {"name": "snap-inside-one-step", "start": [0, 0], "target": [639, -639], "path": movement_path((0, 0), (639, -639))},
            {"name": "exact-step", "start": [0, 0], "target": [640, -640], "path": movement_path((0, 0), (640, -640))},
        ],
        "reservationContention": {
            "processingOrder": [7, 8],
            "desiredTile": [1, 0],
            "winnerObjectId": 7,
            "loserFallbackDirection": 1,
            "rule": "ascending object ID reserves first; loser retries direct, CCW, then CW",
        },
        "subturnOrder": {
            "wireEvents": ["event-A", "event-B", "event-C"],
            "objectIdsBeforeAdvance": [2, 7, 10],
            "objectIdsMarkedKilledDuringAdvance": [2],
            "execution": [
                "event:event-A",
                "event:event-B",
                "event:event-C",
                "advance:2",
                "advance:7",
                "advance:10",
                "remove:2",
            ],
            "avatarCollisionPhase": [
                "tick activity timer",
                "advance avatar movement",
                "skip collision when stunned or invincible",
                "scan active snowballs by ascending object ID at their pre-advance locations",
                "advance later snowball objects",
                "remove all killed objects after the complete object pass",
            ],
        },
        "simultaneousHitOrder": {
            "avatarObjectIds": [2, 7],
            "projectileObjectIds": [9, 10],
            "targetObjectId": 7,
            "initialHealth": 2,
            "eventOrder": ["target-7-removes-ball-9", "hit:9->7", "target-7-removes-ball-10", "hit:10->7", "knockout:10->7"],
            "finalHealth": 0,
            "throwerScoreDelta": 7,
            "rule": "the ascending target avatar scans projectile IDs at pre-advance positions; damage resolves sequentially and the hit point precedes the knockout bonus",
        },
        "collisionBoundaries": {
            "horizontal": [
                {"distance": distance, "collides": circle_collision(distance)}
                for distance in (1999, 2000, 2001)
            ],
            "vertical": [
                {"snowballZ": value, "collides": avatar_height_collision(value)}
                for value in (4999, 5000, 5001)
            ],
            "ground": [
                {"snowballZ": 2400, "occupiedHeight": height, "collides": height > 2400}
                for height in (2399, 2400, 2401)
            ],
            "selfCollision": {"physicalCollision": False, "projectileRemoved": False, "damage": 0, "score": 0},
            "friendlyCollision": {"physicalCollision": True, "projectileRemoved": True, "damage": 1, "score": 0},
            "enemyCollision": {"physicalCollision": True, "projectileRemoved": True, "damage": 1, "score": 1},
        },
        "projectileVectors": {
            "precedence": "raw bytecode: (offset^2 - temp^2) * multiplier + baseHeight",
            "cases": projectile_cases,
            "obstacleBoundaryCases": boundary_paths,
            "quickAdditionalTileRangeCap": None,
        },
        "stateMachine": {
            "throwCooldown": {
                "clock": "game-turn",
                "minimumSubturnDelta": 5,
                "attemptSubturns": [0, 1, 4, 5, 6, 10],
                "acceptedSubturns": [0, 5, 10],
            },
            "actionMatrix": [
                {"state": 0, "walk": True, "throw": True, "make": True, "avatarCollision": True},
                {"state": 1, "walk": True, "throw": False, "make": False, "avatarCollision": True},
                {"state": 2, "walk": False, "throw": False, "make": False, "avatarCollision": False},
                {"state": 3, "walk": True, "throw": True, "make": True, "avatarCollision": False},
            ],
            "creatingCompletes": state_timeline(1, 20, 4, 2, 20),
            "creatingCancelledByMove": {
                "before": {"activityState": 1, "activityTimer": 12, "ammo": 2},
                "after": {"activityState": 0, "activityTimer": 0, "ammo": 2},
            },
            "knockoutLifecycle": {
                "onKnockout": {"activityState": 2, "activityTimer": 125, "health": 0, "ammo": 0},
                "timeline": state_timeline(2, 125, 0, 0, 185),
            },
        },
        "scoringVectors": [
            {"name": "ffa-other-player-hit", "teamCount": 1, "sameAvatar": False, "sameTeam": True, "damage": 1, "hitScore": 1},
            {"name": "team-enemy-hit", "teamCount": 2, "sameAvatar": False, "sameTeam": False, "damage": 1, "hitScore": 1},
            {"name": "team-friendly-stray-hit", "teamCount": 2, "sameAvatar": False, "sameTeam": True, "damage": 1, "hitScore": 0},
            {"name": "self-overlap", "teamCount": 1, "sameAvatar": True, "sameTeam": True, "damage": 0, "hitScore": 0},
            {"name": "enemy-knockout", "teamCount": 2, "sameAvatar": False, "sameTeam": False, "damage": 1, "hitScore": 1, "knockoutScore": 5},
        ],
        "machineVectors": [
            {
                "name": "immediate-refill-then-transfer",
                "collectors": [{"objectId": 7, "ammo": 0, "enterSubturn": 0, "leaveSubturn": 1}],
                "result": machine_timeline([{"objectId": 7, "ammo": 0, "enterSubturn": 0, "leaveSubturn": 1}], 0),
            },
            {
                "name": "sustained-collection",
                "collectors": [{"objectId": 7, "ammo": 0, "enterSubturn": 0, "leaveSubturn": 151}],
                "result": machine_timeline([{"objectId": 7, "ammo": 0, "enterSubturn": 0, "leaveSubturn": 151}], 150),
            },
            {
                "name": "ascending-avatar-contention",
                "collectors": [
                    {"objectId": 11, "ammo": 0, "enterSubturn": 0, "leaveSubturn": 1},
                    {"objectId": 7, "ammo": 0, "enterSubturn": 0, "leaveSubturn": 1},
                ],
                "result": machine_timeline(
                    [
                        {"objectId": 11, "ammo": 0, "enterSubturn": 0, "leaveSubturn": 1},
                        {"objectId": 7, "ammo": 0, "enterSubturn": 0, "leaveSubturn": 1},
                    ],
                    0,
                ),
            },
            {
                "name": "no-collector-no-refill",
                "collectors": [],
                "result": machine_timeline([], 100),
            },
        ],
    }

    fixture_text = json.dumps(fixture, indent=2) + "\n"
    java_text = generate_java(velocity_x, velocity_y, sqrt_table, angle_table, runtime_hashes)
    as3_text = generate_as3(velocity_x, velocity_y, sqrt_table, angle_table, runtime_hashes)
    if args.check:
        for path, expected in ((OUTPUT, fixture_text), (JAVA_OUTPUT, java_text), (AS3_OUTPUT, as3_text)):
            if not path.exists() or path.read_text(encoding="utf-8") != expected:
                raise RuntimeError(f"Generated SnowStorm simulation output is stale: {path}")
        print("PASS: exact r26 simulation fixtures and Java/AS3 lookup tables synchronized")
        return

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(fixture_text, encoding="utf-8", newline="\n")
    JAVA_OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    JAVA_OUTPUT.write_text(java_text, encoding="utf-8", newline="\n")
    AS3_OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    AS3_OUTPUT.write_text(as3_text, encoding="utf-8", newline="\n")
    print(OUTPUT.resolve())
    print(JAVA_OUTPUT.resolve())
    print(AS3_OUTPUT.resolve())


if __name__ == "__main__":
    main()

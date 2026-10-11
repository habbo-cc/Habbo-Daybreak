#!/usr/bin/env python3
"""Verify that SnowStorm v1 ranges are either free or atomically registered."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from collections import defaultdict
from pathlib import Path


SERVER_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_CLIENT_ROOT = Path("C:/habbo/client/habbo-client-clean")
OUTPUT = Path(__file__).resolve().parent / "fixtures" / "protocol_registry_report.json"

SERVER_INCOMING = SERVER_ROOT / "src/main/java/com/eu/habbo/messages/incoming/Incoming.java"
SERVER_OUTGOING = SERVER_ROOT / "src/main/java/com/eu/habbo/messages/outgoing/Outgoing.java"

CLIENT_REGISTRY = Path("src/com/sulake/habbo/communication/HabboMessages.as")

CLIENT_TO_SERVER = tuple(range(7700, 7714))
SERVER_TO_CLIENT = tuple(range(7800, 7817))

EXPECTED = {
    "serverIncoming7700To7713": [
        "SnowStormGetLoungeEvent", "SnowStormSetGameParametersEvent", "SnowStormDeleteEvent",
        "SnowStormInitiateJoinEvent", "SnowStormSetJoinParametersEvent", "SnowStormLeaveEvent",
        "SnowStormKickEvent", "SnowStormWatchEvent", "SnowStormStartEvent", "SnowStormGameEvent",
        "SnowStormRequestFullStatusEvent", "SnowStormRequestFinishedGameEvent",
        "SnowStormAddBotEvent", "SnowStormRemoveBotEvent",
    ],
    "clientOutgoing7700To7713": [
        "SnowStormGetLoungeComposer", "SnowStormSetGameParametersComposer", "SnowStormDeleteComposer",
        "SnowStormInitiateJoinComposer", "SnowStormSetJoinParametersComposer", "SnowStormLeaveComposer",
        "SnowStormKickComposer", "SnowStormWatchComposer", "SnowStormStartComposer",
        "SnowStormGameEventComposer", "SnowStormRequestFullStatusComposer",
        "SnowStormRequestFinishedGameComposer", "SnowStormAddBotComposer",
        "SnowStormRemoveBotComposer",
    ],
    "serverOutgoing7800To7816": [
        "ShockwaveSnowStormLoungeInfoComposer", "ShockwaveSnowStormInstanceListComposer",
        "ShockwaveSnowStormGameInstanceComposer", "ShockwaveSnowStormInstanceRemovedComposer",
        "ShockwaveSnowStormJoinParametersComposer", "ShockwaveSnowStormOperationFailedComposer",
        "ShockwaveSnowStormGameLocationComposer", "ShockwaveSnowStormGameResetComposer",
        "ShockwaveSnowStormGameStartComposer", "ShockwaveSnowStormFullGameStatusComposer",
        "ShockwaveSnowStormGameStatusComposer", "ShockwaveSnowStormGameEndComposer",
        "ShockwaveSnowStormPlayerRemovedComposer", "ShockwaveSnowStormSpectatorCountComposer",
        "ShockwaveSnowStormFinishedGameComposer", "ShockwaveSnowStormSkillLevelChangedComposer",
        "ShockwaveSnowStormIdleWarningComposer",
    ],
    "clientIncoming7800To7816": [
        "SnowStormLoungeInfoEvent", "SnowStormInstanceListEvent", "SnowStormGameInstanceEvent",
        "SnowStormInstanceRemovedEvent", "SnowStormJoinParametersEvent",
        "SnowStormOperationFailedEvent", "SnowStormGameLocationEvent", "SnowStormGameResetEvent",
        "SnowStormGameStartEvent", "ShockwaveSnowStormFullGameStatusEvent",
        "ShockwaveSnowStormGameStatusEvent",
        "SnowStormGameEndEvent", "SnowStormPlayerRemovedEvent", "SnowStormSpectatorCountEvent",
        "SnowStormFinishedGameEvent", "SnowStormSkillLevelChangedEvent", "SnowStormIdleWarningEvent",
    ],
}

JAVA_ID = re.compile(
    r"public\s+(?:static\s+final|final\s+static)\s+int\s+"
    r"(?P<name>[A-Za-z0-9_]+)\s*=\s*(?P<id>\d+)\s*;"
)
AS3_ID = re.compile(
    r"(?P<direction>INCOMING_PACKETS|OUTGOING_PACKETS)\[(?P<id>\d+)\]\s*=\s*(?P<name>[A-Za-z0-9_]+)\s*;"
)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest().upper()


def duplicates(entries: list[dict[str, object]]) -> list[dict[str, object]]:
    by_id: dict[int, list[str]] = defaultdict(list)
    for entry in entries:
        by_id[int(entry["id"])].append(str(entry["name"]))
    return [
        {"id": packet_id, "names": names}
        for packet_id, names in sorted(by_id.items())
        if len(names) > 1
    ]


def duplicate_classes(entries: list[dict[str, object]]) -> list[dict[str, object]]:
    """MessageClassManager rejects one AS3 event/composer class assigned to multiple IDs."""
    by_name: dict[str, list[int]] = defaultdict(list)
    for entry in entries:
        by_name[str(entry["name"])].append(int(entry["id"]))
    return [
        {"name": name, "ids": ids}
        for name, ids in sorted(by_name.items())
        if len(ids) > 1
    ]


def parse_java(path: Path) -> list[dict[str, object]]:
    return [
        {"id": int(match.group("id")), "name": match.group("name")}
        for match in JAVA_ID.finditer(path.read_text(encoding="utf-8"))
    ]


def parse_client(path: Path) -> dict[str, list[dict[str, object]]]:
    result: dict[str, list[dict[str, object]]] = {
        "INCOMING_PACKETS": [],
        "OUTGOING_PACKETS": [],
    }
    for match in AS3_ID.finditer(path.read_text(encoding="utf-8")):
        result[match.group("direction")].append(
            {"id": int(match.group("id")), "name": match.group("name")}
        )
    return result


def conflicts(entries: list[dict[str, object]], reserved: tuple[int, ...]) -> list[dict[str, object]]:
    reserved_set = set(reserved)
    return [entry for entry in entries if int(entry["id"]) in reserved_set]


def expected_entries(ids: tuple[int, ...], names: list[str]) -> list[dict[str, object]]:
    return [{"id": packet_id, "name": name} for packet_id, name in zip(ids, names, strict=True)]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--client-root", type=Path, default=DEFAULT_CLIENT_ROOT)
    parser.add_argument("--output", type=Path, default=OUTPUT)
    args = parser.parse_args()

    client_path = args.client_root.resolve() / CLIENT_REGISTRY
    paths = [SERVER_INCOMING, SERVER_OUTGOING, client_path]
    missing = [str(path) for path in paths if not path.is_file()]
    if missing:
        raise SystemExit("Missing registry file(s): " + ", ".join(missing))

    server_incoming = parse_java(SERVER_INCOMING)
    server_outgoing = parse_java(SERVER_OUTGOING)
    client = parse_client(client_path)

    registry_duplicates = {
        "serverIncoming": duplicates(server_incoming),
        "serverOutgoing": duplicates(server_outgoing),
        "clientIncoming": duplicates(client["INCOMING_PACKETS"]),
        "clientOutgoing": duplicates(client["OUTGOING_PACKETS"]),
    }
    class_duplicates = {
        "clientIncoming": duplicate_classes(client["INCOMING_PACKETS"]),
        "clientOutgoing": duplicate_classes(client["OUTGOING_PACKETS"]),
    }
    snowstorm_entries = {
        "serverIncoming7700To7713": conflicts(server_incoming, CLIENT_TO_SERVER),
        "clientOutgoing7700To7713": conflicts(client["OUTGOING_PACKETS"], CLIENT_TO_SERVER),
        "serverOutgoing7800To7816": conflicts(server_outgoing, SERVER_TO_CLIENT),
        "clientIncoming7800To7816": conflicts(client["INCOMING_PACKETS"], SERVER_TO_CLIENT),
    }
    expected = {
        key: expected_entries(CLIENT_TO_SERVER if "7700To7713" in key else SERVER_TO_CLIENT, names)
        for key, names in EXPECTED.items()
    }
    all_empty = all(not entries for entries in snowstorm_entries.values())
    all_registered = all(snowstorm_entries[key] == expected[key] for key in expected)
    phase = "available" if all_empty else ("registered" if all_registered else "invalid-partial-or-conflicting")
    unexpected = {
        key: [] if snowstorm_entries[key] == expected[key] or not snowstorm_entries[key]
        else snowstorm_entries[key]
        for key in snowstorm_entries
    }

    report = {
        "schemaVersion": 2,
        "contract": "SnowStorm v1",
        "reserved": {
            "clientToServer": list(CLIENT_TO_SERVER),
            "serverToClient": list(SERVER_TO_CLIENT),
        },
        "files": [
            {"role": "serverIncoming", "path": SERVER_INCOMING.as_posix(), "sha256": sha256(SERVER_INCOMING)},
            {"role": "serverOutgoing", "path": SERVER_OUTGOING.as_posix(), "sha256": sha256(SERVER_OUTGOING)},
            {"role": "clientRegistry", "path": client_path.as_posix(), "sha256": sha256(client_path)},
        ],
        "parsedCounts": {
            "serverIncoming": len(server_incoming),
            "serverOutgoing": len(server_outgoing),
            "clientIncoming": len(client["INCOMING_PACKETS"]),
            "clientOutgoing": len(client["OUTGOING_PACKETS"]),
        },
        "existingRegistryDuplicates": registry_duplicates,
        "existingClientClassDuplicates": class_duplicates,
        "snowstormRangePhase": phase,
        "snowstormRangeEntries": snowstorm_entries,
        "snowstormRangeUnexpected": unexpected,
    }
    report["passed"] = (not any(registry_duplicates.values())
                        and not any(class_duplicates.values())
                        and (all_empty or all_registered))

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(args.output.resolve())
    print("PASS" if report["passed"] else "FAIL")
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())

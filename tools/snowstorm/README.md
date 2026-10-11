# SnowStorm evidence and generation tools

This directory contains deterministic evidence and generation tooling for the original r26
Shockwave SnowStorm port. It does not use the later AIR SnowWar implementation as gameplay truth.

## Evidence audit

Install the pinned cross-version Director parser outside the repositories:

```powershell
New-Item -ItemType Directory -Force C:/habbo/ccttoswf/.tools/projectorrays
npm install --prefix C:/habbo/ccttoswf/.tools/projectorrays projectorrays@1.1.1 --ignore-scripts --no-audit --no-fund
```

Then run:

```powershell
node C:/habbo/server/arcturus-community/tools/snowstorm/audit_evidence.mjs
```

The audit hashes the target/comparison casts, pins source revisions and licences, inventories both
Director generations through ProjectorRays, maps all 29 recovered r26 scripts to their cast IDs, and
writes its deterministic reports under `tools/snowstorm/evidence`.

It also extracts the exact r26 shared runtime scripts from `hh_gamesys.cct` and `hh_games.cct` into
`evidence/shared_gamesystem_scripts.json`. SnowStorm delegates common turn, checksum and lobby
behavior to those casts, so they are first-party evidence rather than a later-client reconstruction.

The Habbo CCTs are preservation/reference evidence. No redistribution licence is present beside the
archive. Igor's recovery is GPL-3.0, the local Havana snapshot is AGPL-3.0, Quackster's guide is
Apache-2.0, and ProjectorRays is MPL-2.0.

## Source precedence

1. r26 casts, resources and raw bytecode.
2. Cleaned Lingo only after matching it to raw bytecode.
3. 14.1 b8 for historical comparison.
4. Havana only where r26 independently agrees.
5. Quackster's guide as an index/explanation.
6. The later AIR `src/snowwar` package is excluded as gameplay truth.

Generated files must be rerun and compared before a Gate 0 or Gate 1 checklist item is marked
complete.

Generate the original shared-runtime checksum vectors with:

```powershell
node C:/habbo/server/arcturus-community/tools/snowstorm/generate_core_fixtures.mjs
```

The output freezes signed overflow, arithmetic right shift, sync-field weighting and global numeric
object-ID ordering for the later Java/AS3 parity tests.

Generate the canonical seven-map candidate manifest, floor/collision text fixtures and review PNGs
with:

```powershell
python C:/habbo/server/arcturus-community/tools/snowstorm/generate_maps.py
```

This generator deliberately preserves raw Havana capture values while replacing its approximate
collision heights with the r26 variable-index values. It separates Director `locZ` visual sorting
from physical altitude and hashes the calibrated r26 arena previews as placement evidence. All seven
reference/overlay pairs and the combined contact sheet were reviewed after generation; maps 1..3
contain 1/1/3 machines and maps 4..7 are intentionally machine-free.

Generate the bounded deterministic spawn allocation fixtures with:

```powershell
python C:/habbo/server/arcturus-community/tools/snowstorm/generate_spawn_fixtures.py
```

The generator interprets the captured radius/minimum-distance fields in tile-space Euclidean units,
deduplicates physical tiles, and uses a stable farthest-legal search capped at 64 attempts. Every map
must allocate the r26 maximum of 12 players; the output is
`fixtures/spawn_allocation_fixtures.json`.

Generate the immutable SQL rows plus the AS3 and Java runtime map payloads from that same canonical
manifest with:

```powershell
python C:/habbo/server/arcturus-community/tools/snowstorm/generate_runtime_maps.py
```

The command writes `generated/snowstorm_map_seed.sql` and
`generated/SnowStormGeneratedMapData.as`, writes that exact AS3 payload into
`C:/habbo/client/habbo-client-clean/src/com/sulake/habbo/snowstorm/data`, writes the deterministic
compressed Java payload to `SnowStormGeneratedMapData.java`, replaces only the marked seed block in
standalone `sqlupdates/snowstorm.sql`, and synchronizes the marked complete install block in the
production one-off. Settings use `INSERT IGNORE`, while map/item/machine/spawn replacement is
transactional. The generated client, server and SQL payloads carry the same canonical revision,
checksum and SHA-256. Verify every destination without rewriting it with:

```powershell
python C:/habbo/server/arcturus-community/tools/snowstorm/generate_runtime_maps.py --check
```

Verify all SQL placements and reconstruct the client JSON byte-for-byte with:

```powershell
python C:/habbo/server/arcturus-community/tools/snowstorm/verify_runtime_maps.py
```

Generate movement, coordinate, exact square-root/direction/velocity, complete quick/short/long
projectile paths, collision boundaries, state/scoring order and approved machine-cadence vectors
with:

```powershell
python C:/habbo/server/arcturus-community/tools/snowstorm/generate_simulation_fixtures.py
```

Machine vectors deliberately preserve Havana's externally visible initial/immediate/3-second/
1-second behavior while replacing its independent wall-clock jobs and race-prone collector lookup
with deterministic subturn timing and object-ID arbitration.

The projectile generator uses the raw r26 bytecode ordering. In particular, the recovered cleaned
Lingo omitted parentheses around `(offset² - temp²) * multiplier + baseHeight`; the fixtures retain
the proved bytecode expression rather than the misleading cleaned rendering. Havana's wall-clock
flight jobs, 3,000 velocity, landing-tile collision, distance quick TTL and 22-tile cap are rejected.

Verify the packet ranges against all four current client/server directional registries:

```powershell
python C:/habbo/server/arcturus-community/tools/snowstorm/check_protocol_ids.py
```

The command accepts either a completely free pre-registration range or the complete exact v1
registration in all four directions. It rejects duplicates, partial registration and unexpected
occupation of client-to-server `7700..7711` / server-to-client `7800..7816`, and writes the hashed
proof to `fixtures/protocol_registry_report.json`.

`hh_games.cct` and `hh_gamesys.cct` are first-party shared-runtime evidence only. The already
converted `hh_games.swf` remains owned by the seated Games Hall widget and is not loaded, modified or
reused by SnowStorm.

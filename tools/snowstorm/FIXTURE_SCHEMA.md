# SnowStorm deterministic fixture contract

This is the frozen Gate 1 interchange contract for the independent Java and AS3 simulations. Both
runtimes must deserialize these version-1 JSON documents in tests and reproduce every expected
value without platform floating-point, iteration-order or rounding behavior.

## Common rules

- JSON integers are signed 32-bit simulation values unless a field explicitly holds an ID/count.
- Object collections that affect state or checksums are ordered by ascending numeric object ID.
- Coordinates are world units; one tile is 3,200 units and one avatar subturn is 640 units.
- Division truncates toward zero. Overflow wraps as signed 32-bit. Right shifts are arithmetic.
- Unknown `schemaVersion`, fields required below but missing, duplicate IDs, oversized collections
  and values outside documented enums are test failures, not forward-compatible live state.

## `maps/snowstorm_maps.json`

Top level: `schemaVersion`, `revision`, `status`, `canonicalChecksum`, `canonicalSha256`, provenance,
review evidence, and exactly seven `maps`.

Each map contains its `mapId`, `revision`, dimensions/origins, 50 rectangular `floorRows` and
`collisionRows`, ordered visual `items`, independent `collisionObjects`, `machines`,
`spawnClusters`, and its runtime `checksum`/`sha256`. Review paths/hashes do not contribute to the
runtime checksum. Runtime checksums include geometry, exact cast-member metadata, walking and
projectile heights, machine rules and spawn rules.

## `fixtures/core_math_fixtures.json`

- `seedVectors`: input and exact signed XOR-shift output.
- `contributionVectors`: insertion-ordered integer values and weighted contribution.
- `checksumVectors`: turn seed, unsorted input objects, required sorted IDs and final checksum.

## `fixtures/simulation_fixtures.json`

- `coordinateVectors`, `directionVectors`, `neighbourChoiceVectors`, `movementVectors`.
- `reservationContention` and `subturnOrder`, including avatar-centric pre-projectile collision.
- `collisionBoundaries` for strict horizontal radius, vertical height and occupied-height equality.
- `projectileVectors.cases` and `obstacleBoundaryCases`. Every case records trajectory, start,
  target, direction, initial TTL, parabola offset and each post-advance subturn X/Y/Z/TTL/removal.
- `stateMachine` action permissions, creation completion/cancellation and knockout lifecycle.
- `scoringVectors` and `simultaneousHitOrder` for FFA, enemy, friendly, self and multi-hit behavior.
- `machineVectors` for initial collection, sustained cadence, no-collector and contention behavior.

Trajectory enums are `0=quick`, `1=short`, `2=long`; activity states are `0=normal`, `1=creating`,
`2=stunned`, `3=invincible`. A path record describes state after that subturn's movement and ground
test. `removed=true` is terminal.

## `fixtures/spawn_allocation_fixtures.json`

Each of seven map records contains the immutable map checksum, candidate count, bounded successful
attempt and exactly twelve unique legal tile positions satisfying the frozen global separation
rule. Java and AS3 need not share a random generator at runtime; the server is authoritative for
spawn selection, while both must validate the supplied positions against the same immutable map.

## Compatibility rule

The schema is frozen before production simulation work. A required semantic change must update the
protocol, evidence matrix, generator and both runtime tests atomically. The `version 1` label is a
shared-build contract, not a legacy migration promise.

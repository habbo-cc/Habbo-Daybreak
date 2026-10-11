# Original BattleBall protocol (version 2)

This is the NGH protocol for the original Shockwave `hh_game_bb` game, not Battle Banzai.
Version 2 is the only supported format and server/client changes must be deployed atomically. Unless
specified otherwise, integers are signed 32-bit values, strings use Arcturus length-prefixed UTF-8,
and booleans use the normal Arcturus one-byte encoding. Receivers reject invalid counts, identifiers,
coordinates, enum values, versions, and trailing payload.

BattleBall uses the hotel-wide ticket currency configured by `hotel.ticket.currency.type` (default
106). Headers 7515, 7621 and 7622 are neutral ticket purchase/balance messages shared with other
classic ticketed public-room activities; they retain their existing wire layouts.

The former `battleball.ticket.currency.type` key is migration input only. Both BattleBall SQL paths
copy its value to `hotel.ticket.currency.type` when the neutral key does not already exist, create
the neutral key with value 106 when neither exists, and then remove the old key. An administrator's
existing neutral value is never overwritten.

## Settings

| Setting | Default | Meaning |
|---|---:|---|
| `hotel.ticket.currency.type` | `106` | Hotel-wide ticket activity-point type shared by classic ticketed public-room activities |
| `battleball.ticket.cost` | `2` | Tickets charged to each human participant immediately before a BattleBall round starts |
| `battleball.ranked.requires.human.opponent` | `1` | Requires humans on at least two teams before a round can update ranked BattleBall results |

The currency type is shared; the entry cost and ranking policy remain BattleBall-specific. A future
SnowStorm implementation therefore uses the same balance but owns its own `snowstorm.ticket.cost`.

## Shared limits and enums

- Status protocol version: `2`
- Instance name: 1-32 trimmed characters
- Map: `1..5`
- Teams: `2..4`; team IDs are zero-based
- Players per team: 2 teams allow `1..6`, 3 teams `1..4`, and 4 teams `1..3` (12 total)
- Round duration: `60..600` seconds
- Enabled powers: distinct IDs `1..8`, at most 8. Power `9` is the question-mark pickup and resolves
  server-side to one enabled power.
- Instance state: `0=lobby`, `1=countdown`, `2=running`, `3=ending`, `4=disposed`
- Tile state: `0=default`, `1=touched`, `2=clicked`, `3=pressed`, `4=sealed`
- Tile colour: `-2=disabled`, `-1=default`, `0=red`, `1=blue`, `2=yellow`, `3=green`
- Player state: `0=normal`, `1=stunned`, `2=turbo`, `3=high-jumps`, `4=cleaning`,
  `5=colouring-for-opponent`, `6=entering-cannon`, `7=flying`, `8=ball-broken`
- Stable failure reasons: `0=unknown`, `1=not-found`, `2=not-owner`, `3=bad-state`,
  `4=full`, `5=bad-team`, `6=bad-parameters`, `7=insufficient-tickets`,
  `8=already-in-game`, `9=not-enough-teams`, `10=not-a-player`, `11=not-allowed`
- Player removal reasons: `0=kicked`, `1=idle`

## Client to server

| Header | Name | Payload |
|---:|---|---|
| 7500 | GetInstanceList | none |
| 7504 | SetGameParameters | `name:string, mapId:int, roundSeconds:int, teamCount:int, teamCapacity:int, selectedTeam:int, private:boolean, powerCount:int, powerId:int...` |
| 7505 | Delete | `instanceId:int` |
| 7506 | InitiateJoin | `instanceId:int` |
| 7507 | SetJoinParameters | `instanceId:int, selectedTeam:int` |
| 7508 | Leave | `instanceId:int` |
| 7509 | Kick | `instanceId:int, targetUserId:int` |
| 7510 | Watch | `instanceId:int` |
| 7511 | Start | `instanceId:int` |
| 7512 | GameEvent | `instanceId:int, subtype:int`, then subtype `2`: `x:int,y:int`; subtype `4`: `powerObjectId:int` |
| 7514 | RequestFullStatus | `instanceId:int` |
| 7515 | BuyTickets | `bundleId:int, recipient:string` (empty recipient means self) |
| 7516 | AddBot | `instanceId:int, teamId:int` |
| 7517 | RemoveBot | `instanceId:int, botUserId:int` |

Movement and power activation are requests only. Arcturus remains authoritative for paths, tile
ownership, pickup ownership, effects, scoring, and match state.

### Shared ticket purchase (7515)

The neutral hotel-ticket handler accepts two established bundles:

- Bundle 1 costs 1 credit and adds 2 tickets.
- Bundle 2 costs 6 credits and adds 20 tickets.
- An empty recipient buys for the requesting user. A non-empty recipient is trimmed, limited to 32
  characters on the wire, and resolved by exact database username; that recipient may be offline.

Purchases are limited to one request per second per client. The server validates the entire payload
and rejects trailing data. Buyer credit debit, recipient lookup and ticket credit occur in one
database transaction. A failed lookup, insufficient balance or database failure changes neither
wallet. Online in-memory balances are updated only after commit, then the buyer receives the normal
credit-balance packet and the recipient receives both 7621 and the normal activity-point update.

## Server to client

All game-bearing messages carry `instanceId`. Status packets carry a monotonically increasing
mutation `sequence`; a 500 ms delta may aggregate multiple mutations. A delta also carries the
server's global per-game `baseSequence`. Stale or duplicate mutation sequences are ignored. A base
at or behind the client's current sequence is safe because the client already holds that state; only
a base ahead of the client proves a gap and requests a throttled 7514/7612 recovery snapshot.

| Header | Name | Payload |
|---:|---|---|
| 7600 | LoungeInfo | `protocolVersion:int, ticketCost:int, playerSkillLevel:int, ticketBalance:int` |
| 7601 | InstanceList | `count:short`, then instance summaries |
| 7602 | GameInstance | one instance summary |
| 7603 | InstanceNotAvailable | `instanceId:int, reason:int` |
| 7605 | CreateFailed | `reason:int` |
| 7606 | GameDeleted | `instanceId:int` |
| 7607 | JoinParameters | `instanceId:int, teamCount:byte, teamCapacity:byte, playerCount:byte[teamCount]` (team IDs are implicit) |
| 7608 | JoinFailed | `instanceId:int, reason:int` |
| 7609 | WatchFailed | `instanceId:int, reason:int` |
| 7610 | GameLocation | `instanceId:int, mapId:byte, spectator:boolean` |
| 7611 | StartFailed | `instanceId:int, reason:int` |
| 7612 | FullGameStatus | full snapshot below |
| 7613 | GameStatus | delta below |
| 7614 | PlayerRemoved | `instanceId:int, reason:int` |
| 7615 | GameStart | `instanceId:int, sequence:int, countdownMs:short, roundSeconds:short` |
| 7616 | GameEnd | `instanceId:int, sequence:int, winningTeam:byte, endingMs:int`, team scores, then player scores |
| 7617 | SpectatorCount | `instanceId:int, watcherCount:int` |
| 7619 | IdleWarning | `instanceId:int, secondsRemaining:int` |
| 7620 | SkillLevelChanged | `skillLevel:int` |
| 7621 | TicketBalance | `ticketBalance:int` |
| 7622 | TicketPurchaseResult | `status:int, recipient:string, ticketsAdded:int` |

Ticket purchase result statuses are `0=success`, `1=insufficient-credits`,
`2=recipient-not-found`, `3=invalid-bundle`, and `4=database-error`. Failure responses carry an
empty recipient and zero tickets added. On success, `recipient` is the database-canonical username.

An instance summary is:

`instanceId:int, name:string, ownerUserId:int, ownerName:string, mapId:byte, state:byte,
private:boolean, teamCount:byte, playerCount:byte, capacity:byte, roundSeconds:short`

Team scores are `count:byte`, followed by `(teamId:byte, score:int)`. Final player scores are
`count:short`, followed by `(userId:int, score:int)`; negative user IDs are match-local bots.
An aborted round uses `winningTeam=-1`, remains recorded for ticket/accounting reconciliation, and
does not update ranked player aggregates, skill levels, or the BattleBall high-score ranking. The
existing leaderboard backend may be reused internally, but BattleBall is not categorized as a Game
Hall game.

## Full snapshot (7612)

```text
protocolVersion:int, instanceId:int, sequence:int, state:int, timeRemainingMs:int,
objectCount:short, fullObject[objectCount], width:int, height:int,
packedTile:short[width*height], teamScores
```

Full object records start with `objectType:byte`:

- Player `0`: `objectId:int, userId:int, x:byte, y:byte, direction:byte, playerState:byte,
  colouringForUserId:int, name:string, figure:string, teamId:byte`
- Pickup `1`: `objectId:int, holderObjectId:int, powerType:byte, secondsRemaining:byte, x:byte, y:byte`
- Pin `2`: `objectId:int, x:byte, y:byte`

Motto, sex, room index, TTL, and Z are deliberately absent. They are not used by BattleBall; Z is
derived from immutable map geometry client-side.

Full tiles are dense row-major values, so coordinates and count are implicit. Each positive short
packs `colour + 2` in bits 0..2, tile state in bits 3..5, floor height in bits 6..9, and walkability
in bit 10. Bits 11..15 must be zero. A 28x28 board therefore costs 1,568 bytes for its tile section.

## Delta (7613)

```text
protocolVersion:int, instanceId:int, baseSequence:int, sequence:int, state:int,
timeRemainingMs:int,
spawnCount:short, fullObject[spawnCount],
updateCount:short, dynamicObject[updateCount],
removeCount:short, objectId:int[removeCount],
boardReset:boolean,
priorityTileCount:short, deltaTile[priorityTileCount],
fillTileCount:short, deltaTile[fillTileCount],
teamScorePatches,
effectCount:short, effectType:byte[effectCount]
```

Dynamic object records contain only mutable fields:

- Player `0`: `objectId:int, x:byte, y:byte, direction:byte, playerState:byte,
  colouringForUserId:int, teamId:byte` (14 bytes including type)
- Pickup `1`: `objectId:int, holderObjectId:int, powerType:byte, secondsRemaining:byte, x:byte, y:byte`
- Pin `2`: `objectId:int, x:byte, y:byte`

Object operations are deduplicated per tick. A spawn and removal in the same tick cancel. Board
reset is atomic and sends every retained player as a full spawn so newly joined players and bots do
not depend on an earlier queued delta.

A delta tile is `x:byte, y:byte, packed:byte`. The packed byte holds `colour + 2` in bits 0..2 and
tile state in bits 3..5; bits 6..7 are zero. Floor height and walkability remain from the full
snapshot. Priority tiles are direct landings/power effects and animate before flood-fill tiles.

Delta team scores are patches: only changed teams are sent and the client merges them atomically.
Effect types are presentation hints only: `3=pickup`, `4=stun`, `5=activation`. Authoritative object,
tile, and score state is carried separately in the same sequence.

## Lifecycle and reliability

- Requesting 7500 subscribes the user to lobby updates; only that lounge audience receives 7602/7606.
- Creation and joining leave players in the hotel room while the lobby window is open. Start removes
  participants from the lounge audience and enters the isolated arena.
- Creation/start are idempotent where retrying could otherwise double-charge. Tickets are charged
  atomically once per active human participant immediately before the round enters countdown.
- One scheduled task per game publishes compact deltas every 500 ms. Start does not send an immediate
  full snapshot; the next delta carries the atomic board reset.
- Join/watch sends GameLocation, after which the client requests one full snapshot. Normal play uses
  deltas; only a sequence gap or an unconfirmed local countdown transition requests recovery.
- New joins are accepted only in the lobby. New watchers are accepted only during countdown/running.
  A retained watcher can become a player after the game returns to its lobby.
- Watchers receive the same status stream but cannot move or activate powers. SpectatorCount is an
  optional side channel sent on watcher membership changes and full recovery; it deliberately does
  not alter the positional version-2 snapshot layouts.
- Team switching, adding/removing bots and kicking share one per-user/game 500 ms mutation bucket.
- Kicked and idle-removed users receive PlayerRemoved directly so they can release the arena and
  return to the lounge even though they are no longer status-stream recipients.
- Disconnect removes membership immediately. A finished match remains available for standings until
  participants leave; abandoned games are collected by the normal ownerless/disposal path.

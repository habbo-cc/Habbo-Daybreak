# Original Shockwave SnowStorm protocol (version 4)

This is the NGH client/server contract for the original Director/Shockwave SnowStorm game recovered
from `hh_game_snowwar.cct`, `hh_game_snowwar_room.cct` and `hh_game_snowwar_ui.cct`. It recreates
that game's lobby, rules, synchronized arena and result flow in the AIR client.

It is deliberately unrelated to the later Flash/AIR SnowWar implementation already present under
`habbo-client-clean/src/snowwar`. The later implementation's queue, stages, UI, message classes and
packet ranges `5000..5029` / `6000..6026` are out of scope and must not be used to drive this port.
The new client component lives under `com.sulake.habbo.snowstorm`; the server implementation lives
under `com.eu.habbo.habbohotel.games.snowstorm`.

Version 3 is the shared contract name, not a migration project or compatibility layer. Client and
server are deployed atomically; there is no legacy compatibility or migration handshake. Packet
layouts were frozen at Gate 2 and must change atomically in both registries and their round-trip
tests. Unless stated otherwise,
integers are signed 32-bit,
shorts are signed 16-bit, bytes are unsigned values stored in one byte, strings use Arcturus
length-prefixed UTF-8, and booleans use the normal Arcturus one-byte encoding. Receivers reject
invalid counts, identifiers, coordinates, enums and trailing payload.

## Product invariants

- The original Shockwave casts and Lingo behavior are the product truth.
- SnowStorm uses its own game simulation. No BattleBall tile, power-up, scoring or arena code is
  allowed in SnowStorm.
- SnowStorm uses the same hotel ticket balance and ticket shop as BattleBall.
- The server is authoritative. Client messages contain intentions, never accepted positions, hits,
  health, snowball counts, scores or match results.
- The client performs the same deterministic subturn simulation for smooth presentation and verifies
  it with checksums. A mismatch recovers through a full snapshot.
- The lobby is a native WindowManager window hosted over the current hotel room. The arena embeds a
  dedicated private RoomEngine room and non-desktop canvas inside its native window, following the
  `BattleBallArena` integration pattern without sharing BattleBall game state.
- Arena avatars are real private-room RoomEngine avatars. Their SnowStorm coat/team figure is derived
  locally from the authoritative base figure and team; the hotel avatar and persisted account figure
  are never mutated.

## Shared hotel tickets

Tickets are a hotel-wide currency historically shared by BattleBall, SnowStorm and other ticketed
public-room activities. They are neither BattleBall-owned nor part of the separate Game Hall
category. Implementation must extract the current BattleBall ticket repository/manager behavior
into a neutral ticket service while keeping the existing wire layout stable.

- Currency type: `hotel.ticket.currency.type`, default `106`.
- SnowStorm match cost: `snowstorm.ticket.cost`, default `2`.
- BattleBall continues using `battleball.ticket.cost` against the same currency type.
- Bundle 1: one credit buys two tickets.
- Bundle 2: six credits buy twenty tickets.
- An empty recipient buys for the sender; a non-empty recipient gifts the tickets.
- Purchases and match charges are database transactions and update in-memory wallet state only from
  the committed balances.
- Every committed change sends the normal activity-point notification for currency type `106` and a
  shared ticket-balance packet so every visible classic-game ticket counter updates immediately.

The following existing packet IDs become neutral hotel-ticket packets. Renaming their Java/AS3
classes is allowed, but their numeric IDs and payloads must not change.

| Direction | Header | Name | Payload |
|---|---:|---|---|
| Client to server | 7515 | BuyTickets | `bundleId:int, recipient:string` |
| Server to client | 7621 | TicketBalance | `ticketBalance:int` |
| Server to client | 7622 | TicketPurchaseResult | `status:int, recipient:string, ticketsAdded:int` |

Purchase statuses retain the already-shipped neutral/BattleBall contract:
`0=success`, `1=insufficient-credits`, `2=recipient-not-found`, `3=invalid-bundle`, and
`4=database-error`. An authenticated packet always supplies the buyer, so there is no separate
buyer-not-found status. BattleBall and SnowStorm controllers both consume 7621 and 7622. Only the
controller with an open purchase dialog renders the purchase result; both update their cached
balance.

## Shared limits and enums

- Protocol version: `3`.
- Game name: 1..32 trimmed characters.
- Maps: `1..7`.
- Team count: `1..4`; team IDs are zero-based.
- Total human players: at most 12.
- Team capacity is fixed by the original layouts: `1 team=12`, `2 teams=6 each`,
  `3 teams=4 each`, `4 teams=3 each`. It is not a creation option.
- Length choice: `1=120 seconds`, `2=180 seconds`, `3=300 seconds`.
- Spectators per game: at most 20.
- Live snowballs per game: at most 320; total dynamic objects are at most 337 (12 avatars, five
  machines and 320 snowballs).
- Incremental events: at most 128 per subturn and 512 per five-subturn status.
- Finished rows published to a lounge subscriber: at most 15.
- Instance states: `0=lobby`, `1=countdown`, `2=running`, `3=ending`, `4=disposed`.
- Instance row kinds: `0=live`, `1=finished`.
- Activity states: `0=normal`, `1=creating-snowball`, `2=stunned`, `3=invincible`.
- Trajectories: `0=quick`, `1=short-lob`, `2=long-lob`.
- Directions: avatar direction is `0..7`; projectile direction is `0..359`.
- World coordinates are signed integer units; one tile is exactly 3,200 units.
- A one-team match is free-for-all: every other player is opposition and results are ordered by
  individual score. Multi-team matches aggregate player scores by team.
- Starting always requires opposition: two players in deathmatch or two occupied sides in team
  modes, counting NGH bots. Ranked persistence separately requires genuine human opposition when
  `snowstorm.ranked.requires.human.opponent=1`; bot-practice rounds still run and charge humans.

Stable failure reasons:

`0=unknown`, `1=not-found`, `2=not-owner`, `3=bad-state`, `4=full`, `5=bad-team`,
`6=bad-parameters`, `7=insufficient-tickets`, `8=already-in-game`,
`9=not-enough-opposition`, `10=not-a-player`, `11=not-allowed`, `12=rate-limited`,
`13=map-unavailable`, `14=protocol-mismatch`.

Failure operations:

`0=lounge`, `1=create`, `2=delete`, `3=join`, `4=team`, `5=leave`, `6=kick`,
`7=watch`, `8=start`, `9=game-event`, `10=full-status`, `11=finished-result`,
`12=add-bot`, `13=remove-bot`.

## Ownership boundary

The Java game owner task exclusively mutates membership, reservations, object IDs, simulation state,
machine cadence, health, ammo, scores, sequence/checksum state and terminal results under one
per-instance lock. Packet handlers validate and enqueue intentions; they never mutate game objects
directly. The repository owns transactions and durable snapshots but performs no simulation.

The AS3 component owns native windows, input translation, presentation-only prediction, the private
RoomEngine world/canvas and deterministic checksum mirror. It never decides a hit, balance, score,
spawn, placement or winner. A stale/gapped delta cannot mutate the view; it enters bounded snapshot
recovery. Disposing SnowStorm releases only SnowStorm objects and never modifies the hotel room,
camera, zoom, `HabboInfo` or persisted figure.

The neutral hotel ticket service owns currency type 106, purchases and atomic roster charging.
BattleBall and SnowStorm are consumers of that one service and the unchanged 7515/7621/7622 wire
family. Neither game caches an independently authoritative ticket balance.

Durable round records are immutable accounting facts once charged. Live manager state may reset for
the next game, but finished rows and result packets are reconstructed from the stored result ID and
starting roster rather than mutable lobby objects.

## Client to server

The ranges `7700..7713` and `7800..7816` are the current allocation. Gate 2 parsed the current
server incoming/outgoing and client incoming/outgoing registries, found no directional duplicates
or range conflicts, and preserved the proof in `tools/snowstorm/fixtures/protocol_registry_report.json`
(current SHA-256 `CF160E3DBBC09DB15FD864829898A7AFB592829226A700D92CB416B5087820CF`). The checker also rejects
assigning one AS3 event or composer class to multiple packet IDs, matching `MessageClassManager`'s
runtime constraint. It must pass
again after production registrations are added.

| Header | Name | Payload |
|---:|---|---|
| 7700 | SnowStormGetLounge | none |
| 7701 | SnowStormSetGameParameters | creation payload below |
| 7702 | SnowStormDelete | `instanceId:int` |
| 7703 | SnowStormInitiateJoin | `instanceId:int` |
| 7704 | SnowStormSetJoinParameters | `instanceId:int, selectedTeam:byte` |
| 7705 | SnowStormLeave | `instanceId:int` |
| 7706 | SnowStormKick | `instanceId:int, targetUserId:int` |
| 7707 | SnowStormWatch | `instanceId:int` |
| 7708 | SnowStormStart | `instanceId:int` |
| 7709 | SnowStormGameEvent | action payload below |
| 7710 | SnowStormRequestFullStatus | `instanceId:int, lastSequence:int, reason:byte` |
| 7711 | SnowStormRequestFinishedGame | `resultId:int` |
| 7712 | SnowStormAddBot | `instanceId:int, selectedTeam:byte` |
| 7713 | SnowStormRemoveBot | `instanceId:int, botUserId:int` (strictly negative) |

Creation payload:

```text
name:string, mapId:byte, lengthChoice:byte, teamCount:byte,
selectedTeam:byte
```

Creation opens a lobby instance but does not charge tickets. The creator joins the selected team.
The owner may change team through 7704 while the instance remains in the lobby.

Game action payload:

```text
instanceId:int, clientActionSequence:int, subtype:byte, subtype fields...
```

| Subtype | Action | Fields |
|---:|---|---|
| 0 | Walk | `targetWorldX:int, targetWorldY:int` |
| 1 | Throw at player | `targetObjectId:int, trajectory:byte` |
| 2 | Throw at location | `targetWorldX:int, targetWorldY:int, trajectory:byte` |
| 3 | Make snowball | none |
| 4 | Cancel making snowball | none |

`clientActionSequence` is monotonically increasing per client/game. Repeated or older requests are
dropped. It provides deduplication only and is never used as simulation state.

The server validates that the sender belongs to the instance, is not a spectator, the game is
running, the activity state permits the action, a targeted avatar is active and eligible, the
trajectory is valid, cooldowns are satisfied, and requested coordinates fall within the loaded map
bounds. It does not copy Havana's precomputed tile-path clipping: once launched, the proved
per-subturn world-coordinate height, ground and avatar collision decides the impact.

Full-status reasons are `0=initial-location`, `1=sequence-gap`, `2=checksum-mismatch`,
`3=unconfirmed-transition`, `4=manual-debug`. Production clients must not send reason 4.

## Server to client

| Header | Name | Payload |
|---:|---|---|
| 7800 | SnowStormLoungeInfo | lounge payload below |
| 7801 | SnowStormInstanceList | `count:short`, instance summary repeated |
| 7802 | SnowStormGameInstance | one instance summary |
| 7803 | SnowStormInstanceRemoved | `rowKind:byte, id:int` |
| 7804 | SnowStormJoinParameters | join payload below |
| 7805 | SnowStormOperationFailed | `operation:byte, instanceId:int, reason:byte` |
| 7806 | SnowStormGameLocation | location payload below |
| 7807 | SnowStormGameReset | `instanceId:int, sequence:int, roundNumber:int` |
| 7808 | SnowStormGameStart | start payload below |
| 7809 | SnowStormFullGameStatus | full snapshot below |
| 7810 | SnowStormGameStatus | incremental turn below |
| 7811 | SnowStormGameEnd | final result below |
| 7812 | SnowStormPlayerRemoved | `instanceId:int, reason:byte` |
| 7813 | SnowStormSpectatorCount | `instanceId:int, watcherCount:short` |
| 7814 | SnowStormFinishedGame | finished-game detail below |
| 7815 | SnowStormSkillLevelChanged | `skillLevel:int, rankedPoints:int` |
| 7816 | SnowStormIdleWarning | `instanceId:int, secondsRemaining:int` |

Lounge payload:

```text
protocolVersion:int, ticketCost:int, skillLevel:int, rankedPoints:int,
ticketBalance:int, mapCount:byte, mapId:byte[mapCount]
```

`rankedPoints` is the player's non-negative all-time eligible SnowStorm score, clamped to the
signed v1 wire integer. `skillLevel` maps the original server-supplied SnowStorm rank bands to
stable values: `1=Beginner (0..10,000)`, `2=Amateur (10,001..100,000)`,
`3=Intermediate (100,001..500,000)`, and `4=Expert (500,001+)`. The r26 client supplied the
rank name and score range from its server rather than deriving arbitrary 5,000-point levels; these
period-authentic bands are therefore an explicit NGH server policy, not a client-side formula.

Requesting 7700 subscribes the player to live lobby changes, sends 7800, then sends 7801. The
subscription is removed when the player leaves the SnowStorm lounge, enters an arena, disconnects
or explicitly closes the component. Reopening the lobby always asks for a fresh authoritative
balance and list.

### Instance summary

```text
rowKind:byte, id:int, name:string, ownerUserId:int, ownerName:string,
mapId:byte, state:byte, teamCount:byte,
playerCount:byte, spectatorCount:short, lengthChoice:byte, resultId:int,
playerNameCount:byte, playerNames:string[playerNameCount]
```

For a live row, `id` is the instance ID and `resultId=-1`. For a finished row, `id` remains the
original instance ID, `state=3`, and `resultId` identifies the immutable persisted result.
`playerNameCount` must equal `playerCount`. Live names follow the waiting roster; finished names
follow persisted placement order and allow the lounge row to credit every participant rather than
displaying only the original owner.

The server sends 7802 whenever a visible row changes. The client upserts by `(rowKind,id,resultId)`;
it never appends blindly. Live rows are grouped by state and returned newest first within each
state. Finished rows are returned newest first and capped at 15.

### Joining and game location

Join parameters:

```text
instanceId:int, teamCount:byte, selectedTeam:byte,
  (playerCount:byte, (userId:int, name:string)[playerCount])[teamCount]
```

Players are grouped by ascending team ID and sorted by ascending user ID within each team. The
payload is a complete waiting-roster snapshot: the client replaces, rather than appends to, the
team lists whenever it arrives. This supplies the original game-info screen's player names and the
owner's kick targets without exposing figures or runtime object IDs before the round starts.

7703 requests the team-selection view. 7704 commits the selection. A user without enough tickets
receives reason 7; the client opens the shared ticket dialog instead of leaving a dead Join button.
Tickets are still not charged until the owner successfully starts the round.

Game location:

```text
instanceId:int, mapId:byte, spectator:boolean, playerObjectId:int,
mapRevision:int, mapChecksum:int
```

After 7806, the client creates the SnowStorm arena window, loads the matching immutable map and sends
7710 with reason 0. It does not reuse or mutate the hotel room's normal entity collection.

### Round start and ticket charging

Start payload:

```text
instanceId:int, sequence:int, roundNumber:int, countdownMs:short,
roundSeconds:short, ranked:boolean
```

The owner may start only from the lobby. The server performs one atomic transaction which:

1. Revalidates the immutable starting roster and mode-appropriate opposition, including bots.
2. Locks every human participant's currency row in stable user-ID order.
3. Verifies every human participant has `snowstorm.ticket.cost` tickets.
4. Debits each human participant exactly once; bots cost zero.
5. Inserts the round and complete starting roster (`human=0` for bots).
6. Commits, updates online wallet caches from committed values and publishes 7621/activity-point
   notifications.

Any validation or debit failure leaves all balances unchanged and returns 7805. Retrying 7708 is
idempotent for `(instanceId,roundNumber)` and cannot charge twice. Charged tickets are not refunded
when a participant leaves or disconnects.

## Synchronized simulation

The simulation is fixed, not emulator-configurable:

- Server tick: 300 ms.
- Subturns per tick: 5.
- Effective subturn: 60 ms.
- Tile size: 3,200 world units.
- Avatar movement: 640 world units per subturn.
- Initial/max health: 4.
- Initial/max carried snowballs: 5.
- Hit award: 1 point.
- Knockout award: 5 additional points.
- Create-snowball timer: 20 subturns.
- Stunned timer: 125 subturns.
- Post-stun invincibility: 60 subturns.
- Machine initial stock: 0.
- Machine maximum stock: 5.
- Machine collection begins with a deterministic refill attempt followed by a transfer attempt.
- While an eligible collector remains, refill attempts occur every 3,000 ms and transfer attempts
  every 1,000 ms. Due times are evaluated on the 60 ms subturn clock; they are never independent
  wall-clock jobs.
- A machine has one collection state. Machines and eligible collectors are processed by ascending
  object ID, so one available ball has one deterministic recipient and re-entry cannot create
  duplicate jobs.
- Quick throw TTL: 13 subturns.
- Quick throws have no additional Havana-style 22-tile target cap. Coordinates must still be valid
  for the selected map, but physical reach is determined solely by the proved fixed TTL and velocity.
- Quick/short/long horizontal velocities are 2,000 world units per subturn. Lob initial TTL is the
  r26 integer-square-root distance divided by 2,000; parabola offset is integer `TTL / 2`.
- Projectile Z is exactly `(offset² - temp²) * multiplier + baseHeight`: quick uses multiplier 4 and
  base 4,000, short uses 10/3,000, and long uses 100/3,000. Raw bytecode proves the parentheses that
  are missing from the cleaned Lingo rendering.
- Ground/obstacle collision occurs only when occupied height is strictly greater than projectile Z;
  equality passes. Avatar collision requires squared horizontal distance strictly below the combined
  2,000 radius and projectile Z strictly below 5,000; tangent and height equality pass.
- Normal and creating avatars can be physically hit. Stunned and post-stun-invincible avatars do not
  scan for projectile collision. A projectile never collides with its thrower. A stray projectile
  can physically hit and damage a teammate, but friendly damage awards no score; a direct targeted
  friendly intention is rejected before launch.
- Creating permits movement and throwing. A newly accepted move cancels creation without granting
  ammo; an accepted throw replaces the visible pick pose for its 400 ms animation but leaves the
  creation timer running, exactly as the r26 client does. Duplicate Make intentions are rejected.
- The original input mapping is retained inside the private arena: click another avatar for a quick
  throw; Shift-click the field for a short lob when released under one second; Shift-hold at least
  one second or Option/Alt-click for a long lob; click the own avatar or embedded Make control to
  create a ball. Ordinary field click walks. Native input may expose equivalent accessible controls,
  but it sends the same three trajectory enums.
- The server accepts at most one throw per avatar per 300 ms game turn. This is evaluated on the
  deterministic game clock, not wall-clock scheduler time.

Java and AS3 use signed 32-bit integer overflow semantics. Coordinate conversion, square root,
direction lookup, velocity lookup, division and rounding must use ported deterministic helpers; no
platform `Math.round`, floating-point angle or unordered collection iteration may influence state.

### Client arena HUD contract

The private arena window owns an embedded r26-style bottom HUD. This is client presentation over
authoritative state and adds no trusted gameplay fields:

- `health` controls the vertical green/yellow/red health meter and its proved fill height.
- `snowballs` controls exactly five visible inventory pips.
- Make Snowball sends action subtype 3 once; the server's activity/ammo state decides whether it
  starts and subsequent snapshots/events decide its pressed/disabled state.
- At maximum ammo the button is disabled. At zero ammo the original tray/button highlight animation
  may run while the player is active, but it does not create ammunition locally.
- Spectators, stunned players, game-over state and disposed arenas cannot activate it.
- The HUD belongs to `SnowStormArenaWindow`; it must never replace or mutate the hotel's global
  `RoomBarID`, even though the Director client historically merged `sw_ui.window` there.

Compact v3 object types (unchanged from version 2):

`0=avatar`, `1=snowball`, `2=snowball-machine`.

The r26 parser used a wider historical type table (`0=player-link`, `1=snowball`, `2=unused`,
`3=large-snowball`, `4=machine`, `5=avatar`). The compact v3 numbers are an NGH wire choice, not a
claim about the original values. The historical `large-snowball` branch is deliberately excluded:
r26 has a deserializer and collision constants but no registered game-object class, no processor
subscribed to its X/Y-only event 6 and no UI/action creation path; Havana also has no implementation.
Receiving its old object/event value is therefore a protocol error, not a partially supported state.

Static map obstacles are identified by the selected map and are not repeated in status packets.

### Full snapshot

```text
protocolVersion:int, instanceId:int, sequence:int, state:byte,
roundNumber:int, turnNumber:int, timeRemainingMs:int, playerObjectId:int,
ranked:boolean, teamCount:byte,
teamScoreCount:byte, (teamId:byte, score:int)[teamScoreCount],
objectCount:short, fullObject[objectCount], checksum:int
```

Objects are sorted by object ID. Counts above the documented maximum, duplicate IDs,
unknown object types and checksum disagreement make the snapshot invalid.

Avatar object:

```text
type:byte=0, objectId:int, userId:int, name:string, figure:string, sex:string,
teamId:byte, bot:boolean, worldX:int, worldY:int, bodyDirection:byte,
health:byte, snowballs:byte, activityState:byte, activityTimer:short,
nextGoalTileX:int, nextGoalTileY:int,
moveTargetX:int, moveTargetY:int, score:int,
standingDirection:byte, fallPose:byte
```

`nextGoalTileX/Y` are the currently reserved intermediate movement tile, or the avatar's current
rounded tile when no intermediate goal exists. They are required because both fields participate in
the deterministic checksum; omitting them would make a moving full snapshot impossible to verify.

`fallPose` is `0=none`, `1=face-down (ff)`, `2=face-up (fb)`. On knockout the server first applies
Shockwave's `stopMovement`: it snaps the avatar to its reserved tile, clears both movement targets,
stores `standingDirection`, chooses one death direction/pose, and freezes those values for the full
stunned timer. A recovery snapshot therefore cannot rotate or stand up an already-downed avatar.
When invincibility begins, `standingDirection` is restored and `fallPose` returns to zero.

`figure` and `sex` describe the authoritative base hotel avatar. They are not a pre-mutated account
look. For the private arena object, the client applies the r26 `fixSnowWarFigure` presentation rule:
SnowStorm action parts use model `snowwar`; a one-team match uses the player's chest colour when
present (white otherwise); a multi-team match uses team colours `#FF5136`, `#29C2FF`, `#FFDD36`,
and `#ED28FF` for teams 0..3; the SnowStorm shoes use that same selected colour. Humans and bots use
the same rule. This derived figure exists only on the private SnowStorm RoomEngine object and is
discarded with it.

Snowball object:

```text
type:byte=1, objectId:int, throwerObjectId:int,
worldX:int, worldY:int, height:int, direction:short, trajectory:byte,
timeToLive:short, parabolaOffset:short
```

Machine object:

```text
type:byte=2, objectId:int, worldX:int, worldY:int,
snowballCount:byte, refillTimer:short
```

`refillTimer` is the remaining deterministic subturn count for snapshot recovery; `-1` means no
eligible collector and therefore no active refill cadence. Transfer eligibility remains
server-owned and is represented by authoritative machine-transfer events rather than an independent
client timer.

### Incremental status

```text
protocolVersion:int, instanceId:int, baseSequence:int, sequence:int,
turnNumber:int, timeRemainingMs:int, state:byte, serverChecksum:int,
subturnCount:byte,
  eventCount:short, event[eventCount]
repeated subturnCount times,
teamScorePatchCount:byte, (teamId:byte, score:int)[teamScorePatchCount]
```

`state` is `1` (countdown) or `2` (running); it is carried on every delta so the countdown-to-running
transition cannot leave the client input-locked while turns continue. `subturnCount` is exactly 5.
`baseSequence` must equal the client's last applied sequence. A status
at or behind the current sequence is stale and ignored. A base ahead of the current sequence proves
a gap and triggers a backoff-controlled 7710 request.

Incremental event types:

| Type | Event | Fields |
|---:|---|---|
| 0 | Spawn object | one `fullObject` |
| 1 | Remove object | `objectId:int` |
| 2 | Avatar move | `objectId:int, worldX:int, worldY:int, targetWorldX:int, targetWorldY:int, nextGoalTileX:int, nextGoalTileY:int, bodyDirection:byte` |
| 3 | Avatar throw | `objectId:int, trajectory:byte, targetWorldX:int, targetWorldY:int` |
| 4 | Launch snowball | complete snowball object |
| 5 | Hit | `throwerObjectId:int, targetObjectId:int, impactWorldX:int, impactWorldY:int, impactWorldZ:int, hitDirection:byte, pendingHealth:byte` |
| 6 | Stun | `throwerObjectId:int, targetObjectId:int, deathDirection:byte, fallPose:byte` |
| 7 | Activity state | `objectId:int, activityState:byte, activityTimer:short, health:byte` |
| 8 | Snowball count | `objectId:int, snowballCount:byte` |
| 9 | Machine restock | `machineObjectId:int, snowballCount:byte` |
| 10 | Machine transfer | `machineObjectId:int, playerObjectId:int, machineCount:byte, playerCount:byte` |
| 11 | Player score | `objectId:int, score:int` |
| 12 | Snowball miss | `objectId:int` |

The activity event carries health because the stunned-to-invincible transition restores all four
hearts in the same authoritative subturn. Omitting it makes the client retain zero health and fail
the next whole-model checksum, visibly pausing every avatar while full-status recovery runs.

The original event table also contains an unconsumed X/Y-only event 6 alongside the dead
large-snowball parser branch. It is deliberately absent from v3 and must be rejected if received.

Each event both drives presentation and supplies the deterministic state transition. The packet's
checksum validates the state at the start of that turn, before any event in the packet is applied.
Avatar movement events carry the server's current position and reserved next tile as well as the
final click target. The client presents the five authoritative subturns at their native 60 ms
cadence and never runs a second pathfinder. Arcturus uses the shared `GridPathfinder` A* implementation over SnowStorm's immutable
collision grid and live avatar reservations; this intentionally replaces the original three-choice
Shockwave steering so arena walking follows normal hotel routing around obstacles.
Within each subturn, events are applied in wire order and then every live object advances once in
ascending object-ID order. An avatar first ticks its state timer, advances movement, and—unless
stunned or invincible—scans active snowballs in ascending object-ID order at their current
pre-advance positions. Dynamic snowball IDs follow the initial avatar/machine IDs, so surviving
snowballs advance and test ground later in that object pass. Objects marked for removal are removed
only after every object has advanced. Server-detected effects are emitted in that same stable order
at the appropriate subsequent transition; damage is applied sequentially, a hit point precedes its
same-collision knockout bonus, and pending knockout prevents later damage. The client never invents
damage, score or inventory changes.

### Checksum

The checksum starts with the signed 32-bit XOR-shift seed for `turnNumber`:

```text
if seed == 0: seed = -1
seed ^= seed << 13
seed ^= seed >> 17
seed ^= seed << 5
```

Every live object is processed in ascending object-ID order. Using signed 32-bit overflow, the
checksum is folded as `checksum = checksum * 31 + value` for the object's type, object ID, and then
each compact state value in its documented order. Including identity makes projectile or machine
state reassignment detectable instead of allowing two objects' additive contributions to commute.

Machine values:

```text
[worldX, worldY, snowballCount]
```

Avatar values:

```text
[worldX, worldY, bodyDirection, health, snowballs, botAsInt,
 activityTimer, activityState, nextGoalTileX, nextGoalTileY,
 moveTargetWorldX, moveTargetWorldY, score, userId, teamId, objectId,
 standingDirection, fallPose]
```

Snowball values:

```text
[worldX, worldY, height, direction, trajectory,
 timeToLive, throwerObjectId, parabolaOffset]
```

The checksum is calculated from the state at the start of `turnNumber`. The client compares it
before processing the first subturn in that packet. A mismatch discards the buffered turn and enters
backoff-controlled full-snapshot recovery; no event from the mismatching turn is applied. After a
successful comparison, the five subturns transition the client to the state that the next packet's
checksum will validate.

## Ending and persistence

Final result payload:

```text
instanceId:int, resultId:int, sequence:int, roundNumber:int,
endReason:byte, winningTeam:byte, ranked:boolean,
teamCount:byte, (teamId:byte, score:int, placement:byte)[teamCount],
playerCount:short,
  (userId:int, name:string, teamId:byte, score:int, placement:byte, winner:boolean)[playerCount],
nextGameCountdownMs:int
```

End reasons are `0=time-expired`, `1=owner-ended-before-charge`, `2=not-enough-opposition`,
`3=server-shutdown`, `4=internal-failure`. `winningTeam=255` denotes free-for-all or no team winner.
Placements are authoritative and sorted by descending score, then stable team/user ID. The client
must not infer trophies from source array order.

7807 starts the next round's lobby reset without destroying the SnowStorm window. Final scores hold
an immutable result snapshot and remain correct if the live instance resets behind them.

7814 returns the same immutable result structure without `sequence` and `nextGameCountdownMs` for a
historical finished row. Opening historical scores does not hide or unsubscribe the lobby.

Every charged round is persisted, including aborted/internal-failure rounds for accounting. Only
eligible ranked results update monthly/all-time SnowStorm points, wins, skill level and SnowStorm
high-score rankings. The existing leaderboard backend may be reused internally, but the product is
not presented or categorized as a Game Hall game.

The internal ranking key is `SNOWSTORM`. Raw eligible player score is appended to its daily,
weekly, monthly and all-time buckets in the same caller-owned transaction as the immutable round,
charged-player results and `snowstorm_player_stats`. It is deliberately excluded from the Games
Hall `ALL` aggregate. Cache invalidation happens only after commit; an idempotent result retry that
finds the same already-finalized round performs no aggregate update and only repairs any stale cache.

## SQL contract

`sqlupdates/snowstorm.sql` is the standalone install and the generated SnowStorm block in
`ngh_prod_post_wired2_one_off.sql` is byte-synchronized from it. Both create:

- immutable `snowstorm_maps`, `snowstorm_map_items`, `snowstorm_map_machines` and
  `snowstorm_map_spawns` definitions;
- charged immutable `snowstorm_rounds` and `snowstorm_round_players` accounting/results;
- `snowstorm_player_stats` monthly/all-time scores, wins, ranked points and skill level;
- the original `snowwar_lobby_1` public model/room/navigation entry without assuming a room ID.

The marked map seed is generated only from `tools/snowstorm/maps/snowstorm_maps.json`. It upserts
logical map IDs and replaces item/machine/spawn children inside one transaction after all tables
exist. Revision 1 has canonical checksum `19544273` and SHA-256
`9B3F4ADF235BF0AF1B400381936E2FFE4BAFD0EC1D16F94BEEBAD7788C721F54`; the generated AS3 payload
carries the same values. Reruns preserve every administrator setting through `INSERT IGNORE`.

The default settings are `hotel.ticket.currency.type=106`, `snowstorm.enabled=0`,
`snowstorm.ticket.cost=2`, `snowstorm.ranked.requires.human.opponent=1`,
`snowstorm.bots.enabled=1`, `snowstorm.bots.max-per-game=8`,
`snowstorm.bots.decision.interval.ms=1200`, `snowstorm.bots.throw-radius.tiles=10`,
`snowstorm.max.instances=100`, `snowstorm.max.spectators=20`,
`snowstorm.idle.timeout.seconds=60` and `snowstorm.idle.warning.seconds=15`.

## Rate limits and recovery

- Lobby request: one per second per user.
- Create/delete/join/team/watch/start/leave: one per 500 ms per user and instance/action.
- Kick and bot membership mutations: one per 500 ms per owner/game and operation bucket.
- Walk: at most ten accepted requests per second; only the newest reachable destination is retained.
- Throw/make/cancel: authoritative activity/cooldown rules plus at most five requests per second.
- Full status: one per second per user/game, with client exponential backoff of 1, 2, 4 and 8 seconds.
- Finished result: one per second per user/result.
- Ticket purchase: one transaction per second per buyer.

One malformed or failing instance must not stop other SnowStorm games. Repeated checksum failure
after four successful full snapshots closes that arena with a stable failure rather than creating an
unbounded recovery loop.

## Lifecycle summary

1. Entering `snowwar_lobby_1` opens the native SnowStorm lobby and sends 7700.
2. Server returns lounge/ticket data and live plus finished rows.
3. Create or Join opens the matching CCT-derived view. Insufficient tickets opens the shared shop.
4. The owner starts; the server atomically charges the starting human roster.
5. Server sends location; client creates the private RoomEngine room/canvas inside the arena window,
   loads its immutable map and avatar assets, then requests a full snapshot.
6. Countdown completes and the server publishes one five-subturn status every 300 ms.
7. Client simulates, renders and verifies checksums; the server alone accepts hits and scores.
8. Time expiry produces an immutable result and final-score view.
9. The game returns to its lobby for a possible next round; leaving releases only SnowStorm state and
   restores the normal room view.

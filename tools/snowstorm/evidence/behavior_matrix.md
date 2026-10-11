# SnowStorm r26 behavior matrix

This matrix freezes facts before production code is written. Status meanings:

- `verified-r26`: proved directly by the pinned r26 cast, its recovered bytecode, or an extracted
  target-cast resource.
- `verified-both`: independently agrees between r26 evidence and a secondary implementation.
- `derived`: an NGH design decision or a conclusion derived from several proved facts; it is not
  presented as an original Shockwave constant.
- `unresolved`: insufficient evidence for implementation. The matching entry in
  `open_questions.md` remains a gate blocker when gameplay-critical.

Paths below are relative to their named evidence roots. The exact roots, revisions and hashes are
recorded in `source_manifest.json`.

## Runtime and coordinate model

| Fact | Status | r26 evidence | Secondary evidence / decision | Fixture required |
|---|---|---|---|---|
| One tile is 3,200 world units | verified-r26 | Snowball Lingo divides target deltas by 200; avatar subturn movement is 640 and recovered world helpers use the 32x100 scale | Havana `SnowStormGame.convertToWorldCoordinate` agrees | tile/world boundary table |
| A server turn contains five subturns | verified-r26 | game-status parser reads five event lists and applies a turn object | Havana `SnowStormGameTask.maxGameTurns=5` agrees | empty and populated five-subturn packet |
| Avatar movement is 640 world units per subturn | verified-r26 | variable index `snowwar.object_avatar.subturn_movement=640`; Avatar `calculateMovement` | — | cardinal, diagonal, arrival and blocked-neighbour cases |
| Avatar collision radius/height are 1,600/5,000 | verified-r26 | core variable index | — | equality-boundary collision cases |
| Machine collision radius/height are 1,200/2,400 | verified-r26 | core variable index | — | tile reservation and projectile-height cases |
| Snowball collision radius is 400 | verified-r26 | core variable index | — | avatar/snowball tangent cases |
| Direction/tie-breaking and occupied-tile semantics | verified-r26 | cleaned Avatar `calculateMovement` matched to raw handler plus shared r26 World/Tile/Geometry scripts | Try direct 8-direction neighbour, then 45 degrees CCW, then 45 degrees CW. A tile must be floor and unoccupied. Selecting a next tile clears the avatar's previous reservation and reserves the next tile; global object-ID movement order therefore resolves contention | `MOV-*` golden cases |
| Tile directions are N, NE, E, SE, S, SW, W, NW for IDs 0..7 | verified-r26 | shared r26 World `getTileNeighborInDirection` | — | direction lookup |
| World-to-tile selection is `(world + 1600) / 3200`, with coordinates below -1600 rejected | verified-r26 | shared r26 World and Location classes | Port Director integer/truncation behavior explicitly | negative and half-tile boundaries |

## Player state, health, ammo and score

| Fact | Status | r26 evidence | Secondary evidence / decision | Fixture required |
|---|---|---|---|---|
| Maximum health is 4 | verified-r26 | `snowwar.health.maximum=4`; avatar resurrect handler | Havana agrees | initial, four hits, resurrection |
| Maximum carried snowballs is 5 | verified-r26 | `snowwar.snowball.maximum=5`; make guard and machine max | Havana agrees | 0..5 inventory transitions |
| Activity states are normal=0, creating=1, stunned=2, invincible=3 | verified-r26 | core variable index and Avatar handlers | Havana's `CREATING(0,20)` is wrong and must not be copied | state serialization |
| Create/stun/invincible timers are 20/125/60 subturns | verified-r26 | core variable index and Avatar `activityTimerTriggered` | Havana has the same numeric timers but converts them through wall-clock schedulers; use deterministic subturns instead | boundary at N, 1 and 0 |
| Creating permits movement; setting a target cancels creating and grants no ball | verified-r26 | Avatar `getStateAllowsMoving`; `set_target` clears creating state/timer and sends create-stopped | — | cancel-on-move fixture |
| Creating accepts a new movement target, which cancels creation; the authoritative server rejects throw/make while already creating | verified-r26 / derived server | Avatar movement explicitly permits creating and `set_target` clears its state/timer without an ammo grant; the presentation handler could animate a server-emitted throw while creating | Havana rejects actions through its non-walkable creating state. NGH freezes that validation so a client cannot overlap creation with a throw | create/throw/move matrix |
| Timer completion grants one snowball, resets figure and returns to normal | verified-r26 | Avatar `activityTimerTriggered` | — | create completion fixture |
| A hit removes one health | verified-r26 | event 5 -> `substract_hit_points` | Havana agrees | hit fixture |
| A knockout zeros carried snowballs, stuns, later restores health to 4, then grants invincibility | verified-r26 | event 9 and Avatar timer handlers | Havana broadly agrees, but restores health at knockout rather than at resurrection; reject that ordering | knockout lifecycle |
| A valid opposing hit awards 1; a knockout awards 5 additional points | verified-r26 | score constants plus AvatarManager events 5 and 9 | Havana agrees on additive 1+5 | FFA, enemy and friendly hit fixtures |
| One-team games treat every other avatar as opposition; multi-team games reject same-team score | verified-r26 | AvatarManager event 5/9 branch on `pNumTeams=1`, otherwise compare team IDs | Havana `isOppositionPlayer` agrees | FFA/team scoring |
| A turn checksum is tested before any event/subturn in that packet | verified-r26 | shared r26 `hh_gamesys.cct`: `CGameComponent.update` calls `_MinigameTestChecksum` before `ProcessSubTurn` | The guide's “deferred after checksum” description is explained by this start-of-turn boundary | checksum-before-event fixture |
| Within a subturn, events execute in wire order, then live objects advance in ascending object-ID order; removals happen after all advances | verified-r26 | shared r26 `CGameComponent.ProcessSubTurn` and Gamesystem Component `executeSubturnMoves`; component sorts `pObjects` on creation | NGH server must emit deterministic event order to mirror this client loop | simultaneous-hit/removal ordering |

## Throwing and projectiles

| Fact | Status | r26 evidence | Secondary evidence / decision | Fixture required |
|---|---|---|---|---|
| Trajectories are quick=0, short lob=1, long lob=2 | verified-r26 | Snowball `SetConstants` and send handlers | Havana agrees | action decoding |
| Original gestures: click another avatar for quick; Shift-click field under one second for short; Shift-hold at least one second or Option-click for long; click own avatar or HUD control to make | verified-r26 | Avatar room-object `select`, GameFieldManager mouse handlers and Statusbar create control | AIR maps these to native mouse/keyboard input inside the arena window without using the hotel room canvas | gesture/action fixtures |
| At most one throw is accepted per player per 300 ms turn | derived / approved server boundary | the r26 simulation consumes actions in 300 ms turn packets; no client-local trusted cooldown exists | Havana independently applies a 300 ms last-throw guard. NGH uses the deterministic game clock rather than wall time | repeated/duplicate action fixture |
| Quick/short/long horizontal velocities are all 2,000 | verified-r26 | core variable index | Havana uses 3,000 and is rejected | cardinal/diagonal displacement |
| Quick base height is 4,000; lob base height is 3,000 | verified-r26 | core variable index | — | height curve fixtures |
| Quick TTL is fixed at 13 subturns | verified-r26 | `QUICK_THROW_TIME_TO_LIVE=13`; Snowball `calculateFlightPath` | Havana computes distance-based quick TTL and is rejected | full quick path |
| Lob TTL is integer distance/2,000 | verified-r26 | Snowball `calculateFlightPath` | Havana broadly agrees but uses velocity 3,000 | short/long ranges |
| Parabola offset is `TTL / 2` using Director integer behavior | verified-r26 | Snowball `calculateFlightPath` | Java/AS3 need an explicit signed truncating helper | odd/even TTL |
| Projectile height is `(offset² - temp²) * multiplier + base`; quick/short/long multipliers are 4/10/100 | verified-r26 | raw Snowball `calculateFrameMovement` bytecode proves multiplication occurs after subtracting the squared terms; the cleaned Lingo omitted these parentheses | — | full per-subturn height tables |
| Quick descent-point constant is 3 | verified-r26 | core variable index | — | final four quick subturns |
| Snowball is removed with miss sound when Z < 1 or occupied ground height exceeds Z | verified-r26 | Snowball `calculateFrameMovement` and `testCollisionWithGround` | — | equality and just-below cases |
| Throw-at-player and throw-at-location consume one carried ball when the server emits event 3 or 4 | verified-r26 | AvatarManager events 3/4 | Server remains authoritative and must validate before event emission | empty-ammo and duplicate action |
| Event 8 creates the live snowball at the thrower's current location and derives its flight path | verified-r26 | Engine `createSnowballGameObject` | — | moving thrower fixture |
| A throw is not pre-clipped by a Havana tile path; live per-subturn height/ground/avatar collision determines impact | verified-r26 | Snowball `calculateFrameMovement` samples its current world tile after moving; r26 action senders provide the requested target | Reject Havana's precomputed tile visibility path and landing-tile-only hit test | obstacle and boundary paths |
| Quick target clipping/retargeting maximum | derived | r26 Snowball fixes quick TTL=13 and contains no additional target-distance guard; identical near/far inputs retain that TTL | Reject Havana's uncorroborated `MAX_QUICK_THROW_DISTANCE=22`. The server validates map coordinates but adds no distance cap; fixed TTL/velocity determine reach | `simulation_fixtures.json` quick near/far vectors |
| Complete obstacle/avatar collision phase order | verified-r26 / derived server | shared r26 component applies wire events, then advances live objects by ascending ID. Each avatar ticks state, moves, and scans active snowballs by ascending ID at their current pre-advance positions; later snowball objects then move/test ground. Killed objects are removed after the full pass | Dynamic snowball IDs follow the initial avatar/machine IDs. Server hit resolution mirrors the avatar-centric scan, applies damage sequentially, emits the hit point before the same collision's knockout bonus, and rejects additional damage after pending knockout | `simulation_fixtures.json` subturn, boundary and simultaneous-hit vectors |
| Avatar/snowball horizontal collision is strict radius overlap; projectile Z must be strictly below avatar height | verified-r26 | shared Collision circle test uses squared distance `<` combined radii; Avatar checks `snowball.z < PLAYER_HEIGHT` | Radius sum is 2,000; tangent distance 2,000 and Z exactly 5,000 do not collide | collision-boundary vectors |
| Physical collision ignores only the thrower; a stray same-team snowball is removed and damages, but awards no score | verified-r26 | Avatar collision loop compares only thrower ID; AvatarManager event 5 always subtracts health and gates only score on team opposition | Direct targeted teammate intentions are rejected server-side, but an already-live projectile retains original friendly-fire collision behavior | self/friendly/enemy scoring vectors |

## Machines

| Fact | Status | r26 evidence | Secondary evidence / decision | Fixture required |
|---|---|---|---|---|
| Machine stock is 0..5 | verified-r26 | variable index and Machine `setBallCount` | Havana agrees | stock bounds |
| Selecting a machine walks to its collection tile: south when unrotated, east when rotated | verified-r26 | room-object `select` handler | — | both orientations |
| Event 11 adds one ball and plays the B1 refill layer; event 12 transfers from machine to avatar | verified-r26 | gamesystem processor index, GameFieldManager and Machine class | — | restock/transfer events |
| Visible machine states are steady A0..A5+B0 and refill A0..A4+B1 over the shared shadow | verified-r26 | room cast and machine room-object handlers | deterministic dump in `snowball_machine_states` | image/state lookup |
| Refill and transfer cadence, arbitration and initial stock per map | derived / approved deviation | r26 proves capacity 5, single-ball add/transfer events and a 1,000 ms refill animation, but not server timing | Preserve Havana-visible stock 0, immediate refill/transfer and 3s/1s cadence on the deterministic subturn clock. Use one state per machine and ascending machine/avatar IDs; do not copy its independent scheduler races | `simulation_fixtures.json` cadence/contention vectors |

## Wire objects and events

| Fact | Status | r26 evidence | Secondary evidence / decision | Fixture required |
|---|---|---|---|---|
| Historical object types are player-link=0, snowball=1, unused=2, large-snowball=3, machine=4, avatar=5 | verified-r26 | GameMessageParser `parse_snowwar_gameobject` | — | parser inventory |
| Historical event payloads 0..12 are exactly those listed in the r26 parser | verified-r26 | GameMessageParser `parse_event` | mapped below | raw event fixtures |
| Large snowball is excluded from NGH v1 | derived | parser branch exists, but no registered class, no event-6 subscriber and no UI/action creation path | Havana has no implementation | reject legacy object/event |
| NGH compact object types avatar=0, snowball=1, machine=2 | derived | new client and server run together; legacy IDs are not exposed externally | frozen in `SNOWSTORM_PROTOCOL.md` | serializer parity |
| r26 avatar fields include world X/Y, direction, health, ammo, bot, activity timer/state, next tile, move target, score, player/team/room IDs and identity strings | verified-r26 | GameMessageParser avatar parser | Havana serializer broadly agrees | full snapshot round-trip |
| r26 snowball fields include world X/Y/Z, 360 direction, trajectory, TTL, thrower and parabola offset | verified-r26 | GameMessageParser snowball parser | — | full snapshot round-trip |
| r26 machine fields include world X/Y and stock | verified-r26 | GameMessageParser machine parser | — | full snapshot round-trip |
| Checksum seed is exact signed XOR-shift with zero mapped to -1 | verified-r26 | shared r26 `hh_gamesys.cct` `CIterateSeed` | Java/AS3 must reproduce Director signed overflow and arithmetic right shift | seed vectors |
| Each object contribution weights its insertion-ordered integer sync values by 1..N | verified-r26 | shared r26 Gamesystem Gameobject `addChecksum` | non-integer instance metadata is excluded | per-type contribution vectors |
| Checksum iterates all live objects in ascending numeric object-ID order | verified-r26 | shared r26 Gamesystem Component sorts `pObjects`, then `calculateChecksum` iterates it | NGH compact v1 omits the historical player-link object but preserves ordering/formula for avatar, snowball and machine | mixed-object vectors |

### Historical r26 event table

| r26 event | Payload | Consumer / outcome | Status |
|---:|---|---|---|
| 0 | full game object | Engine create | verified-r26 |
| 1 | object ID | Engine remove | verified-r26 |
| 2 | avatar ID, X, Y | Avatar set target | verified-r26 |
| 3 | avatar ID, target avatar ID, throw height | throw animation + ammo decrement | verified-r26 |
| 4 | avatar ID, target X/Y, throw height | throw animation + ammo decrement | verified-r26 |
| 5 | thrower ID, target ID, hit direction | health decrement + eligible hit point | verified-r26 |
| 6 | X, Y | no registered processor; excluded | verified-r26 |
| 7 | avatar ID | start creating snowball | verified-r26 |
| 8 | snowball ID, thrower ID, target X/Y, trajectory | create/launch snowball | verified-r26 |
| 9 | target ID, thrower ID, hit direction | stun + ammo zero + eligible knockout points | verified-r26 |
| 10 | none | no registered SnowStorm consumer found | verified-r26 parser / unresolved purpose |
| 11 | machine ID | machine adds snowball | verified-r26 |
| 12 | player ID, machine ID | machine transfers ball to player | verified-r26 |

## Lobby, arena and presentation

| Fact | Status | r26 evidence | Secondary evidence / decision | Fixture required |
|---|---|---|---|---|
| Public model identifier is `snowwar_lobby_1` | verified-r26 | room STXT variable index and lobby script/class name | existing NGH public-room seed uses the same model | room-load smoke test |
| The product supports seven arena selections | verified-both | r26 lobby/layout evidence and seven target map/reference sets | Havana provides seven candidate map files | list/create validation |
| Team count supports 1..4, with original fixed capacities 12/6/4/3 | verified-r26 | four game-info layouts and lounge slot logic | — | all team-count layouts |
| SnowStorm v1 has no private-match flag | verified-r26 / derived product boundary | `sw_gcreate.window` exposes only name, duration, field and team count; no private control, password, allowlist or invitation handler exists | Remove the provisional BattleBall-style private flag rather than create an owner-only dead-end game | create/summary packet round-trip |
| Arena uses a private RoomEngine world inside the SnowStorm window | derived | preserves Shockwave room/avatar semantics while matching the proven BattleBall host architecture | user-approved design | lifecycle/leak tests |
| Arena avatars receive the SnowStorm coat/shoes presentation without mutating account figure | verified-r26 / derived host | r26 avatar room-object `fixSnowWarFigure`; private overlay ownership is NGH design | BattleBall arena figure override pattern | one- and multi-team image tests |
| Arena HUD contains health meter, five pips and Make Snowball button inside the arena window | verified-r26 / derived host | statusbar and UI cast assets | user explicitly required BattleBall-style embedded ownership | HUD state matrix |
| Health meter thresholds are >0.7 green, >0.3 yellow, else red; percent is `value * 1.0 / maxHealth` | verified-r26 | ProjectorRays raw bytecode recovery of Statusbar `updateHealth` | corrected a bad cleaned expression that multiplied by zero | health 0..4 pixels/colour |
| Skill progression uses Beginner 0..10,000, Amateur 10,001..100,000, Intermediate 100,001..500,000 and Expert 500,001+ | derived / period-authentic server policy | shared r26 lounge parser receives skill name plus minimum/maximum from the server, and the level-change parser receives the new rank name; the client contains no 5,000-point formula | Havana's `games_ranks` seed supplies the four SnowStorm bands and its finish task adds raw round score to monthly/all-time totals. NGH maps them to frozen v1 numeric levels 1..4 | exact threshold and overflow-clamp tests |

## Explicitly rejected Havana behavior

The Havana snapshot is valuable protocol and map candidate material, but these defects are confirmed
and must not enter production:

- path occupancy compares the moving avatar's current position with the candidate inside a loop over
  other players; `nextGoal.equals(player)` compares unrelated types;
- multi-team `hasEnoughPlayers` accepts one non-empty team;
- configured ticket charge is ignored and the debit is hard-coded to 2;
- recursive random spawn selection is unbounded and its `nextInt(0, size - 1)` excludes the final
  candidate (and fails for a one-element list);
- quick throws use distance TTL and all trajectories use velocity 3,000, contradicting r26;
- creating is serialized with state ID 0 instead of 1;
- health is restored during knockout rather than when stun expires;
- movement, projectile and machine changes use independent wall-clock scheduled tasks, so ordering is
  not deterministic;
- empty exception handling in the turn queue can silently discard events;
- machine collection spawns recurring schedulers per arrival and does not establish deterministic
  multi-player arbitration;
- map checksum/heightmap fallback and incomplete map-machine sources remain candidate data only.

# BattleBall integration pattern map for Shockwave SnowStorm

This document records what the completed BattleBall work proves about integrating an original
Shockwave game into this client and server. It is an architecture map, not permission to copy
BattleBall gameplay. Every SnowStorm mechanic still comes from the r26 SnowStorm evidence dossier.

The reviewed guidance commits are:

- server `5e3d61b0` and `f96376d6`;
- client `999c4483a` and `9b265927b`.

Files in those commits that concern Android, free-flow chat, camera, friends, generic Game Hall,
Wired, imaging, Habbicons or unrelated boot cleanup are outside the SnowStorm change set.

## Client component and lifecycle

| BattleBall source/pattern | SnowStorm replacement | What is retained | What is deliberately not copied |
|---|---|---|---|
| `HabboGameManager.ensureBattleBall()` | `ensureSnowStorm()` | Construct only after communication, windows, session, avatars, RoomEngine, RoomUI, sound, localization and `flash.client.url` exist; dispose from the parent component exactly once | The existing later AIR `SnowWarEngine`; it stays a separate legacy component and is not the Shockwave implementation |
| `BattleBallController` | `SnowStormController` | Network/event orchestration, room-model detection, cached lobby state, bounded snapshot recovery, presentation switching and idempotent disposal | BattleBall packet layouts, simulation, powers, bots, scores and state IDs |
| `LOBBY_MODEL = "bb_lobby_1"` plus `RoomReadyMessageEvent` | exact r26 `LOBBY_MODEL = "snowwar_lobby_1"` | Open and refresh only when the authoritative public-room model is ready; hide on other rooms without disturbing an active private game recovery path | Login-time global opening or Game Center queue entry |
| `BattleBallAssets` | `SnowStormAssets` | Independently load the converted core, room and UI libraries; cache rasterized members; own and dispose loaders, libraries, bitmaps and sounds | `hh_games.swf`; that library already belongs to the seated Games Hall widget and is not a SnowStorm dependency |
| `BattleBallProtocol` plus one parser façade | `SnowStormProtocol` plus `SnowStormMessageParser` | One client contract table, strict typed parsing, version/revision guards and no legacy duplicate constants | BattleBall v2 headers or payloads; later SnowWar `5000..5029` / `6000..6026` families |
| ticket incoming/outgoing classes under `communication.messages.*.tickets` | reuse the same neutral ticket events/composer | One hotel-ticket wire and one authoritative balance shared with BattleBall; all visible counters refresh immediately | A SnowStorm-specific wallet, duplicate balance cache or optimistic ticket grant |

## Client view ownership

| BattleBall source/pattern | SnowStorm replacement | SnowStorm responsibility |
|---|---|---|
| `BattleBallView` façade | `SnowStormView` | Own orchestration only. Construct collaborators, route state, snapshot final results and expose a small controller-facing API. It must not become a monolithic renderer. |
| `BattleBallViewState` | `SnowStormViewState` | Shared ticket cost/balance, selected lobby row/page, current overlay and immutable final/historical result snapshots. |
| `BattleBallViewServices` with `assertWired()` | `SnowStormViewServices` | Late-bound renderer actions such as create, join, watch, tickets, rules, rankings, next game and leave; construction fails immediately if a required callback is absent. |
| `BattleBallWindowHost` | `SnowStormWindowHost` | One root sized to the hotel room view. Lobby, ticket, history/results and arena windows are siblings under this root; it owns z-order and root disposal but no renderer internals. |
| `BattleBallLobbyWindow` | `SnowStormLobbyWindow` | Native WindowManager shell, title, authored content well/footer, stable drag/position state and generated r26 layout mounting. |
| `BattleBallWindowSurface` | `SnowStormWindowSurface` | Small helper for native bitmap, text, input and action controls generated from r26 element lists. Keep authored order/metadata and explicit disposal. |
| `BattleBallChrome` | `SnowStormChrome` | Only SnowStorm cast chrome and font/layout rules. |
| `BattleBallLobbyRenderer` | `SnowStormLobbyRenderer` | Original live and immutable finished rows, paging, scores links, Create, High Scores, Game Rules and ticket counter. Opening detail keeps this lobby window visible behind it. |
| `BattleBallCreateRenderer` | `SnowStormCreateRenderer` | Original SnowStorm name/map/team settings. Every submit produces a next screen, shared ticket dialog or localized error. |
| `BattleBallGameInfoRenderer` | `SnowStormGameInfoRenderer` | Waiting roster, team selection, owner controls and proved SnowStorm game information. No bot UI unless separately approved. |
| `BattleBallTicketRenderer` | shared-ticket-backed `SnowStormTicketRenderer` | Present the original SnowStorm ticket window, but send the neutral shared ticket purchase composer and read the shared authoritative balance. |
| `BattleBallFinalScoresRenderer` | `SnowStormFinalScoresRenderer` | Render one- through four-team r26 layouts from an immutable snapshot; order placements by descending score with stable tie-breaking; never read a reset live model. |
| independent BattleBall history shell | independent SnowStorm history/result shell | Finished-score detail opens above the still-visible lobby and cannot change lobby selection/page/title. |

## Private RoomEngine arena

| BattleBall source/pattern | SnowStorm replacement | Required difference/parity rule |
|---|---|---|
| `BattleBallArenaWindow` | `SnowStormArenaWindow` | Own the SnowStorm frame, canvas aperture and bottom HUD. The actual hotel room remains alive and clickable behind the window. |
| `BattleBallArena` | `SnowStormArena` | Build a dedicated private RoomEngine room with a non-desktop canvas ID, one registered world type per canonical map, real RoomEngine avatars and idempotent object/room/canvas disposal. |
| `BattleBallArenaHud` | `SnowStormArenaHud` | Port r26 health meter, five ammo pips, make-ball normal/hover/pressed/disabled states, timer and personal/team score into the arena window footer. Never replace the global `RoomBarID`. |
| `BattleBallArenaOverlays` | `SnowStormArenaOverlays` | Own name tags, projectile/hit/countdown/game-over presentation and input targeting without owning simulation state. |
| BattleBall per-map world registration | seven SnowStorm world registrations generated from the canonical manifest | Static obstacles and machines must use preserved geometry/depth metadata and participate in RoomEngine-compatible ordering; no flattened arena bitmap is used as runtime geometry. |
| BattleBall private derived rider visuals | SnowStorm private derived coat/team figure | Derive `fixSnowWarFigure` from the authoritative base figure only for the private arena avatar. Never mutate `HabboInfo`, the hotel-room avatar or persisted look; discard it on all exits. |

The SnowStorm arena is therefore **not** a Games Hall widget, a replacement hotel room, an overlay
on the desktop canvas, or a reuse of BattleBall's board renderer. It is a separate private
RoomEngine world/canvas embedded in a native SnowStorm window.

## Server ownership and lifecycle

| BattleBall source/pattern | SnowStorm replacement | What is retained | SnowStorm-specific requirement |
|---|---|---|---|
| `GameEnvironment` owns `BattleBallManager` | own `SnowStormManager` | Construct during environment load, expose one getter, and dispose/drain during shutdown | Boot logging follows existing manager conventions and missing optional settings do not flood boot |
| `BattleBallManager` | `SnowStormManager` | Lobby subscriptions, bounded live/finished collections, per-game locks, permission checks, safe sends, failure isolation and lifecycle coordination | Fixed SnowStorm 300 ms turn ownership and immutable charged roster; no BattleBall bots or ranked classification |
| `BattleBallGame` | `SnowStormGame` | One authoritative mutable aggregate behind its game lock and immutable outward snapshots | Independent r26 integer simulation with exactly five ordered 60 ms subturns |
| `BattleBallRepository` | `SnowStormRepository` | Transactional/idempotent charging and results, immutable finished rows, async persistence isolation | SnowStorm maps/machines/spawns/results/ranked totals generated from its canonical manifest |
| `BattleBallRoundResult` / `BattleBallFinishedGame` | `SnowStormRoundResult` / `SnowStormFinishedGame` | Snapshot before live reset; preserve charged/aborted audit rows; stable score ordering | SnowStorm team/player scores and human-opposition eligibility only |
| `HotelTicketService` wallet locks and committed-balance broadcast | reuse the same `HotelTicketService` | Freeze human start roster, lock wallets in stable order, debit atomically/idempotently, then send committed balance to every affected client | Cost comes from `snowstorm.ticket.cost`; no refund after a committed charge |
| BattleBall lounge audience | separate SnowStorm lounge audience | Requesting lounge/list subscribes; leaving/unloading unsubscribes; create/change/finish pushes bounded updates | Do not call it Games Hall and do not share BattleBall instance IDs/state |
| BattleBall explicit removal notification | SnowStorm removal notification | Kick/idle/delete/disconnect paths tell affected clients why they left before state disappears | Every removed client disposes only its private SnowStorm arena and returns to the lounge safely |

## Packets, validation and throttling

| BattleBall source/pattern | SnowStorm rule |
|---|---|
| `Incoming`, `Outgoing`, `PacketManager` registrations | Add a collision-tested SnowStorm block to all three registries atomically with client `HabboMessages`; add uniqueness and round-trip tests. |
| small `BattleBallHandler` subclasses | Use one narrowly parsed event per operation, validate bounds/enums/string lengths before lookup, require no trailing bytes and resolve membership/ownership inside the manager. |
| base `BattleBallHandler.getRatelimit() == 100` plus manager operation checks | Do not treat the blanket 100 ms transport limit as sufficient. Define and test separate limits for lounge/list, create/join/team mutations, start/delete/kick, walk/throw/make, results, ticket purchases and snapshots. |
| BattleBall full-status request backoff | Keep client per-game exponential recovery backoff and server per-game snapshot throttling; one damaged client cannot force repeated full snapshots for everyone. |
| authoritative BattleBall snapshots/deltas | SnowStorm packets carry intentions from the client and authoritative ordered turns/snapshots from the server, with sequence, map revision and checksum validation. |

## SQL and generated data

| BattleBall source/pattern | SnowStorm replacement |
|---|---|
| standalone `sqlupdates/battleball.sql` | standalone idempotent `sqlupdates/snowstorm.sql` |
| production one-off section | a matching idempotent SnowStorm production section only when deployment is requested; no database is mutated by this implementation task |
| transactional map/spawn seed replacement | generate all seven maps, visual/collision objects, spawn clusters and machines from `tools/snowstorm/maps/snowstorm_maps.json`; table creation precedes the replacement transaction |
| settings preserve administrator values on rerun | insert absent SnowStorm settings only; never overwrite an existing administrator value |
| map validation at manager startup | require seven rectangular maps, unique stable object IDs, valid machine footprints, usable bounded spawn candidates and the same manifest revision/checksum as the client |

## Test pattern mapping

- `BattleBallWireTest` -> `SnowStormWireTest`: every payload, bound, version, registry collision and
  trailing-byte rejection.
- `BattleBallManagerTest` -> `SnowStormManagerTest`: permissions, subscriptions, idempotent start,
  atomic charge, removals, disconnects, failure isolation and shutdown.
- `BattleBallGameTest` and focused resolver tests -> SnowStorm deterministic math, movement,
  projectile, state, machine, scoring, ordering and checksum fixture suites. BattleBall test values
  are never reused as SnowStorm expectations.
- BattleBall layout generator/checker -> deterministic SnowStorm r26 element-list generator with a
  clean-check mode and all required native layout resources registered in `HabboGamesCom`.
- Existing BattleBall tests and asset/layout checks remain regression gates after SnowStorm changes.

## Explicit non-patterns

The following BattleBall behavior is not a SnowStorm template:

- coloured/sealed tile simulation, bouncing-ball rider additions, power-ups, pins or cannons;
- BattleBall map count, spawn scheme, bot strategies or bot lobby controls;
- BattleBall protocol headers, compact object formats, sequence meanings or score formulas;
- BattleBall Game Hall leaderboard naming/types (SnowStorm may use the backend, but remains its own
  original game in all UI and domain naming);
- the `hh_games.swf` Games Hall asset library;
- temporary monolithic views, hand-maintained duplicate layouts or optimistic wallet/model changes.


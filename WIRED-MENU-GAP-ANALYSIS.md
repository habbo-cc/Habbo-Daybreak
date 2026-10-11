# Wired Menu — Gap Analysis vs Seth's Creator Tools

**Date:** 2026-07-28
**Scope:** `Arcturus-Community` branch `wired2` (referred to as **A**) compared against
`Arcturus-Community-Wired` `main` — Seth/iSetht's public release (referred to as **B**).
**Subject:** A's `habbohotel/wired/menu/` subsystem vs B's `habbohotel/wired/creator/`.

Creator-Tools-exclusive features (tool catalogue, `wired_tools` table, item placement and
cancellation, the `:wired` open command, chest logs, chest lock room-actions) are **out of
scope** — those are a deliberate non-goal for A. Everything recorded here is
**shared-scope functionality where A is materially thinner**.

---

## Method and limits of this analysis

Read-only static comparison of both source trees. Every factual claim below carries a
`file:line` citation and was verified by direct read or grep at the time of writing.

**What was NOT done — treat conclusions accordingly:**

- Neither server was built or run.
- No client was connected; no packet capture was taken.
- **The July/May AIR client's actual expectations were not inspected.** Where this document
  says a field is "missing", it means *missing relative to B*, not proven to be required by
  the client. Items marked ⚠️ below depend on client behaviour that is unverified.
- B's own correctness was not audited. B's README self-describes as "NOT PROD READY … treat
  this as a sandbox", so B is used here as a *feature reference*, not a correctness oracle.
- No judgement is made on whether each gap is a defect or an intentional scope decision.
  Several may be deliberate.

**Surface comparison:** A = 14 incoming subcommands / 11 outgoing composers.
B = 11 incoming / 9 outgoing (+ open composer + command). Comparable packet count,
substantially different payload depth.

---

## Issue index

| # | Issue | Severity | Confidence |
|---|---|---|---|
| 1 | No internal `@` variable model (room / furni / user) | Blocker | Verified |
| 2 | Room-stats payload omits variable name lists | High | Verified |
| 3 | Object inspection returns no internal fields | High | Verified |
| 4 | Furni-reference panel hardcoded empty | High | Verified |
| 5 | No Variable Highlight packet or resolver | High | Verified |
| 6 | Variable write-back cannot write internal fields | High | Verified |
| 7 | Playtest mode stored but never enforced | High | Verified |
| 8 | Log pipeline emits a single level/source | Medium | Verified |
| 9 | No builder-facing WriteLog effect | Medium | Verified |
| 10 | Room-stats omits toolbar / inspect / playtest / handitem flags | Medium | Verified |
| 11 | Inspection cannot target bots or pets | Medium | Verified |
| 12 | Timezone stored and echoed but never consumed | Medium | Verified |
| 13 | Permission mask collapses GROUP_ADMINS tier | Low | Verified |
| 14 | Mask normalization silently rewrites client input | Low | Verified ⚠️ |
| 15 | No handitem-passing block | Low | Verified |

---

## 1. No internal `@` variable model — Blocker

**A has no concept of engine-provided `@`-prefixed variables anywhere.**

Verification:
```bash
grep -rn '"@current_time\|"@furni_count\|"@user_count\|"@teams\.' \
  Arcturus-Community/src/main/java/com/eu/habbo --include=*.java
# → zero matches
```

B exposes three families:

**Room globals** — `WiredCreatorToolsRoomStats.getGlobalInternalValues`
(`habbohotel/wired/creator/WiredCreatorToolsRoomStats.java:150`), ~25 entries:
```
@furni_count  @user_count  @wired_timer  @room_id  @group_id
@teams.{red,green,blue,yellow}.score   and  .size
@current_time
@current_time.milliseconds_of_seconds  .seconds_of_minute  .minute_of_hour
             .hour_of_day  .day_of_week  .day_of_month  .day_of_year
             .week_of_year  .month_of_year  .year
```

**Furni internals** — `WiredCreatorToolsInspectionValues.getFurniInternalValues`
(`.../WiredCreatorToolsInspectionValues.java:76`), ~25 entries: `@id`, `@class_id`,
`@height`, `@state`, `@position.x`, `@position.y`, `@rotation`, `@altitude`, `@type`,
`@dimensions.x`, `@dimensions.y`, `@owner_id`, `@is_invisible`, `@is_stackable`,
`@can_stand_on`, `@can_sit_on`, `@can_lay_on`, `@wallitem_offset`, plus projectile,
chest (6) and AreaHide (7) blocks.

**User internals** — `getUserInternalValues` (`.../WiredCreatorToolsInspectionValues.java:161`),
~30 entries: `@index`, `@type`, `@gender`, `@achievement_score`, `@is_hc`,
`@favourite_group_id`, `@has_rights`, `@is_group_admin`, `@is_owner`, `@position.x/y`,
`@direction`, `@altitude`, `@room_entry.method`, `@room_entry.teleport_id`, `@handitem`,
`@effect`, `@is_frozen`, `@is_muted`, `@is_trading`, `@dance`, `@sign`, `@is_idle`,
`@user_id` / `@pet_id` / `@bot_id`, plus mouse-hold (6), team (3) and transaction (6) blocks.

**Why this is the blocker:** issues 2, 3, 6 and 12 are all downstream of this. Nothing else
on the list can be closed without it.

**Note on scope:** B's chest, transaction, mouse-hold and projectile sub-blocks depend on
subsystems A does not have. Those specific entries are out of scope; the base furni/user/room
internals are not.

---

## 2. Room-stats payload omits variable name lists — High

`WiredCreatorToolsRoomStatsComposer` writes 26 `append*` calls; A's stats response writes 13
numeric fields and stops (`messages/incoming/wired/WiredMenuMessageEvent.java:133`).

| Field | A | B |
|---|---|---|
| usage / cap / heavy | ✅ | ✅ |
| floor + wall furni counts and limits | ✅ | ✅ |
| permanent furni/user/global variable counts and limits | ✅ | ✅ |
| `globalValues` key/value map | ❌ | ✅ (~25, see issue 1) |
| `furniVariables` name list | ❌ | ✅ |
| `userVariables` name list | ❌ | ✅ |
| `globalVariables` name list | ❌ | ✅ |

⚠️ The three name lists are what a client would use to populate variable dropdowns and
autocomplete in wired editors. That the July client consumes them this way is **inferred from
B's implementation, not verified against the client.**

---

## 3. Object inspection returns no internal fields — High

A's `sendInspection` (`WiredMenuMessageEvent.java:207`) writes only stored wired-variable
values: `type`, `visibleId`, count, then `(variableId, value)` pairs. No `@` internals, no
variable name list.

B returns internals **plus** stored variable values **plus** a `variables` name list
(`WiredCreatorToolsInspectionValues.java:60` and `:145`).

Practical effect: A's inspect tool shows nothing at all for any object that has no wired
variable assigned to it.

---

## 4. Furni-reference panel hardcoded empty — High

`messages/incoming/wired/WiredMenuMessageEvent.java:218-222`:
```java
if (type == 0) {
    // InteractionWired exposes no generic, read-only view of its saved
    // furni selections. Do not infer references from heterogeneous JSON.
    response.appendInt(0);
}
```

The "which wired boxes reference this furni?" list is permanently empty. The comment is an
explicit acknowledgement in-tree, not an oversight.

**Root cause:** `InteractionWired` has no generic read-only accessor for saved furni
selections; each subclass stores them in its own JSON shape. Closing this requires adding
such an accessor to the base class or interface, which touches every wired type.

---

## 5. No Variable Highlight packet or resolver — High

B ships `WiredCreatorToolsVariableHighlightEvent` →
`WiredCreatorToolsVariableHighlight.forVariable`
(`habbohotel/wired/creator/WiredCreatorToolsVariableHighlight.java`), returning every furni
and user in the room holding a named variable as `(objectId, category, value)` with
`CATEGORY_FLOOR = 10`, `CATEGORY_WALL = 20`, `CATEGORY_UNIT = 100`.

A has no equivalent packet, handler, composer or resolver. Verified by absence — no file in
A matches `*VariableHighlight*`, and no header is declared in `Outgoing.java`.

---

## 6. Variable write-back cannot write internal fields — High

B's `WiredCreatorToolsVariableActionEvent` supports `give` / `remove` / `set` and writes
internal fields as real state mutations:

| Target | Writable internals in B |
|---|---|
| furni | `@state` (updates item state), `@altitude` (`WiredMovement.moveFurniAltitude`), `@position.x`, `@position.y`, `@rotation` (real `moveFurniTo`) |
| user | `@direction` (`setRotation` + status), `@altitude`, `@position.x`, `@position.y` (real move via `RoomUnitOnRollerComposer`) |
| global | `@teams.{red,green,blue,yellow}.score` (real `WiredTeamScoreHelper.addScore` on the running game) |

B additionally routes writes through generated-variable providers
(`WiredExtraTimeUtilities`, `WiredExtraLevelUpSystem`) and tags every write
`InteractionWiredVariable.CHANGE_ORIGIN_CREATOR_TOOL` so wired can distinguish tool-driven
changes from gameplay changes.

A's `modify()` (`WiredMenuMessageEvent.java:266`) supports three operations
(set / setIfAbsent / remove) on real wired variables only, has no change-origin concept,
and explicitly rejects `QUEST` and `QUEST_CHAIN` definition types.

**A is ahead here in one respect:** A has a separate permanent-variable mutation path
(`WiredMenuMutatePermanentVariableMessageEvent`) able to target offline users by account id,
plus a paged/sorted user-variable browser (6 sort modes, type filter). B has no offline-user
variable browser.

---

## 7. Playtest mode stored but never enforced — High

A persists `playtestMode` in `users_wired_preferences` and echoes it back. Consumer search:

```bash
grep -rn "playtestMode" Arcturus-Community/src/main/java/com/eu/habbo --include=*.java
```
Returns only the record declaration (`habbohotel/wired/menu/WiredMenuPreferences.java:13`),
the SQL bind (`:55`), the packet read (`WiredMenuMessageEvent.java:445`) and the echo
(`messages/outgoing/users/AccountPreferencesMessageComposer.java:38`).
**No behavioural consumer exists.**

B enforces it, in `habbohotel/rooms/Room.java:2477-2490`:
```java
public boolean isOwner(Habbo habbo) {
  if (this.isWiredCreatorToolsPlaytesting()) { return false; }
  return this.rightsManager.isOwner(habbo);
}

public boolean hasRights(Habbo habbo) {
  if (this.isWiredCreatorToolsPlaytesting()) { return false; }
  return this.rightsManager.hasRights(habbo);
}
```
That is the actual semantic — the builder temporarily drops to visitor so they experience the
room as players do. In A the toggle is inert.

**Design divergence worth deciding explicitly:** A scopes toolbar / inspect / playtest
**per-user** (`users_wired_preferences`); B scopes them **per-room**
(`rooms.wired_creator_tools_preferences`). B's room-scoped model is what its stats packet
implies. Which is correct depends on the client — unverified.

⚠️ Enforcing playtest via `isOwner`/`hasRights` as B does is broad and touches every
rights check in the codebase, not only wired. Copying it verbatim carries blast radius
beyond the wired subsystem and should be reviewed before adoption.

---

## 8. Log pipeline emits a single level/source — Medium

A's `WiredRoomMonitor` has two write entry points and exactly four call sites:

| Site | Call |
|---|---|
| `habbohotel/wired/core/WiredEngine.java:350` | `runtimeError(room, "STACK", ex)` |
| `habbohotel/wired/core/WiredEngine.java:383` | `executionCap(room)` |
| `habbohotel/wired/core/WiredEngine.java:842` | `runtimeError(ctx.room(), "EFFECT", e)` |
| `habbohotel/wired/core/WiredEngine.java:895` | `runtimeError(room, "DELAYED_EFFECT", e)` |

Every log row is constructed with hardcoded `level = 2, source = 1`
(`habbohotel/wired/menu/WiredRoomMonitor.java:85`):
```java
log = new LogEntry(state.nextLogId++, 2, 1, ...);
```

A's logs request (`WiredMenuMessageEvent.java:335`) accepts `level`, `source` and a text
`query` filter — but only one `(level, source)` pair can ever be produced, so two of the
three filters are dead in practice.

B has 15 write call sites emitting ERROR and WARN across 9 distinct codes:
`DELAYED_EVENTS_CAP`, `EXECUTION_CAP`, `EXECUTOR_OVERLOAD`, `KILLED`, `PLACEMENT_FAILURE`,
`RECURSION_TIMEOUT`, `TOO_MANY_VARIABLES`, `TRANSACTION_FAILURE`, `MARKED_AS_HEAVY`.

**A is ahead here:** A aggregates errors with occurrence counts and first/last-seen
timestamps, persists them to `room_wired_monitor_errors`, and offers `clearErrors`. B's log
list is flat with no aggregation and no separate error tab. Both persist logs
(A → `room_wired_monitor_logs`; B → `wired_logs`, with trim-on-insert).

**Two valid resolutions:** widen the write sites to emit distinct codes and levels, **or**
drop the unusable filters from A's protocol. Doing neither leaves a UI that appears
functional and is not.

---

## 9. No builder-facing WriteLog effect — Medium

B ships `WiredEffectWriteLog` and `WiredEffectNotWriteLog`
(`habbohotel/items/interactions/wired/effects/`), letting builders write their own log lines
with placeholder resolution via `WiredTextPlaceholders.resolve`.

A has no such effect:
```bash
ls Arcturus-Community/src/main/java/com/eu/habbo/habbohotel/items/interactions/wired/effects/ | grep -i log
# → empty
```
Consequence: in A the logs tab can only ever show engine-generated errors. Builders have no
way to instrument their own wired.

---

## 10. Room-stats omits toolbar / inspect / playtest / handitem flags — Medium

B's stats composer carries `showToolbar`, `showInspectButton`, `playtestingMode`,
`handitemPassingBlocked`, plus `timezone`, `wiredModifyPermissions` and
`wiredInspectPermissions` in the same packet
(`messages/outgoing/wired/WiredCreatorToolsRoomStatsComposer.java`).

A splits permissions and timezone into a separate settings response (`sendSettings`,
`WiredMenuMessageEvent.java:125`) and does not send the three UI flags or the handitem flag
at all.

⚠️ Whether this split matters depends on whether the July client expects them in one
response. Unverified. If the split is correct for the client, only the four missing flags
are a real gap.

---

## 11. Inspection cannot target bots or pets — Medium

A's `holder()` (`WiredMenuMessageEvent.java:527`) resolves `type == 1` via
`room.getHabboByRoomUnitId(visibleId)` only, returning `null` for any room unit that is not a
logged-in Habbo. Bots and pets cannot be inspected.

B's `getUserInternalValues` resolves Habbo → Bot → Pet and emits `@bot_id` / `@pet_id`
accordingly (`WiredCreatorToolsInspectionValues.java:161`).

**Caveat for the audit:** A's variable model is holder-scoped
(`WiredVariableHolder.Scope` = FURNI / USER / ROOM), so bots and pets legitimately have no
*variable holder*. This is therefore a gap in **inspection coverage**, not necessarily a
defect in the variable model. Closing it likely means decoupling "inspectable target" from
"variable holder".

---

## 12. Timezone stored and echoed but never consumed — Medium

`WiredMenuSettings.timezone()` has exactly one reader across A:
```bash
grep -rn "\.timezone()" Arcturus-Community/src/main/java/com/eu/habbo --include=*.java
# → messages/incoming/wired/WiredMenuMessageEvent.java:129  (the echo back to client)
```
It is persisted in `room_wired_settings` and round-tripped, but no server-side logic reads it.

In B the timezone drives `@current_time.*` computation
(`WiredCreatorToolsRoomStats.getGlobalInternalValues`, `:150`). Since A has no `@current_time`
family (issue 1), A's timezone is currently decorative. This closes automatically once
issue 1 is done.

---

## 13. Permission mask collapses GROUP_ADMINS tier — Low

| | A (`WiredMenuSettings.hasPermission`) | B (`Room.hasWiredPermission`, `Room.java:1916`) |
|---|---|---|
| Tiers | `EVERYONE(1)`, `RIGHTS(1<<1)`, `GROUP_MEMBERS(1<<2)` | EVERYONE, RIGHTS, **GROUP_ADMINS**, GROUP_MEMBERS |
| Staff bypass | `Permission.ACC_SUPERWIRED` bypasses | none (owner only) |
| Owner bypass | always | yes, but suppressed while playtesting |
| Storage | `room_wired_settings` table | `rooms.wired_modify_permissions` / `_inspect_permissions` columns |

A cannot express "group admins but not group members". Low severity — a missing granularity,
not a correctness bug.

---

## 14. Mask normalization silently rewrites client input — Low ⚠️

`habbohotel/wired/menu/WiredMenuSettings.java`, `normalizeModify`:
```java
int normalized = mask & VALID_MASK & ~1;      // strips EVERYONE from modify
if ((normalized & (1 << 2)) != 0) {
    normalized |= 1 << 3;                     // forces bit 3 when bit 2 set
}
```
Default when no row exists: `new WiredMenuSettings(1 << 3, VALID_MASK, "UTC")` — modify mask
`8`, read mask `15`.

Bit 3 is set and defaulted-to but is not one of the three named constants
(`EVERYONE = 1`, `RIGHTS = 1 << 1`, `GROUP_MEMBERS = 1 << 2`). Its meaning is not documented
in-tree.

⚠️ **Flagged for verification, not asserted as a bug.** The behaviour may be a deliberate
match for the July client's mask semantics. If it is, it needs a comment; if it is not, a
client sending a mask will get a different mask back than it sent, with no error.

---

## 15. No handitem-passing block — Low

B has a room-level `isHanditemPassingBlocked()` (`Room.java:2602`) enforced in
`HandItemCommand:19`, `RoomUserGiveHandItemEvent:20`, and surfaced to the client via
`ClickAvatarEvent:51` / `WiredClickUserResponseComposer`.

A has no such flag and no enforcement. Relevance depends on whether the July client offers
the toggle — unverified.

---

## Where A is genuinely ahead of B

Recorded so the comparison is not read as one-directional:

| Area | A | B |
|---|---|---|
| Authorization | `WiredAuthorizationService`, 9 typed operations, fail-closed, gates all 14 subcommands | single `canUseWiredCreatorTools` boolean per handler |
| Input validation | `readBoundedString`, page/amount clamps, `boundedFilter`, `MalformedPacketException` on trailing bytes | raw `readString()` / `readInt()`, no bounds |
| Rate limiting | `getRatelimit() = 150` on the menu handler | none |
| Capability gate | menu refused unless `CAPABILITY_WIRED_MENU` negotiated for the room | none |
| Error reporting | aggregated entries with counts + first/last timestamps, persisted, clearable | flat list, no aggregation |
| Offline variables | paged browser, 6 sort modes, filters, offline-user targeting | none |

---

## Proposed close-out order

Dependency-ordered. Items 2, 3, 6 and 12 cannot start before item 1.

| Step | Work | Closes |
|---|---|---|
| 1 | Internal `@` variable model — three providers (room globals, furni internals, user internals), read path only | #1, #12 |
| 2 | Wire providers into room-stats and inspection responses; add the three variable name lists | #2, #3 |
| 3 | Variable Highlight — resolver + packet + composer | #5 |
| 4 | Internal-field **writes** in `modify()` + a change-origin marker on `InteractionWiredVariable` | #6 |
| 5 | Playtest enforcement — decide per-user vs per-room first; review blast radius before touching `isOwner`/`hasRights` | #7 |
| 6 | Log levels/sources: widen write sites, **or** remove the dead filters from the protocol | #8 |
| 7 | `WriteLog` / `NotWriteLog` effects with placeholder resolution | #9 |
| 8 | Decouple inspection target from variable holder; add bot and pet resolution | #11 |
| 9 | Generic read-only furni-selection accessor on `InteractionWired`; populate the reference panel | #4 |
| 10 | Add `GROUP_ADMINS` tier; document or fix bit-3 normalization | #13, #14 |
| 11 | Room-stats UI flags; handitem-passing block if the client offers it | #10, #15 |

**Before starting step 1:** capture the July AIR client's actual expectations for the
room-stats, inspection and highlight payloads. Several items above are inferred from B's
implementation and would be built to the wrong shape if the client differs.

---

## Licensing note

B is GPL-3.0. A already carries attribution for prior adaptations in `package-info.java`
across `wired/core`, `wired/variables`, and
`interactions/wired/{addons,conditions,effects,selectors,triggers,variables}`, and in
`habbohotel/wired/menu/WiredRoomMonitor.java:26`. Any further porting from B must preserve
and extend that attribution.

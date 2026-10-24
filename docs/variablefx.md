# Native VariableFX review slice

Companion client: https://github.com/0-es/kupla-cc/pull/588. Parent parity work:
https://github.com/0-es/kupla-cc/issues/586 and https://github.com/0-es/kupla-cc/issues/558.
This source change does not establish complete AIR parity or production availability.

## Evidence boundary

The read-only AIR snapshot is `4b418c68357a15872253d37ef2322278db169779`, build
WIN63-202610201032-107964540. Only identity/option metadata and interoperable
wire/editor contracts informed this code. No decompiled implementation was copied.
Original graphics are in the separately reviewed client change, with the user's
permission and original SWF payload hashes; the server ships no AIR assets.

Source contracts:

- [Six editor identities](https://github.com/0-es/kupla-cc/tree/4b418c68357a15872253d37ef2322278db169779/reference/habboair-decompiled/scripts/com/sulake/habbo/roomevents/wired_setup/addons/variablefx).
- [Style identities/options](https://github.com/0-es/kupla-cc/blob/4b418c68357a15872253d37ef2322278db169779/reference/habboair-decompiled/scripts/com/sulake/habbo/room/object/visualization/variablefx/VariableFxPreviewStyleDefinitions.as).
- [Client contract inventory](https://github.com/0-es/kupla-cc/blob/839cafc6e2e18921df97b8ccd30a5ecb6e22869e/docs/wired-parity/VariableFX-editors.md)
  and its linked runtime/drawing reports.

## Implemented native path

`WiredAddonVariableFx` and six concrete classes own category-local codes
1200–1205, atomic strict validation of 23 integer words/three positional variable
strings, defensive copies, and version-1 JSON reload. Invalid state leaves the
last accepted configuration intact. No source selectors or item selections are
owned. The existing native editor authorization and successful-save ACK path
remain in force. Style metadata covers 38 styles and 37 number-icon options;
renderer 101 is not invented. Known ranch icons are accepted server-side; the
client campaign/security option gate remains presentation policy, not a new
server authorization privilege.

`ItemManager` registers **local opt-in interaction aliases**:
`wf_xtra_variable_fx_{health,progress,level,status,boss,number}`. These are not
claims about official furnidata interaction strings. No base items, catalogue
offers, SQL IDs, automatic migration or production registrations are supplied.

Four native composers write AIR headers 739 (configuration), 754 (status),
236 (full status-key removal), and 2588 (configuration removal). Signed longs,
both initialization flags, range pairs and sorted extras use literal field order.
Status identity is `configId|opaqueVariableId`, with `|u|unitId` or `|f|visibleId`
appended only for removal/diff identity. Stored stable user/database-item IDs are
never rewritten to room IDs. Negative temporary-furniture holder IDs are valid.

The room owns a `WiredVariableFxRuntime` and a separate `VariableFxSession` for
each connection identity. A new connection gets an initialized snapshot;
subsequent unchanged state is silent. Removed statuses precede removed configs;
changed configs precede statuses. Queuing failure does not advance the baseline.
Packets are batched at 256 rows without truncating the view. Configuration changes
reinitialize their existing rows; new mutation-created rows remain events.

Publication runs after native committed variable notifications, before their
nested Wired triggers; room/tile invalidation, item/user removal, capability
negotiation and periodic room cycles refresh structure and dynamic inputs.
Periodic refresh covers internal/quest values, override values, team membership
and audience changes. Room disposal closes the publisher. Capability revocation
removes a previously authorized view before forgetting its baseline.

Add-ons apply to valued furniture/user variables on their tile, including native
aliases. Overrides read a holder of the same kind or the global room holder;
missing values fall back to configured bounds. Level displays use the native
level-up add-on on the resolved definition's tile, current-step progress/range,
level/max-level metadata and an equal terminal range at the cap. Invisible users
and area-hidden furniture are excluded from this local projection.

Visibility is projected **before** serialization, without staff bypass: self,
same assigned team within the same game, everyone, viewer-variable presence, or
viewer-variable equality. Unassigned teams/missing variables fail closed.
Configs are sent only when the viewer has a matching row. Team colors, stacking,
source-tile association and missing-reference behavior here are documented local
server policies; official server captures have not established their equivalence.

Project-local capability bit 19 requires add-ons, variables and variable-sync.
Old clients do not receive these messages or the new editors. This bit is not
presented as an official AIR extension. An unhealthy variable manager disables it.

## Review and acceptance still needed

`mvn -o -B -ntp package` passes all 374 tests and builds both JARs, with JDK
25.0.1 targeting Java 21. A subsequently added native-editor wire check also
passes with all four composer tests: it verifies 23 words and all three variable
slots on header 7101. No runtime source changed after the full package run.
The real-manager fixture covers ten room cases, including mutation-before-trigger
ordering, per-viewer isolation, identity translation, dynamic audience/overrides,
team changes, level caps, removal/disposal, capability revocation and failed queue
retry/isolation. Five settings cases exercise all metadata options, rejected saves,
signed bounds and persistence; six session/policy cases include 1025-row batching.
The companion client capability suite passes four checks. These tests replace
database/socket boundaries and are not official runtime/catalogue evidence.

Still unproved: active catalogue/furnidata identities; real open/edit/save/ACK/reopen;
stock-AIR packet captures; official audience/source/level/color semantics;
cross-room/loading and concurrent commit ordering under real traffic; packet
interception/loss behavior; room-scale cost; full graphics/editor/live acceptance.
The periodic refresh is a local reconciliation mechanism, not a claim that every
internal/quest transition between cycles is delivered. Messages have no room ID;
client connection/room generation handling cannot prove network ordering. No
merge, core replacement, SQL/config operation or deployment is part of this PR.

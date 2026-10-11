package com.eu.habbo.habbohotel.games.battleball;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BattleBallPowerResolverTest {
    @Test
    void springDrillAndTemporaryStateExpiryUseAuthoritativeEvents() {
        Fixture fixture = fixture();

        fixture.resolver.apply(fixture.owner, BattleBallPowerType.SPRING, 1_000L);
        assertEquals(BattleBallPlayerState.HIGH_JUMPS, fixture.owner.state());
        assertEquals(11_000L, fixture.owner.stateUntil());
        assertFalse(fixture.resolver.expire(10_999L));
        assertTrue(fixture.resolver.expire(11_000L));
        assertEquals(BattleBallPlayerState.NORMAL, fixture.owner.state());

        fixture.resolver.apply(fixture.owner, BattleBallPowerType.DRILL, 20_000L);
        assertEquals(BattleBallPlayerState.CLEANING_TILES, fixture.owner.state());
        assertTrue(fixture.events.stream().anyMatch(event -> event.type() == 4));
    }

    @Test
    void lightBulbAndFlashlightPaintForTheEffectiveScoringTeam() {
        Fixture fixture = fixture();
        fixture.opponent.setColouringForUserId(fixture.owner.userId());

        fixture.resolver.apply(fixture.opponent, BattleBallPowerType.LIGHT_BULB, 1_000L);
        BattleBallTile centre = fixture.tiles[fixture.opponent.position().x()][fixture.opponent.position().y()];
        assertEquals(fixture.owner.teamId(), centre.colour());
        assertEquals(fixture.owner.userId(), centre.scoreReferences().getFirst().userId());

        fixture.opponent.setPosition(new BattleBallPosition(7, 7, 0.0, 0));
        fixture.resolver.apply(fixture.opponent, BattleBallPowerType.FLASHLIGHT, 2_000L);
        assertEquals(fixture.owner.teamId(), fixture.tiles[7][6].colour());
        assertTrue(fixture.priorityTiles.contains(fixture.tiles[7][6]));
    }

    @Test
    void harlequinRedirectsOpponentScoringUntilItExpires() {
        Fixture fixture = fixture();

        fixture.resolver.apply(fixture.owner, BattleBallPowerType.HARLEQUIN, 5_000L);
        assertEquals(BattleBallPlayerState.COLORING_FOR_OPPONENT, fixture.opponent.state());
        assertEquals(fixture.owner.userId(), fixture.opponent.colouringForUserId());
        assertEquals(fixture.owner.teamId(), fixture.resolver.scoringTeam(fixture.opponent));
        assertEquals(fixture.owner.userId(), fixture.resolver.scoringUser(fixture.opponent));

        assertTrue(fixture.resolver.expire(15_000L));
        assertEquals(BattleBallPlayerState.NORMAL, fixture.opponent.state());
        assertEquals(BattleBallPlayer.NOT_COLOURING, fixture.opponent.colouringForUserId());
    }

    @Test
    void bombResetsNearbyTilesAndStunsNearbyPlayers() {
        Fixture fixture = fixture();
        BattleBallTile painted = fixture.tiles[7][6];
        painted.paint(fixture.owner.teamId(), BattleBallTileState.PRESSED, fixture.owner.userId());

        fixture.resolver.apply(fixture.owner, BattleBallPowerType.BOMB, 3_000L);

        assertEquals(BattleBallTileState.DEFAULT, painted.state());
        assertEquals(BattleBallPlayerState.STUNNED, fixture.owner.state());
        assertEquals(BattleBallPlayerState.STUNNED, fixture.opponent.state());
        assertTrue(fixture.priorityTiles.contains(painted));
    }

    @Test
    void bombDoesNotPushAPlayerOntoAnOccupiedTile() {
        Fixture fixture = fixture();
        BattleBallPlayer blocker = player(3, 30, 1, 9, 7);
        fixture.players.put(blocker.userId(), blocker);

        fixture.resolver.apply(fixture.owner, BattleBallPowerType.BOMB, 3_000L);

        assertEquals(8, fixture.opponent.position().x());
        assertEquals(7, fixture.opponent.position().y());
    }

    @Test
    void pinsSpawnHitAndExpireThroughDeltaEvents() {
        Fixture fixture = fixture();
        fixture.resolver.apply(fixture.owner, BattleBallPowerType.BOX_OF_PINS, 10_000L);
        assertTrue(fixture.resolver.pins().size() >= 8);
        BattleBallPin pin = fixture.resolver.pins().iterator().next();

        assertTrue(fixture.resolver.hitPin(fixture.opponent,
                pin.position().x(), pin.position().y(), 11_000L));
        assertEquals(BattleBallPlayerState.BALL_BROKEN, fixture.opponent.state());
        assertFalse(fixture.resolver.pins().stream().anyMatch(value -> value.objectId() == pin.objectId()));
        assertTrue(fixture.events.stream().anyMatch(event -> event.type() == 1
                && event.objectId() == pin.objectId()));

        assertTrue(fixture.resolver.expire(30_000L));
        assertTrue(fixture.resolver.pins().isEmpty());
    }

    @Test
    void cannonSealsItsLinePushesOpponentsAndCompletesItsFlight() {
        Fixture fixture = fixture();
        fixture.owner.setPosition(new BattleBallPosition(7, 7, 0.0, 0));
        fixture.opponent.setPosition(new BattleBallPosition(7, 5, 0.0, 0));

        fixture.resolver.apply(fixture.owner, BattleBallPowerType.CANNON, 1_000L);

        assertEquals(BattleBallTileState.SEALED, fixture.tiles[7][6].state());
        assertEquals(BattleBallPlayerState.FLYING_THROUGH_AIR, fixture.owner.state());
        assertEquals(BattleBallPlayerState.STUNNED, fixture.opponent.state());
        assertEquals(7, fixture.owner.position().x());
        assertEquals(0, fixture.owner.position().y());
        assertTrue(fixture.resolver.expire(1_800L));
        assertEquals(BattleBallPlayerState.STUNNED, fixture.owner.state());
    }

    @Test
    void cannonTriesTheFreeLeftTileWhenTheRightTileIsOccupied() {
        Fixture fixture = fixture();
        fixture.owner.setPosition(new BattleBallPosition(7, 7, 0.0, 0));
        fixture.opponent.setPosition(new BattleBallPosition(7, 5, 0.0, 0));
        BattleBallPlayer blocker = player(3, 30, 1, 8, 5);
        fixture.players.put(blocker.userId(), blocker);

        fixture.resolver.apply(fixture.owner, BattleBallPowerType.CANNON, 1_000L);

        assertEquals(6, fixture.opponent.position().x());
        assertEquals(5, fixture.opponent.position().y());
    }

    @Test
    void cannonStopsOnTheLastUnoccupiedTileWhenItsVictimCannotMove() {
        Fixture fixture = fixture();
        fixture.owner.setPosition(new BattleBallPosition(7, 7, 0.0, 0));
        fixture.opponent.setPosition(new BattleBallPosition(7, 0, 0.0, 0));
        BattleBallPlayer right = player(3, 30, 1, 8, 0);
        BattleBallPlayer left = player(4, 40, 1, 6, 0);
        fixture.players.put(right.userId(), right);
        fixture.players.put(left.userId(), left);

        fixture.resolver.apply(fixture.owner, BattleBallPowerType.CANNON, 1_000L);

        assertEquals(7, fixture.opponent.position().x());
        assertEquals(0, fixture.opponent.position().y());
        assertEquals(7, fixture.owner.position().x());
        assertEquals(1, fixture.owner.position().y());
    }

    private static Fixture fixture() {
        String row = "1".repeat(15);
        BattleBallMap map = new BattleBallMap(1, String.join("|", java.util.Collections.nCopies(15, row)));
        BattleBallTile[][] tiles = new BattleBallTile[map.width()][map.height()];
        for (int y = 0; y < map.height(); y++) {
            for (int x = 0; x < map.width(); x++) tiles[x][y] = new BattleBallTile(x, y, true);
        }
        BattleBallPlayer owner = player(1, 10, 0, 7, 7);
        BattleBallPlayer opponent = player(2, 20, 1, 8, 7);
        Map<Integer, BattleBallPlayer> players = new LinkedHashMap<>();
        players.put(owner.userId(), owner);
        players.put(opponent.userId(), opponent);
        LinkedHashSet<BattleBallTile> priority = new LinkedHashSet<>();
        LinkedHashSet<BattleBallTile> fill = new LinkedHashSet<>();
        List<BattleBallEvent> events = new ArrayList<>();
        AtomicInteger ids = new AtomicInteger(100);
        BattleBallPowerResolver resolver = new BattleBallPowerResolver(map, tiles, players,
                priority, fill, events, new Random(7), ids::getAndIncrement);
        return new Fixture(tiles, players, owner, opponent, priority, events, resolver);
    }

    private static BattleBallPlayer player(int objectId, int userId, int teamId, int x, int y) {
        return new BattleBallPlayer(objectId, userId, "player", "", "", "M", teamId,
                new BattleBallPosition(x, y, 0.0, 0));
    }

    private record Fixture(BattleBallTile[][] tiles, Map<Integer, BattleBallPlayer> players,
                           BattleBallPlayer owner,
                           BattleBallPlayer opponent, LinkedHashSet<BattleBallTile> priorityTiles,
                           List<BattleBallEvent> events, BattleBallPowerResolver resolver) {}
}

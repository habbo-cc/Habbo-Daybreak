package com.eu.habbo.habbohotel.games.snowstorm;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnowStormSimulationTest {
    @Test
    void advancesExactlyFiveSubturnsAndHandsOffTheChecksum() {
        SnowStormMapDefinition map = SnowStormMapCatalog.loadGenerated().get(1);
        SnowStormSimulation simulation = new SnowStormSimulation(map, 1, List.of(
                player(1, 19, 0, 5)));

        int initialChecksum = simulation.checksum();
        SnowStormSimulation.TurnResult first = simulation.advanceTurn();
        assertEquals(0, first.turnNumber());
        assertEquals(initialChecksum, first.startChecksum());
        assertEquals(5, first.subturns().size());

        int nextChecksum = simulation.checksum();
        SnowStormSimulation.TurnResult second = simulation.advanceTurn();
        assertEquals(1, second.turnNumber());
        assertEquals(nextChecksum, second.startChecksum());
        assertEquals(5, second.subturns().size());
    }

    @Test
    void resolvesSimultaneousHitsInSnowballObjectOrderAndDefersRemoval() {
        SnowStormMapDefinition map = SnowStormMapCatalog.loadGenerated().get(1);
        List<SnowStormSimulation.PlayerSeed> roster = new ArrayList<>();
        roster.add(player(1, 1, 20, 1, 0));
        int[][] shooterTiles = {{19, 0}, {20, 0}, {21, 0}, {19, 1}, {21, 1}};
        for (int index = 0; index < shooterTiles.length; index++) {
            roster.add(player(index + 2, 0, shooterTiles[index][0], shooterTiles[index][1], 1));
        }
        SnowStormSimulation simulation = new SnowStormSimulation(map, 2, roster);
        for (int shooterObjectId = 2; shooterObjectId <= 6; shooterObjectId++) {
            simulation.submit(shooterObjectId,
                    new SnowStormIntention.ThrowAvatar(SnowStormTrajectory.QUICK, 1));
        }

        SnowStormSimulation.TurnResult turn = simulation.advanceTurn();
        List<SnowStormSimulationEvent> events = turn.subturns().stream().flatMap(List::stream).toList();
        List<SnowStormSimulationEvent.Hit> hits = events.stream()
                .filter(SnowStormSimulationEvent.Hit.class::isInstance)
                .map(SnowStormSimulationEvent.Hit.class::cast)
                .filter(event -> event.targetObjectId() == 1).toList();
        assertEquals(4, hits.size());
        assertEquals(List.of(3, 2, 1, 0), hits.stream()
                .map(SnowStormSimulationEvent.Hit::health).toList());
        assertEquals(1, events.stream()
                .filter(event -> event.type() == SnowStormSimulationEvent.Type.STUN).count());
        assertEquals(5, events.stream()
                .filter(event -> event.type() == SnowStormSimulationEvent.Type.REMOVE_OBJECT).count());
        assertFalse(turn.objects().stream().anyMatch(snapshot -> snapshot.type() == 1));

        SnowStormSimulation.ObjectSnapshot target = turn.objects().stream()
                .filter(snapshot -> snapshot.objectId() == 1).findFirst().orElseThrow();
        assertEquals(0, target.values().get(3));
        assertEquals(SnowStormActivityState.STUNNED.id(), target.values().get(7));
        SnowStormSimulationEvent.Stun stun = events.stream()
                .filter(SnowStormSimulationEvent.Stun.class::isInstance)
                .map(SnowStormSimulationEvent.Stun.class::cast)
                .findFirst().orElseThrow();
        assertEquals(stun.direction(), target.values().get(2));
        assertEquals(stun.fallPose(), target.values().get(17));
        int stunSubturn = java.util.stream.IntStream.range(0, turn.subturns().size())
                .filter(index -> turn.subturns().get(index).stream().anyMatch(event ->
                        event.type() == SnowStormSimulationEvent.Type.STUN))
                .findFirst().orElseThrow();
        assertEquals(SnowStormAvatarActivity.STUN_SUBTURNS - (4 - stunSubturn),
                target.values().get(6));

        int knockoutThrowerId = hits.stream()
                .filter(event -> event.health() == 0)
                .map(SnowStormSimulationEvent.Hit::throwerObjectId).findFirst().orElseThrow();
        SnowStormSimulation.ObjectSnapshot knockoutThrower = turn.objects().stream()
                .filter(snapshot -> snapshot.objectId() == knockoutThrowerId).findFirst().orElseThrow();
        assertEquals(6, knockoutThrower.values().get(12));
        int lateThrowerId = java.util.stream.IntStream.rangeClosed(2, 6)
                .filter(objectId -> hits.stream().noneMatch(event -> event.throwerObjectId() == objectId))
                .findFirst().orElseThrow();
        SnowStormSimulation.ObjectSnapshot lateThrower = turn.objects().stream()
                .filter(snapshot -> snapshot.objectId() == lateThrowerId).findFirst().orElseThrow();
        assertEquals(0, lateThrower.values().get(12));

        int teamScoreBeforeDeparture = simulation.teamScoresSnapshot().get(0);
        assertTrue(simulation.removeUser(knockoutThrowerId));
        SnowStormSimulation.TurnResult departureTurn = simulation.advanceTurn();
        assertFalse(departureTurn.objects().stream()
                .anyMatch(snapshot -> snapshot.objectId() == knockoutThrowerId));
        assertEquals(6, simulation.playerScoresSnapshot().get(knockoutThrowerId));
        assertEquals(teamScoreBeforeDeparture, simulation.teamScoresSnapshot().get(0));
    }

    @Test
    void knockoutSnapsAndFreezesMovementAndFacingUntilShockwaveRecovery() {
        SnowStormMapDefinition map = SnowStormMapCatalog.loadGenerated().get(1);
        List<SnowStormSimulation.PlayerSeed> roster = new ArrayList<>();
        roster.add(player(1, 1, 20, 1, 0));
        int[][] shooterTiles = {{19, 0}, {20, 0}, {21, 0}, {19, 1}};
        for (int index = 0; index < shooterTiles.length; index++) {
            roster.add(player(index + 2, 0, shooterTiles[index][0], shooterTiles[index][1], 1));
        }
        SnowStormSimulation simulation = new SnowStormSimulation(map, 2, roster);
        simulation.submit(1, new SnowStormIntention.Walk(
                SnowStormMath.tileToWorld(19), SnowStormMath.tileToWorld(4)));
        for (int shooterObjectId = 2; shooterObjectId <= 5; shooterObjectId++) {
            simulation.submit(shooterObjectId,
                    new SnowStormIntention.ThrowAvatar(SnowStormTrajectory.QUICK, 1));
        }

        SnowStormSimulation.TurnResult knockout = simulation.advanceTurn();
        SnowStormSimulation.ObjectSnapshot downed = knockout.objects().stream()
                .filter(snapshot -> snapshot.objectId() == 1).findFirst().orElseThrow();
        assertEquals(SnowStormActivityState.STUNNED.id(), downed.values().get(7));
        int frozenX = downed.values().get(0);
        int frozenY = downed.values().get(1);
        int deathDirection = downed.values().get(2);
        int standingDirection = downed.values().get(16);
        int fallPose = downed.values().get(17);
        assertTrue(fallPose == 1 || fallPose == 2);
        assertEquals(frozenX, downed.values().get(10));
        assertEquals(frozenY, downed.values().get(11));

        boolean recovered = false;
        for (int turnIndex = 0; turnIndex < 30; turnIndex++) {
            SnowStormSimulation.TurnResult turn = simulation.advanceTurn();
            SnowStormSimulation.ObjectSnapshot avatar = turn.objects().stream()
                    .filter(snapshot -> snapshot.objectId() == 1).findFirst().orElseThrow();
            if (avatar.values().get(7) == SnowStormActivityState.STUNNED.id()) {
                assertEquals(frozenX, avatar.values().get(0));
                assertEquals(frozenY, avatar.values().get(1));
                assertEquals(deathDirection, avatar.values().get(2));
                assertEquals(fallPose, avatar.values().get(17));
                assertFalse(turn.subturns().stream().flatMap(List::stream).anyMatch(event ->
                        event.type() == SnowStormSimulationEvent.Type.AVATAR_MOVE
                                && ((SnowStormSimulationEvent.AvatarMove) event).objectId() == 1));
            } else if (avatar.values().get(7)
                    == SnowStormActivityState.INVINCIBLE_AFTER_STUN.id()) {
                assertEquals(frozenX, avatar.values().get(0));
                assertEquals(frozenY, avatar.values().get(1));
                assertEquals(standingDirection, avatar.values().get(2));
                assertEquals(0, avatar.values().get(17));
                recovered = true;
                break;
            }
        }
        assertTrue(recovered);
    }

    @Test
    void rejectsDuplicateUsersBeforeAllocatingSimulationObjects() {
        SnowStormMapDefinition map = SnowStormMapCatalog.loadGenerated().get(1);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new SnowStormSimulation(map, 1, List.of(
                        player(1, 19, 0, 5),
                        player(1, 20, 0, 5))));
    }

    @Test
    void machineContentionUsesAscendingAvatarObjectIdInsideTheTurnOwner() {
        SnowStormMapDefinition map = SnowStormMapCatalog.loadGenerated().get(1);
        SnowStormMachineDefinition machine = map.machines().get(0);
        SnowStormSimulation simulation = new SnowStormSimulation(map, 1, List.of(
                player(1, machine.collectionTileX(), machine.collectionTileY(), 4),
                player(2, machine.collectionTileX(), machine.collectionTileY(), 4)));

        SnowStormSimulation.TurnResult turn = simulation.advanceTurn();
        List<SnowStormSimulationEvent> firstSubturn = turn.subturns().get(0);
        assertTrue(firstSubturn.stream().anyMatch(event ->
                event.type() == SnowStormSimulationEvent.Type.MACHINE_RESTOCK));
        SnowStormSimulationEvent.MachineTransfer transfer = firstSubturn.stream()
                .filter(SnowStormSimulationEvent.MachineTransfer.class::isInstance)
                .map(SnowStormSimulationEvent.MachineTransfer.class::cast)
                .findFirst().orElseThrow();
        assertEquals(1, transfer.playerObjectId());
        assertEquals(5, transfer.playerCount());
    }

    @Test
    void throwingStopsTheActiveRouteAndFacesTheTargetLikeShockwave() {
        SnowStormMapDefinition map = SnowStormMapCatalog.loadGenerated().get(1);
        SnowStormSimulation simulation = new SnowStormSimulation(map, 1, List.of(
                player(1, 19, 0, 5)));
        simulation.submit(1, new SnowStormIntention.Walk(
                SnowStormMath.tileToWorld(19), SnowStormMath.tileToWorld(4)));
        simulation.advanceTurn();

        simulation.submit(1, new SnowStormIntention.ThrowLocation(
                SnowStormTrajectory.QUICK,
                SnowStormMath.tileToWorld(23), SnowStormMath.tileToWorld(1)));
        SnowStormSimulation.TurnResult turn = simulation.advanceTurn();
        List<SnowStormSimulationEvent> events = turn.subturns().get(0);
        SnowStormSimulationEvent.AvatarMove movement = events.stream()
                .filter(SnowStormSimulationEvent.AvatarMove.class::isInstance)
                .map(SnowStormSimulationEvent.AvatarMove.class::cast)
                .findFirst().orElseThrow();
        SnowStormSimulationEvent.AvatarThrow throwing = events.stream()
                .filter(SnowStormSimulationEvent.AvatarThrow.class::isInstance)
                .map(SnowStormSimulationEvent.AvatarThrow.class::cast)
                .findFirst().orElseThrow();
        SnowStormSimulation.ObjectSnapshot avatar = turn.objects().stream()
                .filter(snapshot -> snapshot.objectId() == 1).findFirst().orElseThrow();

        assertEquals(throwing.objectId(), movement.objectId());
        assertEquals(avatar.values().get(0), avatar.values().get(8) * SnowStormMath.WORLD_UNITS_PER_TILE);
        assertEquals(avatar.values().get(1), avatar.values().get(9) * SnowStormMath.WORLD_UNITS_PER_TILE);
        assertEquals(avatar.values().get(0), avatar.values().get(10));
        assertEquals(avatar.values().get(1), avatar.values().get(11));
        assertEquals(2, avatar.values().get(2));
    }

    @Test
    void makingASnowballStopsWalkingAndEntersTheTwentySubturnActivity() {
        SnowStormMapDefinition map = SnowStormMapCatalog.loadGenerated().get(1);
        SnowStormSimulation simulation = new SnowStormSimulation(map, 1, List.of(
                player(1, 19, 0, 0)));
        simulation.submit(1, new SnowStormIntention.Walk(
                SnowStormMath.tileToWorld(19), SnowStormMath.tileToWorld(4)));
        simulation.advanceTurn();

        simulation.submit(1, new SnowStormIntention.MakeSnowball());
        SnowStormSimulation.TurnResult turn = simulation.advanceTurn();
        SnowStormSimulation.ObjectSnapshot avatar = turn.objects().stream()
                .filter(snapshot -> snapshot.objectId() == 1).findFirst().orElseThrow();

        assertEquals(avatar.values().get(0), avatar.values().get(10));
        assertEquals(avatar.values().get(1), avatar.values().get(11));
        assertEquals(SnowStormActivityState.CREATING.id(), avatar.values().get(7));
        assertEquals(SnowStormAvatarActivity.CREATE_SUBTURNS - 5, avatar.values().get(6));
        assertTrue(turn.subturns().get(0).stream().anyMatch(event ->
                event.type() == SnowStormSimulationEvent.Type.AVATAR_MOVE));
        SnowStormSimulationEvent.ActivityState activity = turn.subturns().get(0).stream()
                .filter(SnowStormSimulationEvent.ActivityState.class::isInstance)
                .map(SnowStormSimulationEvent.ActivityState.class::cast)
                .findFirst().orElseThrow();
        assertEquals(avatar.values().get(3), activity.health());
    }

    @Test
    void throwingDuringCreationMatchesShockwaveAndDoesNotCancelTheCreationTimer() {
        SnowStormMapDefinition map = SnowStormMapCatalog.loadGenerated().get(1);
        SnowStormSimulation simulation = new SnowStormSimulation(map, 1, List.of(
                player(1, 19, 0, 2)));
        simulation.submit(1, new SnowStormIntention.MakeSnowball());
        simulation.advanceTurn();

        assertTrue(simulation.canSubmit(1, new SnowStormIntention.ThrowLocation(
                SnowStormTrajectory.QUICK,
                SnowStormMath.tileToWorld(23), SnowStormMath.tileToWorld(1))));
        simulation.submit(1, new SnowStormIntention.ThrowLocation(
                SnowStormTrajectory.QUICK,
                SnowStormMath.tileToWorld(23), SnowStormMath.tileToWorld(1)));
        SnowStormSimulation.TurnResult turn = simulation.advanceTurn();
        SnowStormSimulation.ObjectSnapshot avatar = turn.objects().stream()
                .filter(snapshot -> snapshot.objectId() == 1).findFirst().orElseThrow();

        assertEquals(SnowStormActivityState.CREATING.id(), avatar.values().get(7));
        assertEquals(SnowStormAvatarActivity.CREATE_SUBTURNS - 10, avatar.values().get(6));
        assertEquals(1, avatar.values().get(4));
        assertTrue(turn.subturns().get(0).stream().anyMatch(event ->
                event.type() == SnowStormSimulationEvent.Type.AVATAR_THROW));
    }

    @Test
    void everyShockwaveTrajectoryReachesADistantStationaryAvatarAfterItsArcCounterReachesZero() {
        for (SnowStormTrajectory trajectory : SnowStormTrajectory.values()) {
            SnowStormSimulation simulation = new SnowStormSimulation(openMap(), 1, List.of(
                    player(1, 1, 1, 5),
                    player(2, 11, 1, 5)));
            simulation.submit(1, new SnowStormIntention.ThrowAvatar(trajectory, 2));

            boolean hit = false;
            for (int turnIndex = 0; turnIndex < 8 && !hit; turnIndex++) {
                SnowStormSimulation.TurnResult turn = simulation.advanceTurn();
                hit = turn.subturns().stream().flatMap(List::stream).anyMatch(event ->
                        event instanceof SnowStormSimulationEvent.Hit hitEvent
                                && hitEvent.throwerObjectId() == 1
                                && hitEvent.targetObjectId() == 2);
            }

            assertTrue(hit, trajectory + " should reach a stationary avatar ten tiles away");
        }
    }

    @Test
    void quickProjectileRemainsLiveAfterTheShockwaveArcCounterTurnsNegative() {
        SnowStormSimulation simulation = new SnowStormSimulation(openMap(), 1, List.of(
                player(1, 1, 1, 5)));
        simulation.submit(1, new SnowStormIntention.ThrowLocation(
                SnowStormTrajectory.QUICK,
                SnowStormMath.tileToWorld(11), SnowStormMath.tileToWorld(1)));

        SnowStormSimulation.ObjectSnapshot projectile = null;
        for (int turnIndex = 0; turnIndex < 3; turnIndex++) {
            projectile = simulation.advanceTurn().objects().stream()
                    .filter(snapshot -> snapshot.type() == 1).findFirst().orElseThrow();
        }

        assertTrue(projectile.values().get(5) < 0);
    }

    private static SnowStormMapDefinition openMap() {
        int width = 40;
        int height = 3;
        List<String> rows = java.util.stream.IntStream.range(0, height)
                .mapToObj(ignored -> ".".repeat(width)).toList();
        return new SnowStormMapDefinition(
                999, 1, width, height, SnowStormMath.WORLD_UNITS_PER_TILE,
                0, 0, 0, 0, 0, "", List.of(), rows, rows,
                List.of(), List.of(), List.of(), 0, "test");
    }

    private static SnowStormSimulation.PlayerSeed player(
            int userId, int tileX, int tileY, int snowballs) {
        return player(userId, 0, tileX, tileY, snowballs);
    }

    private static SnowStormSimulation.PlayerSeed player(
            int userId, int teamId, int tileX, int tileY, int snowballs) {
        return new SnowStormSimulation.PlayerSeed(
                userId, teamId, SnowStormMath.tileToWorld(tileX), SnowStormMath.tileToWorld(tileY),
                2, false, snowballs);
    }
}

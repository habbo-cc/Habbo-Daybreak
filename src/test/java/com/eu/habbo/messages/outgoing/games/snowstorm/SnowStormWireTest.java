package com.eu.habbo.messages.outgoing.games.snowstorm;

import com.eu.habbo.habbohotel.games.snowstorm.SnowStormFinishedGame;
import com.eu.habbo.habbohotel.games.snowstorm.SnowStormGame;
import com.eu.habbo.habbohotel.games.snowstorm.SnowStormInstanceSummary;
import com.eu.habbo.habbohotel.games.snowstorm.SnowStormMapCatalog;
import com.eu.habbo.habbohotel.games.snowstorm.SnowStormProtocol;
import com.eu.habbo.habbohotel.games.snowstorm.SnowStormRoundResult;
import com.eu.habbo.habbohotel.games.snowstorm.SnowStormSimulation;
import com.eu.habbo.habbohotel.games.snowstorm.SnowStormSimulationEvent;
import com.eu.habbo.habbohotel.games.snowstorm.SnowStormStartingRoster;
import com.eu.habbo.habbohotel.games.snowstorm.SnowStormState;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.Outgoing;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnowStormWireTest {
    @Test
    void everyTypedEventPreservesTheFrozenWireShape() {
        List<SnowStormSimulationEvent> events = List.of(
                new SnowStormSimulationEvent.RemoveObject(101),
                new SnowStormSimulationEvent.AvatarMove(102, 201, 202, 203, 204, 5, 6, 7),
                new SnowStormSimulationEvent.AvatarThrow(103, 2, 205, 206),
                new SnowStormSimulationEvent.LaunchSnowball(
                        104, 105, 207, 208, 209, 210, 2, 211, 212),
                new SnowStormSimulationEvent.Hit(106, 107, 213, 214, 215, 6, 4),
                new SnowStormSimulationEvent.Stun(108, 109, 5, 2),
                new SnowStormSimulationEvent.ActivityState(110, 3, 216, 4),
                new SnowStormSimulationEvent.SnowballCount(111, 5),
                new SnowStormSimulationEvent.MachineRestock(112, 6),
                new SnowStormSimulationEvent.MachineTransfer(113, 114, 7, 8),
                new SnowStormSimulationEvent.PlayerScore(115, 217),
                new SnowStormSimulationEvent.SnowballMiss(116));
        SnowStormSimulation.TurnResult turn = new SnowStormSimulation.TurnResult(
                1, 2, List.of(events, List.of(), List.of(), List.of(), List.of()),
                Map.of(), List.of());
        ServerMessage response = new ServerMessage(1);
        SnowStormWire.writeTurn(response, turn);
        ByteBuf packet = payload(response.get(), 1);

        assertEquals(5, packet.readUnsignedByte());
        assertEquals(events.size(), packet.readUnsignedShort());
        assertEquals(1, packet.readUnsignedByte()); assertEquals(101, packet.readInt());
        assertEquals(2, packet.readUnsignedByte());
        assertEquals(102, packet.readInt()); assertEquals(201, packet.readInt());
        assertEquals(202, packet.readInt()); assertEquals(203, packet.readInt());
        assertEquals(204, packet.readInt()); assertEquals(5, packet.readInt());
        assertEquals(6, packet.readInt()); assertEquals(7, packet.readUnsignedByte());
        assertEquals(3, packet.readUnsignedByte()); assertEquals(103, packet.readInt());
        assertEquals(2, packet.readUnsignedByte()); assertEquals(205, packet.readInt());
        assertEquals(206, packet.readInt());
        assertEquals(4, packet.readUnsignedByte()); assertEquals(1, packet.readUnsignedByte());
        assertEquals(104, packet.readInt()); assertEquals(105, packet.readInt());
        assertEquals(207, packet.readInt()); assertEquals(208, packet.readInt());
        assertEquals(209, packet.readInt()); assertEquals(210, packet.readUnsignedShort());
        assertEquals(2, packet.readUnsignedByte()); assertEquals(211, packet.readUnsignedShort());
        assertEquals(212, packet.readUnsignedShort());
        assertEquals(5, packet.readUnsignedByte()); assertEquals(106, packet.readInt());
        assertEquals(107, packet.readInt()); assertEquals(213, packet.readInt());
        assertEquals(214, packet.readInt()); assertEquals(215, packet.readInt());
        assertEquals(6, packet.readUnsignedByte()); assertEquals(4, packet.readUnsignedByte());
        assertEquals(6, packet.readUnsignedByte()); assertEquals(108, packet.readInt());
        assertEquals(109, packet.readInt()); assertEquals(5, packet.readUnsignedByte());
        assertEquals(2, packet.readUnsignedByte());
        assertEquals(7, packet.readUnsignedByte()); assertEquals(110, packet.readInt());
        assertEquals(3, packet.readUnsignedByte()); assertEquals(216, packet.readUnsignedShort());
        assertEquals(4, packet.readUnsignedByte());
        assertEquals(8, packet.readUnsignedByte()); assertEquals(111, packet.readInt());
        assertEquals(5, packet.readUnsignedByte());
        assertEquals(9, packet.readUnsignedByte()); assertEquals(112, packet.readInt());
        assertEquals(6, packet.readUnsignedByte());
        assertEquals(10, packet.readUnsignedByte()); assertEquals(113, packet.readInt());
        assertEquals(114, packet.readInt()); assertEquals(7, packet.readUnsignedByte());
        assertEquals(8, packet.readUnsignedByte());
        assertEquals(11, packet.readUnsignedByte()); assertEquals(115, packet.readInt());
        assertEquals(217, packet.readInt());
        assertEquals(12, packet.readUnsignedByte()); assertEquals(116, packet.readInt());
        for (int index = 0; index < 4; index++) assertEquals(0, packet.readUnsignedShort());
        assertEquals(0, packet.readableBytes());
    }

    @Test
    void instanceListRejectsRowsBeyondTheSharedClientLimit() {
        SnowStormInstanceSummary value = SnowStormInstanceSummary.live(startedGame());
        assertThrows(IllegalArgumentException.class, () -> new SnowStormInstanceListComposer(
                Collections.nCopies(SnowStormProtocol.MAX_INSTANCE_ROWS + 1, value)));
    }

    @Test
    void frozenSimplePacketsUseTheirExactHeadersAndFieldWidths() {
        SnowStormGame game = startedGame();
        assertHeader(new SnowStormLoungeInfoComposer(2, 3, 4, 5,
                List.of(1, 2, 3, 4, 5, 6, 7)).compose().get(), Outgoing.ShockwaveSnowStormLoungeInfoComposer);
        assertHeader(new SnowStormInstanceListComposer(List.of(
                SnowStormInstanceSummary.live(game))).compose().get(), Outgoing.ShockwaveSnowStormInstanceListComposer);
        assertHeader(new SnowStormGameInstanceComposer(
                SnowStormInstanceSummary.live(game)).compose().get(), Outgoing.ShockwaveSnowStormGameInstanceComposer);
        assertHeader(new SnowStormInstanceRemovedComposer(0, game.id()).compose().get(),
                Outgoing.ShockwaveSnowStormInstanceRemovedComposer);
        assertHeader(new SnowStormJoinParametersComposer(game, 0).compose().get(),
                Outgoing.ShockwaveSnowStormJoinParametersComposer);
        assertHeader(new SnowStormOperationFailedComposer(3, game.id(), 7).compose().get(),
                Outgoing.ShockwaveSnowStormOperationFailedComposer);
        assertHeader(new SnowStormGameLocationComposer(game, 10, false).compose().get(),
                Outgoing.ShockwaveSnowStormGameLocationComposer);
        assertHeader(new SnowStormGameResetComposer(game).compose().get(),
                Outgoing.ShockwaveSnowStormGameResetComposer);
        assertHeader(new SnowStormGameStartComposer(game).compose().get(),
                Outgoing.ShockwaveSnowStormGameStartComposer);
        assertHeader(new SnowStormPlayerRemovedComposer(game.id(), 1).compose().get(),
                Outgoing.ShockwaveSnowStormPlayerRemovedComposer);
        assertHeader(new SnowStormSpectatorCountComposer(game.id(), 20).compose().get(),
                Outgoing.ShockwaveSnowStormSpectatorCountComposer);
        assertHeader(new SnowStormSkillLevelChangedComposer(3, 9_000).compose().get(),
                Outgoing.ShockwaveSnowStormSkillLevelChangedComposer);
        assertHeader(new SnowStormIdleWarningComposer(game.id(), 15).compose().get(),
                Outgoing.ShockwaveSnowStormIdleWarningComposer);
    }

    @Test
    void instanceSummaryCarriesEveryParticipantName() {
        SnowStormGame game = startedGame();
        ByteBuf packet = payload(new SnowStormInstanceListComposer(List.of(
                SnowStormInstanceSummary.live(game))).compose().get(),
                Outgoing.ShockwaveSnowStormInstanceListComposer);
        assertEquals(1, packet.readUnsignedShort());
        assertEquals(SnowStormInstanceSummary.LIVE, packet.readUnsignedByte());
        assertEquals(game.id(), packet.readInt());
        assertEquals("SnowStorm", readString(packet));
        assertEquals(10, packet.readInt());
        assertEquals("Owner", readString(packet));
        packet.skipBytes(1 + 1 + 1 + 1 + 2 + 1 + 4);
        assertEquals(2, packet.readUnsignedByte());
        assertEquals("Owner", readString(packet));
        assertEquals("Opponent", readString(packet));
        assertEquals(0, packet.readableBytes());
    }

    @Test
    void fullSnapshotRoundTripsEveryObjectAndEndsAtTheChecksum() {
        SnowStormGame game = startedGame();
        SnowStormGame.FullStatusSnapshot snapshot = game.fullStatusSnapshot(
                10, SnowStormGame.COUNTDOWN_MS);
        ByteBuf packet = payload(new SnowStormFullGameStatusComposer(snapshot).compose().get(),
                Outgoing.ShockwaveSnowStormFullGameStatusComposer);
        assertEquals(SnowStormProtocol.VERSION, packet.readInt());
        assertEquals(game.id(), packet.readInt());
        assertEquals(game.sequence(), packet.readInt());
        assertEquals(game.state().id(), packet.readUnsignedByte());
        assertEquals(game.roundNumber(), packet.readInt());
        assertEquals(snapshot.turnNumber(), packet.readInt());
        packet.readInt();
        assertEquals(1, packet.readInt());
        assertTrue(packet.readBoolean());
        assertEquals(2, packet.readUnsignedByte());
        readScores(packet, 2);
        int objects = packet.readUnsignedShort();
        assertEquals(snapshot.objects().size(), objects);
        for (int index = 0; index < objects; index++) readObject(packet);
        assertEquals(snapshot.checksum(), packet.readInt());
        assertEquals(0, packet.readableBytes());
    }

    @Test
    void joinParametersCarryACompleteDeterministicallyOrderedWaitingRoster() {
        SnowStormGame game = new SnowStormGame(91, "Roster", 30, "Owner",
                SnowStormMapCatalog.loadGenerated().get(1), 1, 2, 20);
        assertTrue(game.addPlayer(30, "Owner", "hd-180-1", "M", 1));
        assertTrue(game.addPlayer(20, "Second", "hd-180-2", "F", 0));
        assertTrue(game.addPlayer(10, "First", "hd-180-3", "M", 0));
        assertTrue(game.addBot(1));

        ByteBuf packet = payload(new SnowStormJoinParametersComposer(game, 1).compose().get(),
                Outgoing.ShockwaveSnowStormJoinParametersComposer);
        assertEquals(91, packet.readInt());
        assertEquals(2, packet.readUnsignedByte());
        assertEquals(1, packet.readUnsignedByte());
        assertEquals(2, packet.readUnsignedByte());
        assertEquals(10, packet.readInt());
        assertEquals("First", readString(packet));
        assertEquals(20, packet.readInt());
        assertEquals("Second", readString(packet));
        assertEquals(2, packet.readUnsignedByte());
        assertEquals(-1, packet.readInt());
        assertEquals("Bot 1", readString(packet));
        assertEquals(30, packet.readInt());
        assertEquals("Owner", readString(packet));
        assertEquals(0, packet.readableBytes());
    }

    @Test
    void fullSnapshotCarriesTheAvatarGoalTileUsedByTheChecksum() {
        SnowStormGame game = startedGame();
        SnowStormGame.FullStatusSnapshot snapshot = game.fullStatusSnapshot(
                10, SnowStormGame.COUNTDOWN_MS);
        SnowStormSimulation.ObjectSnapshot avatar = snapshot.objects().stream()
                .filter(object -> object.type() == 0).findFirst().orElseThrow();
        ByteBuf packet = payload(new SnowStormFullGameStatusComposer(snapshot).compose().get(),
                Outgoing.ShockwaveSnowStormFullGameStatusComposer);
        packet.skipBytes(4 + 4 + 4 + 1 + 4 + 4 + 4 + 4 + 1 + 1);
        readScores(packet, 2);
        assertTrue(packet.readUnsignedShort() > 0);
        assertEquals(0, packet.readUnsignedByte());
        assertEquals(avatar.objectId(), packet.readInt());
        packet.readInt(); readString(packet); readString(packet); readString(packet);
        packet.skipBytes(1 + 1 + 4 + 4 + 1 + 1 + 1 + 1 + 2);
        assertEquals(avatar.values().get(8).intValue(), packet.readInt());
        assertEquals(avatar.values().get(9).intValue(), packet.readInt());
    }

    @Test
    void incrementalStatusRoundTripsFiveOrderedSubturns() {
        SnowStormGame game = startedGame();
        assertTrue(game.beginRunning(SnowStormGame.COUNTDOWN_MS));
        SnowStormSimulation.TurnResult turn = game.advance(SnowStormGame.COUNTDOWN_MS);
        int baseSequence = game.sequence() - 1;
        ByteBuf packet = payload(new SnowStormGameStatusComposer(game, turn, baseSequence,
                game.sequence(), game.timeRemainingMs(SnowStormGame.COUNTDOWN_MS)).compose().get(),
                Outgoing.ShockwaveSnowStormGameStatusComposer);
        assertEquals(SnowStormProtocol.VERSION, packet.readInt());
        assertEquals(game.id(), packet.readInt());
        assertEquals(baseSequence, packet.readInt());
        assertEquals(game.sequence(), packet.readInt());
        assertEquals(turn.turnNumber(), packet.readInt());
        packet.readInt();
        assertEquals(SnowStormState.RUNNING.id(), packet.readUnsignedByte());
        assertEquals(turn.startChecksum(), packet.readInt());
        assertEquals(5, packet.readUnsignedByte());
        for (int subturn = 0; subturn < 5; subturn++) {
            int count = packet.readUnsignedShort();
            assertEquals(turn.subturns().get(subturn).size(), count);
            for (int index = 0; index < count; index++) readEvent(packet);
        }
        readScores(packet, 2);
        assertEquals(0, packet.readableBytes());
    }

    @Test
    void liveAndHistoricalResultsSerializeByPlacementInsteadOfMapOrder() {
        SnowStormRoundResult result = result();
        ByteBuf live = payload(new SnowStormGameEndComposer(result, 5_000).compose().get(),
                Outgoing.ShockwaveSnowStormGameEndComposer);
        assertEquals(1, live.readInt());
        assertEquals(77, live.readInt());
        assertEquals(12, live.readInt());
        assertEquals(1, live.readInt());
        readResultBody(live);
        assertEquals(5_000, live.readInt());
        assertEquals(0, live.readableBytes());

        SnowStormFinishedGame finished = new SnowStormFinishedGame(result.resultId(), result.instanceId(),
                result.roundNumber(), result.name(), result.ownerUserId(), result.ownerName(), result.mapId(),
                result.teamCount(), result.lengthChoice(), result.roundSeconds(), 123, result.endReason(),
                result.winningTeam(), result.ranked(), result.teams(), result.players());
        ByteBuf historical = payload(new SnowStormFinishedGameComposer(finished).compose().get(),
                Outgoing.ShockwaveSnowStormFinishedGameComposer);
        assertEquals(1, historical.readInt());
        assertEquals(77, historical.readInt());
        assertEquals(1, historical.readInt());
        readResultBody(historical);
        assertEquals(0, historical.readableBytes());
    }

    @Test
    void legacyLargeSnowballObjectTypeIsRejected() {
        SnowStormGame game = startedGame();
        ServerMessage response = new ServerMessage(1);
        SnowStormSimulation.ObjectSnapshot legacy = new SnowStormSimulation.ObjectSnapshot(
                3, 999, List.of(1, 2), 0);
        assertThrows(IllegalArgumentException.class,
                () -> SnowStormWire.writeObject(response, game.startingRoster(), legacy));
    }

    private static SnowStormGame startedGame() {
        SnowStormGame game = new SnowStormGame(1, "SnowStorm", 10, "Owner",
                SnowStormMapCatalog.loadGenerated().get(1), 1, 2, 20);
        assertTrue(game.addPlayer(10, "Owner", "hd-180-1", "M", 0));
        assertTrue(game.addPlayer(20, "Opponent", "hd-180-2", "F", 1));
        SnowStormStartingRoster roster = game.prepareStart(10, true);
        assertTrue(game.beginChargedRound(roster, 77, 0));
        return game;
    }

    private static SnowStormRoundResult result() {
        Map<Integer, SnowStormRoundResult.TeamResult> teams = new LinkedHashMap<>();
        teams.put(0, new SnowStormRoundResult.TeamResult(0, 3, 2));
        teams.put(1, new SnowStormRoundResult.TeamResult(1, 9, 1));
        List<SnowStormRoundResult.PlayerResult> players = List.of(
                new SnowStormRoundResult.PlayerResult(10, "Second", 0, 3, 2, false, false),
                new SnowStormRoundResult.PlayerResult(20, "Winner", 1, 9, 1, true, false));
        return new SnowStormRoundResult(77, 1, 12, 1, "SnowStorm", 10, "Owner", 1,
                2, 1, 120, SnowStormRoundResult.END_TIME_EXPIRED, false, true, 1, teams, players);
    }

    private static void readResultBody(ByteBuf packet) {
        assertEquals(SnowStormRoundResult.END_TIME_EXPIRED, packet.readUnsignedByte());
        assertEquals(1, packet.readUnsignedByte());
        assertTrue(packet.readBoolean());
        assertEquals(2, packet.readUnsignedByte());
        assertEquals(1, packet.readUnsignedByte());
        assertEquals(9, packet.readInt());
        assertEquals(1, packet.readUnsignedByte());
        assertEquals(0, packet.readUnsignedByte());
        assertEquals(3, packet.readInt());
        assertEquals(2, packet.readUnsignedByte());
        assertEquals(2, packet.readUnsignedShort());
        assertEquals(20, packet.readInt());
        assertEquals("Winner", readString(packet));
        packet.skipBytes(1 + 4);
        assertEquals(1, packet.readUnsignedByte());
        assertTrue(packet.readBoolean());
        assertEquals(10, packet.readInt());
        assertEquals("Second", readString(packet));
        packet.skipBytes(1 + 4);
        assertEquals(2, packet.readUnsignedByte());
        assertTrue(!packet.readBoolean());
    }

    private static void readScores(ByteBuf packet, int expected) {
        assertEquals(expected, packet.readUnsignedByte());
        for (int index = 0; index < expected; index++) {
            assertEquals(index, packet.readUnsignedByte());
            packet.readInt();
        }
    }

    private static void readObject(ByteBuf packet) {
        int type = packet.readUnsignedByte();
        packet.readInt();
        if (type == 0) {
            packet.readInt(); readString(packet); readString(packet); readString(packet);
            packet.skipBytes(1 + 1 + 4 + 4 + 1 + 1 + 1 + 1 + 2 + 4 + 4 + 4 + 4 + 4 + 1 + 1);
        } else if (type == 1) {
            packet.skipBytes(4 + 4 + 4 + 4 + 2 + 1 + 2 + 2);
        } else if (type == 2) {
            packet.skipBytes(4 + 4 + 1 + 2);
        } else {
            throw new AssertionError("Unknown SnowStorm object " + type);
        }
    }

    private static void readEvent(ByteBuf packet) {
        int type = packet.readUnsignedByte();
        switch (type) {
            case 0, 4 -> readObject(packet);
            case 1, 12 -> packet.skipBytes(4);
            case 2 -> packet.skipBytes(4 + 4 + 4 + 4 + 4 + 4 + 4 + 1);
            case 3 -> packet.skipBytes(4 + 1 + 4 + 4);
            case 5 -> packet.skipBytes(4 + 4 + 4 + 4 + 4 + 1 + 1);
            case 6 -> packet.skipBytes(4 + 4 + 1);
            case 7 -> packet.skipBytes(4 + 1 + 2 + 1);
            case 8, 9 -> packet.skipBytes(4 + 1);
            case 10 -> packet.skipBytes(4 + 4 + 1 + 1);
            case 11 -> packet.skipBytes(4 + 4);
            default -> throw new AssertionError("Unknown SnowStorm event " + type);
        }
    }

    private static ByteBuf payload(ByteBuf packet, int header) {
        packet.skipBytes(4);
        assertEquals(header, packet.readUnsignedShort());
        return packet;
    }

    private static void assertHeader(ByteBuf packet, int expected) {
        packet.skipBytes(4);
        assertEquals(expected, packet.readUnsignedShort());
    }

    private static String readString(ByteBuf packet) {
        int length = packet.readUnsignedShort();
        return packet.readCharSequence(length, StandardCharsets.UTF_8).toString();
    }
}

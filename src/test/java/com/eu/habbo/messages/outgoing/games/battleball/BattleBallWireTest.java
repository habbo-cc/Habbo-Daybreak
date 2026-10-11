package com.eu.habbo.messages.outgoing.games.battleball;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.games.battleball.BattleBallEvent;
import com.eu.habbo.habbohotel.games.battleball.BattleBallGame;
import com.eu.habbo.habbohotel.games.battleball.BattleBallMap;
import com.eu.habbo.habbohotel.games.battleball.BattleBallPlayer;
import com.eu.habbo.habbohotel.games.battleball.BattleBallPosition;
import com.eu.habbo.habbohotel.games.battleball.BattleBallPowerType;
import com.eu.habbo.habbohotel.games.battleball.BattleBallState;
import com.eu.habbo.habbohotel.games.battleball.BattleBallTile;
import com.eu.habbo.habbohotel.games.battleball.BattleBallUpdate;
import com.eu.habbo.messages.ServerMessage;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BattleBallWireTest {
    @Test
    void fullTilesUseOnePackedShortInImplicitRowMajorOrder() {
        BattleBallTile tile = new BattleBallTile(0, 0, true, 9, true);
        tile.land(1, 3, 1, true, false);
        ServerMessage message = new ServerMessage(1);

        BattleBallWire.writeFullTiles(message, List.of(tile), 1, 1);

        ByteBuf packet = message.get();
        assertEquals(8, packet.readableBytes());
        packet.skipBytes(6);
        assertEquals(5 | 4 << 3 | 9 << 6 | 1 << 10, packet.readUnsignedShort());
    }

    @Test
    void deltaTilesUseShortCountAndThreeBytesPerChange() {
        BattleBallTile tile = new BattleBallTile(12, 27, true);
        tile.land(1, 2, 1, false, false);
        ServerMessage message = new ServerMessage(1);

        BattleBallWire.writeDeltaTiles(message, List.of(tile));

        ByteBuf packet = message.get();
        assertEquals(11, packet.readableBytes());
        packet.skipBytes(6);
        assertEquals(1, packet.readUnsignedShort());
        assertEquals(12, packet.readUnsignedByte());
        assertEquals(27, packet.readUnsignedByte());
        assertEquals(4 | 2 << 3, packet.readUnsignedByte());
    }

    @Test
    void dynamicPlayerChangeUsesFourteenBytes() {
        BattleBallPlayer player = player(1, 1, 0, 12, 27);
        BattleBallUpdate update = update(List.of(player), List.of(BattleBallEvent.move(1, 12, 27)), false);
        ServerMessage message = new ServerMessage(1);

        BattleBallWire.writeObjectChanges(message, update);

        // Six-byte packet header + 2-byte spawn count + 2-byte update count +
        // 14-byte dynamic player + 2-byte removal count.
        assertEquals(26, message.get().readableBytes());
    }

    @Test
    void boardResetSendsCompletePlayersAsSpawns() {
        BattleBallPlayer player = player(1, 1, 0, 0, 0);
        ServerMessage message = new ServerMessage(1);

        BattleBallWire.writeObjectChanges(message, update(List.of(player), List.of(), true));

        ByteBuf packet = message.get();
        packet.skipBytes(6);
        assertEquals(1, packet.readUnsignedShort());
        assertEquals(0, packet.readUnsignedByte());
        packet.skipBytes(33);
        assertEquals(0, packet.readUnsignedShort());
        assertEquals(0, packet.readUnsignedShort());
    }

    @Test
    void maximumRosterFullSnapshotStaysBelowFiveKilobytes() {
        BattleBallGame game = maximumRosterGame();
        BattleBallUpdate update = game.consumeUpdate(0, true);

        int bytes = new BattleBallFullGameStatusComposer(game, update).compose().get().readableBytes();

        assertEquals(2_564, bytes);
    }

    @Test
    void maximumRosterMovementDeltaStaysBelowFourHundredBytes() {
        BattleBallGame game = maximumRosterGame();
        List<BattleBallPlayer> players = List.copyOf(game.players());
        List<BattleBallEvent> movements = players.stream()
                .map(player -> BattleBallEvent.move(player.objectId(), player.position().x(), player.position().y()))
                .toList();
        BattleBallUpdate update = update(players, movements, false);

        int bytes = new BattleBallGameStatusComposer(game, update, 0).compose().get().readableBytes();

        assertEquals(212, bytes);
    }

    @Test
    void gameEndCarriesTheAuthoritativeEndingDelay() {
        BattleBallMap map = new BattleBallMap(1, "111|111|111|");
        BattleBallGame game = new BattleBallGame(100, "ending", 1, "owner", map, 60, 2, 1, false,
                Set.of(), Map.of(0, List.of(new BattleBallPosition(0, 0)),
                        1, List.of(new BattleBallPosition(2, 2))), 60, 15);
        assertTrue(game.addPlayer(1, "owner", "", "", "M", 0));
        assertTrue(game.addPlayer(2, "opponent", "", "", "F", 1));
        long now = System.currentTimeMillis();
        assertTrue(game.start(1, now - 63_000, true, true));
        game.tick(now - 60_000);
        game.tick(now);
        game.tick(System.currentTimeMillis());

        ByteBuf packet = new BattleBallGameEndComposer(game).compose().get();
        packet.skipBytes(6);
        assertEquals(game.id(), packet.readInt());
        assertEquals(game.sequence(), packet.readInt());
        packet.readByte();
        int endingMs = packet.readInt();
        assertTrue(endingMs > 0 && endingMs <= 5_000);
    }

    @Test
    void playerRemovalPacketCarriesInstanceAndReason() {
        ByteBuf packet = new BattleBallPlayerRemovedComposer(44, 1).compose().get();
        packet.skipBytes(4);
        assertEquals(7614, packet.readUnsignedShort());
        assertEquals(44, packet.readInt());
        assertEquals(1, packet.readInt());
        assertEquals(0, packet.readableBytes());
    }

    @Test
    void spectatorCountPacketCarriesNonNegativeCount() {
        ByteBuf packet = new BattleBallSpectatorCountComposer(45, -3).compose().get();
        packet.skipBytes(4);
        assertEquals(7617, packet.readUnsignedShort());
        assertEquals(45, packet.readInt());
        assertEquals(0, packet.readInt());
        assertEquals(0, packet.readableBytes());
    }

    private static BattleBallUpdate update(List<BattleBallPlayer> players, List<BattleBallEvent> events,
                                           boolean boardReset) {
        return new BattleBallUpdate(1, BattleBallState.RUNNING, 30_000, players, List.of(), List.of(),
                List.of(), List.of(), Map.of(), events, boardReset);
    }

    private static BattleBallPlayer player(int objectId, int userId, int teamId, int x, int y) {
        return new BattleBallPlayer(objectId, userId, "John", "unused motto", "hd-180-1", "M",
                teamId, new BattleBallPosition(x, y));
    }

    private static BattleBallGame maximumRosterGame() {
        String row = "1".repeat(28);
        BattleBallMap map = new BattleBallMap(1, String.join("|", java.util.Collections.nCopies(28, row)));
        Map<Integer, List<BattleBallPosition>> spawns = new LinkedHashMap<>();
        for (int team = 0; team < 4; team++) {
            List<BattleBallPosition> positions = new ArrayList<>();
            for (int slot = 0; slot < 3; slot++) positions.add(new BattleBallPosition(team * 3 + slot, team));
            spawns.put(team, positions);
        }
        BattleBallGame game = new BattleBallGame(99, "packet budget", 1, "owner", map, 600, 4, 3, false,
                Set.of(BattleBallPowerType.LIGHT_BULB), spawns, 60, 15);
        for (int userId = 1; userId <= 12; userId++) {
            int team = (userId - 1) / 3;
            String figure = "hr-100-61.hd-190-7.ch-210-66.lg-270-82.sh-290-80";
            assertTrue(game.addPlayer(userId, "Player " + userId, "motto is not sent", figure, "M", team));
        }
        return game;
    }
}

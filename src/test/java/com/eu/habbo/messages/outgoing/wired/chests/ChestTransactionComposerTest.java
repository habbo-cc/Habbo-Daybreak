package com.eu.habbo.messages.outgoing.wired.chests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.items.chests.ChestTransactionLog;
import com.eu.habbo.messages.outgoing.Outgoing;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Golden coverage for the five July transaction-history packets' two responses. */
class ChestTransactionComposerTest {
  @Test
  void serializesJulyLogPageWithEchoedPageSizeAndLongIds() {
    ChestTransactionLog.Info info =
        new ChestTransactionLog.Info(
            0x1_0000_0002L,
            77,
            ChestTransactionLog.TYPE_CONTRACT_TRADE,
            "contract:91",
            12,
            "Habbo",
            1_725_000_000_000L,
            2,
            3,
            4,
            5,
            6);
    ByteBuf packet =
        new ChestTransactionLogsComposer(
                new ChestTransactionLog.Page(
                    ChestTransactionLog.LIST_ROOM, 77, 31, 2, 25, List.of(info)))
            .compose()
            .get();
    try {
      assertHeader(packet, Outgoing.ChestTransactionLogsComposer, 7134);
      assertEquals(ChestTransactionLog.LIST_ROOM, packet.readInt());
      assertEquals(77, packet.readLong());
      assertEquals(31, packet.readInt());
      assertEquals(2, packet.readInt());
      assertEquals(25, packet.readInt());
      assertEquals(1, packet.readInt());
      assertInfo(packet, info);
      assertEquals(0, packet.readableBytes());
    } finally {
      packet.release();
    }
  }

  @Test
  void serializesJulyDetailsInDepositThenWithdrawalOrder() {
    ChestTransactionLog.Info info =
        new ChestTransactionLog.Info(
            42,
            77,
            ChestTransactionLog.TYPE_CONTRACT_REWARD,
            "",
            12,
            "Habbo",
            1_725_000_000_000L,
            2,
            1,
            3,
            9,
            0);
    ChestTransactionLog.ItemType deposit =
        new ChestTransactionLog.ItemType(false, 501, "", 3);
    ChestTransactionLog.ItemType withdrawal =
        new ChestTransactionLog.ItemType(true, 601, "poster-9", 1);
    ByteBuf packet =
        new ChestTransactionDetailsComposer(
                new ChestTransactionLog.Details(
                    info, List.of(100, 101), List.of(deposit), List.of(withdrawal), true))
            .compose()
            .get();
    try {
      assertHeader(packet, Outgoing.ChestTransactionDetailsComposer, 7135);
      assertInfo(packet, info);
      assertEquals(2, packet.readInt());
      assertEquals(100, packet.readInt());
      assertEquals(101, packet.readInt());
      assertEquals(1, packet.readInt());
      assertFalse(packet.readBoolean());
      assertEquals(501, packet.readInt());
      assertEquals("", readString(packet));
      assertEquals(3, packet.readInt());
      assertEquals(1, packet.readInt());
      assertTrue(packet.readBoolean());
      assertEquals(601, packet.readInt());
      assertEquals("poster-9", readString(packet));
      assertEquals(1, packet.readInt());
      assertTrue(packet.readBoolean());
      assertEquals(0, packet.readableBytes());
    } finally {
      packet.release();
    }
  }

  @Test
  void mapsContractFurnitureKindsToJulyTransactionKinds() {
    assertEquals(2, ChestTransactionLog.contractType(0));
    assertEquals(4, ChestTransactionLog.contractType(1));
    assertEquals(3, ChestTransactionLog.contractType(2));
  }

  private static void assertInfo(ByteBuf packet, ChestTransactionLog.Info expected) {
    assertEquals(expected.transactionId(), packet.readLong());
    assertEquals(expected.roomId(), packet.readInt());
    assertEquals(expected.transactionType(), packet.readInt());
    assertEquals(expected.definitionInfo(), readString(packet));
    assertEquals(expected.userId(), packet.readInt());
    assertEquals(expected.username(), readString(packet));
    assertEquals(expected.timestamp(), packet.readLong());
    assertEquals(expected.readableTimestamp(), readString(packet));
    assertEquals(expected.chestCount(), packet.readInt());
    assertEquals(expected.withdrawFurniCount(), packet.readInt());
    assertEquals(expected.depositFurniCount(), packet.readInt());
    assertEquals(expected.withdrawCoinsCount(), packet.readInt());
    assertEquals(expected.depositCoinsCount(), packet.readInt());
  }

  private static void assertHeader(ByteBuf packet, int header, int localHeader) {
    assertEquals(packet.readInt(), packet.readableBytes());
    assertEquals(localHeader, header);
    assertEquals(header, packet.readUnsignedShort());
  }

  private static String readString(ByteBuf packet) {
    int length = packet.readUnsignedShort();
    return packet.readCharSequence(length, StandardCharsets.UTF_8).toString();
  }
}

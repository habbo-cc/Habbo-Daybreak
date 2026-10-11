package com.eu.habbo.messages.outgoing.wired.chests;

import com.eu.habbo.messages.outgoing.Outgoing;
import com.eu.habbo.habbohotel.items.chests.ChestContractPlan;
import com.eu.habbo.habbohotel.items.interactions.InteractionChestContract;
import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Golden coverage for the July AIR chest base packets on collision-checked local headers.
 */
class ChestBaseComposerTest {
    @Test
    void serializesOpenInstruction() {
        ByteBuf packet = new ChestOpenInstructionComposer(321).compose().get();
        try {
            assertHeader(packet, Outgoing.ChestOpenInstructionComposer, 7120);
            assertEquals(321, packet.readInt());
            assertExhausted(packet);
        } finally {
            packet.release();
        }
    }

    @Test
    void serializesEmptyFurniContentsFragment() {
        ByteBuf packet = new ChestFurniContentsComposer(321, 3, 1, List.of()).compose().get();
        try {
            assertHeader(packet, Outgoing.ChestFurniContentsComposer, 7121);
            assertEquals(321, packet.readInt());
            assertEquals(3, packet.readInt());
            assertEquals(1, packet.readInt());
            assertEquals(0, packet.readInt());
            assertExhausted(packet);
        } finally {
            packet.release();
        }
    }

    @Test
    void serializesEmptyFurniContentsDelta() {
        ByteBuf packet = new ChestFurniContentsUpdateComposer(321, new int[] {7, 9}, List.of())
                .compose().get();
        try {
            assertHeader(packet, Outgoing.ChestFurniContentsUpdateComposer, 7122);
            assertEquals(321, packet.readInt());
            assertEquals(2, packet.readInt());
            assertEquals(7, packet.readInt());
            assertEquals(9, packet.readInt());
            assertEquals(0, packet.readInt());
            assertExhausted(packet);
        } finally {
            packet.release();
        }
    }

    @Test
    void serializesCoinBalance() {
        ByteBuf packet = new ChestCoinBalanceComposer(321, 4567, true).compose().get();
        try {
            assertHeader(packet, Outgoing.ChestCoinBalanceComposer, 7123);
            assertEquals(321, packet.readInt());
            assertEquals(4567, packet.readInt());
            assertTrue(packet.readBoolean());
            assertExhausted(packet);
        } finally {
            packet.release();
        }
    }

    @Test
    void serializesSettingsResult() {
        ByteBuf packet = new ChestSettingsResultComposer(321, false).compose().get();
        try {
            assertHeader(packet, Outgoing.ChestSettingsResultComposer, 7124);
            assertEquals(321, packet.readInt());
            assertFalse(packet.readBoolean());
            assertExhausted(packet);
        } finally {
            packet.release();
        }
    }

    @Test
    void serializesUpgradeResult() {
        ByteBuf packet = new ChestUpgradeResultComposer(321, 7).compose().get();
        try {
            assertHeader(packet, Outgoing.ChestUpgradeResultComposer, 7125);
            assertEquals(321, packet.readInt());
            assertEquals(7, packet.readInt());
            assertExhausted(packet);
        } finally {
            packet.release();
        }
    }

    @Test
    void serializesManualCoinTradeInitiation() {
        ByteBuf packet = new WiredTradeInitiateComposer(
                com.eu.habbo.habbohotel.items.chests.ChestType.COINS, true, 60)
                .compose().get();
        try {
            assertHeader(packet, Outgoing.WiredTradeInitiateComposer, 7129);
            assertEquals(0, packet.readInt());
            assertEquals("", readString(packet));
            assertEquals("generic", readString(packet));
            assertFalse(packet.readBoolean());
            assertTrue(packet.readBoolean());
            assertEquals(60, packet.readInt());
            assertExhausted(packet);
        } finally {
            packet.release();
        }
    }

    @Test
    void serializesManualFurniTradeInitiation() {
        ByteBuf packet = new WiredTradeInitiateComposer(
                com.eu.habbo.habbohotel.items.chests.ChestType.FURNI, true, 60)
                .compose().get();
        try {
            assertHeader(packet, Outgoing.WiredTradeInitiateComposer, 7129);
            assertEquals(1, packet.readInt());
            assertEquals("", readString(packet));
            assertEquals("generic", readString(packet));
            assertFalse(packet.readBoolean());
            assertTrue(packet.readBoolean());
            assertEquals(60, packet.readInt());
            assertExhausted(packet);
        } finally {
            packet.release();
        }
    }

    @Test
    void serializesTradeCancellationAndEmptyCompletion() {
        ByteBuf cancelled = new WiredTradeCancelledComposer(19).compose().get();
        try {
            assertHeader(cancelled, Outgoing.WiredTradeCancelledComposer, 7126);
            assertEquals(19, cancelled.readInt());
            assertExhausted(cancelled);
        } finally {
            cancelled.release();
        }
        ByteBuf completed = new WiredTradeCompletedComposer().compose().get();
        try {
            assertHeader(completed, Outgoing.WiredTradeCompletedComposer, 7127);
            assertExhausted(completed);
        } finally {
            completed.release();
        }
    }

    @Test
    void serializesAnythingContractWithTheJulySimpleDiscriminator() throws Exception {
        ChestContractPlan contract = contract(
                InteractionChestContract.TYPE_PAYMENT, null, null,
                ChestContractPlan.MULTIPLIER_AUTO, 500);
        ByteBuf packet = new WiredTradeInitiateComposer(contract, true, false, 0)
                .compose().get();
        try {
            assertHeader(packet, Outgoing.WiredTradeInitiateComposer, 7129);
            assertEquals(2, packet.readInt());
            assertEquals("receive", readString(packet));
            assertEquals("generic", readString(packet));
            assertTrue(packet.readBoolean());
            assertFalse(packet.readBoolean());
            assertEquals(0, packet.readInt());
            assertExhausted(packet);
        } finally {
            packet.release();
        }
    }

    @Test
    void serializesRuleContractAndFixedMultiplierInJulyOrder() throws Exception {
        ChestContractPlan.Rule give = new ChestContractPlan.Rule(List.of(
                new ChestContractPlan.Node(0, 10, null)));
        ChestContractPlan.Rule get = new ChestContractPlan.Rule(List.of(
                new ChestContractPlan.Node(1, 2,
                        new ChestContractPlan.ItemType(true, 901, "poster-7"))));
        ChestContractPlan contract = contract(
                InteractionChestContract.TYPE_TRADE, List.of(give), get,
                ChestContractPlan.MULTIPLIER_FIXED, 3);
        ByteBuf packet = new WiredTradeInitiateComposer(contract, false, false, 300)
                .compose().get();
        try {
            assertHeader(packet, Outgoing.WiredTradeInitiateComposer, 7129);
            assertEquals(4, packet.readInt());
            assertEquals("receive", readString(packet));
            assertEquals("generic", readString(packet));
            assertTrue(packet.readBoolean());
            assertEquals(1, packet.readInt());
            assertEquals(1, packet.readInt());
            assertEquals(0, packet.readUnsignedByte());
            assertEquals(10, packet.readInt());
            assertTrue(packet.readBoolean());
            assertEquals(1, packet.readInt());
            assertEquals(1, packet.readUnsignedByte());
            assertEquals(2, packet.readInt());
            assertTrue(packet.readBoolean());
            assertEquals(901, packet.readInt());
            assertEquals("poster-7", readString(packet));
            assertEquals(ChestContractPlan.MULTIPLIER_FIXED, packet.readInt());
            assertEquals(3, packet.readInt());
            assertFalse(packet.readBoolean());
            assertFalse(packet.readBoolean());
            assertEquals(300, packet.readInt());
            assertExhausted(packet);
        } finally {
            packet.release();
        }
    }

    @Test
    void serializesScaledJulyRewardSuccessContents() throws Exception {
        ChestContractPlan.Rule get = new ChestContractPlan.Rule(List.of(
                new ChestContractPlan.Node(0, 7, null),
                new ChestContractPlan.Node(1, 2,
                        new ChestContractPlan.ItemType(false, 902, ""))));
        ChestContractPlan contract = contract(
                InteractionChestContract.TYPE_REWARD, null, get,
                ChestContractPlan.MULTIPLIER_FIXED, 3);
        ByteBuf packet = new WiredTransactionSuccessComposer(contract, 3).compose().get();
        try {
            assertHeader(packet, Outgoing.WiredTransactionSuccessComposer, 7133);
            assertEquals(2, packet.readInt());
            assertEquals(2, packet.readInt());
            assertEquals(0, packet.readUnsignedByte());
            assertEquals(21, packet.readInt());
            assertEquals(1, packet.readUnsignedByte());
            assertEquals(6, packet.readInt());
            assertFalse(packet.readBoolean());
            assertEquals(902, packet.readInt());
            assertEquals("", readString(packet));
            assertEquals("reward", readString(packet));
            assertTrue(packet.readBoolean());
            assertExhausted(packet);
        } finally {
            packet.release();
        }
    }

    @Test
    void serializesDirectChestActionRewardSuccess() {
        ChestContractPlan.Rule reward = new ChestContractPlan.Rule(List.of(
                new ChestContractPlan.Node(1, 4,
                        new ChestContractPlan.ItemType(true, 903, "poster-8"))));
        ByteBuf packet = new WiredTransactionSuccessComposer(
                reward, "You found a prize", false).compose().get();
        try {
            assertHeader(packet, Outgoing.WiredTransactionSuccessComposer, 7133);
            assertEquals(2, packet.readInt());
            assertEquals(1, packet.readInt());
            assertEquals(1, packet.readUnsignedByte());
            assertEquals(4, packet.readInt());
            assertTrue(packet.readBoolean());
            assertEquals(903, packet.readInt());
            assertEquals("poster-8", readString(packet));
            assertEquals("You found a prize", readString(packet));
            assertFalse(packet.readBoolean());
            assertExhausted(packet);
        } finally {
            packet.release();
        }
    }

    private static void assertHeader(ByteBuf packet, int header, int localHeader) {
        assertEquals(packet.readInt(), packet.readableBytes());
        assertEquals(localHeader, header);
        assertEquals(header, packet.readUnsignedShort());
    }

    private static void assertExhausted(ByteBuf packet) {
        assertEquals(0, packet.readableBytes());
    }

    private static String readString(ByteBuf packet) {
        int length = packet.readUnsignedShort();
        return packet.readCharSequence(length, java.nio.charset.StandardCharsets.UTF_8)
                .toString();
    }

    @SuppressWarnings("unchecked")
    private static ChestContractPlan contract(
            int type,
            List<ChestContractPlan.Rule> give,
            ChestContractPlan.Rule get,
            int multiplierMode,
            int multiplier) throws Exception {
        Constructor<ChestContractPlan> constructor = ChestContractPlan.class.getDeclaredConstructor(
                int.class, int.class, int.class, List.class, ChestContractPlan.Rule.class,
                String.class, String.class, int.class, boolean.class, String.class,
                int.class, int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(
                100, 200, type, give, get, "receive", "generic", 11, true, "reward",
                multiplierMode, multiplier);
    }
}

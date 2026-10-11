package com.eu.habbo.habbohotel.catalog;

import com.eu.habbo.habbohotel.catalog.marketplace.MarketPlace;
import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredCategoryType;
import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.messages.MalformedPacketException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityBoundsTest {

    @Test
    void marketplaceCommissionCannotOverflowSignedInteger() {
        assertTrue(MarketPlace.isValidListingPrice(MarketPlace.MAX_LISTING_PRICE));
        assertEquals(2_020_000_000, MarketPlace.calculateCommision(MarketPlace.MAX_LISTING_PRICE));

        assertFalse(MarketPlace.isValidListingPrice(Integer.MAX_VALUE));
        assertEquals(0, MarketPlace.calculateCommision(Integer.MAX_VALUE));
        assertEquals(0, MarketPlace.calculateCommision(0));
        assertEquals(0, MarketPlace.calculateCommision(-1));
    }

    @Test
    void catalogPurchaseQuantityIsBounded() {
        assertTrue(CatalogPurchaseLimits.isValidAmount(1));
        assertTrue(CatalogPurchaseLimits.isValidAmount(CatalogPurchaseLimits.MAXIMUM_QUANTITY));
        assertFalse(CatalogPurchaseLimits.isValidAmount(0));
        assertFalse(CatalogPurchaseLimits.isValidAmount(-1));
        assertFalse(CatalogPurchaseLimits.isValidAmount(Integer.MAX_VALUE));

        assertEquals(-1, CatalogPurchaseLimits.checkedPriceProduct(Integer.MAX_VALUE, 2));
        assertEquals(200, CatalogPurchaseLimits.checkedPriceProduct(2, 100));
    }

    @Test
    void wiredTwoParserRejectsHugeCountBeforeAllocation() {
        ByteBuf buffer = Unpooled.buffer(Integer.BYTES).writeInt(Integer.MAX_VALUE);
        try {
            ClientMessage packet = new ClientMessage(0, buffer);
            assertThrows(MalformedPacketException.class,
                    () -> InteractionWired.readSettingsV2(packet, WiredCategoryType.TRIGGER, null));
        } finally {
            buffer.release();
        }
    }

    @Test
    void legacyWiredParserRejectsHugeCountBeforeAllocation() {
        ByteBuf buffer = Unpooled.buffer(Integer.BYTES).writeInt(Integer.MAX_VALUE);
        try {
            ClientMessage packet = new ClientMessage(0, buffer);
            assertThrows(MalformedPacketException.class, () -> InteractionWired.readSettings(packet, false));
        } finally {
            buffer.release();
        }
    }

    @Test
    void boundedCountRejectsPayloadThatCannotFitRemainingBytes() {
        ByteBuf buffer = Unpooled.buffer().writeInt(2).writeInt(1);
        try {
            ClientMessage packet = new ClientMessage(0, buffer);
            assertThrows(MalformedPacketException.class,
                    () -> packet.readBoundedCount(10, Integer.BYTES));
        } finally {
            buffer.release();
        }
    }

    @Test
    void boundedStringRejectsOversizedLengthBeforeAllocation() {
        ByteBuf buffer = Unpooled.buffer().writeShort(32001);
        try {
            ClientMessage packet = new ClientMessage(0, buffer);
            assertThrows(MalformedPacketException.class, () -> packet.readBoundedString(32000));
        } finally {
            buffer.release();
        }
    }
}

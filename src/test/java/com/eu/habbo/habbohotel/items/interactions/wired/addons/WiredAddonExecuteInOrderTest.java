package com.eu.habbo.habbohotel.items.interactions.wired.addons;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.wired.WiredAddonType;
import com.eu.habbo.messages.outgoing.Outgoing;
import com.eu.habbo.messages.outgoing.wired.WiredAddonDataMessageComposer;
import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WiredAddonExecuteInOrderTest {
    @Test
    void emptySettingsSaveAndReloadKeepJulyCategoryCode17Identity() throws Exception {
        WiredAddonExecuteInOrder addon = new WiredAddonExecuteInOrder(41, 1, baseItem(321), "0", 0, 0);
        WiredSettingsV2 empty = settings(new int[0], "", new int[0], new int[0], new String[0],
                new int[0], new int[0]);

        assertTrue(addon.saveData(empty));
        assertEquals("", addon.getWiredData());
        addon.loadWiredData(null, null);
        assertEquals(WiredAddonType.EXECUTE_IN_ORDER, addon.getType());

        RoomSpecialTypes types = new RoomSpecialTypes();
        types.addAddon(addon);
        assertSame(addon, types.getAddon(41));
        assertTrue(types.getAddons(WiredAddonType.EXECUTE_IN_ORDER).contains(addon));

        ByteBuf packet = new WiredAddonDataMessageComposer(addon, null).compose().get();
        try {
            int declaredLength = packet.readInt();
            assertEquals(packet.readableBytes(), declaredLength);
            assertEquals(Outgoing.WiredAddonDataMessageComposer, packet.readUnsignedShort());
            assertEquals(0, packet.readInt());                 // furniLimit
            assertEquals(0, packet.readInt());                 // stuffIds
            assertEquals(0, packet.readInt());                 // stuffIds2
            assertEquals(321, packet.readInt());               // sprite id
            assertEquals(41, packet.readInt());                // room-visible id fallback
            assertEquals("", readString(packet));
            assertEquals(0, packet.readInt());                 // intParams
            assertEquals(0, packet.readInt());                 // variableIds
            assertEquals(0, packet.readInt());                 // furniSourceTypes
            assertEquals(0, packet.readInt());                 // userSourceTypes
            assertEquals(17, packet.readInt());                // July add-on code
            assertFalse(packet.readBoolean());                 // advancedMode
            assertEquals(0, packet.readInt());                 // furni source slots
            assertEquals(0, packet.readInt());                 // user source slots
            assertEquals(0, packet.readInt());                 // furni defaults
            assertEquals(0, packet.readInt());                 // user defaults
            assertFalse(packet.readBoolean());                 // allowWallFurni
            assertEquals(1, packet.readInt());                 // WiredContext blocks
            assertEquals(0, packet.readInt());                 // catalog block
            assertEquals(0, packet.readInt());                 // unavailable catalog hash
            assertEquals(0, packet.readInt());                 // defaultIntParams
            assertEquals(0, packet.readableBytes());
        } finally {
            packet.release();
        }
    }

    @Test
    void rejectsFieldsThatCode17DoesNotOwn() {
        WiredAddonExecuteInOrder addon = new WiredAddonExecuteInOrder(41, 1, null, "0", 0, 0);
        assertFalse(addon.saveData(settings(new int[] {1}, "", new int[0], new int[0],
                new String[0], new int[0], new int[0])));
        assertFalse(addon.saveData(settings(new int[0], "unexpected", new int[0], new int[0],
                new String[0], new int[0], new int[0])));
        assertFalse(addon.saveData(settings(new int[0], "", new int[] {7}, new int[0],
                new String[0], new int[0], new int[0])));
        assertFalse(addon.saveData(settings(new int[0], "", new int[0], new int[0],
                new String[] {"room.score"}, new int[0], new int[0])));
    }

    private static WiredSettingsV2 settings(int[] ints, String text, int[] furni, int[] furni2,
                                             String[] variables, int[] furniSources, int[] userSources) {
        return new WiredSettingsV2(ints, text, furni, furni2, variables, furniSources, userSources,
                0, 0, false, false);
    }

    private static String readString(ByteBuf packet) {
        int length = packet.readUnsignedShort();
        return packet.readCharSequence(length, StandardCharsets.UTF_8).toString();
    }

    private static Item baseItem(int spriteId) throws Exception {
        Constructor<Item> constructor = Item.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        Item item = constructor.newInstance();
        Field sprite = Item.class.getDeclaredField("spriteId");
        sprite.setAccessible(true);
        sprite.setInt(item, spriteId);
        return item;
    }
}

package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionMatchStatePosition;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectMatchFurni;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.WiredMatchFurniSetting;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.messages.outgoing.wired.WiredConditionDataMessageComposer;
import com.eu.habbo.messages.outgoing.wired.WiredEffectDataMessageComposer;
import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WiredV2SelectionSanitizationTest {
    private static final int ROOM_ID = 77;

    @Test
    void v2SerializerPrunesBothSlotsAndKeepsLiveVisibleIds() throws Exception {
        Room room = room();
        HabboItem livePrimary = item(room, 101, 900_101);
        HabboItem stalePrimary = staleItem(102, 900_102);
        HabboItem liveSecondary = item(room, 201, 900_201);
        HabboItem staleSecondary = missingFromRoomManager(202, 900_202);

        DummyEffect effect = new DummyEffect(baseItem(500));
        effect.setRoomId(ROOM_ID);
        effect.primary.addAll(List.of(livePrimary, stalePrimary));
        effect.secondary.addAll(List.of(staleSecondary, liveSecondary));

        ByteBuf packet = new WiredEffectDataMessageComposer(effect, room).compose().get();
        try {
            skipFrameHeader(packet);
            assertEquals(20, packet.readInt());
            assertEquals(List.of(900_101), readIds(packet));
            assertEquals(List.of(900_201), readIds(packet));
        } finally {
            packet.release();
        }

        assertEquals(List.of(livePrimary), effect.primary);
        assertEquals(List.of(liveSecondary), effect.secondary);
        assertTrue(effect.needsUpdate());
    }

    @Test
    void matchEffectRemovesMissingSettingInsteadOfFallingBackToDatabaseId() throws Exception {
        Room room = room();
        HabboItem live = item(room, 301, 800_301);
        WiredEffectMatchFurni effect = new WiredEffectMatchFurni(
                41, 1, baseItem(501), "0", 0, 0);
        effect.setRoomId(ROOM_ID);
        effect.getMatchFurniSettings().add(setting(301));
        effect.getMatchFurniSettings().add(setting(302));

        ByteBuf packet = new WiredEffectDataMessageComposer(effect, room).compose().get();
        try {
            skipFrameHeader(packet);
            packet.readInt();
            assertEquals(List.of(800_301), readIds(packet));
        } finally {
            packet.release();
        }

        assertEquals(1, effect.getMatchFurniSettings().size());
        assertEquals(301, effect.getMatchFurniSettings().iterator().next().item_id);
        assertTrue(effect.needsUpdate());
        assertEquals(live, room.getHabboItemByDatabaseId(301));
    }

    @Test
    void matchConditionRemovesMissingSettingBeforeWritingEditorData() throws Exception {
        Room room = room();
        item(room, 401, 700_401);
        WiredConditionMatchStatePosition condition = new WiredConditionMatchStatePosition(
                42, 1, baseItem(502), "0", 0, 0);
        condition.setRoomId(ROOM_ID);
        condition.getMatchFurniSettings().add(setting(401));
        condition.getMatchFurniSettings().add(setting(402));

        ByteBuf packet = new WiredConditionDataMessageComposer(condition, room).compose().get();
        try {
            skipFrameHeader(packet);
            packet.readInt();
            assertEquals(List.of(700_401), readIds(packet));
        } finally {
            packet.release();
        }

        assertEquals(1, condition.getMatchFurniSettings().size());
        assertEquals(401, condition.getMatchFurniSettings().iterator().next().item_id);
        assertTrue(condition.needsUpdate());
    }

    private static Room room() {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(ROOM_ID);
        return room;
    }

    private static HabboItem item(Room room, int databaseId, int visibleId) {
        HabboItem item = mock(HabboItem.class);
        when(item.getId()).thenReturn(databaseId);
        when(item.getRoomId()).thenReturn(ROOM_ID);
        when(item.getRoomVisibleId()).thenReturn(visibleId);
        when(room.getHabboItemByDatabaseId(databaseId)).thenReturn(item);
        return item;
    }

    private static HabboItem staleItem(int databaseId, int oldVisibleId) {
        HabboItem item = mock(HabboItem.class);
        when(item.getId()).thenReturn(databaseId);
        when(item.getRoomId()).thenReturn(0);
        when(item.getRoomVisibleId()).thenReturn(oldVisibleId);
        return item;
    }

    private static HabboItem missingFromRoomManager(int databaseId, int oldVisibleId) {
        HabboItem item = mock(HabboItem.class);
        when(item.getId()).thenReturn(databaseId);
        when(item.getRoomId()).thenReturn(ROOM_ID);
        when(item.getRoomVisibleId()).thenReturn(oldVisibleId);
        return item;
    }

    private static WiredMatchFurniSetting setting(int databaseId) {
        return new WiredMatchFurniSetting(databaseId, "0", 0, 1, 1, 0);
    }

    private static void skipFrameHeader(ByteBuf packet) {
        packet.readInt();
        packet.readUnsignedShort();
    }

    private static List<Integer> readIds(ByteBuf packet) {
        int count = packet.readInt();
        List<Integer> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add(packet.readInt());
        }
        return ids;
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

    private static final class DummyEffect extends InteractionWiredEffect {
        private final List<HabboItem> primary = new ArrayList<>();
        private final List<HabboItem> secondary = new ArrayList<>();

        private DummyEffect(Item item) {
            super(40, 1, item, "0", 0, 0);
        }

        @Override public WiredEffectType getType() { return WiredEffectType.TOGGLE_STATE; }
        @Override public void execute(WiredContext context) { }
        @Override public boolean saveData(WiredSettings settings, GameClient client) throws WiredSaveException {
            return false;
        }
        @Override public String getWiredData() { return ""; }
        @Override public void serializeWiredData(ServerMessage message, Room room) { }
        @Override public void loadWiredData(ResultSet set, Room room) throws SQLException { }
        @Override public void onPickUp() { }
        @Override protected int getMaxFurniSelection() { return 20; }
        @Override protected Collection<HabboItem> getSelectedItems() { return this.primary; }
        @Override protected Collection<HabboItem> getSelectedItems2() { return this.secondary; }
    }
}

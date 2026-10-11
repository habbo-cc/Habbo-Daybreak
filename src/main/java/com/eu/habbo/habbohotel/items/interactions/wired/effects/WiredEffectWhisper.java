package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.*;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.messages.outgoing.rooms.users.WhisperMessageComposer;
import gnu.trove.procedure.TObjectProcedure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class WiredEffectWhisper extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.SHOW_MESSAGE;

    protected String message = "";
    private int visibility = 0;
    private int notificationStyle = RoomChatMessageBubbles.WIRED.getType();

    public WiredEffectWhisper(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectWhisper(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    // Wired 2.0 getters
    @Override
    protected String getWiredStringParam() { return this.message; }

    @Override
    protected int[] getWiredIntParams() { return new int[]{ this.visibility, this.notificationStyle }; }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getRoomVisibleId());
        message.appendString(this.message);
        message.appendInt(2);
        message.appendInt(this.visibility);
        message.appendInt(this.notificationStyle);
        message.appendInt(0);
        message.appendInt(type.code);
        message.appendInt(this.getDelay());

        if (this.requiresTriggeringUser()) {
            List<Integer> invalidTriggers = new ArrayList<>();
            room.getRoomSpecialTypes().getTriggers(this.getX(), this.getY()).forEach(new TObjectProcedure<InteractionWiredTrigger>() {
                @Override
                public boolean execute(InteractionWiredTrigger object) {
                    if (!object.isTriggeredByRoomUnit()) {
                        invalidTriggers.add(object.getBaseItem().getSpriteId());
                    }
                    return true;
                }
            });
            message.appendInt(invalidTriggers.size());
            for (Integer i : invalidTriggers) {
                message.appendInt(i);
            }
        } else {
            message.appendInt(0);
        }
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        String message = settings.getStringParam();

        if(gameClient.getHabbo() == null || !gameClient.getHabbo().hasPermission(Permission.ACC_SUPERWIRED)) {
            message = Emulator.getGameEnvironment().getWordFilter().filter(message, null);
            message = message.substring(0, Math.min(message.length(), Emulator.getConfig().getInt("hotel.wired.message.max_length", 100)));
        }

        int delay = settings.getDelay();

        if(delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20))
            throw new WiredSaveException("Delay too long");

        int visibility = settings.getIntParams().length > 0 ? settings.getIntParams()[0] : 0;
        if (visibility < 0 || visibility > 1)
            throw new WiredSaveException("Visibility is invalid");

        int notificationStyle = settings.getIntParams().length > 1 ? settings.getIntParams()[1] : RoomChatMessageBubbles.WIRED.getType();
        if (notificationStyle < 0)
            throw new WiredSaveException("Notification style is invalid");

        this.message = message;
        this.visibility = visibility;
        this.notificationStyle = notificationStyle;
        this.setDelay(delay);
        return true;
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (this.message.length() > 0) {
            for (RoomUnit roomUnit : resolveUserSource(ctx, this.getWiredUserSourceTypes(), 0)) {
                Habbo habbo = room.getHabbo(roomUnit);

                if (habbo != null) {
                    String msg = this.message.replace("%user%", habbo.getHabboInfo().getUsername()).replace("%online_count%", Emulator.getGameEnvironment().getHabboManager().getOnlineCount() + "").replace("%room_count%", Emulator.getGameEnvironment().getRoomManager().getActiveRooms().size() + "");
                    if (this.visibility == 0) {
                        habbo.getClient().sendResponse(new WhisperMessageComposer(new RoomChatMessage(msg, habbo, habbo, RoomChatMessageBubbles.getBubble(this.notificationStyle))));
                    } else {
                        for (Habbo h : room.getHabbos()) {
                            h.getClient().sendResponse(new WhisperMessageComposer(new RoomChatMessage(msg, h, h, RoomChatMessageBubbles.getBubble(this.notificationStyle))));
                        }
                    }

                    if (habbo.getRoomUnit().isIdle()) {
                        habbo.getRoomUnit().getRoom().unIdle(habbo);
                    }
                }
            }
        }
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.message, this.visibility, this.notificationStyle, this.getDelay()));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if(wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.setDelay(data.delay);
            this.message = data.message;
            this.visibility = data.visibility;
            this.notificationStyle = data.notificationStyle == 0 ? RoomChatMessageBubbles.WIRED.getType() : data.notificationStyle;
        }
        else {
            this.message = "";

            if (wiredData.split("\t").length >= 2) {
                super.setDelay(Integer.parseInt(wiredData.split("\t")[0]));
                this.message = wiredData.split("\t")[1];
            }

            this.needsUpdate(true);
        }
    }

    @Override
    public void onPickUp() {
        this.message = "";
        this.visibility = 0;
        this.notificationStyle = RoomChatMessageBubbles.WIRED.getType();
        this.setDelay(0);
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public boolean requiresTriggeringUser() {
        return true;
    }

    @Override
    protected boolean supportsUserPicking() {
        return true;
    }

    @Override
    protected boolean isWiredAdvancedMode() {
        return true;
    }

    @Override
    protected long requiredCooldown() {
        return 500; // Habbo throttles Show Message per triggering room unit, even under a 50ms trigger
    }

    static class JsonData {
        String message;
        int visibility;
        int notificationStyle;
        int delay;

        public JsonData(String message, int visibility, int notificationStyle, int delay) {
            this.message = message;
            this.visibility = visibility;
            this.notificationStyle = notificationStyle;
            this.delay = delay;
        }
    }
}

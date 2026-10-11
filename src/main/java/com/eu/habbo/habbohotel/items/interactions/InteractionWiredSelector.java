package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredCategoryType;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredSelectorType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredTargets;
import com.eu.habbo.habbohotel.wired.core.WiredAuthorizationService;
import com.eu.habbo.habbohotel.wired.core.WiredFeatureCapabilityGuard;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.wired.OpenMessageComposer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;

public abstract class InteractionWiredSelector extends InteractionWired {
    private boolean filter;
    private boolean invert;

    public InteractionWiredSelector(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionWiredSelector(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    public abstract WiredSelectorType getType();

    public abstract boolean saveData(WiredSettingsV2 settings);

    public abstract WiredTargets resolve(Room room, WiredContext ctx);

    public boolean isFilter() {
        return filter;
    }

    public boolean isInvert() {
        return invert;
    }

    protected void setSelectorFlags(boolean filter, boolean invert) {
        this.filter = filter;
        this.invert = invert;
    }

    @Override
    protected WiredCategoryType getWiredCategory() {
        return WiredCategoryType.SELECTOR;
    }

    @Override
    protected int getWiredTypeCode() {
        return getType().code;
    }

    @Override
    protected boolean isWiredFilter() {
        return filter;
    }

    @Override
    protected boolean isWiredInvert() {
        return invert;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.serializeWiredDataV2(message, room);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (WiredFeatureCapabilityGuard.isEditorReady(client, room, this)
                && WiredAuthorizationService.isAuthorized(
                WiredAuthorizationService.Operation.VIEW_EDITOR,
                client,
                room,
                this,
                WiredCategoryType.SELECTOR)) {
            client.sendResponse(new OpenMessageComposer(this));
        }
    }

    protected boolean saveBase(WiredSettingsV2 settings) {
        this.filter = settings.isFilter();
        this.invert = settings.isInvert();
        return true;
    }

    protected int[] emptyInts() {
        return new int[0];
    }

    protected String[] emptyStrings() {
        return new String[0];
    }

    protected Collection<HabboItem> noItems() {
        return java.util.Collections.emptyList();
    }
}

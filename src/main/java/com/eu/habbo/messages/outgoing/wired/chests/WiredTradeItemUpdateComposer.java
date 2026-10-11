package com.eu.habbo.messages.outgoing.wired.chests;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.chests.ChestManager;
import com.eu.habbo.habbohotel.items.chests.ChestTradeSession;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.List;

/** Exact July S2C 2488 payload on local header 7128. */
public final class WiredTradeItemUpdateComposer extends MessageComposer {
    private final ChestTradeSession session;
    private final boolean canAccept;
    private final int extra;

    public WiredTradeItemUpdateComposer(ChestTradeSession session,
            boolean canAccept, int extra) {
        this.session = session;
        this.canAccept = canAccept;
        this.extra = extra;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredTradeItemUpdateComposer);
        List<HabboItem> items = this.session.items();
        this.response.appendInt(this.session.userId());
        appendItems(items);
        this.response.appendInt(items.size());
        this.response.appendInt(items.stream().mapToInt(ChestManager::creditValue).sum());
        this.response.appendInt(-1);
        this.response.appendInt(0);
        this.response.appendInt(0);
        this.response.appendInt(0);
        this.response.appendBoolean(this.canAccept);
        this.response.appendInt(this.extra);
        return this.response;
    }

    private void appendItems(List<HabboItem> items) {
        this.response.appendInt(items.size());
        for (HabboItem item : items) {
            this.response.appendInt(item.getId());
            this.response.appendString(item.getBaseItem().getType().code);
            this.response.appendInt(item.getId());
            this.response.appendInt(item.getBaseItem().getSpriteId());
            this.response.appendInt(0);
            this.response.appendBoolean(item.getBaseItem().allowInventoryStack()
                    && !item.isLimited());
            item.serializeExtradata(this.response);
            this.response.appendInt(0);
            this.response.appendInt(0);
            this.response.appendInt(0);
            if (item.getBaseItem().getType() == FurnitureType.FLOOR) {
                this.response.appendInt(0);
            }
        }
    }
}

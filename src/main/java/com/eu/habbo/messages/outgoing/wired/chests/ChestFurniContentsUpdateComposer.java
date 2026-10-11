package com.eu.habbo.messages.outgoing.wired.chests;

import com.eu.habbo.habbohotel.items.chests.ChestRepository;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.List;

/** July S2C 2738 shape on local header 7122. */
public final class ChestFurniContentsUpdateComposer extends MessageComposer {
    private final int chestVisibleId;
    private final int[] removedIds;
    private final List<ChestRepository.StoredHabboItem> added;

    public ChestFurniContentsUpdateComposer(int chestVisibleId, int[] removedIds,
            List<ChestRepository.StoredHabboItem> added) {
        this.chestVisibleId = chestVisibleId;
        this.removedIds = removedIds == null ? new int[0] : removedIds.clone();
        this.added = added == null ? List.of() : List.copyOf(added);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ChestFurniContentsUpdateComposer);
        this.response.appendInt(this.chestVisibleId);
        this.response.appendInt(this.removedIds.length);
        for (int removedId : this.removedIds) {
            this.response.appendInt(removedId);
        }
        this.response.appendInt(this.added.size());
        for (ChestRepository.StoredHabboItem stored : this.added) {
            ChestItemSerializer.serialize(this.response, stored.item(), stored.lockState(),
                    stored.transactionId());
        }
        return this.response;
    }
}

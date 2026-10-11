package com.eu.habbo.messages.outgoing.wired.chests;

import com.eu.habbo.habbohotel.items.chests.ChestRepository;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.List;

/** July S2C 2323 shape on local header 7121. */
public final class ChestFurniContentsComposer extends MessageComposer {
    private final int chestVisibleId;
    private final int totalFragments;
    private final int fragmentNumber;
    private final List<ChestRepository.StoredHabboItem> items;

    public ChestFurniContentsComposer(int chestVisibleId, int totalFragments,
            int fragmentNumber, List<ChestRepository.StoredHabboItem> items) {
        this.chestVisibleId = chestVisibleId;
        this.totalFragments = totalFragments;
        this.fragmentNumber = fragmentNumber;
        this.items = List.copyOf(items);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ChestFurniContentsComposer);
        this.response.appendInt(this.chestVisibleId);
        this.response.appendInt(this.totalFragments);
        this.response.appendInt(this.fragmentNumber);
        this.response.appendInt(this.items.size());
        for (ChestRepository.StoredHabboItem stored : this.items) {
            ChestItemSerializer.serialize(this.response, stored.item(), stored.lockState(),
                    stored.transactionId());
        }
        return this.response;
    }
}

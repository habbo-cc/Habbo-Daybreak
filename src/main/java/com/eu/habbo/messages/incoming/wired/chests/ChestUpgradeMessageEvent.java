package com.eu.habbo.messages.incoming.wired.chests;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.chests.ChestCapacityPolicy;
import com.eu.habbo.habbohotel.items.chests.ChestRepository;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.wired.chests.ChestUpgradeResultComposer;

/** July C2S 3407 shape on local header 7024. */
public final class ChestUpgradeMessageEvent extends AbstractChestMessageEvent {
    @Override
    public void handle() {
        int visibleId = this.packet.readRequiredInt();
        int levels = this.packet.readRequiredInt();
        requireEnd();
        Room room = currentRoom();
        HabboItem chest = chest(room, visibleId, true);
        if (chest == null) {
            return;
        }
        int resultCode;
        if (ChestCapacityPolicy.isStarter(chest)) {
            resultCode = 10;
        } else {
            int credits = Emulator.getConfig().getInt("wired.chests.upgrade_cost_credits", 10);
            int diamonds = Emulator.getConfig().getInt("wired.chests.upgrade_cost_diamonds", 10);
            ChestRepository.Result result = Emulator.getGameEnvironment().getChestManager()
                    .upgrade(this.client, room, chest, levels, credits, diamonds);
            resultCode = switch (result) {
                case OK -> 0;
                case NOT_OWNER -> 7;
                case LOCKED -> 3;
                case INSUFFICIENT_CREDITS -> 5;
                case INSUFFICIENT_DIAMONDS -> 6;
                case INVALID_AMOUNT -> 2;
                default -> 4;
            };
        }
        this.client.sendResponse(new ChestUpgradeResultComposer(visibleId, resultCode));
    }
}

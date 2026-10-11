package com.eu.habbo.threading.runnables;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionCrackable;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.inventory.UnseenItemsMessageComposer;
import com.eu.habbo.messages.outgoing.inventory.FurniListInvalidateMessageComposer;
import com.eu.habbo.messages.outgoing.rooms.items.ObjectAddMessageComposer;
import com.eu.habbo.messages.outgoing.rooms.items.ObjectRemoveMessageComposer;

import java.util.List;

public class CrackableExplode implements Runnable {
    private final Room room;
    private final InteractionCrackable habboItem;
    private final Habbo habbo;
    private final boolean toInventory;
    private final short x;
    private final short y;

    public CrackableExplode(Room room, InteractionCrackable item, Habbo habbo, boolean toInventory, short x, short y) {
        this.room = room;
        this.habboItem = item;
        this.habbo = habbo;
        this.toInventory = toInventory;

        this.x = x;
        this.y = y;
    }

    @Override
    public void run() {
        if (this.habboItem.getRoomId() == 0) {
            return;
        }

        if (!this.habboItem.resetable()) {
            this.room.removeHabboItem(this.habboItem);
            this.room.sendComposer(new ObjectRemoveMessageComposer(this.habboItem, true).compose());
            this.habboItem.setRoomId(0);
            Emulator.getGameEnvironment().getItemManager().deleteItem(this.habboItem);
        } else {
            this.habboItem.reset(this.room);
        }

        // One outcome per tier; bundles contribute multiple items. Give each reward in turn.
        List<Integer> rewardIds = Emulator.getGameEnvironment().getItemManager().getCrackableRewards(this.habboItem.getBaseItem().getId());

        for (int rewardId : rewardIds) {
            Item rewardItem = Emulator.getGameEnvironment().getItemManager().getItem(rewardId);
            if (rewardItem == null) {
                continue;
            }

            HabboItem newItem = Emulator.getGameEnvironment().getItemManager().createItem(this.habboItem.allowAnyone() ? this.habbo.getHabboInfo().getId() : this.habboItem.getUserId(), rewardItem, 0, 0, "");

            if (newItem == null) {
                continue;
            }

            //Add to inventory in case if isn't possible place the item or in case is wall item
            if (this.toInventory || newItem.getBaseItem().getType() == FurnitureType.WALL) {
                this.habbo.getInventory().getItemsComponent().addItem(newItem);
                this.habbo.getClient().sendResponse(new UnseenItemsMessageComposer(newItem));
                this.habbo.getClient().sendResponse(new FurniListInvalidateMessageComposer());
            } else {
                newItem.setX(this.x);
                newItem.setY(this.y);
                // Exclude the just-cracked furni: removeHabboItem() above doesn't clear the tile
                // cache, so a non-stackable crackable would still be the "top item" here and make
                // getStackHeight return -1, sinking the reward below the floor. Clamp to floor too.
                double rewardZ = this.room.getStackHeight(this.x, this.y, false, this.habboItem);
                if (rewardZ < 0) {
                    rewardZ = this.room.getLayout().getHeightAtSquare(this.x, this.y);
                }
                newItem.setZ(rewardZ);
                newItem.setRoomId(this.room.getId());
                newItem.needsUpdate(true);
                this.room.addHabboItem(newItem);
                this.room.updateItem(newItem);
                this.room.sendComposer(new ObjectAddMessageComposer(newItem, this.room.getFurniOwnerNames().get(newItem.getUserId())).compose());
            }
        }

        this.room.updateTile(this.room.getLayout().getTile(this.x, this.y));
    }
}

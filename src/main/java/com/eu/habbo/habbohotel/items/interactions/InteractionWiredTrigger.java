package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredCategoryType;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.api.IWiredTrigger;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredAuthorizationService;
import com.eu.habbo.habbohotel.wired.core.WiredFeatureCapabilityGuard;
import com.eu.habbo.messages.outgoing.wired.WiredTriggerDataMessageComposer;
import com.eu.habbo.messages.outgoing.wired.OpenMessageComposer;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Base class for all wired triggers in the game.
 * <p>
 * Triggers are the entry points for wired execution. They now implement
 * {@link IWiredTrigger} for the new context-driven architecture.
 * </p>
 */
public abstract class InteractionWiredTrigger extends InteractionWired implements IWiredTrigger {
    private int delay;

    protected InteractionWiredTrigger(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    protected InteractionWiredTrigger(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return true;
    }

    @Override
    public boolean isWalkable() {
        return true;
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (WiredFeatureCapabilityGuard.isEditorReady(client, room, this)
                && WiredAuthorizationService.isAuthorized(
                WiredAuthorizationService.Operation.VIEW_EDITOR,
                client,
                room,
                this,
                WiredCategoryType.TRIGGER)) {
            client.sendResponse(new OpenMessageComposer(this));
            this.activateBox(room);
        }
    }

    public abstract WiredTriggerType getType();

    @Override
    protected WiredCategoryType getWiredCategory() { return WiredCategoryType.TRIGGER; }

    @Override
    protected int getWiredTypeCode() { return this.getType().code; }

    @Override
    protected boolean isWiredAdvancedMode() {
        return getFurniSourceSlotCount() > 0 || getUserSourceSlotCount() > 0;
    }

    public abstract boolean saveData(WiredSettings settings);

    /**
     * Optional July localization key describing the most recent save failure.
     * Returning {@code null} keeps the legacy generic validation message.
     */
    public String getSaveErrorLocalizationKey() {
        return null;
    }

    protected int getDelay() {
        return this.delay;
    }

    protected void setDelay(int value) {
        this.delay = value;
    }

    public boolean isTriggeredByRoomUnit() {
        return false;
    }
    
    // ========== IWiredTrigger Implementation ==========
    
    /**
     * Returns the event type this trigger responds to.
     * Maps the WiredTriggerType to the new WiredEvent.Type.
     * 
     * @return the event type this trigger responds to
     */
    @Override
    public WiredEvent.Type listensTo() {
        return WiredEvent.Type.fromLegacyType(this.getType());
    }
    
    /**
     * Checks if this trigger matches the given event.
     * Subclasses must implement this to define their matching logic.
     * 
     * @param triggerItem the wired trigger furniture item
     * @param event the event that occurred
     * @return true if this trigger should activate
     */
    @Override
    public abstract boolean matches(HabboItem triggerItem, WiredEvent event);
    
    /**
     * Returns whether this trigger requires an actor (user) to activate.
     */
    @Override
    public boolean requiresActor() {
        return isTriggeredByRoomUnit();
    }

}

package com.eu.habbo.messages.outgoing.wired.chests;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.interactions.InteractionExternalImage;
import com.eu.habbo.habbohotel.items.interactions.InteractionGift;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;

/** Exact July ChestStorage field order. */
final class ChestItemSerializer {
    private ChestItemSerializer() {
    }

    static void serialize(ServerMessage message, HabboItem item, int lockState,
            long transactionId) {
        boolean wall = item.getBaseItem().getType() == FurnitureType.WALL;
        int specialType = specialType(item);
        message.appendInt(item.getId());
        message.appendInt(Math.max(0, Math.min(3, lockState)));
        message.appendLong(Math.max(0, transactionId));
        message.appendBoolean(wall);
        message.appendInt(item.getBaseItem().getSpriteId());
        message.appendString(specialType == 6 ? item.getExtradata() : "");
        message.appendBoolean(item.getBaseItem().allowInventoryStack() && !item.isLimited());
        message.appendInt(specialType);
        item.serializeExtradata(message);
        if (!wall) {
            message.appendInt(extra(item));
        }
    }

    private static int specialType(HabboItem item) {
        String name = item.getBaseItem().getName();
        return switch (name) {
            case "wallpaper" -> 2;
            case "floor" -> 3;
            case "landscape" -> 4;
            case "poster" -> 6;
            case "song_disk" -> 8;
            case "gnome_box" -> 13;
            default -> item instanceof InteractionExternalImage ? 19 : 1;
        };
    }

    private static int extra(HabboItem item) {
        if (item instanceof InteractionGift gift) {
            return gift.getColorId() * 1000 + gift.getRibbonId();
        }
        if ("song_disk".equals(item.getBaseItem().getName())) {
            String[] fields = item.getExtradata().split("\\n");
            try {
                return Integer.parseInt(fields[fields.length - 1]);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 1;
    }
}

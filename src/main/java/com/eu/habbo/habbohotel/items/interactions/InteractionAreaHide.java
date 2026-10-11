package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.rooms.items.AreaHideMessageComposer;

import java.awt.Rectangle;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** July 2026 AIR Area Hide furniture (`conf_area_hide`). */
public class InteractionAreaHide extends HabboItem {
    private static final Pattern INTEGER_PATTERN = Pattern.compile("-?\\d+");

    private boolean enabled;
    private int rootX;
    private int rootY;
    private int width;
    private int length;
    private boolean invisibleFurni;
    private boolean hideWallItems;
    private boolean inverted;

    public InteractionAreaHide(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        loadState();
    }

    public InteractionAreaHide(int id, int userId, Item item, String extradata,
                               int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        loadState();
    }

    @Override
    public synchronized void serializeExtradata(ServerMessage message) {
        message.appendInt(5 + (this.isLimited() ? 256 : 0));
        message.appendInt(8);
        message.appendInt(this.enabled ? 1 : 0);
        message.appendInt(this.rootX);
        message.appendInt(this.rootY);
        message.appendInt(this.width);
        message.appendInt(this.length);
        message.appendInt(this.invisibleFurni ? 1 : 0);
        message.appendInt(this.hideWallItems ? 1 : 0);
        message.appendInt(this.inverted ? 1 : 0);
        super.serializeExtradata(message);
    }

    public synchronized boolean save(Room room, int rootX, int rootY, int width, int length,
                                     boolean invisibleFurni, boolean hideWallItems, boolean inverted) {
        if (!validArea(room, rootX, rootY, width, length)) {
            return false;
        }
        this.rootX = rootX;
        this.rootY = rootY;
        this.width = width;
        this.length = length;
        this.invisibleFurni = invisibleFurni;
        this.hideWallItems = hideWallItems;
        this.inverted = inverted;
        persist(room, true);
        return true;
    }

    public synchronized void toggle(Room room) {
        this.enabled = !this.enabled;
        persist(room, true);
    }

    private void persist(Room room, boolean broadcastAreaState) {
        this.setExtradata(encodeState());
        this.needsUpdate(true);
        if (room != null) {
            room.updateItem(this);
            if (broadcastAreaState) {
                room.sendComposer(new AreaHideMessageComposer(this).compose());
            }
            room.refreshAreaHideVisibility();
        }
        Emulator.getThreading().run(this);
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) {
        if (AreaHideAccess.canModify(client, room, this)) {
            toggle(room);
        }
    }

    @Override
    public void onPlace(Room room) {
        super.onPlace(room);
        if (room != null) {
            room.refreshAreaHideVisibility();
        }
    }

    @Override
    public void onPickUp(Room room) {
        this.enabled = false;
        this.setExtradata(encodeState());
        if (room != null) {
            room.sendComposer(new AreaHideMessageComposer(this).compose());
            room.refreshAreaHideVisibility();
        }
        super.onPickUp(room);
    }

    public synchronized boolean hidesFloorItem(Room room, HabboItem item) {
        if (!this.enabled || item == null || item == this || item instanceof InteractionAreaHide
                || item.getBaseItem() == null
                || item.getBaseItem().getType() != FurnitureType.FLOOR) {
            return false;
        }
        Rectangle area = selectedArea();
        Rectangle itemArea = item.getRectangle();
        return this.inverted ? !area.contains(itemArea) : area.intersects(itemArea);
    }

    public synchronized boolean hidesWallItem(Room room, HabboItem item) {
        if (!this.enabled || !this.hideWallItems || room == null || item == null
                || item.getBaseItem() == null
                || item.getBaseItem().getType() != FurnitureType.WALL) {
            return false;
        }
        int[] anchor = parseWallAnchor(item.getWallPosition());
        if (anchor == null) {
            return false;
        }
        String direction = parseWallDirection(item.getWallPosition());
        int[][] candidates = "l".equals(direction)
                ? new int[][]{{anchor[0], anchor[1]}, {anchor[0] + 1, anchor[1]}}
                : "r".equals(direction)
                ? new int[][]{{anchor[0], anchor[1]}, {anchor[0], anchor[1] + 1}}
                : new int[][]{{anchor[0], anchor[1]}, {anchor[0] + 1, anchor[1]}, {anchor[0], anchor[1] + 1}};
        RoomLayout layout = room.getLayout();
        for (int[] candidate : candidates) {
            int x = candidate[0];
            int y = candidate[1];
            if (x >= 0 && y >= 0 && x < layout.getMapSizeX() && y < layout.getMapSizeY()
                    && !layout.isVoidTile((short) x, (short) y)
                    && hidesTile(x, y)) {
                return true;
            }
        }
        return false;
    }

    public synchronized boolean shouldHideSelf(Room room) {
        return this.enabled && this.invisibleFurni && InteractionInvisControl.isInvisibleFurniHidden(room);
    }

    public synchronized boolean hidesRoomTile(int x, int y) {
        return this.enabled && hidesTile(x, y);
    }

    private boolean hidesTile(int x, int y) {
        boolean contains = selectedArea().contains(x, y);
        return this.inverted ? !contains : contains;
    }

    private Rectangle selectedArea() {
        return new Rectangle(this.rootX, this.rootY, this.width, this.length);
    }

    private void loadState() {
        String[] fields = String.valueOf(this.getExtradata()).split(":", -1);
        if (fields.length == 8) {
            try {
                this.enabled = parseBoolean(fields[0]);
                this.rootX = Math.max(0, Integer.parseInt(fields[1]));
                this.rootY = Math.max(0, Integer.parseInt(fields[2]));
                this.width = Math.max(1, Integer.parseInt(fields[3]));
                this.length = Math.max(1, Integer.parseInt(fields[4]));
                this.invisibleFurni = parseBoolean(fields[5]);
                this.hideWallItems = parseBoolean(fields[6]);
                this.inverted = parseBoolean(fields[7]);
                return;
            } catch (NumberFormatException ignored) {
            }
        }
        this.enabled = false;
        this.rootX = Math.max(0, this.getX());
        this.rootY = Math.max(0, this.getY());
        this.width = 1;
        this.length = 1;
        this.invisibleFurni = false;
        this.hideWallItems = true;
        this.inverted = false;
        this.setExtradata(encodeState());
    }

    private String encodeState() {
        return (this.enabled ? 1 : 0) + ":" + this.rootX + ":" + this.rootY + ":"
                + this.width + ":" + this.length + ":" + (this.invisibleFurni ? 1 : 0)
                + ":" + (this.hideWallItems ? 1 : 0) + ":" + (this.inverted ? 1 : 0);
    }

    private static boolean parseBoolean(String value) {
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    private static boolean validArea(Room room, int rootX, int rootY, int width, int length) {
        if (room == null || room.getLayout() == null || rootX < 0 || rootY < 0 || width < 1 || length < 1) {
            return false;
        }
        long endX = (long) rootX + width;
        long endY = (long) rootY + length;
        return endX <= room.getLayout().getMapSizeX() && endY <= room.getLayout().getMapSizeY();
    }

    private static int[] parseWallAnchor(String position) {
        if (position == null || position.isBlank()) {
            return null;
        }
        String[] parts = position.trim().split("\\s+");
        for (String part : parts) {
            if (part.startsWith(":w=")) {
                return firstTwoIntegers(part.substring(3));
            }
        }
        return firstTwoIntegers(position);
    }

    private static int[] firstTwoIntegers(String value) {
        Matcher matcher = INTEGER_PATTERN.matcher(value);
        int[] result = new int[2];
        int index = 0;
        while (matcher.find() && index < result.length) {
            result[index++] = Integer.parseInt(matcher.group());
        }
        return index == result.length ? result : null;
    }

    private static String parseWallDirection(String position) {
        if (position == null || position.isBlank()) {
            return "";
        }
        String[] parts = position.trim().split("\\s+");
        return parts.length >= 3 ? parts[2].toLowerCase() : "";
    }

    public synchronized boolean isEnabled() { return this.enabled; }
    public synchronized int getRootX() { return this.rootX; }
    public synchronized int getRootY() { return this.rootY; }
    public synchronized int getAreaWidth() { return this.width; }
    public synchronized int getAreaLength() { return this.length; }
    public synchronized boolean isInverted() { return this.inverted; }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return false;
    }

    @Override
    public boolean isWalkable() {
        return false;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) {
    }
}

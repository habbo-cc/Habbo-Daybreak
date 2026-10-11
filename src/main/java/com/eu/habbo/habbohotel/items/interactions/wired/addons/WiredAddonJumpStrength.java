package com.eu.habbo.habbohotel.items.interactions.wired.addons;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredAddonType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableHolder;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableManager;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableValue;
import com.eu.habbo.habbohotel.wired.variables.WiredInternalVariableRuntime;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** July AIR add-on 22: vertical movement curve (the editor calls it Jump Strength). */
public final class WiredAddonJumpStrength extends WiredMovementAddon {
    private static final int VALUE = 0, VARIABLE = 1;
    private static final int FURNI = 0, USER = 1, GLOBAL = -10, CONTEXT = -20;
    private int mode = VALUE, value = 80, target = FURNI;
    private String variableId = "";

    public WiredAddonJumpStrength(ResultSet set, Item item) throws SQLException { super(set, item); }
    public WiredAddonJumpStrength(int id, int userId, Item item, String extra, int stack, int sells) { super(id, userId, item, extra, stack, sells); }
    @Override public WiredAddonType getType() { return WiredAddonType.JUMP_STRENGTH; }

    @Override public boolean saveData(WiredSettingsV2 settings) {
        if (settings == null || settings.getIntParams().length != 3 || settings.getVariableIds().length != 1
                || !settings.getStringParam().isEmpty()
                || settings.getFurniIds().length != 0 || settings.getFurniIds2().length != 0
                || settings.getFurniSourceTypes().length != 1
                || settings.getUserSourceTypes().length != 1) return false;
        int[] p = settings.getIntParams();
        if ((p[0] != VALUE && p[0] != VARIABLE) || p[1] < -1000 || p[1] > 1000
                || !validTarget(p[2])
                || (p[0] == VARIABLE && settings.getVariableIds()[0].isBlank())) return false;
        mode = p[0]; value = mode == VALUE ? p[1] : 80; target = p[2]; variableId = mode == VARIABLE ? settings.getVariableIds()[0] : "";
        return true;
    }

    public int resolve(WiredContext context) {
        if (mode == VALUE || context == null) return value;
        if (target == CONTEXT) {
            Integer current = context.contextVariables().get(variableId);
            return current == null ? 0 : clamp(current);
        }
        WiredVariableManager manager = context.room().getRoomSpecialTypes().getWiredVariableManager();
        WiredVariableHolder holder = resolveHolder(context);
        if (holder == null) {
            return 0;
        }
        WiredVariableValue current = manager == null ? null : manager.get(variableId, holder);
        Integer value = current == null
                ? WiredInternalVariableRuntime.read(
                        context.room(), variableId, holder)
                : current.value();
        return value == null ? 0 : clamp(value);
    }

    private WiredVariableHolder resolveHolder(WiredContext context) {
        if (this.target == GLOBAL) {
            return WiredVariableHolder.room();
        }
        if (this.target == FURNI) {
            List<HabboItem> items = resolveFurniSource(
                    context, this.wiredFurniSourceTypes, 0, List.of(), List.of()).stream()
                    .filter(item -> item != null && item.getRoomId() == context.room().getId())
                    .limit(2)
                    .toList();
            return items.size() == 1 ? WiredVariableHolder.furni(items.get(0).getId()) : null;
        }
        List<RoomUnit> users = resolveUserSource(
                context, this.wiredUserSourceTypes, 0).stream()
                .filter(unit -> unit != null && unit.isInRoom() && unit.getRoom() == context.room())
                .limit(2)
                .toList();
        if (users.size() != 1) {
            return null;
        }
        var habbo = context.room().getHabbo(users.get(0));
        return habbo != null && habbo.getHabboInfo() != null
                ? WiredVariableHolder.user(habbo.getHabboInfo().getId())
                : null;
    }

    private static int clamp(int value) { return Math.max(-1000, Math.min(1000, value)); }
    @Override public String getWiredData() { return WiredManager.getGson().toJson(new Data(1, mode, value, target, variableId)); }
    @Override public void loadWiredData(ResultSet set, Room room) throws SQLException { onPickUp(); try { Data d=WiredManager.getGson().fromJson(set==null?null:set.getString("wired_data"),Data.class); if(d!=null&&d.v==1&&(d.mode==0||d.mode==1)&&d.value>=-1000&&d.value<=1000&&validTarget(d.target)&&(d.mode==0||(d.variableId!=null&&!d.variableId.isBlank()))){mode=d.mode;value=d.value;target=d.target;variableId=d.mode==1?d.variableId:"";} } catch(RuntimeException ignored){onPickUp();} }
    @Override public void onPickUp(){mode=VALUE;value=80;target=FURNI;variableId="";}
    @Override protected int[] getWiredIntParams(){return new int[]{mode,value,target};}
    @Override protected String[] getWiredVariableIds(){return new String[]{variableId};}
    @Override protected int getFurniSourceSlotCount(){return 1;}
    @Override protected int getUserSourceSlotCount(){return 1;}
    @Override protected int[] getAllowedFurniSourcesForSlot(int slot){return new int[]{FURNI_SOURCE_TRIGGERING_ITEM,FURNI_SOURCE_SELECTOR,FURNI_SOURCE_SIGNAL};}
    @Override protected int[] getAllowedUserSourcesForSlot(int slot){return new int[]{USER_SOURCE_TRIGGERING_USER,USER_SOURCE_SELECTOR,USER_SOURCE_SIGNAL};}
    @Override protected boolean isWiredAdvancedMode(){return true;}
    private static boolean validTarget(int target) {
        return target == FURNI || target == USER || target == GLOBAL || target == CONTEXT;
    }
    static final class Data { int v,mode,value,target; String variableId=""; Data(){} Data(int v,int m,int n,int t,String id){this.v=v;mode=m;value=n;target=t;variableId=id;} }
}

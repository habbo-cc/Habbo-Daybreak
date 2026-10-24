package com.eu.habbo.habbohotel.items.interactions.wired.addons;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredAddon;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.variablefx.VariableFxSettings;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Native editor/persistence common to the six VariableFX add-ons. */
public abstract class WiredAddonVariableFx extends InteractionWiredAddon {
    private VariableFxSettings configuration;
    protected WiredAddonVariableFx(ResultSet set,Item item) throws SQLException { super(set,item); }
    protected WiredAddonVariableFx(int id,int userId,Item item,String extra,int stack,int sells) { super(id,userId,item,extra,stack,sells); }
    public final synchronized VariableFxSettings configuration() {
        if(configuration==null) configuration=VariableFxSettings.defaults(getType().code-1200);
        return configuration;
    }
    @Override public final synchronized boolean saveData(WiredSettingsV2 settings) {
        try { configuration=VariableFxSettings.parse(getType().code-1200,settings); return true; }
        catch(IllegalArgumentException exception) { return false; }
    }
    @Override public final synchronized String getWiredData() {
        var c=configuration();
        return WiredManager.getGson().toJson(new Persisted(1,c.category(),c.intParams(),c.icon(),c.variables()));
    }
    @Override public final synchronized void loadWiredData(ResultSet set,Room room) throws SQLException {
        onPickUp();
        if(set!=null) loadPersistedData(set.getString("wired_data"));
    }
    final synchronized boolean loadPersistedData(String json) {
        try {
            Persisted data=WiredManager.getGson().fromJson(json,Persisted.class);
            if(data==null || data.v!=1 || data.category!=getType().code-1200
                    || data.ints==null || data.icon==null || data.variables==null) return false;
            return saveData(new WiredSettingsV2(data.ints,data.icon,null,null,data.variables,null,null,0,0,false,false));
        } catch(RuntimeException exception) { return false; }
    }
    @Override public final synchronized void onPickUp() { configuration=null; }
    @Override protected final int[] getWiredIntParams() { return configuration().intParams(); }
    @Override protected final String getWiredStringParam() { return configuration().icon(); }
    @Override protected final String[] getWiredVariableIds() { return configuration().variables(); }
    @Override protected final int getMaxFurniSelection() { return 0; }
    private record Persisted(int v,int category,int[] ints,String icon,String[] variables) {}
}

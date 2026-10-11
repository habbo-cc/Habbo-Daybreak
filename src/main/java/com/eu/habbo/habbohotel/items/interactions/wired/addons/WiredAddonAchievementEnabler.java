package com.eu.habbo.habbohotel.items.interactions.wired.addons;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredAddon;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.WiredAddonType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** July AIR add-on 2001: stack-local whitelist for Progress Achievement. */
public final class WiredAddonAchievementEnabler extends InteractionWiredAddon {
    private static final int VERSION = 1;
    private static final int MAX_LINES = 60;
    private static final int MAX_CHARACTERS = 2000;
    private String text = "";
    private Set<String> identifiers = Collections.emptySet();

    public WiredAddonAchievementEnabler(ResultSet set, Item item) throws SQLException { super(set, item); }
    public WiredAddonAchievementEnabler(int id,int userId,Item item,String extraData,int limitedStack,int limitedSells){super(id,userId,item,extraData,limitedStack,limitedSells);}
    @Override public WiredAddonType getType(){return WiredAddonType.ACHIEVEMENT_ENABLER;}
    @Override public boolean saveData(WiredSettingsV2 settings){
        if(settings==null||settings.getIntParams().length!=0||settings.getFurniIds().length!=0
                ||settings.getFurniIds2().length!=0||settings.getVariableIds().length!=0
                ||settings.getFurniSourceTypes().length!=0||settings.getUserSourceTypes().length!=0
                ||settings.getDelay()!=0)return false;
        Parsed parsed=parse(settings.getStringParam(),true);if(parsed==null)return false;apply(parsed);return true;
    }
    public boolean enables(String identifier){return identifier!=null&&this.identifiers.contains(normalize(identifier));}
    public Set<String> identifiers(){return this.identifiers;}
    @Override public String getWiredData(){return WiredManager.getGson().toJson(new Data(VERSION,this.text));}
    @Override public void loadWiredData(ResultSet set,Room room)throws SQLException{onPickUp();try{Data data=WiredManager.getGson().fromJson(set==null?null:set.getString("wired_data"),Data.class);Parsed parsed=data!=null&&data.version==VERSION?parse(data.text,false):null;if(parsed!=null)apply(parsed);}catch(RuntimeException ignored){onPickUp();}}
    @Override public void onPickUp(){this.text="";this.identifiers=Collections.emptySet();}
    @Override protected String getWiredStringParam(){return this.text;}
    @Override protected int getMaxFurniSelection(){return 0;}
    private void apply(Parsed parsed){this.text=parsed.text;this.identifiers=parsed.identifiers;}
    private static Parsed parse(String input,boolean requireKnown){
        String normalized=input==null?"":input.replace("\r\n","\n").replace('\r','\n').trim();
        if(normalized.isEmpty()||normalized.length()>MAX_CHARACTERS)return null;
        String[] lines=normalized.split("\n",-1);if(lines.length>MAX_LINES)return null;
        LinkedHashSet<String> values=new LinkedHashSet<>();
        for(String line:lines){String id=normalize(line);if(id.isEmpty()||!id.matches("[A-Za-z0-9_]+")
                ||(requireKnown&&Emulator.getGameEnvironment().getAchievementManager().getAchievement("WF_"+id)==null))return null;values.add(id);}
        return new Parsed(normalized,Collections.unmodifiableSet(values));
    }
    private static String normalize(String value){String id=value==null?"":value.trim();return id.regionMatches(true,0,"ACH_WF_",0,7)?id.substring(7):id;}
    private record Parsed(String text,Set<String> identifiers){}
    private static final class Data{int version;String text;Data(int version,String text){this.version=version;this.text=text;}}
}

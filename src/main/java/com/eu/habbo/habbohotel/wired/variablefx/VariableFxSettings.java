package com.eu.habbo.habbohotel.wired.variablefx;

import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import java.util.*;

/** Immutable native V2 editor state. Validation never partly updates a furniture instance. */
public final class VariableFxSettings {
    private final int category;
    private final int[] ints;
    private final String icon;
    private final String[] variables;
    private VariableFxSettings(int category,int[] ints,String icon,String[] variables) {
        this.category=category; this.ints=ints.clone(); this.icon=icon; this.variables=variables.clone();
    }
    public static VariableFxSettings defaults(int category) {
        var style=VariableFxStyles.first(category);
        return parse(category,new WiredSettingsV2(new int[]{0,2,0,0,0,1500,style.id(),style.color(),style.width(),style.renderers().getFirst(),0,0,0,100,0,0,0,0,0,0,0,category==2 ? 2 : 0,0},"",null,null,new String[]{"","",""},null,null,0,0,false,false));
    }
    public static VariableFxSettings parse(int category,WiredSettingsV2 data) {
        if(data==null || data.getIntParams().length!=23 || data.getVariableIds().length!=3
                || data.getFurniIds().length!=0 || data.getFurniIds2().length!=0
                || data.getFurniSourceTypes().length!=0 || data.getUserSourceTypes().length!=0
                || data.getDelay()!=0 || data.getQuantifierCode()!=0 || data.isFilter() || data.isInvert())
            throw new IllegalArgumentException("VariableFX owns exactly 23 ints and three variable slots");
        int[] p=data.getIntParams().clone(); String icon=data.getStringParam(); String[] vars=data.getVariableIds().clone();
        var style=VariableFxStyles.find(category,p[6]);
        if(style==null || !style.colors().contains(p[7]) || !style.widths().contains(p[8]) || !style.renderers().contains(p[9]))
            throw new IllegalArgumentException("unsupported style options");
        bounded(p[0],0,1); bounded(p[1],0,4); bounded(p[2],0,2); bounded(p[3],0,15);
        bounded(p[4],0,1); bounded(p[5],1500,20000); bounded(p[14],0,1); bounded(p[15],0,1);
        if(p[0]==0 && p[1]<2) throw new IllegalArgumentException("furniture cannot have self/team audience");
        if((p[16]!=-10 && p[16]!=p[0]) || (p[17]!=-10 && p[17]!=p[0])) throw new IllegalArgumentException("invalid override target");
        // The AIR form uses signed-int fields; high words must sign-extend those fields.
        for(int i:new int[]{10,12,18}) if(p[i]!=(p[i+1]<0 ? -1 : 0)) throw new IllegalArgumentException("invalid signed-int high word");
        if(category!=2 && category!=5 && p[13]<=p[11]) throw new IllegalArgumentException("invalid range");
        if(category==2 && (p[11]!=0 || p[13]!=100)) throw new IllegalArgumentException("level range is derived");
        if(category==2 ? !(p[9]==20 ? List.of(2,3,4) : List.of(1)).contains(p[21])
                : category==5 ? p[21]<0 || p[21]>2 : p[21]!=0) throw new IllegalArgumentException("invalid renderer detail");
        int segmentRenderer=category==2 && p[9]==20 ? p[21] : p[9];
        bounded(p[20],0,List.of(2,4,13).contains(segmentRenderer) ? 100 : 0);
        if(icon==null || (category==5 ? !VariableFxStyles.ICONS.contains(icon) : !icon.isEmpty())) throw new IllegalArgumentException("invalid icon");
        for(String variable:vars) if(variable==null || variable.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>1024)
            throw new IllegalArgumentException("invalid variable ID");
        if(p[1]<3 && (!vars[2].isEmpty() || p[19]!=0)) throw new IllegalArgumentException("unused audience state");
        return new VariableFxSettings(category,p,icon,vars);
    }
    private static void bounded(int value,int min,int max) {
        if(value<min || value>max) throw new IllegalArgumentException("out of range");
    }
    public int category() { return category; }
    public int at(int index) { return ints[index]; }
    public int[] intParams() { return ints.clone(); }
    public String icon() { return icon; }
    public String[] variables() { return variables.clone(); }
    public String variable(int index) { return variables[index]; }
    public Map<String,String> extra() {
        Map<String,String> extra=new TreeMap<>(VariableFxStyles.find(category,ints[6]).extra());
        if(ints[20]!=0) extra.put("segments",String.valueOf(ints[20]));
        if(category==2) extra.put("sub_renderer",String.valueOf(ints[21]));
        if(category==5) {
            if(!icon.isEmpty()) extra.put("icon",icon);
            extra.put("icon_alignment",List.of("left","right","double").get(ints[21]));
        }
        extra.put("stack_order",String.valueOf(ints[22]));
        return Collections.unmodifiableMap(extra);
    }
}

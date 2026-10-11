package com.eu.habbo.habbohotel.items.interactions.wired.addons;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
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
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * July AIR add-on 21. Stores the exact nineteen-field July editor contract.
 *
 * <p>Projectile runtime algorithms were adapted in part from Seth/iSetht's
 * GPL-3.0 WiredExtraProjectile:
 * https://github.com/Harmonicrain/Arcturus-Community-Wired. Direction systems,
 * source semantics and serialization were realigned to July AIR.</p>
 */
public final class WiredAddonProjectile extends WiredMovementAddon {
    /** July source code shown as "All furni moved by the Wired stack". */
    private static final int FURNI_SOURCE_STACK_MOVED = 901;
    private int[] p = defaults();
    private String timeVariable="", distanceVariable="";
    public WiredAddonProjectile(ResultSet set,Item item)throws SQLException{super(set,item);}
    public WiredAddonProjectile(int id,int userId,Item item,String extra,int stack,int sells){super(id,userId,item,extra,stack,sells);}
    @Override public WiredAddonType getType(){return WiredAddonType.PROJECTILE;}
    @Override public boolean saveData(WiredSettingsV2 s){
        if(s==null||s.getIntParams().length!=19||s.getVariableIds().length!=2
                ||!s.getStringParam().isEmpty()
                ||s.getFurniIds().length!=0||s.getFurniIds2().length!=0
                ||s.getFurniSourceTypes().length!=3||s.getUserSourceTypes().length!=3)return false;
        int[] n=s.getIntParams().clone();
        if(!validParameters(n,s.getVariableIds()[0],s.getVariableIds()[1]))return false;
        p=n;timeVariable=n[3]==1?s.getVariableIds()[0]:"";distanceVariable=n[15]==1?s.getVariableIds()[1]:"";return true;
    }
    private static boolean validParameters(int[] n,String timeVariable,String distanceVariable){
        if(n==null||n.length!=19)return false;
        for(int i:new int[]{0,2,6,7,8,12,13})if(n[i]<0||n[i]>1)return false;
        if(n[1]<0||n[1]>3||n[3]<0||n[3]>1||n[4]<1||n[4]>100000||!target(n[5])||n[9]<0||n[9]>100000||n[10]<0||n[10]>7||n[11]<0||n[11]>127||n[14]<0||n[14]>2||n[15]<0||n[15]>1||n[16]<-64||n[16]>64||!target(n[17])||n[18]<-1000||n[18]>1000)return false;
        return (n[3]!=1||(timeVariable!=null&&!timeVariable.isBlank()))
                &&(n[15]!=1||(distanceVariable!=null&&!distanceVariable.isBlank()));
    }
    public boolean enabled(){return true;} public boolean rotateProjectile(){return p[0]==1;} public int directionalSystem(){return p[1];}
    public boolean changeShooterDirection(){return p[12]==1;} public boolean bunnyHop(){return p[13]==1;} public int rotationOffset(){return p[10];}
    public int curveStrength(){return p[18];} public int internalVariableMask(){return p[11];} public int distanceMode(){return p[14];}
    public int distance(WiredContext c){return p[14]==0?0:(p[15]==0?p[16]:resolve(c,distanceVariable,p[17],p[16],-64,64,2,2));}
    public int animationTime(WiredContext c, RoomTile from, double fromZ, RoomTile to, double toZ, int fallback){
        if(p[2]==0||from==null||to==null)return fallback;
        int per=p[3]==0?p[4]:resolve(c,timeVariable,p[5],p[4],1,100000,1,0);
        double dx=p[6]==1?to.x-from.x:0;
        double dy=p[7]==1?to.y-from.y:0;
        double dz=p[8]==1?toZ-fromZ:0;
        double distance=Math.sqrt(dx*dx+dy*dy+dz*dz);
        if(distance<=0)distance=1;
        long calculated=Math.round(per*distance-Math.max(0,p[9])*Math.max(0,distance-1));
        return (int)Math.max(1L,Math.min(Integer.MAX_VALUE,calculated));
    }
    public RoomTile resolveTarget(WiredContext context, RoomTile from, RoomTile requested) {
        // "Overshoot" is a renderer extension carried by the optional movement
        // time; the furni's logical destination stays unchanged. Only July's
        // "Always shoot X tiles" mode replaces the logical destination.
        if(context==null||from==null||requested==null||p[14]!=2)return requested;
        int amount=distance(context); if(amount==0)return requested;
        int sx=Integer.compare(requested.x-from.x,0), sy=Integer.compare(requested.y-from.y,0);
        if(sx==0&&sy==0)return requested;
        RoomTile tile=context.room().getLayout().getTile((short)(from.x+sx*amount),(short)(from.y+sy*amount));
        return tile==null||tile.state==RoomTileState.INVALID?requested:tile;
    }
    public int resolveRotation(RoomTile from,RoomTile target,int fallback){
        int result=fallback;
        if(p[0]==1&&from!=null&&target!=null&&(from.x!=target.x||from.y!=target.y)){
            int dx=target.x-from.x,dy=target.y-from.y;
            result=switch(p[1]){
                // Straight only changes direction when the destination lies on one of
                // the eight exact rays shown by July's directional-system preview.
                case 0->dx==0||dy==0||Math.abs(dx)==Math.abs(dy)
                        ? com.eu.habbo.util.pathfinding.Rotation.Calculate(from.x,from.y,target.x,target.y)
                        : fallback;
                // Diffuse divides the complete plane into eight 45-degree sectors.
                case 1->Math.floorMod((int)Math.round(Math.atan2(dy,dx)/(Math.PI/4.0))+2,8);
                // July's two four-direction systems are cardinal and diagonal.
                case 2->Math.abs(dx)>=Math.abs(dy)?(dx>=0?2:6):(dy>=0?4:0);
                case 3->dx>=0?(dy>=0?3:1):(dy>=0?5:7);
                default->fallback;
            };
        }
        return Math.floorMod(result+p[10],8);
    }
    public List<RoomUnit> shooters(WiredContext context){return context==null?List.of():List.copyOf(resolveUserSource(context,wiredUserSourceTypes,1));}
    public List<HabboItem> projectiles(WiredContext context,Collection<HabboItem> movedByStack){
        if(context==null)return List.of();
        int source=wiredFurniSourceTypes.length==0?FURNI_SOURCE_STACK_MOVED:wiredFurniSourceTypes[0];
        Collection<HabboItem> resolved=source==FURNI_SOURCE_STACK_MOVED
                ?movedByStack
                :resolveFurniSource(context,wiredFurniSourceTypes,0,List.of(),List.of());
        if(resolved==null||resolved.isEmpty())return List.of();
        LinkedHashSet<HabboItem> unique=new LinkedHashSet<>();
        for(HabboItem item:resolved)if(item!=null&&item.getRoomId()==context.room().getId())unique.add(item);
        return List.copyOf(unique);
    }
    public List<HabboItem> projectiles(WiredContext context){return projectiles(context,List.of());}
    private int resolve(WiredContext context,String id,int target,int fallback,int min,int max,int furniSlot,int userSlot){
        if(context==null||id==null||id.isBlank())return fallback;
        if(target==-20){
            Integer value=context.contextVariables().get(id);
            return Math.max(min,Math.min(max,value==null?fallback:value));
        }
        WiredVariableHolder holder=resolveHolder(context,target,furniSlot,userSlot);
        if(holder==null)return fallback;
        WiredVariableManager manager=context.room().getRoomSpecialTypes().getWiredVariableManager();
        WiredVariableValue current=manager==null?null:manager.get(id,holder);
        Integer value=current==null?WiredInternalVariableRuntime.read(context.room(),id,holder):current.value();
        return Math.max(min,Math.min(max,value==null?fallback:value));
    }
    private WiredVariableHolder resolveHolder(WiredContext context,int target,int furniSlot,int userSlot){
        if(target==-10)return WiredVariableHolder.room();
        if(target==0){
            List<HabboItem> items=resolveFurniSource(
                    context,wiredFurniSourceTypes,furniSlot,List.of(),List.of()).stream()
                    .filter(item->item!=null&&item.getRoomId()==context.room().getId())
                    .limit(2).toList();
            return items.size()==1?WiredVariableHolder.furni(items.get(0).getId()):null;
        }
        List<RoomUnit> units=resolveUserSource(context,wiredUserSourceTypes,userSlot).stream()
                .filter(unit->unit!=null&&unit.isInRoom()&&unit.getRoom()==context.room())
                .limit(2).toList();
        if(units.size()!=1)return null;
        var habbo=context.room().getHabbo(units.get(0));
        return habbo!=null&&habbo.getHabboInfo()!=null
                ?WiredVariableHolder.user(habbo.getHabboInfo().getId()):null;
    }
    private static boolean target(int n){return n==0||n==1||n==-10||n==-20;}
    @Override public String getWiredData(){return WiredManager.getGson().toJson(new Data(1,p,timeVariable,distanceVariable));}
    @Override public void loadWiredData(ResultSet set,Room room)throws SQLException{onPickUp();try{Data d=WiredManager.getGson().fromJson(set==null?null:set.getString("wired_data"),Data.class);String time=d==null||d.timeVariable==null?"":d.timeVariable;String distance=d==null||d.distanceVariable==null?"":d.distanceVariable;if(d!=null&&d.v==1&&validParameters(d.p,time,distance)){p=d.p.clone();timeVariable=time;distanceVariable=distance;}}catch(RuntimeException ignored){onPickUp();}}
    @Override public void onPickUp(){p=defaults();timeVariable=distanceVariable="";}
    @Override protected int[]getWiredIntParams(){return p.clone();}@Override protected String[]getWiredVariableIds(){return new String[]{timeVariable,distanceVariable};}
    @Override protected int getFurniSourceSlotCount(){return 3;}@Override protected int getUserSourceSlotCount(){return 3;}
    @Override protected int[]getAllowedFurniSourcesForSlot(int slot){return slot==0
            ?new int[]{FURNI_SOURCE_STACK_MOVED,FURNI_SOURCE_TRIGGERING_ITEM,FURNI_SOURCE_SELECTOR,FURNI_SOURCE_SIGNAL}
            :new int[]{FURNI_SOURCE_TRIGGERING_ITEM,FURNI_SOURCE_SELECTOR,FURNI_SOURCE_SIGNAL};}
    @Override protected int[]getAllowedUserSourcesForSlot(int slot){return new int[]{USER_SOURCE_TRIGGERING_USER,USER_SOURCE_SELECTOR,USER_SOURCE_SIGNAL};}
    @Override protected int getDefaultFurniSourceForSlot(int slot){return slot==0?FURNI_SOURCE_STACK_MOVED:FURNI_SOURCE_TRIGGERING_ITEM;}
    @Override protected boolean isWiredAdvancedMode(){return true;}
    private static int[]defaults(){return new int[]{1,1,0,0,500,0,1,1,0,0,0,0,0,0,0,0,0,0,0};}
    static final class Data{int v;int[]p;String timeVariable,distanceVariable;Data(){}Data(int v,int[]p,String t,String d){this.v=v;this.p=p;timeVariable=t;distanceVariable=d;}}
}

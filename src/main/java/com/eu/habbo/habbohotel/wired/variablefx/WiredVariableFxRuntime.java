package com.eu.habbo.habbohotel.wired.variablefx;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.*;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.core.WiredCapabilityService;
import com.eu.habbo.habbohotel.wired.variables.*;
import com.eu.habbo.habbohotel.wired.variablefx.VariableFxData.*;
import java.util.*;

/**
 * Room-owned, per-viewer native VariableFX publisher. Policies are local interoperability
 * choices, not claims about undocumented official server internals. No global broadcast.
 */
public final class WiredVariableFxRuntime {
    private static final org.slf4j.Logger LOGGER=org.slf4j.LoggerFactory.getLogger(WiredVariableFxRuntime.class);
    private final Map<GameClient,VariableFxSession> sessions=new IdentityHashMap<>();
    private boolean closed;
    public synchronized void close() { closed=true; sessions.clear(); }
    public synchronized void forget(GameClient client) { sessions.remove(client); }
    public static void refresh(Room room,boolean structural) {
        try {
            if(room!=null && room.getRoomSpecialTypes()!=null)
                room.getRoomSpecialTypes().getVariableFxRuntime().publish(room,structural);
        } catch(RuntimeException exception) {
            // Display delivery must never abort a committed mutation's Wired trigger.
            LOGGER.error("VariableFX refresh failed in room {}",room!=null ? room.getId() : 0,exception);
        }
    }
    private boolean update(GameClient client,Map<Integer,Config> configs,Map<String,Status> statuses,boolean structural) {
        try {
            sessions.computeIfAbsent(client,k->new VariableFxSession()).update(configs,statuses,structural,client::sendResponse);
            return true;
        } catch(RuntimeException exception) {
            LOGGER.warn("VariableFX connection queue failed; retaining baseline for retry",exception);
            return false;
        }
    }
    private synchronized void publish(Room room,boolean structural) {
        if(closed) return;
        boolean ready=WiredCapabilityService.isRoomCapabilityReady(WiredCapabilityService.CAPABILITY_VARIABLE_FX);
        if(!ready && sessions.isEmpty()) return;
        if(sessions.isEmpty() && room.getRoomSpecialTypes().getAddons().stream().noneMatch(a->a instanceof WiredAddonVariableFx)) return;
        Set<GameClient> active=Collections.newSetFromMap(new IdentityHashMap<>());
        List<Habbo> viewers=new ArrayList<>(room.getHabbos());
        for(Habbo viewer:viewers) {
            GameClient client=viewer.getClient();
            if(client!=null && viewer.getHabboInfo().getCurrentRoom()==room
                    && viewer.getRoomUnit()!=null && viewer.getRoomUnit().isInRoom()
                    && client.getWiredCapabilityState().supportsRoom(WiredCapabilityService.CAPABILITY_VARIABLE_FX,room.getId())) active.add(client);
        }
        // A client can renegotiate while staying in the room. Revoke its old view
        // before forgetting the session; it has already demonstrated these parsers.
        Set<GameClient> pendingRevocation=Collections.newSetFromMap(new IdentityHashMap<>());
        for(Habbo viewer:viewers) {
            GameClient client=viewer.getClient();
            if(client!=null && !active.contains(client) && sessions.containsKey(client)
                    && viewer.getHabboInfo().getCurrentRoom()==room)
                if(!update(client,Map.of(),Map.of(),true)) pendingRevocation.add(client);
        }
        sessions.keySet().removeIf(client->!active.contains(client) && !pendingRevocation.contains(client));
        if(active.isEmpty()) return;
        if(!ready
                || room.getRoomSpecialTypes().getWiredVariableManager()==null
                || !room.getRoomSpecialTypes().getWiredVariableManager().isOperational()) {
            for(GameClient client:active) update(client,Map.of(),Map.of(),true);
            return;
        }
        List<Candidate> candidates=collect(room);
        for(Habbo viewer:viewers) {
            GameClient client=viewer.getClient(); if(!active.contains(client)) continue;
            Map<Integer,Config> configs=new TreeMap<>(); Map<String,Status> statuses=new TreeMap<>();
            for(Candidate c:candidates) {
                Long audience=read(room,c.settings.variable(2),WiredVariableHolder.user(viewer.getHabboInfo().getId()));
                if(!VariableFxAudience.allows(c.settings.at(1),c.status.user(),c.stableId,viewer.getHabboInfo().getId(),
                        team(c.subject),team(viewer),sameGame(c.subject,viewer),audience,c.settings.at(19))) continue;
                configs.put(c.config.id(),c.config); statuses.put(c.status.fullKey(),c.status);
            }
            update(client,configs,statuses,structural);
        }
    }
    private static List<Candidate> collect(Room room) {
        List<Candidate> result=new ArrayList<>();
        var special=room.getRoomSpecialTypes();
        var addons=special.getAddons().stream().filter(a->a instanceof WiredAddonVariableFx)
                .map(a->(WiredAddonVariableFx)a).sorted(Comparator.comparingInt(WiredAddonVariableFx::getId)).toList();
        for(WiredAddonVariableFx addon:addons) {
            VariableFxSettings s=addon.configuration(); Config config=Config.of(addon.getRoomVisibleId(),s);
            List<InteractionWiredVariable> variables=special.getVariables(addon.getX(),addon.getY()).stream()
                    .sorted(Comparator.comparingInt(InteractionWiredVariable::getId)).toList();
            for(var variable:variables) {
                String id="room:"+variable.getId();
                var source=WiredVariableAliasResolver.resolve(room,id);
                if(source==null || !source.definition().hasValue()) continue;
                var scope=source.definition().holderScope();
                if((s.at(0)==1 && scope!=WiredVariableHolder.Scope.USER)
                        || (s.at(0)==0 && scope!=WiredVariableHolder.Scope.FURNI)) continue;
                if(s.at(0)==1) {
                    for(Habbo subject:room.getHabbos()) {
                        if(subject.getRoomUnit()==null || !subject.getRoomUnit().isInRoom() || subject.getRoomUnit().isInvisible()) continue;
                        int stableId=subject.getHabboInfo().getId();
                        append(result,room,config,s,id,source,WiredVariableHolder.user(stableId),subject.getRoomUnit().getId(),subject);
                    }
                } else {
                    // Includes room-local temporary furniture; their negative stable IDs are valid holders.
                    for(var item:room.getFloorItems()) if(!room.isItemHiddenByAreaHide(item))
                        append(result,room,config,s,id,source,WiredVariableHolder.furni(item.getId()),item.getRoomVisibleId(),null);
                }
            }
        }
        return result;
    }
    private static void append(List<Candidate> rows,Room room,Config config,VariableFxSettings s,String id,
                               WiredVariableAliasResolver.Resolved source,WiredVariableHolder holder,int visible,Habbo subject) {
        Long value=read(room,id,holder); if(value==null) return;
        Long min=null,max=null;
        if(s.at(14)==1 || s.at(15)==1) {
            Long a=s.at(14)==1 ? read(room,s.variable(0),s.at(16)==-10 ? WiredVariableHolder.room() : holder) : null;
            Long b=s.at(15)==1 ? read(room,s.variable(1),s.at(17)==-10 ? WiredVariableHolder.room() : holder) : null;
            min=a==null ? (long)s.at(11) : a; max=b==null ? (long)s.at(13) : b;
        }
        Map<String,String> extra=new TreeMap<>();
        String color=teamColor(team(subject)); if(color!=null) extra.put("delegated_color",color);
        if(s.category()==2) {
            WiredAddonVariableLevelUp level=source.room().getRoomSpecialTypes().getAddons().stream()
                    .filter(a->a instanceof WiredAddonVariableLevelUp)
                    .map(a->(WiredAddonVariableLevelUp)a)
                    .filter(a->{var sourceItem=source.room().getHabboItemByDatabaseId(source.definition().definitionItemId());
                        return sourceItem!=null && a.getX()==sourceItem.getX() && a.getY()==sourceItem.getY();})
                    .min(Comparator.comparingInt(WiredAddonVariableLevelUp::getId)).orElse(null);
            if(level==null) return;
            var state=level.calculate(value);
            extra.put("current_level",String.valueOf(state.currentLevel())); extra.put("max_level",String.valueOf(state.maxLevel()));
            extra.put("is_maxed",String.valueOf(state.isMaxed()));
            value=state.progress(); min=0L; max=state.isMaxed() ? 0L : state.xpRequired();
        }
        Status status=new Status(config.id()+"|"+id,false,s.at(0)==1,visible,value,min,max,extra);
        rows.add(new Candidate(config,s,status,holder.stableId(),subject));
    }
    private static Long read(Room room,String id,WiredVariableHolder holder) {
        if(id==null || id.isEmpty()) return null;
        var value=WiredVariableAliasOperations.readValue(room,id,holder);
        return value==null ? null : (long)value.value();
    }
    private static int team(Habbo user) {
        return user==null || user.getHabboInfo().getGamePlayer()==null ? 0 : user.getHabboInfo().getGamePlayer().getTeamColor().type;
    }
    private static boolean sameGame(Habbo a,Habbo b) {
        return a!=null && b!=null && a.getHabboInfo().getCurrentGame()!=null
                && a.getHabboInfo().getCurrentGame().equals(b.getHabboInfo().getCurrentGame());
    }
    private static String teamColor(int team) {
        return switch(team) { case 1->"#df291e"; case 2->"#7dce35"; case 3->"#268fff"; case 4->"#ffd83d"; default->null; };
    }
    private record Candidate(Config config,VariableFxSettings settings,Status status,int stableId,Habbo subject) {}
}

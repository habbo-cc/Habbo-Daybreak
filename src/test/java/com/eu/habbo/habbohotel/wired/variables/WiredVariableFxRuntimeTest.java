package com.eu.habbo.habbohotel.wired.variables;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.*;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.*;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.games.*;
import com.eu.habbo.habbohotel.wired.core.*;
import com.eu.habbo.habbohotel.wired.variablefx.*;
import com.eu.habbo.messages.outgoing.MessageComposer;
import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real native managers, indexes, alias reads, settings and packet composers; socket/database are fixtures. */
class WiredVariableFxRuntimeTest {
    private static class Fixture implements AutoCloseable {
        final WiredAliasRegressionFixture storage=new WiredAliasRegressionFixture();
        final WiredAliasRegressionFixture.State state=storage.room(99);
        final org.mockito.MockedStatic<WiredCapabilityService> caps=mockStatic(WiredCapabilityService.class,CALLS_REAL_METHODS);
        final List<Habbo> users=new ArrayList<>();
        final WiredAddonVariableFxProgress addon=new WiredAddonVariableFxProgress(200,1,null,"0",0,0);
        final Map<Integer,List<byte[]>> packets=new HashMap<>();
        Fixture() {
            caps.when(()->WiredCapabilityService.isRoomCapabilityReady(WiredCapabilityService.CAPABILITY_VARIABLE_FX)).thenReturn(true);
            when(state.room().getHabbos()).thenAnswer(c->List.copyOf(users));
            when(state.room().getFloorItems()).thenReturn(new gnu.trove.set.hash.THashSet<>());
            var source=storage.user(state,80,true,11);
            when(state.room().getHabboItemByDatabaseId(80)).thenReturn(source);
            state.types().addAddon(addon);
            configure(2,"",0);
        }
        void configure(int audience,String variable,int required) {
            int[] p=VariableFxSettings.defaults(1).intParams(); p[0]=1; p[1]=audience; p[16]=1; p[17]=1;
            p[18]=required<0 ? -1 : 0; p[19]=required;
            assertTrue(addon.saveData(new WiredSettingsV2(p,"",null,null,new String[]{"","",variable},null,null,0,0,false,false)));
        }
        Habbo user(int stable,int visible,boolean capable) {
            Habbo user=mock(Habbo.class); HabboInfo info=mock(HabboInfo.class); RoomUnit unit=mock(RoomUnit.class); GameClient client=mock(GameClient.class);
            when(user.getHabboInfo()).thenReturn(info); when(user.getRoomUnit()).thenReturn(unit); when(user.getClient()).thenReturn(client);
            when(info.getId()).thenReturn(stable); when(info.getCurrentRoom()).thenReturn(state.room()); when(unit.getId()).thenReturn(visible); when(unit.isInRoom()).thenReturn(true);
            var capabilities=capable ? WiredCapabilityService.negotiate(true,1,WiredCapabilityService.KNOWN_CAPABILITY_MASK,99) : WiredCapabilityState.unsupported();
            when(client.getWiredCapabilityState()).thenReturn(capabilities);
            List<byte[]> received=new ArrayList<>(); packets.put(stable,received);
            doAnswer(call->{ ByteBuf b=((MessageComposer)call.getArgument(0)).compose().get(); try { byte[] bytes=new byte[b.readableBytes()]; b.readBytes(bytes); received.add(bytes); } finally { b.release(); } return null; }).when(client).sendResponse(any(MessageComposer.class));
            users.add(user); when(state.room().getHabbo(stable)).thenReturn(user); return user;
        }
        void value(int user,int value) { state.manager().set("room:80",WiredVariableHolder.user(user),value); }
        void refresh(boolean structural) { WiredVariableFxRuntime.refresh(state.room(),structural); }
        void clear() { packets.values().forEach(List::clear); }
        void restoreQueue(Habbo user) {
            var received=packets.get(user.getHabboInfo().getId());
            var client=user.getClient();
            doAnswer(call->{ ByteBuf b=((MessageComposer)call.getArgument(0)).compose().get(); try { byte[] bytes=new byte[b.readableBytes()]; b.readBytes(bytes); received.add(bytes); } finally { b.release(); } return null; }).when(client).sendResponse(any(MessageComposer.class));
        }
        List<Integer> headers(int user) { return packets.get(user).stream().map(b->((b[4]&255)<<8)|(b[5]&255)).toList(); }
        List<Row> statuses(int user) {
            List<Row> rows=new ArrayList<>();
            for(byte[] bytes:packets.get(user)) if((((bytes[4]&255)<<8)|(bytes[5]&255))==754) {
                ByteBuf b=io.netty.buffer.Unpooled.wrappedBuffer(bytes);
                try {
                    b.skipBytes(6); boolean packetInitialize=b.readBoolean(); int count=b.readInt();
                    for(int i=0;i<count;i++) {
                        String key=string(b); boolean initialize=b.readBoolean(),userEntity=b.readBoolean(); int entity=b.readInt(); long value=b.readLong();
                        boolean range=b.readBoolean(); Long min=range ? b.readLong() : null,max=range ? b.readLong() : null;
                        Map<String,String> extra=new HashMap<>(); int extras=b.readInt(); for(int j=0;j<extras;j++) extra.put(string(b),string(b));
                        rows.add(new Row(key,packetInitialize||initialize,userEntity,entity,value,min,max,extra));
                    }
                    assertEquals(0,b.readableBytes());
                } finally { b.release(); }
            }
            return rows;
        }
        @Override public void close() { caps.close(); storage.close(); }
    }
    private record Row(String key,boolean initialize,boolean user,int entity,long value,Long min,Long max,Map<String,String> extra) {}
    private static String string(ByteBuf b) { return b.readCharSequence(b.readUnsignedShort(),java.nio.charset.StandardCharsets.UTF_8).toString(); }
    @Test void nativeValuesTranslateOnlyAtPacketBoundaryAndOldClientsReceiveNothing() {
        try(var f=new Fixture()) {
            f.user(101,7,true); f.user(102,9,false); f.value(101,42); f.value(102,-5); f.refresh(true);
            assertEquals(List.of(739,754),f.headers(101)); assertTrue(f.headers(102).isEmpty());
            assertEquals(List.of(7,9),f.statuses(101).stream().map(Row::entity).toList());
            assertEquals(List.of(42L,-5L),f.statuses(101).stream().map(Row::value).toList());
            assertTrue(f.statuses(101).stream().allMatch(Row::initialize));
            String raw=new String(f.packets.get(101).get(1),java.nio.charset.StandardCharsets.ISO_8859_1);
            assertTrue(raw.contains("200|room:80"));
            assertEquals(42,f.state.manager().get("room:80",WiredVariableHolder.user(101)).value());
            f.clear(); f.refresh(true); assertTrue(f.headers(101).isEmpty());
            f.value(101,43); f.refresh(false); assertEquals(List.of(754),f.headers(101)); assertEquals(0,f.packets.get(101).getFirst()[6]);
            assertEquals(List.of(43L),f.statuses(101).stream().map(Row::value).toList()); assertFalse(f.statuses(101).getFirst().initialize());
        }
    }
    @Test void selfAndVariableAudiencesDoNotLeakValuesOrConfigurations() {
        try(var f=new Fixture()) {
            f.user(101,7,true); f.user(102,9,true); f.user(103,11,true); f.value(101,42); f.configure(0,"",0); f.refresh(true);
            assertEquals(List.of(739,754),f.headers(101)); assertTrue(f.headers(102).isEmpty()); assertTrue(f.headers(103).isEmpty());
            f.storage.user(f.state,81,false,0); WiredVariableMenuMutation.apply(f.state.room(),WiredVariableHolder.user(102),"room:81",0,1,false,null);
            f.configure(3,"room:81",0); f.clear(); f.refresh(true);
            assertEquals(List.of(236,2588),f.headers(101)); assertEquals(List.of(739,754),f.headers(102)); assertTrue(f.headers(103).isEmpty());
            f.state.manager().removeValue("room:81",WiredVariableHolder.user(102)); f.clear(); f.refresh(false);
            assertEquals(List.of(236,2588),f.headers(102));
        }
    }
    @Test void deletionDeparturePickupAndDisposalRemoveStaleViews() {
        try(var f=new Fixture()) {
            var departing=f.user(101,7,true); f.user(102,9,true); f.value(101,42); f.value(102,30); f.refresh(true); f.clear();
            f.users.remove(departing); f.state.types().getVariableFxRuntime().forget(departing.getClient()); f.refresh(true);
            assertEquals(List.of(236),f.headers(102));
            f.clear(); f.state.types().removeAddon(f.addon); f.refresh(true); assertEquals(List.of(236,2588),f.headers(102));
            f.state.types().addAddon(f.addon); f.refresh(true); f.clear(); f.state.types().getVariableFxRuntime().close();
            f.value(102,31); f.refresh(false); assertTrue(f.headers(102).isEmpty());
        }
    }
    @Test void reconfigurationRangeOverridesAndLevelStateUseNativeSourceValues() {
        try(var f=new Fixture()) {
            f.user(101,7,true); f.value(101,150);
            var level=new WiredAddonVariableLevelUp(201,1,null,"0",0,0);
            assertTrue(level.saveData(new WiredSettingsV2(new int[]{0,1,100,3},"",null,null,null,null,null,0,0,false,false)));
            f.state.types().addAddon(level); f.state.types().removeAddon(f.addon);
            var display=new WiredAddonVariableFxLevel(202,1,null,"0",0,0); int[] p=display.configuration().intParams(); p[0]=1; p[16]=1; p[17]=1;
            assertTrue(display.saveData(new WiredSettingsV2(p,"",null,null,new String[]{"","",""},null,null,0,0,false,false)));
            f.state.types().addAddon(display); f.refresh(true);
            assertEquals(List.of(739,754),f.headers(101));
            String raw=new String(f.packets.get(101).get(1),java.nio.charset.StandardCharsets.ISO_8859_1);
            assertTrue(raw.contains("current_level")); assertTrue(raw.contains("is_maxed")); assertTrue(raw.contains("max_level"));
            var row=f.statuses(101).getFirst(); assertEquals(50,row.value()); assertEquals(0,row.min()); assertEquals(100,row.max());
            assertEquals(Map.of("current_level","2","max_level","3","is_maxed","false"),row.extra());
            f.clear(); f.value(101,999); f.refresh(false); row=f.statuses(101).getFirst();
            assertEquals(0,row.value()); assertEquals(0,row.max()); assertEquals("true",row.extra().get("is_maxed"));
        }
    }
    @Test void capabilityRevocationClearsPreviouslyAuthorizedViewAndReentryInitializes() {
        try(var f=new Fixture()) {
            var user=f.user(101,7,true); f.value(101,12); f.refresh(true); f.clear();
            when(user.getClient().getWiredCapabilityState()).thenReturn(WiredCapabilityState.unsupported()); f.refresh(true);
            assertEquals(List.of(236,2588),f.headers(101)); f.clear(); f.refresh(true); assertTrue(f.headers(101).isEmpty());
            var capability=WiredCapabilityService.negotiate(true,1,WiredCapabilityService.KNOWN_CAPABILITY_MASK,99);
            when(user.getClient().getWiredCapabilityState()).thenReturn(capability); f.refresh(true);
            assertTrue(f.statuses(101).getFirst().initialize());
        }
    }
    @Test void nativeTemporaryFurnitureUsesVisibleIdAndMissingEntityOrHiddenItemRemovesIt() {
        try(var f=new Fixture()) {
            f.user(101,7,true); int[] p=VariableFxSettings.defaults(1).intParams();
            assertTrue(f.addon.saveData(new WiredSettingsV2(p,"",null,null,new String[]{"","",""},null,null,0,0,false,false)));
            f.addon.setRoomVisibleId(5000);
            var source=mock(InteractionWiredVariable.class); when(source.getId()).thenReturn(81); when(source.getType()).thenReturn(WiredVariableType.FURNI);
            f.state.types().addVariable(source);
            assertTrue(f.state.manager().registerDefinition(WiredVariableDefinition.create(99,81,WiredVariableType.FURNI,"furni",1,false)));
            var item=mock(HabboItem.class); when(item.getId()).thenReturn(-100); when(item.getRoomVisibleId()).thenReturn(77);
            when(f.state.room().getHabboItemByDatabaseId(-100)).thenReturn(item);
            when(f.state.room().getFloorItems()).thenReturn(new gnu.trove.set.hash.THashSet<>(List.of(item)));
            f.state.manager().set("room:81",WiredVariableHolder.furni(-100),12); f.refresh(true);
            assertEquals(List.of(new Row("5000|room:81",true,false,77,12,null,null,Map.of())),f.statuses(101));
            f.clear(); when(f.state.room().isItemHiddenByAreaHide(item)).thenReturn(true); f.refresh(true);
            assertEquals(List.of(236,2588),f.headers(101));
        }
    }
    @Test void equalAudienceAndDynamicOverridesRefreshWithoutChangingStoredIdentity() {
        try(var f=new Fixture()) {
            f.user(101,7,true); f.user(102,9,true); f.value(101,42); f.storage.user(f.state,81,true,0);
            f.state.manager().set("room:81",WiredVariableHolder.user(101),-10);
            int[] p=VariableFxSettings.defaults(1).intParams(); p[0]=1; p[1]=4; p[14]=1; p[16]=1; p[17]=1; p[18]=-1; p[19]=-10;
            assertTrue(f.addon.saveData(new WiredSettingsV2(p,"",null,null,new String[]{"room:81","","room:81"},null,null,0,0,false,false)));
            f.refresh(true); var row=f.statuses(101).getFirst(); assertEquals(-10,row.min()); assertEquals(100,row.max()); assertTrue(f.headers(102).isEmpty());
            f.clear(); f.state.manager().set("room:81",WiredVariableHolder.user(101),-11); f.refresh(false);
            assertEquals(List.of(236,2588),f.headers(101));
        }
    }
    @Test void teamAudienceFollowsLiveMembershipWithoutSharingUnassignedPlayers() {
        try(var f=new Fixture()) {
            var a=f.user(101,7,true); var b=f.user(102,9,true); f.user(103,11,true); f.value(101,42); f.configure(1,"",0);
            var red=mock(GamePlayer.class); when(red.getTeamColor()).thenReturn(GameTeamColors.RED);
            when(a.getHabboInfo().getGamePlayer()).thenReturn(red); when(b.getHabboInfo().getGamePlayer()).thenReturn(red);
            when(a.getHabboInfo().getCurrentGame()).thenAnswer(call->com.eu.habbo.habbohotel.games.freeze.FreezeGame.class);
            when(b.getHabboInfo().getCurrentGame()).thenAnswer(call->com.eu.habbo.habbohotel.games.freeze.FreezeGame.class);
            f.refresh(true); assertEquals(List.of(739,754),f.headers(101)); assertEquals(List.of(739,754),f.headers(102)); assertTrue(f.headers(103).isEmpty());
            assertEquals("#df291e",f.statuses(102).getFirst().extra().get("delegated_color"));
            f.clear(); when(b.getHabboInfo().getGamePlayer()).thenReturn(null); f.refresh(true); assertEquals(List.of(236,2588),f.headers(102));
        }
    }
    @Test void committedMutationPublishesBeforeNestedWiredTriggerDispatch() {
        try(var f=new Fixture(); var wired=mockStatic(WiredManager.class,CALLS_REAL_METHODS)) {
            f.caps.when(()->WiredCapabilityService.isRoomCapabilityReady(WiredCapabilityService.CAPABILITY_VARIABLES)).thenReturn(true);
            wired.when(WiredManager::isEnabled).thenReturn(true);
            f.user(101,7,true); f.value(101,10); f.refresh(true); f.clear();
            var observed=new ArrayList<Long>();
            wired.when(()->WiredManager.handleEvent(any())).thenAnswer(call->{
                observed.add(f.statuses(101).getLast().value()); return true;
            });
            f.state.manager().setAndDispatch("room:80",WiredVariableHolder.user(101),20,
                    mutation->WiredManager.triggerVariableChanged(f.state.room(),null,mutation));
            assertEquals(List.of(20L),observed); assertEquals(List.of(754),f.headers(101));
        }
    }
    @Test void oneFailedSocketDoesNotStopOtherViewersAndFailedRevocationRetries() {
        try(var f=new Fixture()) {
            var broken=f.user(101,7,true); f.user(102,9,true); f.value(101,12); f.refresh(true); f.clear();
            var client=broken.getClient();
            doThrow(new IllegalStateException("fixture queue failure")).when(client).sendResponse(any(MessageComposer.class));
            f.value(101,13); f.refresh(false); assertEquals(13,f.statuses(102).getFirst().value());
            when(broken.getClient().getWiredCapabilityState()).thenReturn(WiredCapabilityState.unsupported()); f.refresh(true);
            f.restoreQueue(broken); f.refresh(true); assertEquals(List.of(236,2588),f.headers(101));
        }
    }
}

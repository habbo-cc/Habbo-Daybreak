package com.eu.habbo.habbohotel.wired.variablefx;

import com.eu.habbo.habbohotel.wired.variablefx.VariableFxData.*;
import com.eu.habbo.messages.outgoing.MessageComposer;
import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class VariableFxSessionTest {
    private static Config config(int style) { return new Config(7,true,1,15,false,1500,1,style,1,2,0,0,100,Map.of()); }
    private static Status row(int entity,long value) { return new Status("7|variable|opaque",false,true,entity,value,null,null,Map.of()); }
    private static Map<String,Status> rows(Status... rows) { Map<String,Status> r=new HashMap<>(); for(var row:rows) r.put(row.fullKey(),row); return r; }
    private static byte[] packet(MessageComposer composer) { ByteBuf b=composer.compose().get(); try { byte[] bytes=new byte[b.readableBytes()]; b.readBytes(bytes); return bytes; } finally { b.release(); } }
    private static int header(byte[] bytes) { return ((bytes[4]&255)<<8)|(bytes[5]&255); }
    private static boolean initial(byte[] bytes) { return bytes[6]!=0; }
    @Test void initialConfigPrecedesRowsThenOnlyChangedRowsAreSent() {
        var session=new VariableFxSession(); List<byte[]> sent=new ArrayList<>(); var a=row(1,10); var b=row(2,20);
        session.update(Map.of(7,config(0)),rows(a,b),true,c->sent.add(packet(c)));
        assertEquals(List.of(739,754),sent.stream().map(VariableFxSessionTest::header).toList()); assertTrue(initial(sent.get(1)));
        sent.clear(); session.update(Map.of(7,config(0)),rows(a,b),true,c->sent.add(packet(c))); assertTrue(sent.isEmpty());
        session.update(Map.of(7,config(0)),rows(a,row(2,30)),false,c->sent.add(packet(c)));
        assertEquals(List.of(754),sent.stream().map(VariableFxSessionTest::header).toList()); assertFalse(initial(sent.getFirst()));
    }
    @Test void exactEntityRemovalPrecedesConfigRemovalAndDoesNotRemoveAnotherEntity() {
        var session=new VariableFxSession(); List<byte[]> sent=new ArrayList<>(); var a=row(1,10); var b=row(2,20);
        session.update(Map.of(7,config(0)),rows(a,b),true,c->{});
        session.update(Map.of(7,config(0)),rows(b),true,c->sent.add(packet(c)));
        assertEquals(236,header(sent.getFirst()));
        String raw=new String(sent.getFirst(),java.nio.charset.StandardCharsets.ISO_8859_1); assertTrue(raw.endsWith(a.fullKey())); assertFalse(raw.contains(b.fullKey()));
        sent.clear(); session.update(Map.of(),Map.of(),true,c->sent.add(packet(c)));
        assertEquals(List.of(236,2588),sent.stream().map(VariableFxSessionTest::header).toList());
    }
    @Test void configurationChangesResendRowsAndReconnectGetsIndependentInitialView() {
        var session=new VariableFxSession(); List<byte[]> sent=new ArrayList<>(); var a=row(1,10);
        session.update(Map.of(7,config(0)),rows(a),true,c->{});
        session.update(Map.of(7,config(1)),rows(a),true,c->sent.add(packet(c)));
        assertEquals(List.of(739,754),sent.stream().map(VariableFxSessionTest::header).toList()); assertFalse(initial(sent.get(1)));
        var reconnect=new VariableFxSession(); sent.clear(); reconnect.update(Map.of(7,config(1)),rows(a),true,c->sent.add(packet(c))); assertTrue(initial(sent.get(1)));
    }
    @Test void failedQueueDoesNotAdvanceBaseline() {
        var session=new VariableFxSession(); var a=row(1,10); List<byte[]> sent=new ArrayList<>();
        assertThrows(IllegalStateException.class,()->session.update(Map.of(7,config(0)),rows(a),true,c->{throw new IllegalStateException();}));
        session.update(Map.of(7,config(0)),rows(a),true,c->sent.add(packet(c)));
        assertEquals(List.of(739,754),sent.stream().map(VariableFxSessionTest::header).toList()); assertTrue(initial(sent.get(1)));
    }
    @Test void largeViewsAreBatchedWithoutDiscardingRows() {
        var session=new VariableFxSession(); Status[] values=new Status[1025]; for(int i=0;i<values.length;i++) values[i]=row(i,i);
        List<byte[]> sent=new ArrayList<>(); session.update(Map.of(7,config(0)),rows(values),true,c->sent.add(packet(c)));
        assertEquals(List.of(739,754,754,754,754,754),sent.stream().map(VariableFxSessionTest::header).toList());
        assertEquals(1,java.nio.ByteBuffer.wrap(sent.getLast(),7,4).getInt());
    }
    @Test void audienceIsFailClosedAndNeverImplicitlyBypassed() {
        assertTrue(VariableFxAudience.allows(0,true,1,1,0,0,false,null,0));
        assertFalse(VariableFxAudience.allows(0,true,1,2,1,1,true,null,0));
        assertFalse(VariableFxAudience.allows(1,true,1,2,0,0,true,null,0));
        assertFalse(VariableFxAudience.allows(1,true,1,2,1,1,false,null,0));
        assertTrue(VariableFxAudience.allows(1,true,1,2,1,1,true,null,0));
        assertFalse(VariableFxAudience.allows(0,false,1,1,1,1,true,null,0));
        assertFalse(VariableFxAudience.allows(4,true,1,1,1,1,true,null,0));
        assertTrue(VariableFxAudience.allows(3,false,1,2,0,0,false,0L,99));
        assertTrue(VariableFxAudience.allows(4,true,1,2,0,0,false,-5L,-5));
        assertFalse(VariableFxAudience.allows(4,true,1,2,0,0,false,5L,-5));
    }
}

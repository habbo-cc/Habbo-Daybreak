package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.habbohotel.wired.variablefx.VariableFxData.*;
import com.eu.habbo.messages.outgoing.MessageComposer;
import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WiredVariableFxComposerTest {
    static ByteBuf body(MessageComposer composer,int header) {
        ByteBuf b=composer.compose().get(); assertEquals(b.readableBytes()-4,b.readInt()); assertEquals(header,b.readUnsignedShort()); return b;
    }
    static String string(ByteBuf b) { return b.readCharSequence(b.readUnsignedShort(),StandardCharsets.UTF_8).toString(); }
    @Test void configWritesLiteralAirOrderBooleansLongsAndSortedExtras() {
        var config=new Config(-7,true,1,15,true,20000,5,2,1002,-1,200,Long.MIN_VALUE,Long.MAX_VALUE,Map.of("z","last","a","first"));
        ByteBuf b=body(new WiredVariableFxConfigMessageComposer(List.of(config)),739);
        try {
            assertEquals(1,b.readInt()); assertEquals(-7,b.readInt()); assertTrue(b.readBoolean()); assertEquals(1,b.readInt()); assertEquals(15,b.readInt());
            assertTrue(b.readBoolean()); assertEquals(20000,b.readInt());
            for(int n:new int[]{5,2,1002,-1,200}) assertEquals(n,b.readInt());
            assertEquals(Long.MIN_VALUE,b.readLong()); assertEquals(Long.MAX_VALUE,b.readLong()); assertEquals(2,b.readInt());
            assertEquals("a",string(b)); assertEquals("first",string(b)); assertEquals("z",string(b)); assertEquals("last",string(b)); assertEquals(0,b.readableBytes());
        } finally { b.release(); }
    }
    @Test void mixedStatusRowsKeepBothInitializationFlagsAndOptionalSigned64Range() {
        var a=new Status("7|room:8|opaque",false,true,17,-4294967297L,-9007199254740991L,9007199254740991L,Map.of("current_level","2"));
        var c=new Status("7|room:8|opaque",true,false,-20,4294967295L,null,null,Map.of());
        ByteBuf b=body(new WiredVariableFxStatusMessageComposer(true,List.of(a,c)),754);
        try {
            assertTrue(b.readBoolean()); assertEquals(2,b.readInt()); assertEquals(a.key(),string(b)); assertFalse(b.readBoolean()); assertTrue(b.readBoolean());
            assertEquals(17,b.readInt()); assertEquals(a.value(),b.readLong()); assertTrue(b.readBoolean()); assertEquals(a.min().longValue(),b.readLong()); assertEquals(a.max().longValue(),b.readLong());
            assertEquals(1,b.readInt()); assertEquals("current_level",string(b)); assertEquals("2",string(b));
            assertEquals(c.key(),string(b)); assertTrue(b.readBoolean()); assertFalse(b.readBoolean()); assertEquals(-20,b.readInt()); assertEquals(4294967295L,b.readLong());
            assertFalse(b.readBoolean()); assertEquals(0,b.readInt()); assertEquals(0,b.readableBytes());
        } finally { b.release(); }
    }
    @Test void removalUsesCompleteOpaqueEntityKeysAndIndependentConfigIds() {
        ByteBuf b=body(new WiredVariableFxRemoveMessageComposer(List.of("7|room:8|opaque|u|17","7|room:8|opaque|f|-20")),236);
        try { assertEquals(2,b.readInt()); assertEquals("7|room:8|opaque|u|17",string(b)); assertEquals("7|room:8|opaque|f|-20",string(b)); assertEquals(0,b.readableBytes()); } finally { b.release(); }
        b=body(new WiredVariableFxConfigRemoveMessageComposer(List.of(7,-20)),2588);
        try { assertEquals(2,b.readInt()); assertEquals(7,b.readInt()); assertEquals(-20,b.readInt()); assertEquals(0,b.readableBytes()); } finally { b.release(); }
    }
    @Test void nativeEditorReopenKeepsExactly23WordsAndThreePositionalVariableSlots() throws Exception {
        var constructor=com.eu.habbo.habbohotel.items.Item.class.getDeclaredConstructor(); constructor.setAccessible(true);
        var item=constructor.newInstance(); var sprite=com.eu.habbo.habbohotel.items.Item.class.getDeclaredField("spriteId"); sprite.setAccessible(true); sprite.setInt(item,123);
        var addon=new com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonVariableFxNumber(88,1,item,"0",0,0);
        int[] ints=addon.configuration().intParams(); ints[22]=-9;
        assertTrue(addon.saveData(new com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2(ints,"wooden_logs",null,null,new String[]{"","room:7",""},null,null,0,0,false,false)));
        ByteBuf b=body(new WiredAddonDataMessageComposer(addon,null),7101);
        try {
            assertEquals(0,b.readInt()); assertEquals(0,b.readInt()); assertEquals(0,b.readInt());
            assertEquals(123,b.readInt()); assertEquals(88,b.readInt()); assertEquals("wooden_logs",string(b));
            assertEquals(23,b.readInt()); for(int expected:ints) assertEquals(expected,b.readInt());
            assertEquals(3,b.readInt()); assertEquals("",string(b)); assertEquals("room:7",string(b)); assertEquals("",string(b));
            assertEquals(0,b.readInt()); assertEquals(0,b.readInt()); assertEquals(1205,b.readInt());
        } finally { b.release(); }
    }
}

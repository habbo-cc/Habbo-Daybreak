package com.eu.habbo.habbohotel.items.interactions.wired.addons;

import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.wired.WiredAddonType;
import com.eu.habbo.habbohotel.wired.variablefx.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WiredAddonVariableFxTest {
    private static List<WiredAddonVariableFx> addons() {
        return List.of(new WiredAddonVariableFxHealth(1,1,null,"0",0,0),new WiredAddonVariableFxProgress(2,1,null,"0",0,0),
                new WiredAddonVariableFxLevel(3,1,null,"0",0,0),new WiredAddonVariableFxStatus(4,1,null,"0",0,0),
                new WiredAddonVariableFxBoss(5,1,null,"0",0,0),new WiredAddonVariableFxNumber(6,1,null,"0",0,0));
    }
    private static WiredSettingsV2 settings(int[] ints,String icon,String... variables) {
        return new WiredSettingsV2(ints,icon,null,null,variables,null,null,0,0,false,false);
    }
    @Test void allSixIdentitiesRoundTripNativeEditorAndVersionedPersistence() {
        for(var addon:addons()) {
            int category=addon.getType().code-1200;
            assertEquals(WiredAddonType.fromCode(1200+category),addon.getType());
            int[] p=addon.getWiredIntParams(); p[0]=1; p[1]=4; p[3]=15; p[4]=1; p[5]=20000;
            p[16]=-10; p[17]=1; p[18]=-1; p[19]=-123; p[22]=Integer.MIN_VALUE;
            String icon=category==5 ? "ranch.carrot" : "";
            assertTrue(addon.saveData(settings(p,icon,"","room:77","internal:1:@user_id")));
            String json=addon.getWiredData();
            addon.onPickUp(); assertTrue(addon.loadPersistedData(json));
            assertArrayEquals(p,addon.getWiredIntParams());
            assertArrayEquals(new String[]{"","room:77","internal:1:@user_id"},addon.getWiredVariableIds());
            assertEquals(icon,addon.getWiredStringParam()); assertEquals(0,addon.getMaxFurniSelection());
        }
    }
    @Test void invalidSaveOrPersistenceNeverPartlyMutatesAcceptedState() {
        var addon=addons().get(1); String baseline=addon.getWiredData();
        int[] valid=addon.getWiredIntParams();
        for(int[] pair:new int[][]{{0,2},{1,1},{2,3},{3,16},{4,2},{5,1499},{5,20001},{6,999},{7,999},{8,999},{9,101},
                {10,1},{12,-1},{14,2},{15,-1},{16,1},{17,1},{18,1},{20,101},{21,1}}) {
            int[] p=valid.clone(); p[pair[0]]=pair[1];
            assertFalse(addon.saveData(settings(p,"","","","")),Arrays.toString(pair));
            assertEquals(baseline,addon.getWiredData());
        }
        assertFalse(addon.saveData(settings(Arrays.copyOf(valid,22),"","","","")));
        assertFalse(addon.saveData(settings(valid,"","","")));
        assertFalse(addon.saveData(settings(valid,"code","","","")));
        assertFalse(addon.loadPersistedData(baseline.replace("\"v\":1","\"v\":2")));
        assertFalse(addon.loadPersistedData("{\"v\":1}"));
        assertFalse(addon.loadPersistedData("not json")); assertEquals(baseline,addon.getWiredData());
    }
    @Test void signedBoundsEmptySlotsAndDefensiveCopiesArePreserved() {
        var addon=addons().get(0); int[] p=addon.getWiredIntParams(); p[10]=-1; p[11]=Integer.MIN_VALUE; p[13]=Integer.MAX_VALUE;
        String[] vars={"a|b","",""}; assertTrue(addon.saveData(settings(p,"",vars)));
        p[11]=0; vars[0]="changed";
        assertEquals(Integer.MIN_VALUE,addon.configuration().at(11)); assertEquals("a|b",addon.configuration().variable(0));
        var returned=addon.configuration().intParams(); returned[11]=12;
        assertEquals(Integer.MIN_VALUE,addon.configuration().at(11));
    }
    @Test void all38MetadataStylesAcceptEveryDeclaredOptionAndProduceRendererExtras() {
        assertEquals(List.of(4L,5L,2L,22L,2L,3L),java.util.stream.IntStream.range(0,6).mapToObj(c->VariableFxStyles.all().stream().filter(s->s.category()==c).count()).toList());
        assertEquals(37,VariableFxStyles.ICONS.size());
        for(var style:VariableFxStyles.all()) for(int color:style.colors()) for(int width:style.widths()) for(int renderer:style.renderers()) {
            var addon=addons().get(style.category()); int[] p=addon.getWiredIntParams();
            p[6]=style.id(); p[7]=color; p[8]=width; p[9]=renderer; if(renderer==21) p[21]=1;
            assertTrue(addon.saveData(settings(p,"","","","")));
        }
        var status=addons().get(3); int[] p=status.getWiredIntParams(); p[6]=8; p[20]=17; p[22]=-8;
        assertTrue(status.saveData(settings(p,"","","","")));
        assertEquals(Map.of("icon","gold","color","#ffc83d","metallic","true","segments","17","stack_order","-8"),status.configuration().extra());
    }
    @Test void rejectsOtherEditorsSelectionsSourcesAndFlags() {
        var addon=addons().get(0); int[] p=addon.getWiredIntParams();
        assertFalse(addon.saveData(new WiredSettingsV2(p,"",new int[]{1},null,new String[]{"","",""},null,null,0,0,false,false)));
        assertFalse(addon.saveData(new WiredSettingsV2(p,"",null,null,new String[]{"","",""},new int[]{0},null,0,0,false,false)));
        assertFalse(addon.saveData(new WiredSettingsV2(p,"",null,null,new String[]{"","",""},null,null,1,0,false,false)));
        assertFalse(addon.saveData(new WiredSettingsV2(p,"",null,null,new String[]{"","",""},null,null,0,0,true,false)));
    }
}

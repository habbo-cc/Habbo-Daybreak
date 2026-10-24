package com.eu.habbo.habbohotel.wired.variablefx;

import java.util.*;

/** Identity/option metadata only, from AIR snapshot 4b418c68. No AIR implementation. */
public final class VariableFxStyles {
    public record Style(int category, int id, List<Integer> colors, List<Integer> widths,
                        List<Integer> renderers, int color, int width, Map<String, String> extra) {
        public Style {
            colors = List.copyOf(colors); widths = List.copyOf(widths);
            renderers = List.copyOf(renderers); extra = Map.copyOf(extra);
        }
    }
    private static final List<Integer> NORMAL = join(range(1, 19), List.of(100,101,102,103,104));
    private static final List<Integer> TEAM = join(NORMAL, List.of(1002));
    private static final List<Integer> WIDTHS = List.of(0,1,2,3,4);
    private static final List<Style> STYLES = build();
    public static final Set<String> ICONS = icons();
    private VariableFxStyles() {}

    public static List<Style> all() { return STYLES; }
    public static Style find(int category, int id) {
        return STYLES.stream().filter(s -> s.category == category && s.id == id).findFirst().orElse(null);
    }
    public static Style first(int category) {
        return STYLES.stream().filter(s -> s.category == category).findFirst().orElseThrow();
    }
    private static List<Style> build() {
        List<Style> s = new ArrayList<>();
        add(s,0,0,List.of(1000),List.of(1,2,3),List.of(10),1000,2,Map.of("icon","misc_heart"));
        add(s,0,3,TEAM,List.of(-1),List.of(11),5,-1,Map.of());
        add(s,0,1,TEAM,List.of(2,3),List.of(12),5,2,Map.of());
        add(s,0,2,List.of(-1),WIDTHS,List.of(13),-1,2,Map.of());
        int[] bars = {0,2,3,4,1};
        for (int i=0;i<bars.length;i++) add(s,1,i,TEAM,WIDTHS,List.of(bars[i]),1,2,Map.of());
        add(s,2,0,join(NORMAL,List.of(1001,1002)),List.of(1,2,3),List.of(20),1001,2,Map.of("sub_renderer","2"));
        add(s,2,1,join(NORMAL,List.of(1001,1002)),List.of(1,2,3,4),List.of(21),1001,2,Map.of("sub_renderer","1"));
        String[] icons = {"energy","shield","magic","food","stamina","poison","mana","health","gold","gems","honor","reputation","cooldown","timeleft","burning","freezing","battery","repairing","stealth","upgrading","star_power","droplet"};
        String[] colors = {"#ffd83d","#4aa9f6","#8751d1","#ff9f24","#86d213","#8ddc35","#268fff","#7dce35","#ffc83d","#416bdd","#fac384","#ffd83d","#b8c3cc","#74b9e8","#ff5a1f","#82cfff","","#c9c5b8","#6254a8","#6bdc34","#ffd900","#4aabf5"};
        for (int i=0;i<icons.length;i++) {
            boolean metallic = i==8 || i==9 || i==17 || i==20;
            add(s,3,i,List.of(i==16 ? 1000 : -1),WIDTHS,List.of(2,3,4),i==16 ? 1000 : -1,2,
                    i==16 ? Map.of("icon",icons[i]) : Map.of("icon",icons[i],"color",colors[i],"metallic",String.valueOf(metallic)));
        }
        add(s,4,0,List.of(5),List.of(3,4,100),List.of(100),5,4,Map.of("icon","misc_skull","icon_alignment","double"));
        add(s,4,1,join(NORMAL,List.of(1000,1002)),List.of(3,4,100),List.of(100),5,4,Map.of());
        add(s,5,0,List.of(5,1,7,3,19,1002),List.of(-1),List.of(201),1,-1,Map.of("design","freeze_style"));
        add(s,5,1,TEAM,List.of(-1),List.of(200),1,-1,Map.of("design","shalimar"));
        add(s,5,2,TEAM,List.of(-1),List.of(200),1,-1,Map.of("design","blocky"));
        return List.copyOf(s);
    }
    private static void add(List<Style> s,int category,int id,List<Integer> colors,List<Integer> widths,List<Integer> renderers,int color,int width,Map<String,String> extra) {
        s.add(new Style(category,id,colors,widths,renderers,color,width,extra));
    }
    private static List<Integer> range(int first,int last) {
        return java.util.stream.IntStream.rangeClosed(first,last).boxed().toList();
    }
    private static List<Integer> join(List<Integer> a,List<Integer> b) {
        List<Integer> result=new ArrayList<>(a); result.addAll(b); return List.copyOf(result);
    }
    private static Set<String> icons() {
        Set<String> result=new HashSet<>(List.of("", "battery","burning","cash","cooldown","droplet","energy","eye","fish","food","freezing","gems","gold","health","honor","magic","mana","poison","repairing","reputation","shield","stamina","star_power","stealth","timeleft","upgrading","wooden_logs"));
        for(String name:List.of("aubergine","carrot","corn","egg","grape","potato","pumpkin","sapling","tomato","wheat")) result.add("ranch."+name);
        return Set.copyOf(result);
    }
}

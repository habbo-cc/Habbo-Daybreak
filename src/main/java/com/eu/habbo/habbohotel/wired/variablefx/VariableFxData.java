package com.eu.habbo.habbohotel.wired.variablefx;

import java.util.*;

/** Immutable outbound snapshots. Stable holder IDs never appear in these DTOs. */
public final class VariableFxData {
    private VariableFxData() {}
    public record Config(int id,boolean user,int showMode,int events,boolean hover,int duration,
                         int category,int style,int color,int width,int renderer,long min,long max,Map<String,String> extra) {
        public Config { extra=Collections.unmodifiableMap(new TreeMap<>(extra)); }
        public static Config of(int id,VariableFxSettings s) {
            return new Config(id,s.at(0)==1,s.at(2),s.at(3),s.at(4)==1,s.at(5),s.category(),s.at(6),s.at(7),s.at(8),s.at(9),s.at(11),s.at(13),s.extra());
        }
    }
    public record Status(String key,boolean initialize,boolean user,int entity,long value,
                         Long min,Long max,Map<String,String> extra) {
        public Status {
            Objects.requireNonNull(key);
            if((min==null)!=(max==null)) throw new IllegalArgumentException("range overrides are a pair");
            extra=Collections.unmodifiableMap(new TreeMap<>(extra));
        }
        public String fullKey() { return key+(user ? "|u|" : "|f|")+entity; }
        public Status initialized(boolean value) { return new Status(key,value,user,entity,this.value,min,max,extra); }
    }
}

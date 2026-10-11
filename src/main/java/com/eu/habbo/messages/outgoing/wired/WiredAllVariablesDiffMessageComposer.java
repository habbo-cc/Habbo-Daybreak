package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.habbohotel.wired.variables.WiredVariableCatalog;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.*;

/** AIR-shaped bounded variable diff, including non-persisted generated children. */
public final class WiredAllVariablesDiffMessageComposer extends MessageComposer {
    public static final int MAX_ENTRIES_PER_CHUNK = 256;
    private final int aggregateHash; private final boolean lastChunk;
    private final List<String> removed; private final List<WiredVariableCatalog.Entry> changed;
    private WiredAllVariablesDiffMessageComposer(int hash, boolean last, List<String> removed,
                                                  List<WiredVariableCatalog.Entry> changed) {
        aggregateHash=hash;lastChunk=last;this.removed=List.copyOf(removed);this.changed=List.copyOf(changed);
    }
    public static List<WiredAllVariablesDiffMessageComposer> chunks(WiredVariableCatalog.Catalog catalog,
                                                                     Map<String,Integer> clientHashes) {
        List<String> removed=clientHashes.keySet().stream().filter(id->!catalog.entries().containsKey(id)).sorted().toList();
        List<WiredVariableCatalog.Entry> changed=catalog.entries().values().stream()
                .filter(e->!Integer.valueOf(e.hash()).equals(clientHashes.get(e.variableId()))).toList();
        List<WiredAllVariablesDiffMessageComposer> result=new ArrayList<>(); int ri=0,ci=0;
        do { int capacity=MAX_ENTRIES_PER_CHUNK; int re=Math.min(removed.size(),ri+capacity);
            List<String> rs=removed.subList(ri,re);capacity-=rs.size();int ce=Math.min(changed.size(),ci+capacity);
            List<WiredVariableCatalog.Entry> cs=changed.subList(ci,ce);ri=re;ci=ce;
            result.add(new WiredAllVariablesDiffMessageComposer(catalog.aggregateHash(),ri==removed.size()&&ci==changed.size(),rs,cs));
        } while(ri<removed.size()||ci<changed.size()); return List.copyOf(result);
    }
    @Override protected ServerMessage composeInternal(){response.init(Outgoing.WiredAllVariablesDiffMessageComposer);
        response.appendInt(aggregateHash);response.appendBoolean(lastChunk);response.appendInt(removed.size());
        for(String id:removed)response.appendString(id);response.appendInt(changed.size());
        for(WiredVariableCatalog.Entry entry:changed){response.appendInt(entry.hash());entry.metadata().serialize(response);}return response;}
}

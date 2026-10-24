package com.eu.habbo.habbohotel.wired.variablefx;

import com.eu.habbo.habbohotel.wired.variablefx.VariableFxData.*;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.wired.*;
import java.util.*;
import java.util.function.Consumer;

/** One connection's last successfully queued view. Never shared between viewers or reconnects. */
public final class VariableFxSession {
    private Map<Integer,Config> configs=Map.of();
    private Map<String,Status> statuses=Map.of();
    private boolean initialized;
    public void update(Map<Integer,Config> nextConfigs,Map<String,Status> nextStatuses,boolean structural,Consumer<MessageComposer> send) {
        List<String> removed=statuses.keySet().stream().filter(key->!nextStatuses.containsKey(key)).sorted().toList();
        List<Integer> removedConfigs=configs.keySet().stream().filter(id->!nextConfigs.containsKey(id)).sorted().toList();
        List<Config> changed=nextConfigs.values().stream().filter(c->!c.equals(configs.get(c.id()))).sorted(Comparator.comparingInt(Config::id)).toList();
        Set<Integer> changedIds=new HashSet<>(); changed.forEach(c->changedIds.add(c.id()));
        List<Status> rows=new ArrayList<>();
        nextStatuses.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry->{
            Status row=entry.getValue().initialized(false);
            Status previous=statuses.get(entry.getKey());
            int separator=row.key().indexOf('|');
            int id=Integer.parseInt(separator<0 ? row.key() : row.key().substring(0,separator));
            boolean configChanged=changedIds.contains(id);
            if(!row.equals(previous) || configChanged)
                rows.add(row.initialized(!initialized || (previous==null && structural) || (previous!=null && configChanged)));
        });
        // No baseline is advanced if queuing throws. Retrying removals/configuration is idempotent.
        chunks(removed,b->send.accept(new WiredVariableFxRemoveMessageComposer(b)));
        chunks(removedConfigs,b->send.accept(new WiredVariableFxConfigRemoveMessageComposer(b)));
        chunks(changed,b->send.accept(new WiredVariableFxConfigMessageComposer(b)));
        chunks(rows,b->send.accept(new WiredVariableFxStatusMessageComposer(!initialized,b)));
        configs=Map.copyOf(nextConfigs);
        Map<String,Status> baseline=new HashMap<>(); nextStatuses.forEach((key,row)->baseline.put(key,row.initialized(false)));
        statuses=Map.copyOf(baseline); initialized=true;
    }
    private static <T> void chunks(List<T> rows,Consumer<List<T>> send) {
        for(int i=0;i<rows.size();i+=256) send.accept(rows.subList(i,Math.min(rows.size(),i+256)));
    }
}

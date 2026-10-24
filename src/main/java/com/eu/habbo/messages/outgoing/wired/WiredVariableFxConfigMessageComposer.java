package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.habbohotel.wired.variablefx.VariableFxData.Config;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.*;

/** AIR header 739: configuration must precede referring status rows. */
public final class WiredVariableFxConfigMessageComposer extends MessageComposer {
    private final List<Config> configs;
    public WiredVariableFxConfigMessageComposer(Collection<Config> configs) { this.configs=List.copyOf(configs); }
    @Override protected ServerMessage composeInternal() {
        response.init(Outgoing.WiredVariableFxConfigMessageComposer);
        response.appendInt(configs.size());
        for(Config c:configs) {
            response.appendInt(c.id()); response.appendBoolean(c.user()); response.appendInt(c.showMode());
            response.appendInt(c.events()); response.appendBoolean(c.hover()); response.appendInt(c.duration());
            response.appendInt(c.category()); response.appendInt(c.style()); response.appendInt(c.color());
            response.appendInt(c.width()); response.appendInt(c.renderer()); response.appendLong(c.min()); response.appendLong(c.max());
            extras(response,c.extra());
        }
        return response;
    }
    static void extras(ServerMessage message,Map<String,String> extras) {
        message.appendInt(extras.size());
        extras.forEach((key,value)->{ message.appendString(key); message.appendString(value); });
    }
}

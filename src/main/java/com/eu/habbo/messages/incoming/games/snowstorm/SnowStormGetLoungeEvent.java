package com.eu.habbo.messages.incoming.games.snowstorm;

import com.eu.habbo.habbohotel.games.snowstorm.SnowStormProtocol;

public final class SnowStormGetLoungeEvent extends SnowStormHandler {
    @Override public void handle() {
        requireEnd();
        if (habbo() != null) failure(SnowStormProtocol.OP_GET_LOUNGE, 0, manager().sendLounge(habbo()));
    }
}

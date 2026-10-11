package com.eu.habbo.messages.incoming.games;

import com.eu.habbo.messages.incoming.MessageHandler;

/**
 * SnowStorm announcing that it has no game loaded, carrying the game type it is talking about.
 *
 * The client builds its SnowWar engine with the games component whether or not SnowStorm is
 * implemented, and that engine answers the club info packet at login by declaring itself unloaded.
 * This server tracks no SnowStorm queue state, so there is nothing to clear -- accepted and
 * discarded to keep it out of the unknown-message log.
 */
public class SnowStormGameUnloadedMessageEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.packet.readInt(); // game type
    }
}

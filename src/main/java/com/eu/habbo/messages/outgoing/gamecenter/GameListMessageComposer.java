package com.eu.habbo.messages.outgoing.gamecenter;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.ArrayList;
import java.util.List;

public class GameListMessageComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        List<String> games = new ArrayList<>();
        String[] configuredGames = Emulator.getConfig().getValue("gamecenter.games", "snowwar,basejump").split(",");
        for (String gameName : configuredGames) {
            gameName = gameName.trim();
            if (!gameName.isEmpty()) {
                games.add(gameName);
            }
        }

        this.response.init(Outgoing.GameListMessageComposer);
        this.response.appendInt(games.size());//Count

        for (String gameName : games) {
            String keyPrefix = "gamecenter.game." + gameName + ".";
            this.response.appendInt(Emulator.getConfig().getInt(keyPrefix + "id", -1));
            this.response.appendString(gameName);
            this.response.appendString(Emulator.getConfig().getValue(keyPrefix + "background.color", ""));
            this.response.appendString(Emulator.getConfig().getValue(keyPrefix + "text.color", ""));
            this.response.appendString(Emulator.getConfig().getValue(keyPrefix + "asset.url", Emulator.getConfig().getValue("images.gamecenter." + gameName, "")));
            this.response.appendString(Emulator.getConfig().getValue(keyPrefix + "support.url", ""));
        }

        return this.response;
    }
}

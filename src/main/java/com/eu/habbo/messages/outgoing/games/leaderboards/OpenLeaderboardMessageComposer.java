package com.eu.habbo.messages.outgoing.games.leaderboards;

import com.eu.habbo.habbohotel.leaderboards.LeaderboardData;
import com.eu.habbo.habbohotel.leaderboards.LeaderboardDefinition;
import com.eu.habbo.habbohotel.leaderboards.LeaderboardRow;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public final class OpenLeaderboardMessageComposer extends MessageComposer {
    private final LeaderboardData data;

    public OpenLeaderboardMessageComposer(LeaderboardData data) {
        this.data = data;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.OpenLeaderboardMessageComposer);
        LeaderboardDefinition selected = this.data.selected();
        this.response.appendInt(selected.id());
        this.serializeDefinition(selected);
        this.response.appendString(this.data.period().name());
        this.response.appendInt(this.data.offset());
        this.response.appendInt(this.data.limit());
        this.response.appendInt(this.data.totalRows());

        this.response.appendInt(this.data.available().size());
        for (LeaderboardDefinition definition : this.data.available()) {
            this.response.appendInt(definition.id());
            this.response.appendString(definition.gameType());
            this.response.appendString(definition.metric());
            this.response.appendString(definition.titleLocalizationCode());
            this.response.appendString(definition.descriptionLocalizationCode());
            this.response.appendString(definition.menuLocalizationCode());
        }

        this.response.appendInt(this.data.rows().size());
        for (LeaderboardRow row : this.data.rows()) this.serializeRow(row);

        this.response.appendBoolean(this.data.ownRow() != null);
        if (this.data.ownRow() != null) this.serializeRow(this.data.ownRow());
        return this.response;
    }

    private void serializeDefinition(LeaderboardDefinition definition) {
        this.response.appendString(definition.gameType());
        this.response.appendString(definition.metric());
        this.response.appendString(definition.titleLocalizationCode());
        this.response.appendString(definition.descriptionLocalizationCode());
        this.response.appendString(definition.menuLocalizationCode());
    }

    private void serializeRow(LeaderboardRow row) {
        this.response.appendInt(row.userId());
        this.response.appendInt(row.rank());
        this.response.appendString(row.username());
        this.response.appendString(row.figure());
        this.response.appendString(row.gender());
        this.response.appendInt(row.value());
        this.response.appendBoolean(row.own());
    }
}

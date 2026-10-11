package com.eu.habbo.messages.outgoing.habbicons;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.messenger.Message;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class HabbiconInstantMessageComposer extends MessageComposer {
    private final int chatId;
    private final Message message;
    private final Habbo sender;

    public HabbiconInstantMessageComposer(int chatId, Message message, Habbo sender) {
        this.chatId = chatId;
        this.message = message;
        this.sender = sender;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.HabbiconInstantMessageComposer);
        this.response.appendInt(this.chatId);
        this.response.appendInt(this.message.getMessageType());
        if (this.message.getMessageType() == Message.TYPE_HABBICON) {
            this.response.appendInt(this.message.getHabbiconId());
        } else {
            this.response.appendString(this.message.getMessage());
        }
        this.response.appendInt(Emulator.getIntUnixTimestamp() - this.message.getTimestamp());
        this.response.appendString(this.message.getMessageId());
        this.response.appendInt(this.message.getConfirmationId());
        this.response.appendInt(this.sender.getHabboInfo().getId());
        this.response.appendString(this.sender.getHabboInfo().getUsername());
        this.response.appendString(this.sender.getHabboInfo().getLook());
        return this.response;
    }
}

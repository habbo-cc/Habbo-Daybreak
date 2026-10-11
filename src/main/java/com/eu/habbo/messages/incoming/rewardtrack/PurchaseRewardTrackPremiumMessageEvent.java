package com.eu.habbo.messages.incoming.rewardtrack;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.incoming.MessageHandler;

public class PurchaseRewardTrackPremiumMessageEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() {
        String trackId = this.packet.readString();
        if (this.client.getHabbo() == null || Emulator.getGameEnvironment().getRewardTrackManager() == null) {
            return;
        }

        Emulator.getGameEnvironment().getRewardTrackManager().purchasePremium(this.client.getHabbo(), trackId);
    }
}

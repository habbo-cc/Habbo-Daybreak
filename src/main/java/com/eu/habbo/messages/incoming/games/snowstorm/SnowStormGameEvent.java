package com.eu.habbo.messages.incoming.games.snowstorm;

import com.eu.habbo.habbohotel.games.snowstorm.SnowStormIntention;
import com.eu.habbo.habbohotel.games.snowstorm.SnowStormProtocol;
import com.eu.habbo.habbohotel.games.snowstorm.SnowStormTrajectory;
import com.eu.habbo.messages.MalformedPacketException;

public final class SnowStormGameEvent extends SnowStormHandler {
    @Override public void handle() {
        int instanceId = requiredPositiveInt();
        int clientSequence = requiredNonNegativeInt();
        int subtype = requiredByte(0, 4);
        SnowStormIntention intention;
        try {
            intention = switch (subtype) {
                case 0 -> new SnowStormIntention.Walk(
                        packet.readRequiredInt(), packet.readRequiredInt());
                case 1 -> readThrowAvatar();
                case 2 -> readThrowLocation();
                case 3 -> new SnowStormIntention.MakeSnowball();
                case 4 -> new SnowStormIntention.CancelMakeSnowball();
                default -> throw new MalformedPacketException("unknown SnowStorm action subtype");
            };
        } catch (IllegalArgumentException exception) {
            throw new MalformedPacketException("invalid SnowStorm action");
        }
        requireEnd();
        if (habbo() != null) failure(SnowStormProtocol.OP_GAME_EVENT, instanceId,
                manager().action(habbo(), instanceId, clientSequence, intention));
    }

    private SnowStormIntention.ThrowAvatar readThrowAvatar() {
        int targetObjectId = requiredPositiveInt();
        SnowStormTrajectory trajectory = SnowStormTrajectory.fromId(requiredByte(0, 2));
        return new SnowStormIntention.ThrowAvatar(trajectory, targetObjectId);
    }

    private SnowStormIntention.ThrowLocation readThrowLocation() {
        int targetWorldX = packet.readRequiredInt();
        int targetWorldY = packet.readRequiredInt();
        SnowStormTrajectory trajectory = SnowStormTrajectory.fromId(requiredByte(0, 2));
        return new SnowStormIntention.ThrowLocation(trajectory, targetWorldX, targetWorldY);
    }
}

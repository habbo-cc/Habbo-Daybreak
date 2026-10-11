package com.eu.habbo.habbohotel.games.snowstorm;

/** Immutable client intentions accepted by the authoritative simulation owner. */
public sealed interface SnowStormIntention
        permits SnowStormIntention.Walk, SnowStormIntention.ThrowLocation,
        SnowStormIntention.ThrowAvatar, SnowStormIntention.MakeSnowball,
        SnowStormIntention.CancelMakeSnowball {

    record Walk(int targetWorldX, int targetWorldY) implements SnowStormIntention {
    }

    record ThrowLocation(
            SnowStormTrajectory trajectory,
            int targetWorldX,
            int targetWorldY) implements SnowStormIntention {
        public ThrowLocation {
            if (trajectory == null) {
                throw new IllegalArgumentException("SnowStorm trajectory is required");
            }
        }
    }

    record ThrowAvatar(
            SnowStormTrajectory trajectory,
            int targetObjectId) implements SnowStormIntention {
        public ThrowAvatar {
            if (trajectory == null) {
                throw new IllegalArgumentException("SnowStorm trajectory is required");
            }
        }
    }

    record MakeSnowball() implements SnowStormIntention {
    }

    record CancelMakeSnowball() implements SnowStormIntention {
    }
}

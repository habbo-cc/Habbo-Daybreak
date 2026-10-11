package com.eu.habbo.messages.incoming.handshake;

import com.eu.habbo.messages.incoming.MessageHandler;

/**
 * Client latency telemetry: average ping, cleared average and sample count, every twenty seconds.
 *
 * Live Habbo fed this to its own monitoring. Nothing here consumes it, but the client sends it
 * regardless of configuration, so it is accepted and discarded rather than left to be logged as an
 * unknown message on every report.
 */
public class LatencyPingReportMessageEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        // Read so the buffer is consumed in full, then discarded.
        this.packet.readInt(); // average ping
        this.packet.readInt(); // average, outliers removed
        this.packet.readInt(); // samples in this report
    }
}

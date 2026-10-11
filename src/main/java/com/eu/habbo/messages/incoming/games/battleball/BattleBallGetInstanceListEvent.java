package com.eu.habbo.messages.incoming.games.battleball;

public final class BattleBallGetInstanceListEvent extends BattleBallHandler {
    @Override public int getRatelimit() { return 1_000; }
    @Override public void handle() { this.requireEnd(); if (this.habbo() != null) this.manager().sendLounge(this.habbo()); }
}

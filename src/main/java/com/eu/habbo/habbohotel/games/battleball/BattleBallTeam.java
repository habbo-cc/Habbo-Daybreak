package com.eu.habbo.habbohotel.games.battleball;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BattleBallTeam {
    private final int id;
    private final int capacity;
    private final Map<Integer, BattleBallPlayer> players = new LinkedHashMap<>();

    BattleBallTeam(int id, int capacity) {
        this.id = id;
        this.capacity = capacity;
    }

    public int id() { return this.id; }
    public int capacity() { return this.capacity; }
    public Collection<BattleBallPlayer> players() { return this.players.values(); }
    public boolean full() { return this.players.size() >= this.capacity; }

    boolean add(BattleBallPlayer player) {
        if (this.full() || this.players.containsKey(player.userId())) return false;
        this.players.put(player.userId(), player);
        return true;
    }

    BattleBallPlayer remove(int userId) {
        return this.players.remove(userId);
    }
}

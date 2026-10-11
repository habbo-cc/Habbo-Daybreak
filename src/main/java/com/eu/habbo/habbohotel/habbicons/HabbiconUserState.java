package com.eu.habbo.habbohotel.habbicons;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HabbiconUserState {
    private final Map<Integer, Boolean> owned;
    private final List<Integer> recents;

    public HabbiconUserState() {
        this.owned = new LinkedHashMap<>();
        this.recents = new ArrayList<>();
    }

    public void addOwned(int habbiconId, boolean favorite) {
        this.owned.put(habbiconId, favorite);
    }

    public boolean isOwned(int habbiconId) {
        return this.owned.containsKey(habbiconId);
    }

    public boolean isFavorite(int habbiconId) {
        return this.owned.getOrDefault(habbiconId, false);
    }

    public Map<Integer, Boolean> getOwned() {
        return Collections.unmodifiableMap(this.owned);
    }

    public void addRecent(int habbiconId) {
        this.recents.add(habbiconId);
    }

    public List<Integer> getRecents() {
        return Collections.unmodifiableList(this.recents);
    }
}

package com.eu.habbo.habbohotel.wired.api;

import com.eu.habbo.habbohotel.wired.WiredAddonType;

/** Marker contract for Wired 2.0 add-on furniture definitions. */
public interface IWiredAddon {
    WiredAddonType getType();
}

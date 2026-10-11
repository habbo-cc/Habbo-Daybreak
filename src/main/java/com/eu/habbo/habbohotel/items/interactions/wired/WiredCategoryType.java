package com.eu.habbo.habbohotel.items.interactions.wired;

/**
 * Wired 2.0 element categories. Drives the type-specific field block in
 * {@code InteractionWired.readSettingsV2} (delay for effects, quantifierCode
 * for conditions, isFilter/isInvert for selectors).
 */
public enum WiredCategoryType {
    TRIGGER,
    EFFECT,
    CONDITION,
    SELECTOR,
    ADDON,
    VARIABLE
}

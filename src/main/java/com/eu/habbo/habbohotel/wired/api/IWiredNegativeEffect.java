package com.eu.habbo.habbohotel.wired.api;

/**
 * Marker for an effect that executes only when its stack's conditions fail.
 *
 * <p>This is semantic rather than protocol-specific: the engine must not infer
 * negative behavior from a donor/Nitro type number.</p>
 */
public interface IWiredNegativeEffect extends IWiredEffect {
}

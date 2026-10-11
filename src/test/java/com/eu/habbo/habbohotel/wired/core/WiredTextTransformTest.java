package com.eu.habbo.habbohotel.wired.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WiredTextTransformTest {
    @Test void unregisteredSeamPreservesLegacyTextByteForByte() {
        String text = "legacy %username%\t\u00a3\u20ac";
        assertSame(text, WiredTextTransform.apply(null, text));
        assertNull(WiredTextTransform.apply(null, null));
        assertTrue(WiredTextTransform.MAX_INPUT_LENGTH >= text.length());
        assertTrue(WiredTextTransform.MAX_OUTPUT_LENGTH >= text.length());
    }
}

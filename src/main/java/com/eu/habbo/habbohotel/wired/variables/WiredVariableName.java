package com.eu.habbo.habbohotel.wired.variables;

import java.util.Locale;

/** Canonical storage name for user-created Wired variables. */
public final class WiredVariableName {
    public static final int MAX_LENGTH = 40;

    private WiredVariableName() {
    }

    public static String normalize(String input) {
        if (input == null) {
            return "";
        }
        // Exact July VariableNameSection behavior: literal spaces become
        // underscores one-for-one, then the complete string is lower-cased.
        return input.replace(' ', '_').toLowerCase(Locale.ROOT);
    }

    public static boolean isValid(String name) {
        if (name == null || name.isEmpty() || name.length() > MAX_LENGTH) {
            return false;
        }

        for (int index = 0; index < name.length(); index++) {
            char character = name.charAt(index);
            if (character == '\0' || Character.isISOControl(character)) {
                return false;
            }
        }
        return true;
    }
}

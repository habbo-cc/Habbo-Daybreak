package com.eu.habbo.habbohotel.items.interactions.wired.addons;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredAddon;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettingsV2;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.WiredAddonType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * July Wired 2.0 add-on 1001: level state derived from a variable's XP value.
 *
 * <p>This class owns the persisted editor state and deterministic level
 * calculation. {@code WiredGeneratedVariableRuntime} exposes its derived
 * subvariables through the room variable manager.</p>
 */
public final class WiredAddonVariableLevelUp extends InteractionWiredAddon {
    public static final int MODE_MANUAL = 0;
    public static final int MODE_LINEAR = 1;
    public static final int MODE_EXPONENTIAL = 2;

    private static final int PERSISTENCE_VERSION = 1;
    private static final int DEFAULT_STEP_SIZE = 100;
    private static final int DEFAULT_BASE_XP = 100;
    private static final int DEFAULT_INCREASE_FACTOR = 20;
    private static final int DEFAULT_MAX_LEVEL = 50;
    private static final int MAX_LEVEL = 100000;
    private static final int MAX_VALUE = 100000;
    private static final int MAX_MANUAL_CHARACTERS = 1000;
    private static final int MAX_MANUAL_LINES = 1000;
    private static final int SUBVARIABLE_MASK = 0xFF;

    private LevelConfiguration configuration = LevelConfiguration.linear(0, DEFAULT_STEP_SIZE, DEFAULT_MAX_LEVEL);

    public WiredAddonVariableLevelUp(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredAddonVariableLevelUp(int id, int userId, Item item, String extradata,
                                     int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredAddonType getType() {
        return WiredAddonType.VARIABLE_LEVEL_UP;
    }

    /**
     * The July client sends one of exactly three payloads:
     * manual {@code [mask, 0]} plus the interpolation text; linear
     * {@code [mask, 1, stepSize, maxLevel]}; exponential
     * {@code [mask, 2, baseXp, increaseFactor, maxLevel]}.
     */
    @Override
    public synchronized boolean saveData(WiredSettingsV2 settings) {
        if (!ownsOnly(settings)) {
            return false;
        }

        int[] params = settings.getIntParams();
        try {
            LevelConfiguration next;
            switch (params.length >= 2 ? params[1] : -1) {
                case MODE_MANUAL:
                    if (params.length != 2) {
                        return false;
                    }
                    next = LevelConfiguration.manual(params[0], settings.getStringParam());
                    break;
                case MODE_LINEAR:
                    if (params.length != 4 || !settings.getStringParam().isEmpty()) {
                        return false;
                    }
                    next = LevelConfiguration.linear(params[0], params[2], params[3]);
                    break;
                case MODE_EXPONENTIAL:
                    if (params.length != 5 || !settings.getStringParam().isEmpty()) {
                        return false;
                    }
                    next = LevelConfiguration.exponential(params[0], params[2], params[3], params[4]);
                    break;
                default:
                    return false;
            }
            this.configuration = next;
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    @Override
    public synchronized String getWiredData() {
        return WiredManager.getGson().toJson(PersistedData.from(configuration));
    }

    @Override
    public synchronized void loadWiredData(ResultSet set, Room room) throws SQLException {
        onPickUp();
        if (set != null) {
            loadPersistedData(set.getString("wired_data"));
        }
    }

    /**
     * Strict, side-effect-free persistence decoder used by the database loader.
     * Invalid, unversioned, or out-of-range data leaves the current configuration
     * untouched so a corrupt row falls back to the defaults installed by load.
     */
    boolean loadPersistedData(String raw) {
        if (raw == null || !raw.startsWith("{")) {
            return false;
        }
        try {
            PersistedData data = WiredManager.getGson().fromJson(raw, PersistedData.class);
            if (data == null || data.v != PERSISTENCE_VERSION || !hasBoundedPersistedFields(data)) {
                return false;
            }
            LevelConfiguration next;
            switch (data.mode) {
                case MODE_MANUAL:
                    next = LevelConfiguration.manual(data.subvariableMask, data.manualText);
                    break;
                case MODE_LINEAR:
                    next = LevelConfiguration.linear(data.subvariableMask, data.stepSize, data.maxLevel);
                    break;
                case MODE_EXPONENTIAL:
                    next = LevelConfiguration.exponential(data.subvariableMask, data.baseXp,
                            data.increaseFactor, data.maxLevel);
                    break;
                default:
                    return false;
            }
            this.configuration = next;
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    @Override
    public synchronized void onPickUp() {
        this.configuration = LevelConfiguration.linear(0, DEFAULT_STEP_SIZE, DEFAULT_MAX_LEVEL);
    }

    @Override
    protected synchronized int[] getWiredIntParams() {
        return configuration.toClientIntParams();
    }

    @Override
    protected synchronized String getWiredStringParam() {
        return configuration.mode == MODE_MANUAL ? configuration.manualText : "";
    }

    @Override
    protected int getMaxFurniSelection() {
        return 0;
    }

    /** Returns the immutable configuration currently shown by the editor. */
    public synchronized LevelConfiguration configuration() {
        return configuration;
    }

    /** Pure calculation entry point, intentionally independent of rooms and variables. */
    public synchronized LevelState calculate(long rawXp) {
        return calculate(configuration, rawXp);
    }

    public synchronized Map<String, Long> derive(long rawXp) {
        LevelState state = calculate(rawXp);
        String[] names = {"current_level", "current_xp", "progress", "progress_percentage",
                "xp_required", "xp_remaining", "is_maxed", "max_level"};
        long[] values = {state.currentLevel(), state.currentXp(), state.progress(),
                state.progressPercentage(), state.xpRequired(), state.xpRemaining(),
                state.isMaxed() ? 1L : 0L, state.maxLevel()};
        LinkedHashMap<String, Long> result = new LinkedHashMap<>();
        int mask = this.configuration.subvariableMask();
        for (int index = 0; index < names.length; index++)
            if ((mask & (1 << index)) != 0) result.put(names[index], values[index]);
        return Collections.unmodifiableMap(result);
    }

    public synchronized java.util.List<String> enabledNames() {
        return java.util.List.copyOf(derive(0L).keySet());
    }

    /**
     * Pure level-state calculation. Integer XP thresholds follow July's editor
     * preview; the runtime's existing capped-value convention is preserved.
     */
    public static LevelState calculate(LevelConfiguration configuration, long rawXp) {
        if (configuration == null) {
            throw new IllegalArgumentException("configuration is required");
        }
        long xp = Math.max(0L, rawXp);
        int effectiveMaxLevel = configuration.effectiveMaxLevel();
        long capXp = configuration.maxXp(effectiveMaxLevel);
        boolean maxed = xp >= capXp;
        long currentXp = maxed ? capXp : xp;
        int currentLevel = configuration.levelForXp(currentXp, effectiveMaxLevel);
        long required = configuration.xpRequiredForLevel(currentLevel);
        long nextRequired = configuration.nextRequirement(currentLevel, effectiveMaxLevel);
        long stepRequired = configuration.stepRequirement(currentLevel, required, nextRequired);
        long progress = maxed ? 0L : Math.max(0L, currentXp - required);
        long remaining = maxed ? 0L : Math.max(0L, nextRequired - currentXp);
        long percentage = maxed ? 100L
                : (nextRequired > required ? Math.round((progress * 100D) / (nextRequired - required)) : 100L);
        return new LevelState(currentLevel, currentXp, progress, percentage, stepRequired, remaining,
                maxed, effectiveMaxLevel);
    }

    private static boolean ownsOnly(WiredSettingsV2 settings) {
        return settings != null
                && settings.getIntParams() != null
                && settings.getStringParam() != null
                && settings.getFurniIds().length == 0
                && settings.getFurniIds2().length == 0
                && settings.getVariableIds().length == 0
                && settings.getFurniSourceTypes().length == 0
                && settings.getUserSourceTypes().length == 0;
    }

    public static final class LevelConfiguration {
        private final int subvariableMask;
        private final int mode;
        private final int stepSize;
        private final int baseXp;
        private final int increaseFactor;
        private final int maxLevel;
        private final String manualText;
        private final Map<Integer, Long> manualRequirements;

        private LevelConfiguration(int subvariableMask, int mode, int stepSize, int baseXp,
                                   int increaseFactor, int maxLevel, String manualText,
                                   Map<Integer, Long> manualRequirements) {
            this.subvariableMask = subvariableMask;
            this.mode = mode;
            this.stepSize = stepSize;
            this.baseXp = baseXp;
            this.increaseFactor = increaseFactor;
            this.maxLevel = maxLevel;
            this.manualText = manualText;
            this.manualRequirements = manualRequirements;
        }

        public static LevelConfiguration manual(int subvariableMask, String interpolation) {
            validateMask(subvariableMask);
            Map<Integer, Long> requirements = parseManualText(interpolation);
            return new LevelConfiguration(normalizeMask(subvariableMask), MODE_MANUAL, DEFAULT_STEP_SIZE,
                    DEFAULT_BASE_XP, DEFAULT_INCREASE_FACTOR, DEFAULT_MAX_LEVEL,
                    normalizeManualText(interpolation), requirements);
        }

        public static LevelConfiguration linear(int subvariableMask, int stepSize, int maxLevel) {
            validateMask(subvariableMask);
            validateRange(stepSize, 1, MAX_VALUE, "stepSize");
            validateRange(maxLevel, 2, MAX_LEVEL, "maxLevel");
            return new LevelConfiguration(subvariableMask, MODE_LINEAR, stepSize, DEFAULT_BASE_XP,
                    DEFAULT_INCREASE_FACTOR, maxLevel, "", Collections.emptyMap());
        }

        public static LevelConfiguration exponential(int subvariableMask, int baseXp,
                                                      int increaseFactor, int maxLevel) {
            validateMask(subvariableMask);
            validateRange(baseXp, 1, MAX_VALUE, "baseXp");
            validateRange(increaseFactor, 1, MAX_VALUE, "increaseFactor");
            validateRange(maxLevel, 2, MAX_LEVEL, "maxLevel");
            return new LevelConfiguration(subvariableMask, MODE_EXPONENTIAL, DEFAULT_STEP_SIZE, baseXp,
                    increaseFactor, maxLevel, "", Collections.emptyMap());
        }

        public int subvariableMask() { return subvariableMask; }
        public int mode() { return mode; }
        public int stepSize() { return stepSize; }
        public int baseXp() { return baseXp; }
        public int increaseFactor() { return increaseFactor; }
        public int maxLevel() { return maxLevel; }
        public String manualText() { return manualText; }
        public Map<Integer, Long> manualRequirements() { return manualRequirements; }

        private int[] toClientIntParams() {
            return switch (mode) {
                case MODE_MANUAL -> new int[] {subvariableMask, MODE_MANUAL};
                case MODE_LINEAR -> new int[] {subvariableMask, MODE_LINEAR, stepSize, maxLevel};
                case MODE_EXPONENTIAL -> new int[] {subvariableMask, MODE_EXPONENTIAL, baseXp, increaseFactor, maxLevel};
                default -> throw new IllegalStateException("Unknown level-up mode");
            };
        }

        private int effectiveMaxLevel() {
            if (mode != MODE_MANUAL) {
                return maxLevel;
            }
            int last = 1;
            for (Integer level : manualRequirements.keySet()) {
                last = level;
            }
            return last;
        }

        private int levelForXp(long xp, int effectiveMaxLevel) {
            int lower = 1;
            int upper = effectiveMaxLevel;
            while (lower < upper) {
                int middle = lower + (upper - lower + 1) / 2;
                if (xpRequiredForLevel(middle) <= xp) {
                    lower = middle;
                } else {
                    upper = middle - 1;
                }
            }
            return lower;
        }

        private long nextRequirement(int currentLevel, int effectiveMaxLevel) {
            if (mode == MODE_MANUAL) {
                return xpRequiredForLevel(Math.min(effectiveMaxLevel, currentLevel + 1));
            }
            return xpRequiredForLevel(Math.min(effectiveMaxLevel + 1, currentLevel + 1));
        }

        private long maxXp(int effectiveMaxLevel) {
            if (mode == MODE_MANUAL) {
                return manualRequirements.isEmpty() ? 0L : manualRequirements.get(effectiveMaxLevel);
            }
            return xpRequiredForLevel(effectiveMaxLevel + 1);
        }

        private long xpRequiredForLevel(int level) {
            int normalizedLevel = Math.max(1, level);
            if (mode == MODE_MANUAL) {
                int previousLevel = 1;
                long previousXp = 0L;
                for (Map.Entry<Integer, Long> entry : manualRequirements.entrySet()) {
                    if (entry.getKey() == normalizedLevel) return entry.getValue();
                    if (entry.getKey() > normalizedLevel) {
                        int levels = entry.getKey() - previousLevel;
                        // Interpolate between anchors, including the implicit
                        // level-one/zero-XP anchor used by July's preview.
                        return previousXp + (long) (((double) (entry.getValue() - previousXp)
                                / levels) * (normalizedLevel - previousLevel));
                    }
                    previousLevel = entry.getKey();
                    previousXp = entry.getValue();
                }
                return previousXp;
            }
            if (mode == MODE_EXPONENTIAL) {
                double strength = increaseFactor / 100D;
                double total = baseXp * ((Math.pow(1D + strength, normalizedLevel - 1)
                        - 1D + 1e-9) / strength);
                return total >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.round(total);
            }
            return saturatingMultiply((long) normalizedLevel - 1L, stepSize);
        }

        private long stepRequirement(int currentLevel, long required, long nextRequired) {
            if (mode == MODE_LINEAR) {
                return stepSize;
            }
            if (mode == MODE_EXPONENTIAL) {
                return Math.max(0L, nextRequired - required);
            }
            return manualRequirements.isEmpty() ? 0L : Math.max(0L, nextRequired - required);
        }
    }

    public static final class LevelState {
        private final int currentLevel;
        private final long currentXp;
        private final long progress;
        private final long progressPercentage;
        private final long xpRequired;
        private final long xpRemaining;
        private final boolean maxed;
        private final int maxLevel;

        private LevelState(int currentLevel, long currentXp, long progress, long progressPercentage,
                           long xpRequired, long xpRemaining, boolean maxed, int maxLevel) {
            this.currentLevel = currentLevel;
            this.currentXp = currentXp;
            this.progress = progress;
            this.progressPercentage = progressPercentage;
            this.xpRequired = xpRequired;
            this.xpRemaining = xpRemaining;
            this.maxed = maxed;
            this.maxLevel = maxLevel;
        }

        public int currentLevel() { return currentLevel; }
        public long currentXp() { return currentXp; }
        public long progress() { return progress; }
        public long progressPercentage() { return progressPercentage; }
        public long xpRequired() { return xpRequired; }
        public long xpRemaining() { return xpRemaining; }
        public boolean isMaxed() { return maxed; }
        public int maxLevel() { return maxLevel; }
    }

    private static Map<Integer, Long> parseManualText(String value) {
        String normalized = normalizeManualText(value);
        if (normalized.isEmpty()) {
            return Collections.emptyMap();
        }
        String[] lines = normalized.split("\\n", -1);
        if (lines.length > MAX_MANUAL_LINES) {
            throw new IllegalArgumentException("manual table has too many lines");
        }
        TreeMap<Integer, Long> parsed = new TreeMap<>();
        int previousLevel = 1;
        long previousXp = 0L;
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            int separator = line.indexOf('=');
            if (separator <= 0 || separator == line.length() - 1 || separator != line.lastIndexOf('=')) {
                throw new IllegalArgumentException("manual lines must use level=xp_required");
            }
            try {
                int level = Integer.parseInt(line.substring(0, separator).trim());
                long xp = Long.parseLong(line.substring(separator + 1).trim());
                validateRange(level, 2, MAX_LEVEL, "manual level");
                if (level <= previousLevel || xp <= previousXp || xp > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("manual anchors must increase in level and integer xp");
                }
                parsed.put(level, xp);
                previousLevel = level;
                previousXp = xp;
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("manual values must be integers", exception);
            }
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(parsed));
    }

    private static String normalizeManualText(String value) {
        String normalized = value == null ? "" : value.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (normalized.length() > MAX_MANUAL_CHARACTERS) {
            throw new IllegalArgumentException("manual table is too long");
        }
        return normalized;
    }

    private static void validateMask(int mask) {
        if (mask < 0 || mask != normalizeMask(mask)) {
            throw new IllegalArgumentException("subvariable mask is out of range");
        }
    }

    private static int normalizeMask(int mask) {
        return mask & SUBVARIABLE_MASK;
    }

    private static void validateRange(int value, int minimum, int maximum, String name) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " is out of range");
        }
    }

    private static boolean hasBoundedPersistedFields(PersistedData data) {
        return data.manualText != null
                && data.subvariableMask >= 0 && data.subvariableMask <= SUBVARIABLE_MASK
                && data.stepSize >= 1 && data.stepSize <= MAX_VALUE
                && data.baseXp >= 1 && data.baseXp <= MAX_VALUE
                && data.increaseFactor >= 1 && data.increaseFactor <= MAX_VALUE
                && data.maxLevel >= 2 && data.maxLevel <= MAX_LEVEL;
    }

    private static long saturatingAdd(long left, long right) {
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }

    private static long saturatingMultiply(long left, long right) {
        if (left <= 0L || right <= 0L) {
            return 0L;
        }
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }

    private static final class PersistedData {
        int v;
        int subvariableMask;
        int mode;
        int stepSize;
        int baseXp;
        int increaseFactor;
        int maxLevel;
        String manualText;

        private PersistedData() {
        }

        private static PersistedData from(LevelConfiguration configuration) {
            PersistedData data = new PersistedData();
            data.v = PERSISTENCE_VERSION;
            data.subvariableMask = configuration.subvariableMask;
            data.mode = configuration.mode;
            data.stepSize = configuration.stepSize;
            data.baseXp = configuration.baseXp;
            data.increaseFactor = configuration.increaseFactor;
            data.maxLevel = configuration.maxLevel;
            data.manualText = configuration.manualText;
            return data;
        }
    }
}

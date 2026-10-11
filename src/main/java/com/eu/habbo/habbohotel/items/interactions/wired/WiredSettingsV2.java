package com.eu.habbo.habbohotel.items.interactions.wired;

/**
 * Wired 2.0 settings DTO — the expanded save payload read from the new client
 * composers using the July field order.
 *
 * Superset of the legacy {@link WiredSettings}. Optional fields default to empty/zero
 * so existing wired types that only read intParams/stringParam/furniIds keep
 * working unchanged. The legacy {@code stuffTypeSelectionCode} is intentionally
 * absent — it was removed from the Wired 2.0 wire format.
 */
public class WiredSettingsV2 {
    private static final int[] EMPTY_INT = new int[0];
    private static final String[] EMPTY_STR = new String[0];

    private int[] intParams;
    private String stringParam;
    private int[] furniIds;
    private int[] furniIds2;          // stuffIds2 — second furni-pick slot
    private String[] variableIds;     // variable references (strings, not ints)
    private int[] furniSourceTypes;
    private int[] userSourceTypes;
    private int delay;                // effects only
    private int quantifierCode;       // conditions only
    private boolean isFilter;         // selectors only
    private boolean isInvert;         // selectors only

    public WiredSettingsV2(int[] intParams, String stringParam, int[] furniIds, int[] furniIds2,
                            String[] variableIds, int[] furniSourceTypes, int[] userSourceTypes,
                            int delay, int quantifierCode, boolean isFilter, boolean isInvert) {
        this.intParams = intParams != null ? intParams : EMPTY_INT;
        this.stringParam = stringParam != null ? stringParam : "";
        this.furniIds = furniIds != null ? furniIds : EMPTY_INT;
        this.furniIds2 = furniIds2 != null ? furniIds2 : EMPTY_INT;
        this.variableIds = variableIds != null ? variableIds : EMPTY_STR;
        this.furniSourceTypes = furniSourceTypes != null ? furniSourceTypes : EMPTY_INT;
        this.userSourceTypes = userSourceTypes != null ? userSourceTypes : EMPTY_INT;
        this.delay = delay;
        this.quantifierCode = quantifierCode;
        this.isFilter = isFilter;
        this.isInvert = isInvert;
    }

    /**
     * Bridge to the legacy {@link WiredSettings} so wired classes not yet migrated
     * to the new DTO keep working. stuffTypeSelectionCode is gone in 2.0 -> 0.
     */
    public WiredSettings toLegacy() {
        WiredSettings settings = new WiredSettings(this.intParams, this.stringParam, this.furniIds, this.furniIds2, this.variableIds,
                this.furniSourceTypes, this.userSourceTypes, 0, this.delay);
        settings.setQuantifierCode(this.quantifierCode);
        return settings;
    }

    public int[] getIntParams() { return intParams; }
    public String getStringParam() { return stringParam; }
    public int[] getFurniIds() { return furniIds; }
    public int[] getFurniIds2() { return furniIds2; }
    public String[] getVariableIds() { return variableIds; }
    public int[] getFurniSourceTypes() { return furniSourceTypes; }
    public int[] getUserSourceTypes() { return userSourceTypes; }
    public int getDelay() { return delay; }
    public int getQuantifierCode() { return quantifierCode; }
    public boolean isFilter() { return isFilter; }
    public boolean isInvert() { return isInvert; }

    public void setIntParams(int[] intParams) { this.intParams = intParams != null ? intParams : EMPTY_INT; }
    public void setStringParam(String stringParam) { this.stringParam = stringParam != null ? stringParam : ""; }
    public void setFurniIds(int[] furniIds) { this.furniIds = furniIds != null ? furniIds : EMPTY_INT; }
    public void setDelay(int delay) { this.delay = delay; }
}

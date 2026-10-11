package com.eu.habbo.habbohotel.items.interactions.wired;

public class WiredSettings {
    private int[] intParams;
    private String stringParam;
    private int[] furniIds;
    private int[] furniIds2;
    private String[] variableIds;
    private int[] furniSourceTypes;
    private int[] userSourceTypes;
    private int stuffTypeSelectionCode;
    private int delay;
    private int quantifierCode;

    public WiredSettings(int[] intParams, String stringParam, int[] furniIds, int stuffTypeSelectionCode, int delay)
    {
        this.furniIds = furniIds;
        this.furniIds2 = new int[0];
        this.variableIds = new String[0];
        this.furniSourceTypes = new int[0];
        this.userSourceTypes = new int[0];
        this.intParams = intParams;
        this.stringParam = stringParam;
        this.stuffTypeSelectionCode = stuffTypeSelectionCode;
        this.delay = delay;
    }

    public WiredSettings(int[] intParams, String stringParam, int[] furniIds, int[] furniIds2, String[] variableIds,
                         int[] furniSourceTypes, int[] userSourceTypes, int stuffTypeSelectionCode, int delay)
    {
        this(intParams, stringParam, furniIds, stuffTypeSelectionCode, delay);
        this.furniIds2 = furniIds2 != null ? furniIds2 : new int[0];
        this.variableIds = variableIds != null ? variableIds : new String[0];
        this.furniSourceTypes = furniSourceTypes != null ? furniSourceTypes : new int[0];
        this.userSourceTypes = userSourceTypes != null ? userSourceTypes : new int[0];
    }

    public WiredSettings(int[] intParams, String stringParam, int[] furniIds, int stuffTypeSelectionCode)
    {
        this(intParams, stringParam, furniIds, stuffTypeSelectionCode, 0);
    }

    public int getStuffTypeSelectionCode() {
        return stuffTypeSelectionCode;
    }

    public void setStuffTypeSelectionCode(int stuffTypeSelectionCode) {
        this.stuffTypeSelectionCode = stuffTypeSelectionCode;
    }

    public int[] getFurniIds() {
        return furniIds;
    }

    public int[] getFurniIds2() {
        return furniIds2;
    }

    public String[] getVariableIds() {
        return variableIds;
    }

    public int[] getFurniSourceTypes() {
        return furniSourceTypes;
    }

    public int[] getUserSourceTypes() {
        return userSourceTypes;
    }

    public void setFurniIds(int[] furniIds) {
        this.furniIds = furniIds;
    }

    public String getStringParam() {
        return stringParam;
    }

    public void setStringParam(String stringParam) {
        this.stringParam = stringParam;
    }

    public int[] getIntParams() {
        return intParams;
    }

    public void setIntParams(int[] intParams) {
        this.intParams = intParams;
    }

    public int getDelay() {
        return delay;
    }

    public int getQuantifierCode() {
        return quantifierCode;
    }

    public void setQuantifierCode(int quantifierCode) {
        this.quantifierCode = quantifierCode;
    }

    public void setDelay(int delay) {
        this.delay = delay;
    }
}

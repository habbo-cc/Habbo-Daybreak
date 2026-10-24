package com.eu.habbo.habbohotel.wired.variablefx;

/** Local server visibility policy. Unassigned teams and missing audience variables fail closed. */
public final class VariableFxAudience {
    private VariableFxAudience() {}
    public static boolean allows(int audience,boolean user,int subjectId,int viewerId,int subjectTeam,int viewerTeam,
                                 boolean sameGame,Long audienceValue,long requiredValue) {
        return switch(audience) {
            case 0 -> user && subjectId==viewerId;
            case 1 -> user && sameGame && subjectTeam>0 && subjectTeam==viewerTeam;
            case 2 -> true;
            case 3 -> audienceValue!=null;
            case 4 -> audienceValue!=null && audienceValue==requiredValue;
            default -> false;
        };
    }
}

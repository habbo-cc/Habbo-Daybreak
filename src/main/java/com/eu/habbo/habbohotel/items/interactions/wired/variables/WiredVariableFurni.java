package com.eu.habbo.habbohotel.items.interactions.wired.variables;
import com.eu.habbo.habbohotel.items.Item; import com.eu.habbo.habbohotel.wired.WiredVariableType;
import java.sql.ResultSet; import java.sql.SQLException;
/** July code 0 ({@code wf_var_furni}). */
public final class WiredVariableFurni extends WiredVariableScoped {
 public WiredVariableFurni(ResultSet s, Item i)throws SQLException{super(s,i);} public WiredVariableFurni(int id,int u,Item i,String e,int l,int n){super(id,u,i,e,l,n);}
 @Override public WiredVariableType getType(){return WiredVariableType.FURNI;} @Override protected boolean isFurni(){return true;} @Override protected int contextBlockType(){return 1;}
 @Override protected int[] encodeParams(boolean h,int a){return new int[]{h?1:0,a};} @Override protected ParsedParams decodeParams(int[] p){return p.length==2&&(p[0]==0||p[0]==1)&&(p[1]==1||p[1]==10)?new ParsedParams(p[0]!=0,p[1]):null;}
}

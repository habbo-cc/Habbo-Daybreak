package com.eu.habbo.habbohotel.items.interactions.wired.variables;
import com.eu.habbo.habbohotel.items.Item; import com.eu.habbo.habbohotel.wired.WiredVariableType;
import java.sql.ResultSet; import java.sql.SQLException;
/** July code 1 ({@code wf_var_user}). */
public final class WiredVariableUser extends WiredVariableScoped {
 public WiredVariableUser(ResultSet s, Item i)throws SQLException{super(s,i);} public WiredVariableUser(int id,int u,Item i,String e,int l,int n){super(id,u,i,e,l,n);}
 @Override public WiredVariableType getType(){return WiredVariableType.USER;} @Override protected boolean isFurni(){return false;} @Override protected int contextBlockType(){return 2;}
 @Override protected int[] encodeParams(boolean h,int a){return new int[]{a,h?1:0};} @Override protected ParsedParams decodeParams(int[] p){return p.length==2&&(p[1]==0||p[1]==1)&&(p[0]==0||p[0]==10||p[0]==11)?new ParsedParams(p[1]!=0,p[0]):null;}
}

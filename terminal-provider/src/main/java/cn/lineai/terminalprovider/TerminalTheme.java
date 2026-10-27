package cn.lineai.terminalprovider;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.TextView;
/** Companion app tokens, kept API 24 compatible independently of the API 26 chat UI. */
final class TerminalTheme {
    static int BG, INPUT_BG, TEXT, TEXT_SECONDARY, ACCENT, TEXT_ON_COLOR;
    static void apply(boolean dark) {
        BG=Color.parseColor(dark?"#171819":"#FCFCFD");
        INPUT_BG=Color.parseColor(dark?"#242629":"#F0F1F3");
        TEXT=Color.parseColor(dark?"#EDF0F2":"#24262A");
        TEXT_SECONDARY=Color.parseColor(dark?"#969DA5":"#6C737D");
        ACCENT=Color.parseColor(dark?"#E5E9EE":"#333B46");
        TEXT_ON_COLOR=Color.parseColor(dark?"#24262A":"#FFFFFF");
    }
    static int dp(Context context,int size){return Math.round(context.getResources().getDisplayMetrics().density*size);}
    static void padding(View view,int l,int t,int r,int b){Context c=view.getContext();view.setPadding(dp(c,l),dp(c,t),dp(c,r),dp(c,b));}
    static GradientDrawable rounded(Context context,int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(context,radius));return d;}
    static TextView text(Context context,String value,int size,int color,int style){TextView v=new TextView(context);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setTypeface(Typeface.DEFAULT,style);v.setIncludeFontPadding(false);return v;}
    static TextView textMedium(Context context,String value,int size,int color){TextView v=text(context,value,size,color,Typeface.NORMAL);v.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return v;}
}

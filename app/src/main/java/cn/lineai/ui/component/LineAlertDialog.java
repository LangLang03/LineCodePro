package cn.lineai.ui.component;
import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.widget.TextView;
import cn.lineai.ui.theme.LineTheme;
public final class LineAlertDialog {
    private LineAlertDialog() { }
    public static final class Builder extends AlertDialog.Builder {
        public Builder(Context context) {
            super(context, android.graphics.Color.red(LineTheme.BG)>128
                    ? android.R.style.Theme_Material_Light_Dialog_Alert : android.R.style.Theme_Material_Dialog_Alert);
        }
        @Override public AlertDialog create() {
            AlertDialog dialog=super.create();
            if(dialog.getWindow()!=null) {
                dialog.getWindow().setBackgroundDrawable(LineTheme.rounded(getContext(),LineTheme.BG,24));
                View decor=dialog.getWindow().getDecorView();
                decor.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                    @Override public void onViewAttachedToWindow(View v) {
                        TextView message=dialog.findViewById(android.R.id.message);
                        if(message!=null){message.setTextColor(LineTheme.TEXT);message.setTextSize(16);message.setLineSpacing(LineTheme.dp(getContext(),6),1);}
                        for(int id:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL}) {
                            android.widget.Button button=dialog.getButton(id);
                            if(button!=null){button.setTextColor(LineTheme.TEXT);button.setTextSize(14);button.setAllCaps(false);button.setMinHeight(LineTheme.dp(getContext(),48));}
                        }
                        dialog.getWindow().setLayout(DialogDimensions.insetDialogWidth(getContext()),-2);
                        v.removeOnAttachStateChangeListener(this);
                    }
                    @Override public void onViewDetachedFromWindow(View v) { }
                });
            }
            return dialog;
        }
    }
}

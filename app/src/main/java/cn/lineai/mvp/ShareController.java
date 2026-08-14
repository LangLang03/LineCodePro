package cn.lineai.mvp;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import cn.lineai.R;
import cn.lineai.model.ChatMessage;
import cn.lineai.share.ExportFormat;
import cn.lineai.share.ExportFormatResolver;
import cn.lineai.share.ExportResult;
import cn.lineai.share.ShareHelper;
import cn.lineai.share.format.ClipboardFormat;
import java.util.List;

public final class ShareController {

    public interface OnShareListener {
        void onShareFile(ExportResult result);
        void onCopyToClipboard(String text);
        void onShareText(String text);
    }

    private final ExportFormatResolver resolver;
    private OnShareListener listener;

    public ShareController(ExportFormatResolver resolver) {
        this.resolver = resolver;
    }

    public void setListener(OnShareListener listener) {
        this.listener = listener;
    }

    public void showFormatPicker(Context context, List<ChatMessage> selected) {
        String[] names = resolver.getDisplayNames();
        new AlertDialog.Builder(context)
                .setTitle(R.string.dialog_export_format_title)
                .setItems(names, (dialog, which) -> {
                    ExportFormat format = resolver.get(which);
                    new Thread(() -> {
                        ExportResult result = format.execute(context, selected);
                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (result != null) {
                                handleResult(context, result, format);
                            }
                        });
                    }, "linecode-share-export").start();
                })
                .show();
    }

    private void handleResult(Context context, ExportResult result, ExportFormat format) {
        switch (result.getAction()) {
            case ExportResult.ACTION_SHARE_FILE:
                ShareHelper.shareFile(context, result.getFile(), result.getMimeType());
                if (listener != null) listener.onShareFile(result);
                break;
            case ExportResult.ACTION_CLIPBOARD:
                String text = result.getContent();
                if (format instanceof ClipboardFormat) {
                    ClipboardFormat clipFormat = (ClipboardFormat) format;
                    if (clipFormat.shouldWarn(text)) {
                        Toast.makeText(context, R.string.toast_clipboard_large, Toast.LENGTH_SHORT).show();
                    }
                }
                ShareHelper.copy(context, text);
                if (listener != null) listener.onCopyToClipboard(text);
                break;
            case ExportResult.ACTION_SHARE_TEXT:
                ShareHelper.shareText(context, result.getContent());
                if (listener != null) listener.onShareText(result.getContent());
                break;
        }
    }
}

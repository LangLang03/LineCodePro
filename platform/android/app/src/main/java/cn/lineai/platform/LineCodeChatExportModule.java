package cn.lineai.platform;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;

import org.huxerui.HuxerUIPlatformChannel;
import org.huxerui.HuxerUIPlatformModule;
import org.huxerui.PlatformPayload;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Native Android delivery and rendering adapter for the C++ chat-export registry. */
public final class LineCodeChatExportModule implements HuxerUIPlatformModule.Factory {
    @Override
    public HuxerUIPlatformModule create(
            Context context,
            PlatformPayload options,
            HuxerUIPlatformChannel.Events events) {
        options.requireNull();
        return new Module(context.getApplicationContext());
    }

    private interface MethodHandler {
        void invoke(PlatformPayload arguments) throws IOException;
    }

    private interface TranscriptRenderer {
        File render(PlatformPayload arguments) throws IOException;
    }

    private static final class Module implements HuxerUIPlatformModule {
        private static final HuxerUIPlatformChannel.Cancellation NO_CANCELLATION =
                () -> { };

        private final Context context;
        private final Map<String, MethodHandler> methods = new LinkedHashMap<>();
        private final Map<String, TranscriptRenderer> renderers = new LinkedHashMap<>();

        Module(Context context) {
            this.context = context;
            renderers.put("pdf", this::renderPdf);
            renderers.put("chat-image", this::renderChatImage);
            methods.put("copyText", this::copyText);
            methods.put("shareText", this::shareText);
            methods.put("shareFile", this::shareFile);
            methods.put("renderAndShare", this::renderAndShare);
        }

        @Override
        public HuxerUIPlatformChannel.Cancellation invoke(
                String method,
                PlatformPayload arguments,
                HuxerUIPlatformChannel.Result result) {
            MethodHandler handler = methods.get(method);
            if (handler == null) {
                result.fail(
                        "linecode/chat-export/not-implemented",
                        "Unknown chat-export method: " + method,
                        PlatformPayload.nullValue());
                return NO_CANCELLATION;
            }
            try {
                handler.invoke(arguments);
                result.complete(PlatformPayload.nullValue());
            } catch (RuntimeException | IOException error) {
                String message = error.getMessage();
                result.fail(
                        "linecode/chat-export/android-error",
                        message == null ? error.getClass().getSimpleName() : message,
                        PlatformPayload.nullValue());
            }
            return NO_CANCELLATION;
        }

        @Override
        public void dispose() {
            methods.clear();
            renderers.clear();
        }

        private void copyText(PlatformPayload arguments) {
            String text = arguments.requireString();
            ClipboardManager clipboard =
                    (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) {
                throw new IllegalStateException("Android clipboard is unavailable");
            }
            clipboard.setPrimaryClip(ClipData.newPlainText("chat", text));
        }

        private void shareText(PlatformPayload arguments) {
            String text = arguments.requireString();
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT, text);
            launchChooser(send, "Share text");
        }

        private void shareFile(PlatformPayload arguments) throws IOException {
            String fileName = safeFileName(
                    arguments.requireField("fileName").requireString());
            String mimeType = arguments.requireField("mimeType").requireString();
            byte[] content = arguments.requireField("content").requireBytes();
            File file = writeBytes(fileName, content);
            launchFileChooser(file, mimeType);
        }

        private void renderAndShare(PlatformPayload arguments) throws IOException {
            String rendererId = arguments.requireField("rendererId").requireString();
            TranscriptRenderer renderer = renderers.get(rendererId);
            if (renderer == null) {
                throw new IllegalArgumentException(
                        "Unknown chat transcript renderer: " + rendererId);
            }
            File file = renderer.render(arguments);
            launchFileChooser(
                    file, arguments.requireField("mimeType").requireString());
        }

        private File renderPdf(PlatformPayload arguments) throws IOException {
            final int pageWidth = 595;
            final int pageHeight = 842;
            final int margin = 40;
            final int titleSize = 16;
            final int bodySize = 12;
            final int lineSpacing = 6;

            Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            titlePaint.setTextSize(titleSize);
            titlePaint.setFakeBoldText(true);
            titlePaint.setColor(0xFF333333);
            Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            bodyPaint.setTextSize(bodySize);
            bodyPaint.setColor(0xFF222222);

            PdfDocument document = new PdfDocument();
            try {
                int pageNumber = 1;
                PdfDocument.Page page = startPage(document, pageNumber++, pageWidth, pageHeight);
                int y = margin + titleSize;
                int contentWidth = pageWidth - 2 * margin;
                for (PlatformPayload block : arguments.requireField("blocks").elements()) {
                    String content = block.requireField("content").requireString();
                    if (content.isEmpty()) {
                        continue;
                    }
                    if (y + titleSize > pageHeight - margin) {
                        document.finishPage(page);
                        page = startPage(document, pageNumber++, pageWidth, pageHeight);
                        y = margin + titleSize;
                    }
                    Canvas canvas = page.getCanvas();
                    canvas.drawText(
                            block.requireField("speaker").requireString(),
                            margin, y, titlePaint);
                    y += titleSize + lineSpacing / 2;

                    int start = 0;
                    while (start < content.length()) {
                        int end = start + 1;
                        while (end < content.length()
                                && bodyPaint.measureText(content.substring(start, end))
                                < contentWidth) {
                            end++;
                        }
                        if (end < content.length()) {
                            end--;
                            if (end <= start) {
                                end = start + 1;
                            }
                        }
                        boolean newlineFound = false;
                        int newline = content.indexOf('\n', start);
                        if (newline >= start && newline < end) {
                            end = newline;
                            newlineFound = true;
                        }
                        String line = content.substring(start, end);
                        if (!line.isEmpty()) {
                            if (y + bodySize > pageHeight - margin) {
                                document.finishPage(page);
                                page = startPage(
                                        document, pageNumber++, pageWidth, pageHeight);
                                y = margin + bodySize;
                            }
                            canvas = page.getCanvas();
                            canvas.drawText(line, margin, y, bodyPaint);
                            y += bodySize + lineSpacing;
                        }
                        start = newlineFound ? end + 1 : end;
                    }
                    y += lineSpacing;
                }
                document.finishPage(page);
                File file = exportFile(arguments);
                try (FileOutputStream output = new FileOutputStream(file)) {
                    document.writeTo(output);
                }
                return file;
            } finally {
                document.close();
            }
        }

        private File renderChatImage(PlatformPayload arguments) throws IOException {
            final int imageWidth = 720;
            final int padding = 32;
            final int fontSize = 28;
            final int bubbleRadius = 24;
            final int spacing = 16;
            final int nameSize = 24;

            Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setTextSize(fontSize);
            textPaint.setColor(Color.WHITE);
            List<ImageBlock> blocks = new ArrayList<>();
            int maximumWidth = imageWidth - 2 * padding;
            int contentMaximumWidth = maximumWidth - 2 * padding;
            int totalHeight = padding;
            for (PlatformPayload value : arguments.requireField("blocks").elements()) {
                String content = value.requireField("content").requireString();
                if (content.isEmpty()) {
                    continue;
                }
                List<String> lines = wrapText(content, textPaint, contentMaximumWidth);
                int blockHeight =
                        nameSize + spacing + lines.size() * (fontSize + 4) + padding;
                blocks.add(new ImageBlock(
                        value.requireField("speaker").requireString(),
                        value.requireField("user").requireBoolean(),
                        lines,
                        blockHeight));
                totalHeight += blockHeight + spacing;
            }
            totalHeight += padding;

            Bitmap bitmap = Bitmap.createBitmap(
                    imageWidth, totalHeight, Bitmap.Config.ARGB_8888);
            try {
                Canvas canvas = new Canvas(bitmap);
                canvas.drawColor(0xFF1E1E2E);
                Paint namePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                namePaint.setTextSize(nameSize);
                namePaint.setColor(0xFFAAAAAA);
                Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                int y = padding;
                for (ImageBlock block : blocks) {
                    canvas.drawText(block.speaker, padding, y + nameSize, namePaint);
                    y += nameSize + spacing / 2;
                    RectF bubble = new RectF(
                            padding,
                            y,
                            padding + maximumWidth,
                            y + block.height - nameSize - spacing / 2);
                    bubblePaint.setColor(block.user ? 0xFF3B3B5C : 0xFF2A2A3E);
                    canvas.drawRoundRect(
                            bubble, bubbleRadius, bubbleRadius, bubblePaint);
                    int textY = y + padding + fontSize;
                    for (String line : block.lines) {
                        canvas.drawText(line, padding + padding / 2, textY, textPaint);
                        textY += fontSize + 4;
                    }
                    y += block.height - nameSize - spacing / 2 + spacing;
                }
                File file = exportFile(arguments);
                try (FileOutputStream output = new FileOutputStream(file)) {
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                        throw new IOException("Failed to encode chat screenshot");
                    }
                }
                return file;
            } finally {
                bitmap.recycle();
            }
        }

        private File exportFile(PlatformPayload arguments) throws IOException {
            File directory = exportDirectory();
            return canonicalChild(
                    directory,
                    safeFileName(arguments.requireField("fileName").requireString()));
        }

        private File writeBytes(String fileName, byte[] content) throws IOException {
            File file = canonicalChild(exportDirectory(), fileName);
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write(content);
            }
            return file;
        }

        private File exportDirectory() throws IOException {
            File directory = new File(context.getCacheDir(), "chat_exports");
            if (!directory.isDirectory() && !directory.mkdirs()) {
                throw new IOException("Failed to create chat export directory");
            }
            return directory.getCanonicalFile();
        }

        private static File canonicalChild(File directory, String name)
                throws IOException {
            File file = new File(directory, name).getCanonicalFile();
            String prefix = directory.getCanonicalPath() + File.separator;
            if (!file.getPath().startsWith(prefix)) {
                throw new IOException("Invalid chat export file name");
            }
            return file;
        }

        private static String safeFileName(String value) {
            if (value.isEmpty()
                    || value.contains("/")
                    || value.contains("\\")
                    || ".".equals(value)
                    || "..".equals(value)) {
                throw new IllegalArgumentException("Invalid chat export file name");
            }
            return value;
        }

        private void launchFileChooser(File file, String mimeType) {
            Uri uri = LineCodeChatExportProvider.uriFor(context, file);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType(mimeType);
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.setClipData(ClipData.newRawUri("", uri));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            launchChooser(send, "Share file");
        }

        private void launchChooser(Intent send, String title) {
            Intent chooser = Intent.createChooser(send, title);
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);
        }

        private static PdfDocument.Page startPage(
                PdfDocument document,
                int pageNumber,
                int width,
                int height) {
            PdfDocument.PageInfo info =
                    new PdfDocument.PageInfo.Builder(width, height, pageNumber).create();
            return document.startPage(info);
        }

        private static List<String> wrapText(String text, Paint paint, int maximumWidth) {
            StringBuilder wrapped = new StringBuilder();
            String[] paragraphs = text.split("\n", -1);
            for (int index = 0; index < paragraphs.length; index++) {
                if (index > 0) {
                    wrapped.append('\n');
                }
                String paragraph = paragraphs[index];
                if (paragraph.isEmpty()) {
                    continue;
                }
                int start = 0;
                while (start < paragraph.length()) {
                    int lastBreak = start;
                    for (int end = start + 1; end <= paragraph.length(); end++) {
                        if (paint.measureText(paragraph.substring(start, end))
                                > maximumWidth) {
                            break;
                        }
                        lastBreak = end;
                    }
                    if (lastBreak <= start) {
                        lastBreak = start + 1;
                    }
                    if (wrapped.length() > 0
                            && wrapped.charAt(wrapped.length() - 1) != '\n') {
                        wrapped.append('\n');
                    }
                    wrapped.append(paragraph, start, lastBreak);
                    start = lastBreak;
                    while (start < paragraph.length()
                            && Character.isWhitespace(paragraph.charAt(start))) {
                        start++;
                    }
                }
            }
            List<String> lines = new ArrayList<>();
            for (String line : wrapped.toString().split("\n")) {
                if (!line.isEmpty()) {
                    lines.add(line);
                }
            }
            return lines;
        }

        private static final class ImageBlock {
            final String speaker;
            final boolean user;
            final List<String> lines;
            final int height;

            ImageBlock(String speaker, boolean user, List<String> lines, int height) {
                this.speaker = speaker;
                this.user = user;
                this.lines = lines;
                this.height = height;
            }
        }
    }
}

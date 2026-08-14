package cn.lineai.share.format;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.text.TextPaint;
import cn.lineai.model.ChatMessage;
import java.util.List;

public final class PdfRenderer {
    private static final int PAGE_WIDTH = 595;   // A4 in points
    private static final int PAGE_HEIGHT = 842;
    private static final int MARGIN = 40;
    private static final int TITLE_SIZE = 16;
    private static final int BODY_SIZE = 12;
    private static final int LINE_SPACING = 6;

    private final int pageWidth;
    private final int pageHeight;
    private final int margin;

    public PdfRenderer() {
        this(PAGE_WIDTH, PAGE_HEIGHT, MARGIN);
    }

    public PdfRenderer(int pageWidth, int pageHeight, int margin) {
        this.pageWidth = pageWidth;
        this.pageHeight = pageHeight;
        this.margin = margin;
    }

    public void render(PdfDocument doc, List<ChatMessage> messages) {
        Paint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setTextSize(TITLE_SIZE);
        titlePaint.setFakeBoldText(true);
        titlePaint.setColor(0xFF333333);

        Paint bodyPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        bodyPaint.setTextSize(BODY_SIZE);
        bodyPaint.setColor(0xFF222222);

        int contentWidth = pageWidth - 2 * margin;
        PdfDocument.Page page = startNewPage(doc);
        int y = margin + TITLE_SIZE;

        for (ChatMessage msg : messages) {
            if (msg.getContent() == null || msg.getContent().isEmpty()) continue;

            String label = msg.getRole() == ChatMessage.Role.USER ? "Me" : "AI";

            // Draw title
            if (y + TITLE_SIZE > pageHeight - margin) {
                doc.finishPage(page);
                page = startNewPage(doc);
                y = margin + TITLE_SIZE;
            }
            Canvas canvas = page.getCanvas();
            canvas.drawText(label, margin, y, titlePaint);
            y += TITLE_SIZE + LINE_SPACING / 2;

            // Draw body with word wrap
            String content = msg.getContent();
            int maxWidth = contentWidth;
            int start = 0;
            while (start < content.length()) {
                int end = start + 1;
                while (end < content.length() && bodyPaint.measureText(content.substring(start, end)) < maxWidth) {
                    end++;
                }
                if (end >= content.length()) {
                    // last segment
                } else {
                    end--; // back up one char
                    if (end <= start) end = start + 1;
                }

                // Handle newlines
                boolean newlineFound = false;
                int newlineIdx = content.indexOf('\n', start);
                if (newlineIdx >= start && newlineIdx < end) {
                    end = newlineIdx;
                    newlineFound = true;
                }

                String line = content.substring(start, end);
                if (!line.isEmpty()) {
                    if (y + BODY_SIZE > pageHeight - margin) {
                        doc.finishPage(page);
                        page = startNewPage(doc);
                        y = margin + BODY_SIZE;
                    }
                    canvas = page.getCanvas();
                    canvas.drawText(line, margin, y, bodyPaint);
                    y += BODY_SIZE + LINE_SPACING;
                }
                // 若本段是因为换行符中断，则跳过换行符；否则退出的 end 是首个放不下的字符，
                // 必须作为下一行起点重新处理，避免丢字符。
                start = newlineFound ? end + 1 : end;
            }
            y += LINE_SPACING;
        }
        doc.finishPage(page);
    }

    private PdfDocument.Page startNewPage(PdfDocument doc) {
        PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create();
        return doc.startPage(info);
    }
}

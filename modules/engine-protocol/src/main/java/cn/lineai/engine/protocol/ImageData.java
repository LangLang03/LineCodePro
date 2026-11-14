package cn.lineai.engine.protocol;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

import cn.lineai.core.model.ContentBlock;
import cn.lineai.core.model.Message;

/** Reads image blocks from disk and encodes them for wire requests. */
public final class ImageData {

    private ImageData() {
    }

    /**
     * Collects the file paths of image blocks in a message.
     *
     * @param message message to scan
     * @return paths of every image block that carries a reference, in message order
     */
    public static List<String> paths(Message message) {
        List<String> result = new ArrayList<>();
        for (ContentBlock block : message.blocks) {
            if (block.type == ContentBlock.Type.IMAGE && !block.refId.isEmpty()) {
                result.add(block.refId);
            }
        }
        return result;
    }

    /** Returns {@code true} when the message contains at least one image block with a reference. */
    public static boolean hasImages(Message message) {
        return !paths(message).isEmpty();
    }

    /**
     * Encodes a file as a Base64 data URL.
     *
     * @param path file path to encode
     * @return data URL for the file, or an empty string when the file is missing or unreadable
     */
    public static String dataUrl(String path) {
        try {
            byte[] bytes = Files.readAllBytes(new File(path).toPath());
            return "data:" + mediaType(path) + ";base64,"
                    + Base64.getEncoder().encodeToString(bytes);
        } catch (IOException error) {
            return "";
        }
    }

    /**
     * Infers the image media type from the file extension.
     *
     * @param path file path; case-insensitive suffix decides the type
     * @return IANA media type; JPEG is the fallback so unknown extensions still produce a valid
     *         image part
     */
    public static String mediaType(String path) {
        String lower = path == null ? "" : path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".bmp")) return "image/bmp";
        return "image/jpeg";
    }

    /**
     * Reads and Base64-encodes a file without a data URL wrapper.
     *
     * @param path file path to encode
     * @return Base64-encoded contents, or an empty string when the file is missing or unreadable
     */
    public static String base64(String path) {
        try {
            return Base64.getEncoder().encodeToString(
                    Files.readAllBytes(new File(path).toPath()));
        } catch (IOException error) {
            return "";
        }
    }
}

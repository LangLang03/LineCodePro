package cn.lineai.tool.builtin;

import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolResult;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

public final class HttpServerTool extends BaseTool {
    private static SimpleFileServer activeServer;

    @Override
    public String getName() {
        return "http_server";
    }

    @Override
    public String getDescription() {
        return "启动或停止本地 HTTP 文件服务器。可以指定端口和工作区内根目录。";
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.SYSTEM;
    }

    @Override
    public JSONObject getParameters() throws org.json.JSONException {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("action", new JSONObject().put("type", "string").put("enum", new org.json.JSONArray().put("start").put("stop")))
                        .put("port", new JSONObject().put("type", "number").put("description", "端口号，默认 0 自动分配"))
                        .put("root", new JSONObject().put("type", "string").put("description", "服务器根目录，相对当前工作区")))
                .put("required", new org.json.JSONArray().put("action"));
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        String action = input.optString("action", "start");
        if ("stop".equals(action)) {
            return stop();
        }
        return start(input.optInt("port", 0), input.optString("root"), context);
    }

    public static synchronized void stopActiveServer() {
        if (activeServer != null) {
            activeServer.stop();
            activeServer = null;
        }
    }

    private synchronized ToolResult start(int port, String rootPath, ToolContext context) {
        try {
            if (activeServer != null && activeServer.isRunning()) {
                return ok("服务器已在运行: http://127.0.0.1:" + activeServer.getPort()
                        + "\n根目录: " + activeServer.getDisplayRoot());
            }
            File root = FileToolPathPolicy.resolve(context.getHomePath(), rootPath);
            if (!root.exists()) {
                return error("HTTP 服务器根目录不存在: " + FileToolPathPolicy.displayPath(context.getHomePath(), root));
            }
            if (!root.isDirectory()) {
                return error("HTTP 服务器根目录不是目录: " + FileToolPathPolicy.displayPath(context.getHomePath(), root));
            }
            SimpleFileServer server = new SimpleFileServer(root, port);
            server.start();
            activeServer = server;
            return ok("HTTP 服务器已启动\n端口: " + server.getPort()
                    + "\n根目录: " + FileToolPathPolicy.displayPath(context.getHomePath(), root)
                    + "\n访问: http://127.0.0.1:" + server.getPort());
        } catch (Exception e) {
            return error("启动服务器失败: " + e.getMessage());
        }
    }

    private synchronized ToolResult stop() {
        if (activeServer == null || !activeServer.isRunning()) {
            return error("服务器未在运行");
        }
        int port = activeServer.getPort();
        activeServer.stop();
        activeServer = null;
        return ok("服务器已停止 (端口 " + port + ")");
    }

    private ToolResult ok(String content) {
        return new ToolResult("", getName(), content, false);
    }

    private ToolResult error(String content) {
        return new ToolResult("", getName(), content, true);
    }

    private static final class SimpleFileServer implements Runnable {
        private final File root;
        private final ServerSocket serverSocket;
        private volatile boolean running = true;
        private Thread thread;

        SimpleFileServer(File root, int port) throws Exception {
            this.root = root.getCanonicalFile();
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), Math.max(0, port)));
        }

        void start() {
            thread = new Thread(this, "linecode-http-server");
            thread.setDaemon(true);
            thread.start();
        }

        @Override
        public void run() {
            while (running) {
                try {
                    Socket socket = serverSocket.accept();
                    new Thread(() -> handle(socket), "linecode-http-client").start();
                } catch (Exception ignored) {
                    if (running) {
                        running = false;
                    }
                }
            }
        }

        int getPort() {
            return serverSocket.getLocalPort();
        }

        boolean isRunning() {
            return running && !serverSocket.isClosed();
        }

        String getDisplayRoot() {
            return root.getPath();
        }

        void stop() {
            running = false;
            try {
                serverSocket.close();
            } catch (Exception ignored) {
            }
        }

        private void handle(Socket socket) {
            try {
                byte[] buffer = new byte[4096];
                int read = socket.getInputStream().read(buffer);
                if (read <= 0) {
                    return;
                }
                String request = new String(buffer, 0, read, StandardCharsets.UTF_8);
                String firstLine = request.split("\\r?\\n", 2)[0];
                String[] parts = firstLine.split(" ");
                if (parts.length < 2 || !"GET".equals(parts[0])) {
                    writeText(socket.getOutputStream(), 405, "Only GET is supported");
                    return;
                }
                String path = parts[1];
                int query = path.indexOf('?');
                if (query >= 0) {
                    path = path.substring(0, query);
                }
                path = URLDecoder.decode(path, "UTF-8");
                File target = new File(root, path.startsWith("/") ? path.substring(1) : path).getCanonicalFile();
                if (!inside(root, target)) {
                    writeText(socket.getOutputStream(), 403, "Forbidden");
                    return;
                }
                if (!target.exists()) {
                    writeText(socket.getOutputStream(), 404, "Not Found");
                    return;
                }
                if (target.isDirectory()) {
                    writeText(socket.getOutputStream(), 200, listing(target, path), "text/html; charset=utf-8");
                    return;
                }
                writeFile(socket.getOutputStream(), target);
            } catch (Exception ignored) {
            } finally {
                try {
                    socket.close();
                } catch (Exception ignored) {
                }
            }
        }

        private boolean inside(File root, File target) {
            String rootPath = root.getPath();
            String targetPath = target.getPath();
            return targetPath.equals(rootPath) || targetPath.startsWith(rootPath + File.separator);
        }

        private String listing(File dir, String path) {
            StringBuilder builder = new StringBuilder();
            builder.append("<!doctype html><meta charset=\"utf-8\"><title>LineCode</title><h3>")
                    .append(escape(path.length() == 0 ? "/" : path))
                    .append("</h3><ul>");
            File[] items = dir.listFiles();
            if (items != null) {
                for (File item : items) {
                    String name = item.getName() + (item.isDirectory() ? "/" : "");
                    String href = (path.endsWith("/") ? path : path + "/") + name;
                    builder.append("<li><a href=\"").append(escape(href)).append("\">")
                            .append(escape(name)).append("</a></li>");
                }
            }
            builder.append("</ul>");
            return builder.toString();
        }

        private String escape(String value) {
            return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
        }

        private void writeFile(OutputStream output, File file) throws Exception {
            output.write(("HTTP/1.1 200 OK\r\nContent-Type: " + mime(file.getName())
                    + "\r\nContent-Length: " + file.length() + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            FileInputStream input = new FileInputStream(file);
            try {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
            } finally {
                input.close();
            }
        }

        private void writeText(OutputStream output, int status, String text) throws Exception {
            writeText(output, status, text, "text/plain; charset=utf-8");
        }

        private void writeText(OutputStream output, int status, String text, String contentType) throws Exception {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            output.write(("HTTP/1.1 " + status + " OK\r\nContent-Type: " + contentType
                    + "\r\nContent-Length: " + bytes.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            output.write(bytes);
        }

        private String mime(String name) {
            String lower = name.toLowerCase(java.util.Locale.US);
            if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html; charset=utf-8";
            if (lower.endsWith(".css")) return "text/css; charset=utf-8";
            if (lower.endsWith(".js")) return "application/javascript; charset=utf-8";
            if (lower.endsWith(".json")) return "application/json; charset=utf-8";
            if (lower.endsWith(".png")) return "image/png";
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
            if (lower.endsWith(".gif")) return "image/gif";
            if (lower.endsWith(".svg")) return "image/svg+xml";
            return "application/octet-stream";
        }
    }
}

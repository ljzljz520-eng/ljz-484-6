package com.researchnotes;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 两个处理器：
 * ApiHandler    —— /api/** JSON 接口，公开接口只读公开数据；/api/admin/** 为研究员维护接口
 * StaticHandler —— web/ 目录下的前端页面与静态资源，禁止路径穿越
 */
public final class HttpHandlers {

    private HttpHandlers() {
    }

    static final class ApiHandler implements HttpHandler {
        private final DataStore store;

        ApiHandler(DataStore store) {
            this.store = store;
        }

        @Override
        public void handle(HttpExchange ex) throws IOException {
            try {
                route(ex);
            } catch (HttpError he) {
                sendJson(ex, he.status, errorBody(he.getMessage()));
            } catch (IllegalArgumentException iae) {
                sendJson(ex, 400, errorBody(iae.getMessage() == null ? "请求参数有误" : iae.getMessage()));
            } catch (IllegalStateException ise) {
                // 例如：删除仍挂有笔记的课题
                sendJson(ex, 400, errorBody(ise.getMessage()));
            } catch (Exception e) {
                e.printStackTrace();
                sendJson(ex, 500, errorBody("服务器内部错误: " + e.getMessage()));
            }
        }

        private void route(HttpExchange ex) throws IOException {
            String method = ex.getRequestMethod();
            String path = ex.getRequestURI().getPath();

            // ---------- 公开接口 ----------
            if ("GET".equals(method) && "/api/catalog".equals(path)) {
                sendJson(ex, 200, store.publicCatalog());
                return;
            }
            if ("GET".equals(method) && path.startsWith("/api/notes/")) {
                String id = decode(lastSegment(path));
                Map<String, Object> note = store.publicNote(id);
                if (note == null) {
                    throw new HttpError(404, "笔记不存在或未公开");
                }
                sendJson(ex, 200, note);
                return;
            }
            if ("GET".equals(method) && "/api/health".equals(path)) {
                Map<String, Object> ok = new LinkedHashMap<String, Object>();
                ok.put("status", "ok");
                sendJson(ex, 200, ok);
                return;
            }

            // ---------- 研究员维护接口 ----------
            if (path.startsWith("/api/admin/")) {
                handleAdmin(ex, method, path);
                return;
            }
            throw new HttpError(404, "接口不存在");
        }

        private void handleAdmin(HttpExchange ex, String method, String path) throws IOException {
            String rest = path.substring("/api/admin/".length());
            String[] parts = rest.split("/");

            // /api/admin/topics
            if (parts.length == 1 && "topics".equals(parts[0])) {
                if ("GET".equals(method)) {
                    sendJson(ex, 200, store.adminTopics());
                } else if ("POST".equals(method)) {
                    sendJson(ex, 201, store.createTopic(readBody(ex)));
                } else {
                    throw new HttpError(405, "不支持的方法: " + method);
                }
                return;
            }
            // /api/admin/topics/{id}
            if (parts.length == 2 && "topics".equals(parts[0])) {
                String id = decode(parts[1]);
                if ("GET".equals(method)) {
                    Map<String, Object> t = store.adminTopic(id);
                    if (t == null) {
                        throw new HttpError(404, "课题不存在");
                    }
                    sendJson(ex, 200, t);
                } else if ("PUT".equals(method)) {
                    Map<String, Object> t = store.updateTopic(id, readBody(ex));
                    if (t == null) {
                        throw new HttpError(404, "课题不存在");
                    }
                    sendJson(ex, 200, t);
                } else if ("DELETE".equals(method)) {
                    if (!store.deleteTopic(id)) {
                        throw new HttpError(404, "课题不存在");
                    }
                    sendNoContent(ex);
                } else {
                    throw new HttpError(405, "不支持的方法: " + method);
                }
                return;
            }
            // /api/admin/notes
            if (parts.length == 1 && "notes".equals(parts[0])) {
                if ("GET".equals(method)) {
                    sendJson(ex, 200, store.adminNotes());
                } else if ("POST".equals(method)) {
                    sendJson(ex, 201, store.createNote(readBody(ex)));
                } else {
                    throw new HttpError(405, "不支持的方法: " + method);
                }
                return;
            }
            // /api/admin/notes/{id}
            if (parts.length == 2 && "notes".equals(parts[0])) {
                String id = decode(parts[1]);
                if ("GET".equals(method)) {
                    Map<String, Object> n = store.adminNote(id);
                    if (n == null) {
                        throw new HttpError(404, "笔记不存在");
                    }
                    sendJson(ex, 200, n);
                } else if ("PUT".equals(method)) {
                    Map<String, Object> n = store.updateNote(id, readBody(ex));
                    if (n == null) {
                        throw new HttpError(404, "笔记不存在");
                    }
                    sendJson(ex, 200, n);
                } else if ("DELETE".equals(method)) {
                    if (!store.deleteNote(id)) {
                        throw new HttpError(404, "笔记不存在");
                    }
                    sendNoContent(ex);
                } else {
                    throw new HttpError(405, "不支持的方法: " + method);
                }
                return;
            }
            throw new HttpError(404, "接口不存在");
        }

        private static Map<String, Object> errorBody(String message) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("error", message);
            return m;
        }

        private static String lastSegment(String path) {
            int idx = path.lastIndexOf('/');
            return path.substring(idx + 1);
        }

        private static String decode(String s) {
            try {
                return URLDecoder.decode(s, "UTF-8");
            } catch (java.io.UnsupportedEncodingException e) {
                throw new IllegalStateException(e);
            }
        }

        private static Map<String, Object> readBody(HttpExchange ex) throws IOException {
            InputStream in = ex.getRequestBody();
            try {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] chunk = new byte[4096];
                int n;
                while ((n = in.read(chunk)) != -1) {
                    buf.write(chunk, 0, n);
                    if (buf.size() > 5 * 1024 * 1024) {
                        throw new HttpError(413, "请求体过大（上限 5MB）");
                    }
                }
                String text = new String(buf.toByteArray(), StandardCharsets.UTF_8).trim();
                if (text.isEmpty()) {
                    return new LinkedHashMap<String, Object>();
                }
                Object parsed = Json.parse(text);
                if (!(parsed instanceof Map)) {
                    throw new HttpError(400, "请求体必须是 JSON 对象");
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) parsed;
                return m;
            } finally {
                in.close();
            }
        }

        static void sendJson(HttpExchange ex, int status, Object body) throws IOException {
            byte[] bytes = Json.stringify(body).getBytes(StandardCharsets.UTF_8);
            Headers h = ex.getResponseHeaders();
            h.set("Content-Type", "application/json; charset=utf-8");
            h.set("Cache-Control", "no-store");
            ex.sendResponseHeaders(status, bytes.length);
            OutputStream out = ex.getResponseBody();
            try {
                out.write(bytes);
            } finally {
                out.close();
            }
        }

        static void sendNoContent(HttpExchange ex) throws IOException {
            ex.sendResponseHeaders(204, -1);
            ex.close();
        }
    }

    static final class StaticHandler implements HttpHandler {
        private final Path root;

        StaticHandler(Path webDir) throws IOException {
            this.root = webDir.toAbsolutePath().normalize();
            if (!Files.isDirectory(this.root)) {
                throw new IOException("前端目录不存在: " + this.root);
            }
        }

        @Override
        public void handle(HttpExchange ex) throws IOException {
            try {
                if (!"GET".equals(ex.getRequestMethod()) && !"HEAD".equals(ex.getRequestMethod())) {
                    ex.sendResponseHeaders(405, -1);
                    ex.close();
                    return;
                }
                String raw = ex.getRequestURI().getPath();
                String rel = URLDecoder.decode(raw, StandardCharsets.UTF_8.name());
                if (rel.equals("/") || rel.isEmpty()) {
                    rel = "/index.html";
                }
                // /note/{id} 这类前端路由统一回到 reader 页面
                if (rel.startsWith("/note/")) {
                    rel = "/reader.html";
                }
                if (rel.startsWith("/admin")) {
                    rel = "/admin.html";
                }
                Path target = root.resolve(rel.substring(1)).normalize();
                // 防路径穿越：解析后的路径必须仍在 web 根目录内，且必须是普通文件
                if (!target.startsWith(root) || Files.isDirectory(target) || !Files.exists(target)) {
                    byte[] msg = "404 Not Found".getBytes(StandardCharsets.UTF_8);
                    ex.sendResponseHeaders(404, msg.length);
                    OutputStream out = ex.getResponseBody();
                    try {
                        out.write(msg);
                    } finally {
                        out.close();
                    }
                    return;
                }
                byte[] bytes = Files.readAllBytes(target);
                Headers h = ex.getResponseHeaders();
                h.set("Content-Type", contentType(target.getFileName().toString()));
                h.set("X-Content-Type-Options", "nosniff");
                h.set("Cache-Control", "no-cache");
                ex.sendResponseHeaders(200, bytes.length);
                if ("GET".equals(ex.getRequestMethod())) {
                    OutputStream out = ex.getResponseBody();
                    try {
                        out.write(bytes);
                    } finally {
                        out.close();
                    }
                } else {
                    ex.getResponseBody().close();
                }
            } catch (Exception e) {
                e.printStackTrace();
                ex.close();
            }
        }

        private static String contentType(String name) {
            String lower = name.toLowerCase();
            if (lower.endsWith(".html")) {
                return "text/html; charset=utf-8";
            }
            if (lower.endsWith(".css")) {
                return "text/css; charset=utf-8";
            }
            if (lower.endsWith(".js")) {
                return "application/javascript; charset=utf-8";
            }
            if (lower.endsWith(".json")) {
                return "application/json; charset=utf-8";
            }
            if (lower.endsWith(".svg")) {
                return "image/svg+xml";
            }
            if (lower.endsWith(".png")) {
                return "image/png";
            }
            if (lower.endsWith(".ico")) {
                return "image/x-icon";
            }
            return "application/octet-stream";
        }
    }

    static final class HttpError extends RuntimeException {
        final int status;

        HttpError(int status, String message) {
            super(message);
            this.status = status;
        }
    }
}

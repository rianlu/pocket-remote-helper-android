package com.pockettv.tv.net;

import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.pockettv.tv.protocol.Constants;
import com.pockettv.tv.protocol.CommandProcessor;
import com.pockettv.tv.store.Prefs;
import com.pockettv.tv.store.TransferStore;

/** 同一端口 17880：GET /ws 升级为 WebSocket，其余走 HTTP /transfer/*。 */
public final class WsHttpServer {
    private static final String TAG = "PocketTvNet";
    private static final String WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private final CommandProcessor commands;
    private final Prefs prefs;
    private final TransferStore store;
    private volatile boolean running;
    private ServerSocket server;
    private final ExecutorService pool = Executors.newCachedThreadPool();

    public WsHttpServer(CommandProcessor commands, Prefs prefs, TransferStore store) {
        this.commands = commands;
        this.prefs = prefs;
        this.store = store;
    }

    public void start() throws Exception {
        server = new ServerSocket(Constants.CONTROL_PORT);
        running = true;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                while (running) {
                    try {
                        final Socket sock = server.accept();
                        pool.execute(new Runnable() {
                            @Override
                            public void run() {
                                handleClient(sock);
                            }
                        });
                    } catch (Exception e) {
                        if (running) {
                            Log.w(TAG, "accept", e);
                        }
                    }
                }
            }
        }, "pockettv-listen");
        t.start();
        Log.i(TAG, "listen " + Constants.CONTROL_PORT);
    }

    public void stop() {
        running = false;
        try {
            if (server != null) {
                server.close();
            }
        } catch (Exception ignored) {
        }
        pool.shutdownNow();
    }

    private void handleClient(Socket sock) {
        try {
            sock.setSoTimeout(30000);
            InputStream in = sock.getInputStream();
            OutputStream out = sock.getOutputStream();
            ByteArrayOutputStream head = new ByteArrayOutputStream();
            while (true) {
                int b = in.read();
                if (b < 0) {
                    return;
                }
                head.write(b);
                if (head.size() > 16384) {
                    return;
                }
                byte[] bytes = head.toByteArray();
                int n = bytes.length;
                if (n >= 4
                        && bytes[n - 4] == '\r'
                        && bytes[n - 3] == '\n'
                        && bytes[n - 2] == '\r'
                        && bytes[n - 1] == '\n') {
                    break;
                }
            }
            String headerText = new String(head.toByteArray(), "ISO-8859-1").replace("\r", "");
            String[] lines = headerText.split("\n");
            if (lines.length == 0 || lines[0].length() == 0) {
                return;
            }
            String[] req = lines[0].split(" ");
            if (req.length < 2) {
                return;
            }
            String method = req[0];
            String path = req[1];
            Map<String, String> headers = new HashMap<String, String>();
            for (int i = 1; i < lines.length; i++) {
                int c = lines[i].indexOf(':');
                if (c > 0) {
                    headers.put(lines[i].substring(0, c).trim().toLowerCase(Locale.US),
                            lines[i].substring(c + 1).trim());
                }
            }
            String upgrade = headers.get("upgrade");
            if ("GET".equals(method) && path.startsWith(Constants.WS_PATH)
                    && upgrade != null && "websocket".equalsIgnoreCase(upgrade)) {
                serveWs(in, out, headers);
                return;
            }
            serveHttp(method, path, headers, in, out);
        } catch (Exception e) {
            Log.w(TAG, "client", e);
        } finally {
            try {
                sock.close();
            } catch (Exception ignored) {
            }
        }
    }

    private void serveWs(InputStream in, OutputStream out, Map<String, String> headers) throws Exception {
        String key = headers.get("sec-websocket-key");
        if (key == null) {
            return;
        }
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] accept = md.digest((key + WS_GUID).getBytes("ASCII"));
        String acc = Base64.encodeToString(accept, Base64.NO_WRAP);
        String resp = "HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + acc + "\r\n\r\n";
        out.write(resp.getBytes("ASCII"));
        out.flush();
        boolean authorized = false;
        while (true) {
            String text = readWsText(in);
            if (text == null) {
                return;
            }
            if (text.length() == 0) {
                continue;
            }
            String reply;
            try {
                org.json.JSONObject obj = new org.json.JSONObject(text);
                String type = obj.optString("type", "");
                boolean authed = authorized;
                if (Constants.TYPE_HELLO.equals(type)) {
                    org.json.JSONObject payload = obj.optJSONObject("payload");
                    if (payload != null && prefs.isTokenValid(payload.optString("token", ""))) {
                        authed = true;
                    }
                }
                reply = commands.handle(text, authed);
                if (Constants.TYPE_HELLO.equals(type) && reply != null && reply.indexOf(Constants.TYPE_HELLO_OK) >= 0) {
                    authorized = true;
                }
            } catch (Exception e) {
                Log.w(TAG, "ws msg", e);
                reply = CommandProcessor.error("", Constants.ERR_PROTOCOL, e.getMessage());
            }
            if (reply != null) {
                writeWsText(out, reply);
            }
        }
    }

    private String readWsText(InputStream in) throws Exception {
        int b0 = in.read();
        int b1 = in.read();
        if (b0 < 0 || b1 < 0) {
            return null;
        }
        int opcode = b0 & 0x0f;
        boolean masked = (b1 & 0x80) != 0;
        long len = b1 & 0x7f;
        if (len == 126) {
            int n = (in.read() << 8) | in.read();
            len = n & 0xffff;
        } else if (len == 127) {
            len = 0;
            for (int i = 0; i < 8; i++) {
                len = (len << 8) | (in.read() & 0xff);
            }
        }
        if (len < 0 || len > 1024 * 1024) {
            return null;
        }
        byte[] mask = new byte[4];
        if (masked) {
            if (in.read(mask) < 4) {
                return null;
            }
        }
        byte[] data = new byte[(int) len];
        int o = 0;
        while (o < data.length) {
            int n = in.read(data, o, data.length - o);
            if (n < 0) {
                return null;
            }
            o += n;
        }
        if (masked) {
            for (int i = 0; i < data.length; i++) {
                data[i] = (byte) (data[i] ^ mask[i & 3]);
            }
        }
        if (opcode == 8) {
            return null;
        }
        if (opcode == 9) {
            return "";
        }
        if (opcode != 1) {
            return "";
        }
        return new String(data, "UTF-8");
    }

    private void writeWsText(OutputStream out, String text) throws Exception {
        byte[] data = text.getBytes("UTF-8");
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.write(0x81);
        if (data.length < 126) {
            frame.write(data.length);
        } else if (data.length <= 65535) {
            frame.write(126);
            frame.write((data.length >> 8) & 0xff);
            frame.write(data.length & 0xff);
        } else {
            frame.write(127);
            for (int i = 7; i >= 0; i--) {
                frame.write((data.length >> (8 * i)) & 0xff);
            }
        }
        frame.write(data);
        out.write(frame.toByteArray());
        out.flush();
    }

    private void serveHttp(String method, String path, Map<String, String> headers,
                           InputStream in, OutputStream out) throws Exception {
        String auth = headers.get("authorization");
        String token = "";
        if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
            token = auth.substring(7).trim();
        }
        if (!prefs.isTokenValid(token)) {
            writeHttp(out, 401, "text/plain", "unauthorized".getBytes("UTF-8"));
            return;
        }
        String q = "";
        int qi = path.indexOf('?');
        String p = path;
        if (qi >= 0) {
            q = path.substring(qi + 1);
            p = path.substring(0, qi);
        }
        Map<String, String> query = parseQuery(q);
        if ("GET".equals(method) && "/transfer/list".equals(p)) {
            org.json.JSONObject o = new org.json.JSONObject();
            o.put("files", store.list());
            byte[] body = o.toString().getBytes("UTF-8");
            writeHttp(out, 200, "application/json; charset=utf-8", body);
            return;
        }
        if ("GET".equals(method) && "/transfer/download".equals(p)) {
            File f = store.findByName(query.get("name"));
            if (f == null || !f.isFile()) {
                writeHttp(out, 404, "text/plain", "missing".getBytes("UTF-8"));
                return;
            }
            writeHttpHeader(out, 200, "application/octet-stream", f.length());
            store.copyTo(f, out);
            out.flush();
            return;
        }
        if ("PUT".equals(method) && "/transfer/upload".equals(p)) {
            String dir = query.get("dir");
            String name = query.get("name");
            File dest = store.resolve(dir, name);
            if (dest == null) {
                writeHttp(out, 400, "text/plain", "path".getBytes("UTF-8"));
                return;
            }
            long len = 0;
            String cl = headers.get("content-length");
            if (cl != null) {
                try {
                    len = Long.parseLong(cl.trim());
                } catch (NumberFormatException e) {
                    len = 0;
                }
            }
            store.save(dest, in, len);
            writeHttp(out, 200, "text/plain", "ok".getBytes("UTF-8"));
            if (Constants.DIR_APK.equals(dir)) {
                commands.installUploaded(dest);
            }
            return;
        }
        writeHttp(out, 404, "text/plain", "no".getBytes("UTF-8"));
    }

    private Map<String, String> parseQuery(String q) {
        Map<String, String> m = new HashMap<String, String>();
        if (q == null || q.length() == 0) {
            return m;
        }
        String[] parts = q.split("&");
        for (int i = 0; i < parts.length; i++) {
            int eq = parts[i].indexOf('=');
            if (eq < 0) {
                continue;
            }
            try {
                m.put(URLDecoder.decode(parts[i].substring(0, eq), "UTF-8"),
                        URLDecoder.decode(parts[i].substring(eq + 1), "UTF-8"));
            } catch (UnsupportedEncodingException ignored) {
            }
        }
        return m;
    }

    private void writeHttp(OutputStream out, int code, String type, byte[] body) throws Exception {
        writeHttpHeader(out, code, type, body.length);
        out.write(body);
        out.flush();
    }

    private void writeHttpHeader(OutputStream out, int code, String type, long length) throws Exception {
        String reason = code == 200 ? "OK" : (code == 401 ? "Unauthorized" : "Error");
        String h = "HTTP/1.1 " + code + " " + reason + "\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Content-Length: " + length + "\r\n"
                + "Connection: close\r\n\r\n";
        out.write(h.getBytes("ASCII"));
    }
}

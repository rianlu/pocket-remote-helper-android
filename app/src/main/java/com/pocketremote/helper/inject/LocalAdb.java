package com.pocketremote.helper.inject;

import android.util.Log;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Best-effort local adbd on 127.0.0.1:5555. Fails quietly if AUTH is required. */
/** 尽力连接本机 adbd；需要 AUTH 时直接失败。 */
final class LocalAdb {
    private static final String TAG = "PocketRemoteAdb";
    private static final int A_CNXN = 0x4e584e43;
    private static final int A_OPEN = 0x4e45504f;
    private static final int A_OKAY = 0x59414b4f;
    private static final int A_CLSE = 0x45534c43;
    private static final int A_WRTE = 0x45545257;
    private static final int A_AUTH = 0x48545541;
    /** 常见本机 adbd 口；5555 优先。 */
    private static final int[] PORTS = { 5555, 5554, 5114, 7896, 1127, 30105, 31015 };
    private static final int LOCAL_ID = 1;
    private static final Object SESSION = new Object();
    private static int cachedPort = -1;
    private static Socket session;
    private static OutputStream sessionOut;
    private static InputStream sessionIn;
    private static int sessionRemote;
    private static Thread drain;

    static boolean shell(String command) {
        String out = shellOutput(command, 3000);
        return out != null;
    }

    /** 按键走长连接 interactive shell，避免每次 CNXN+OPEN。 */
    static boolean keyevent(int code) {
        return sendLine("input keyevent " + code);
    }

    static boolean sendLine(String command) {
        if (command == null || command.length() == 0) {
            return false;
        }
        synchronized (SESSION) {
            if (writeSession(command)) {
                return true;
            }
            closeSession();
            return writeSession(command);
        }
    }

    private static boolean writeSession(String command) {
        if (!ensureSession()) {
            return false;
        }
        try {
            writeMsg(sessionOut, A_WRTE, LOCAL_ID, sessionRemote, (command + "\n").getBytes("UTF-8"));
            return true;
        } catch (Exception e) {
            Log.w(TAG, "session write", e);
            closeSession();
            return false;
        }
    }

    private static boolean ensureSession() {
        if (session != null && session.isConnected() && !session.isClosed()) {
            return true;
        }
        closeSession();
        int[] ports;
        if (cachedPort > 0) {
            ports = new int[] { cachedPort };
        } else {
            ports = PORTS;
        }
        for (int i = 0; i < ports.length; i++) {
            if (openSession(ports[i])) {
                cachedPort = ports[i];
                return true;
            }
        }
        if (cachedPort > 0) {
            cachedPort = -1;
            for (int i = 0; i < PORTS.length; i++) {
                if (openSession(PORTS[i])) {
                    cachedPort = PORTS[i];
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean openSession(int port) {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 400);
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(0);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            writeMsg(out, A_CNXN, 0x01000000, 4096, "host::\0".getBytes("UTF-8"));
            socket.setSoTimeout(2000);
            Msg msg = readMsg(in);
            if (msg == null || msg.cmd == A_AUTH) {
                socket.close();
                return false;
            }
            if (msg.cmd != A_CNXN && msg.cmd != A_OKAY) {
                socket.close();
                return false;
            }
            writeMsg(out, A_OPEN, LOCAL_ID, 0, "shell:\0".getBytes("UTF-8"));
            Msg open = readMsg(in);
            if (open == null || open.cmd != A_OKAY) {
                socket.close();
                return false;
            }
            socket.setSoTimeout(0);
            session = socket;
            sessionOut = out;
            sessionIn = in;
            sessionRemote = open.arg0;
            drain = new Thread(new Runnable() {
                @Override
                public void run() {
                    drainLoop();
                }
            }, "pocketremote-adb-drain");
            drain.setDaemon(true);
            drain.start();
            return true;
        } catch (Exception e) {
            Log.i(TAG, "session skip: " + e.getMessage());
            try {
                socket.close();
            } catch (Exception ignored) {
            }
            return false;
        }
    }

    private static void drainLoop() {
        try {
            while (true) {
                InputStream in;
                OutputStream out;
                int remote;
                synchronized (SESSION) {
                    in = sessionIn;
                    out = sessionOut;
                    remote = sessionRemote;
                }
                if (in == null) {
                    return;
                }
                Msg m = readMsg(in);
                if (m == null || m.cmd == A_CLSE) {
                    synchronized (SESSION) {
                        closeSession();
                    }
                    return;
                }
                if (m.cmd == A_WRTE && out != null) {
                    synchronized (SESSION) {
                        if (sessionOut == out) {
                            writeMsg(out, A_OKAY, LOCAL_ID, remote, new byte[0]);
                        }
                    }
                }
            }
        } catch (Exception e) {
            synchronized (SESSION) {
                closeSession();
            }
        }
    }

    private static void closeSession() {
        if (session != null) {
            try {
                session.close();
            } catch (Exception ignored) {
            }
        }
        session = null;
        sessionOut = null;
        sessionIn = null;
        sessionRemote = 0;
    }

    /** 尽力打开网络 adbd。普通应用经常没权限，失败则忽略。 */
    static void tryEnableTcp() {
        try {
            Runtime.getRuntime().exec(new String[] {"setprop", "service.adb.tcp.port", "5555"}).waitFor();
        } catch (Exception ignored) {
        }
        try {
            Runtime.getRuntime().exec(new String[] {"start", "adbd"}).waitFor();
        } catch (Exception ignored) {
        }
    }

    static boolean available() {
        String id = shellOutput("id", 2000);
        return id != null && id.indexOf("uid=") >= 0;
    }

    /** 读完 shell 输出。本机 adbd 无 AUTH 时才能用。 */
    static String shellOutput(String command, int timeoutMs) {
        if (cachedPort > 0) {
            String hit = shellOutputOn(cachedPort, command, timeoutMs);
            if (hit != null) {
                return hit;
            }
            cachedPort = -1;
        }
        for (int i = 0; i < PORTS.length; i++) {
            String hit = shellOutputOn(PORTS[i], command, timeoutMs);
            if (hit != null) {
                cachedPort = PORTS[i];
                return hit;
            }
        }
        return null;
    }

    private static String shellOutputOn(int port, String command, int timeoutMs) {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 400);
            socket.setSoTimeout(timeoutMs);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            byte[] banner = "host::\0".getBytes("UTF-8");
            writeMsg(out, A_CNXN, 0x01000000, 4096, banner);
            Msg msg = readMsg(in);
            if (msg == null || msg.cmd == A_AUTH) {
                return null;
            }
            if (msg.cmd != A_CNXN && msg.cmd != A_OKAY) {
                return null;
            }
            byte[] dest = ("shell:" + command + "\0").getBytes("UTF-8");
            writeMsg(out, A_OPEN, 1, 0, dest);
            Msg open = readMsg(in);
            if (open == null || open.cmd != A_OKAY) {
                return null;
            }
            int remote = open.arg0;
            StringBuilder body = new StringBuilder();
            while (true) {
                Msg m = readMsg(in);
                if (m == null || m.cmd == A_CLSE) {
                    break;
                }
                if (m.cmd == A_WRTE) {
                    if (m.data != null && m.data.length > 0) {
                        body.append(new String(m.data, "UTF-8"));
                    }
                    writeMsg(out, A_OKAY, 1, remote, new byte[0]);
                }
            }
            writeMsg(out, A_CLSE, 1, remote, new byte[0]);
            return body.toString();
        } catch (Exception e) {
            Log.i(TAG, "adb skip: " + e.getMessage());
            return null;
        } finally {
            try {
                socket.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static void writeMsg(OutputStream out, int cmd, int arg0, int arg1, byte[] data) throws Exception {
        int crc = 0;
        for (int i = 0; i < data.length; i++) {
            crc += data[i] & 0xff;
        }
        ByteBuffer h = ByteBuffer.allocate(24 + data.length).order(ByteOrder.LITTLE_ENDIAN);
        h.putInt(cmd);
        h.putInt(arg0);
        h.putInt(arg1);
        h.putInt(data.length);
        h.putInt(crc);
        h.putInt(cmd ^ 0xffffffff);
        h.put(data);
        out.write(h.array());
        out.flush();
    }

    private static Msg readMsg(InputStream in) throws Exception {
        byte[] header = readFully(in, 24);
        if (header == null) {
            return null;
        }
        ByteBuffer h = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        Msg m = new Msg();
        m.cmd = h.getInt();
        m.arg0 = h.getInt();
        m.arg1 = h.getInt();
        int len = h.getInt();
        h.getInt();
        h.getInt();
        if (len < 0 || len > 1024 * 1024) {
            return null;
        }
        if (len > 0) {
            m.data = readFully(in, len);
        }
        return m;
    }

    private static byte[] readFully(InputStream in, int n) throws Exception {
        byte[] b = new byte[n];
        int o = 0;
        while (o < n) {
            int r = in.read(b, o, n - o);
            if (r < 0) {
                return null;
            }
            o += r;
        }
        return b;
    }

    private static final class Msg {
        int cmd;
        int arg0;
        int arg1;
        byte[] data;
    }
}

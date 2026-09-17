package com.pockettv.tv.inject;

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
    private static final String TAG = "PocketTvAdb";
    private static final int A_CNXN = 0x4e584e43;
    private static final int A_OPEN = 0x4e45504f;
    private static final int A_OKAY = 0x59414b4f;
    private static final int A_CLSE = 0x45534c43;
    private static final int A_WRTE = 0x45545257;
    private static final int A_AUTH = 0x48545541;

    static boolean shell(String command) {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress("127.0.0.1", 5555), 800);
            socket.setSoTimeout(3000);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            byte[] banner = "host::\0".getBytes("UTF-8");
            writeMsg(out, A_CNXN, 0x01000000, 4096, banner);
            Msg msg = readMsg(in);
            if (msg == null || msg.cmd == A_AUTH) {
                return false;
            }
            if (msg.cmd != A_CNXN && msg.cmd != A_OKAY) {
                return false;
            }
            byte[] dest = ("shell:" + command + "\0").getBytes("UTF-8");
            writeMsg(out, A_OPEN, 1, 0, dest);
            Msg open = readMsg(in);
            if (open == null || open.cmd != A_OKAY) {
                return false;
            }
            writeMsg(out, A_CLSE, 1, open.arg0, new byte[0]);
            return true;
        } catch (Exception e) {
            Log.i(TAG, "adb skip: " + e.getMessage());
            return false;
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

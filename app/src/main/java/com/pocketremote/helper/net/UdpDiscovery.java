package com.pocketremote.helper.net;

import android.os.Build;
import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import com.pocketremote.helper.protocol.Constants;

/** 监听 UDP 17882，应答 PKTREMT1 发现包。 */
public final class UdpDiscovery {
    private static final String TAG = "PocketRemoteUdp";
    private volatile boolean running;
    private DatagramSocket socket;

    public void start() {
        running = true;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    socket = new DatagramSocket(Constants.UDP_PORT);
                    socket.setBroadcast(true);
                    byte[] buf = new byte[1024];
                    while (running) {
                        DatagramPacket packet = new DatagramPacket(buf, buf.length);
                        socket.receive(packet);
                        if (packet.getLength() < 10) {
                            continue;
                        }
                        String magic = new String(buf, 0, 8, "US-ASCII");
                        if (!Constants.UDP_MAGIC.equals(magic)) {
                            continue;
                        }
                        byte[] name = Build.MODEL.getBytes("UTF-8");
                        ByteBuffer out = ByteBuffer.allocate(10 + name.length).order(ByteOrder.BIG_ENDIAN);
                        out.put(Constants.UDP_MAGIC.getBytes("US-ASCII"));
                        out.putShort((short) Constants.CONTROL_PORT);
                        out.put(name);
                        byte[] payload = out.array();
                        DatagramPacket reply = new DatagramPacket(
                                payload, payload.length, packet.getAddress(), packet.getPort());
                        socket.send(reply);
                    }
                } catch (Exception e) {
                    if (running) {
                        Log.w(TAG, "udp", e);
                    }
                }
            }
        }, "pocketremote-udp");
        t.start();
    }

    public void stop() {
        running = false;
        if (socket != null) {
            socket.close();
        }
    }
}

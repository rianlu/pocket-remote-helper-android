#!/usr/bin/env python3
"""Talk to PocketTV Helper over the protocol in docs/PROTOCOL.md.

Emulator (from the host):
  adb forward tcp:17880 tcp:17880
  python3 scripts/test_ws.py hello
  python3 scripts/test_ws.py pin 123456
  python3 scripts/test_ws.py key 19
  python3 scripts/test_ws.py text hello

Real device on LAN:
  python3 scripts/test_ws.py --host 192.168.1.20 hello
"""
from __future__ import print_function

import argparse
import json
import os
import socket
import struct
import sys
import uuid

try:
    import base64
except ImportError:
    base64 = None


KEYS = {
    "up": 19,
    "down": 20,
    "left": 21,
    "right": 22,
    "ok": 23,
    "enter": 23,
    "back": 4,
    "home": 3,
    "menu": 82,
    "vol+": 24,
    "vol-": 25,
    "mute": 164,
}


def ws_connect(host, port, path="/ws", timeout=5):
    sock = socket.create_connection((host, port), timeout)
    key = base64.b64encode(os.urandom(16)).decode("ascii")
    req = (
        "GET %s HTTP/1.1\r\n"
        "Host: %s:%s\r\n"
        "Upgrade: websocket\r\n"
        "Connection: Upgrade\r\n"
        "Sec-WebSocket-Key: %s\r\n"
        "Sec-WebSocket-Version: 13\r\n"
        "\r\n"
    ) % (path, host, port, key)
    sock.sendall(req.encode("ascii"))
    buf = b""
    while b"\r\n\r\n" not in buf:
        chunk = sock.recv(4096)
        if not chunk:
            raise IOError("socket closed during websocket handshake")
        buf += chunk
    status = buf.split(b"\r\n", 1)[0]
    if b"101" not in status:
        raise IOError("handshake failed: %s" % status.decode("latin1", "replace"))
    return sock


def send_text(sock, text):
    data = text.encode("utf-8")
    mask = os.urandom(4)
    header = bytearray([0x81])
    n = len(data)
    if n < 126:
        header.append(0x80 | n)
    elif n <= 65535:
        header.append(0x80 | 126)
        header.extend(struct.pack(">H", n))
    else:
        header.append(0x80 | 127)
        header.extend(struct.pack(">Q", n))
    masked = bytes(b ^ mask[i % 4] for i, b in enumerate(data))
    sock.sendall(bytes(header) + mask + masked)


def recv_text(sock, timeout=5):
    sock.settimeout(timeout)
    hdr = _recv_exact(sock, 2)
    opcode = hdr[0] & 0x0F
    ln = hdr[1] & 0x7F
    masked = (hdr[1] & 0x80) != 0
    if ln == 126:
        ln = struct.unpack(">H", _recv_exact(sock, 2))[0]
    elif ln == 127:
        ln = struct.unpack(">Q", _recv_exact(sock, 8))[0]
    mask = _recv_exact(sock, 4) if masked else b"\x00\x00\x00\x00"
    data = _recv_exact(sock, ln)
    if masked:
        data = bytes(b ^ mask[i % 4] for i, b in enumerate(data))
    if opcode == 8:
        return None
    if opcode != 1:
        return ""
    return data.decode("utf-8")


def _recv_exact(sock, n):
    buf = b""
    while len(buf) < n:
        chunk = sock.recv(n - len(buf))
        if not chunk:
            raise IOError("socket closed")
        buf += chunk
    return buf


def send_msg(sock, msg_type, payload):
    msg = {
        "v": 1,
        "id": str(uuid.uuid4()),
        "type": msg_type,
        "payload": payload,
    }
    raw = json.dumps(msg, ensure_ascii=False)
    print(">>", raw)
    send_text(sock, raw)
    try:
        reply = recv_text(sock)
    except socket.timeout:
        print("<< (no reply, timeout — ok for key/text)")
        return None
    print("<<", reply)
    return reply


def parse_key(raw):
    if raw.isdigit():
        return int(raw)
    code = KEYS.get(raw.lower())
    if code is None:
        raise SystemExit("unknown key %r, try %s or a number" % (raw, ", ".join(sorted(KEYS))))
    return code


def main():
    parser = argparse.ArgumentParser(description="PocketTV Helper protocol test client")
    parser.add_argument("--host", default="127.0.0.1", help="helper IP (default 127.0.0.1 after adb forward)")
    parser.add_argument("--port", type=int, default=17880)
    sub = parser.add_subparsers(dest="cmd")
    sub.required = True

    sub.add_parser("hello", help="start pairing, TV shows PIN")
    p_pin = sub.add_parser("pin", help="submit 6-digit PIN")
    p_pin.add_argument("code")
    p_hello_tok = sub.add_parser("token", help="hello with saved token")
    p_hello_tok.add_argument("token")
    p_key = sub.add_parser("key", help="send a key (up/down/left/right/ok/back/home/menu or code)")
    p_key.add_argument("name")
    p_text = sub.add_parser("text", help="send text into current focus")
    p_text.add_argument("value")
    sub.add_parser("apps", help="list installed apps")

    args = parser.parse_args()
    sock = ws_connect(args.host, args.port)
    try:
        if args.cmd == "hello":
            send_msg(sock, "hello", {})
        elif args.cmd == "pin":
            send_msg(sock, "hello", {"pin": args.code})
        elif args.cmd == "token":
            send_msg(sock, "hello", {"token": args.token})
        elif args.cmd == "key":
            send_msg(sock, "key", {"action": "click", "code": parse_key(args.name)})
        elif args.cmd == "text":
            send_msg(sock, "text", {"text": args.value})
        elif args.cmd == "apps":
            send_msg(sock, "apps", {})
    finally:
        sock.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())

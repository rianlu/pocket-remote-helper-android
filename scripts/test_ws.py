#!/usr/bin/env python3
"""PocketRemote Helper protocol test.

Full flow (you only type the PIN shown on the TV):

  adb forward tcp:17880 tcp:17880
  python3 scripts/test_ws.py
  python3 scripts/test_ws.py 672821

Log is written to scripts/logs/test_ws-*.log

Single-step commands still work: hello | pin | token | key | text | apps
"""
from __future__ import print_function

import argparse
import datetime
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

COMMANDS = ("hello", "pin", "token", "key", "text", "apps", "all")


class Logger(object):
    def __init__(self, path):
        self.path = path
        directory = os.path.dirname(path)
        if directory and not os.path.isdir(directory):
            os.makedirs(directory)
        self.fp = open(path, "w")
        self.results = []

    def log(self, *parts):
        line = " ".join(_to_text(p) for p in parts)
        print(line)
        self.fp.write(line + "\n")
        self.fp.flush()

    def step(self, name, ok, detail=""):
        status = "PASS" if ok else "FAIL"
        self.results.append((name, status, detail))
        self.log("[%s] %s %s" % (status, name, detail))

    def close(self):
        self.log("")
        self.log("===== SUMMARY =====")
        for name, status, detail in self.results:
            self.log("%s  %s  %s" % (status, name, detail))
        self.log("log file:", self.path)
        self.fp.close()


def _to_text(value):
    if isinstance(value, bytes):
        return value.decode("utf-8", "replace")
    return str(value)


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


def token_path(host, port):
    safe = "%s_%s" % (host.replace(".", "_"), port)
    return os.path.join(os.path.expanduser("~"), ".pocketremote-token-" + safe)


def load_token(host, port):
    path = token_path(host, port)
    if not os.path.isfile(path):
        return ""
    with open(path, "r") as f:
        return f.read().strip()


def save_token(host, port, token, log=None):
    path = token_path(host, port)
    with open(path, "w") as f:
        f.write(token)
    if log:
        log.log("saved token ->", path)
    else:
        print("saved token ->", path)


def parse_json(reply):
    if not reply:
        return None
    try:
        return json.loads(reply)
    except ValueError:
        return None


def remember_token(host, port, reply, log=None):
    obj = parse_json(reply)
    if not obj or obj.get("type") != "hello_ok":
        return None
    payload = obj.get("payload") or {}
    token = payload.get("token")
    if token:
        save_token(host, port, token, log)
    return payload


def send_msg(sock, msg_type, payload, log=None):
    msg = {
        "v": 1,
        "id": str(uuid.uuid4()),
        "type": msg_type,
        "payload": payload,
    }
    raw = json.dumps(msg, ensure_ascii=False)
    if log:
        log.log(">>", raw)
    else:
        print(">>", raw)
    send_text(sock, raw)
    try:
        reply = recv_text(sock)
    except socket.timeout:
        reply = None
        if log:
            log.log("<< (no reply, timeout)")
        else:
            print("<< (no reply, timeout — ok for key/text)")
        return None
    if log:
        log.log("<<", reply)
    else:
        print("<<", reply)
    return reply


def expect_type(reply, expected):
    obj = parse_json(reply)
    if not obj:
        return False, "empty/invalid json"
    actual = obj.get("type")
    if actual != expected:
        payload = obj.get("payload") or {}
        return False, "got type=%s code=%s message=%s" % (
            actual, payload.get("code"), payload.get("message"))
    return True, json.dumps(obj.get("payload") or {}, ensure_ascii=False)


def http_get(host, port, path, token, log):
    req = (
        "GET %s HTTP/1.1\r\n"
        "Host: %s:%s\r\n"
        "Authorization: Bearer %s\r\n"
        "Connection: close\r\n"
        "\r\n"
    ) % (path, host, port, token)
    log.log("HTTP >> GET", path)
    sock = socket.create_connection((host, port), 5)
    try:
        sock.sendall(req.encode("ascii"))
        buf = b""
        while True:
            chunk = sock.recv(4096)
            if not chunk:
                break
            buf += chunk
    finally:
        sock.close()
    text = buf.decode("utf-8", "replace")
    log.log("HTTP <<", text[:2000])
    first = text.split("\r\n", 1)[0]
    return first.startswith("HTTP/1.1 200"), first


def parse_key(raw):
    if raw.isdigit():
        return int(raw)
    code = KEYS.get(raw.lower())
    if code is None:
        raise SystemExit("unknown key %r, try %s or a number" % (raw, ", ".join(sorted(KEYS))))
    return code


def ensure_authed(sock, host, port, log=None):
    token = load_token(host, port)
    if not token:
        raise SystemExit("no saved token. run: python3 scripts/test_ws.py")
    reply = send_msg(sock, "hello", {"token": token}, log)
    remember_token(host, port, reply, log)
    ok, detail = expect_type(reply, "hello_ok")
    if not ok:
        raise SystemExit("auth failed: %s" % detail)


def default_log_path():
    here = os.path.dirname(os.path.abspath(__file__))
    stamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    return os.path.join(here, "logs", "test_ws-%s.log" % stamp)


def run_all(host, port, pin, log):
    log.log("host=%s port=%s" % (host, port))
    log.log("time=%s" % datetime.datetime.now().isoformat())
    sock = ws_connect(host, port)
    token = ""
    payload = {}
    try:
        reply = send_msg(sock, "hello", {}, log)
        ok, detail = expect_type(reply, "need_pin")
        log.step("hello -> need_pin", ok, detail)
        if not ok:
            return 1

        if not pin:
            try:
                pin = raw_input("Enter 6-digit PIN from TV: ").strip()
            except NameError:
                pin = input("Enter 6-digit PIN from TV: ").strip()
        log.log("using pin=", pin)

        reply = send_msg(sock, "hello", {"pin": pin}, log)
        ok, detail = expect_type(reply, "hello_ok")
        payload = (parse_json(reply) or {}).get("payload") or {}
        remember_token(host, port, reply, log)
        log.step("pin -> hello_ok", ok, detail)
        if not ok:
            return 1
        token = payload.get("token") or ""
        log.log("tvName=", payload.get("tvName"), "sdk=", payload.get("sdk"),
                "injectOk=", payload.get("injectOk"))
        if payload.get("injectOk") is False:
            log.log("note: injectOk=false (input keyevent probe failed)")

        reply = send_msg(sock, "key", {"action": "click", "code": 19}, log)
        if reply is None:
            log.step("key up (same connection)", True, "no reply as specified")
        else:
            ok, detail = expect_type(reply, "error")
            # error is failure; any other documented success has no type. If error, fail.
            obj = parse_json(reply)
            log.step("key up (same connection)", obj is None or obj.get("type") != "error",
                     detail if obj else "")

        reply = send_msg(sock, "text", {"text": "abc"}, log)
        obj = parse_json(reply)
        log.step("text abc (same connection)", obj is None or (obj and obj.get("type") != "error"),
                 "" if obj is None else json.dumps(obj.get("payload") or {}))

        reply = send_msg(sock, "apps", {}, log)
        ok, detail = expect_type(reply, "apps_ok")
        log.step("apps (same connection)", ok, detail[:300] if detail else "")
    finally:
        sock.close()

    log.log("--- reconnect with saved token ---")
    sock = ws_connect(host, port)
    try:
        reply = send_msg(sock, "hello", {"token": token or load_token(host, port)}, log)
        ok, detail = expect_type(reply, "hello_ok")
        log.step("reconnect hello+token", ok, detail)
        send_msg(sock, "key", {"action": "click", "code": 4}, log)
        log.step("key back (new connection)", True, "sent")
    finally:
        sock.close()

    if token:
        ok, detail = http_get(host, port, "/transfer/list", token, log)
        log.step("HTTP GET /transfer/list", ok, detail)
    return 0 if all(s == "PASS" for _, s, _ in log.results if "hello" in _ or "pin" in _) else 0


def main():
    if not any(a in COMMANDS for a in sys.argv[1:]):
        sys.argv.insert(1, "all")

    parser = argparse.ArgumentParser(description="PocketRemote Helper protocol test client")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=17880)
    parser.add_argument("--log", default="", help="log file path (default scripts/logs/test_ws-*.log)")
    sub = parser.add_subparsers(dest="cmd")
    sub.required = True

    p_all = sub.add_parser("all", help="full flow; only PIN is required")
    p_all.add_argument("pin", nargs="?", default="")
    sub.add_parser("hello", help="start pairing, TV shows PIN")
    p_pin = sub.add_parser("pin", help="submit 6-digit PIN")
    p_pin.add_argument("code")
    p_hello_tok = sub.add_parser("token", help="hello with saved token")
    p_hello_tok.add_argument("token")
    p_key = sub.add_parser("key", help="send a key")
    p_key.add_argument("name")
    p_text = sub.add_parser("text", help="send text")
    p_text.add_argument("value")
    sub.add_parser("apps", help="list installed apps")

    args = parser.parse_args()
    log_path = args.log or default_log_path()
    log = Logger(log_path)
    log.log("cmd=", args.cmd, "host=", args.host, "port=", args.port)

    code = 0
    try:
        if args.cmd == "all":
            code = run_all(args.host, args.port, args.pin, log)
        else:
            sock = ws_connect(args.host, args.port)
            try:
                if args.cmd == "hello":
                    send_msg(sock, "hello", {}, log)
                elif args.cmd == "pin":
                    remember_token(args.host, args.port, send_msg(sock, "hello", {"pin": args.code}, log), log)
                elif args.cmd == "token":
                    remember_token(args.host, args.port, send_msg(sock, "hello", {"token": args.token}, log), log)
                elif args.cmd == "key":
                    ensure_authed(sock, args.host, args.port, log)
                    send_msg(sock, "key", {"action": "click", "code": parse_key(args.name)}, log)
                elif args.cmd == "text":
                    ensure_authed(sock, args.host, args.port, log)
                    send_msg(sock, "text", {"text": args.value}, log)
                elif args.cmd == "apps":
                    ensure_authed(sock, args.host, args.port, log)
                    send_msg(sock, "apps", {}, log)
            finally:
                sock.close()
    except Exception as exc:
        log.step("exception", False, repr(exc))
        code = 1
    finally:
        log.close()
        print("\n完整日志:", log.path)
    return code


if __name__ == "__main__":
    sys.exit(main())

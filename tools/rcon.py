"""Minimal RCON client for the dev server (run/server.properties: port 25600, password "atlas").

    python tools/rcon.py "grandatlas where" ["another command" ...]
"""
import socket
import struct
import sys

sys.stdout.reconfigure(encoding="utf-8")  # replies contain characters like →


def packet(sock, req_id, kind, body):
    data = struct.pack("<ii", req_id, kind) + body.encode() + b"\x00\x00"
    sock.sendall(struct.pack("<i", len(data)) + data)
    size = struct.unpack("<i", sock.recv(4))[0]
    buf = b""
    while len(buf) < size:
        buf += sock.recv(size - len(buf))
    return buf[8:-2].decode(errors="replace")


with socket.create_connection(("127.0.0.1", 25600), timeout=600) as s:
    packet(s, 1, 3, "atlas")
    for cmd in sys.argv[1:]:
        print(f"> {cmd}\n{packet(s, 2, 2, cmd)}")

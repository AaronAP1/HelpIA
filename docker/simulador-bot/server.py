"""
Simulador del endpoint de CALLBACK del Agente HelpIA (bot) para probar la Function en local (nunca para Azure).
  POST /helpia/callback/{correlationId}   verifica la firma como debe hacerlo el bot real:
      X-HelpIA-Signature = "v1=" + Base64(HMAC-SHA256(secreto, X-HelpIA-Timestamp + "." + body))
      y rechaza timestamps con más de 5 minutos (anti-replay). Responde 200 o 401.
  GET  /callbacks                         callbacks recibidos (el más reciente primero)
  GET  /callbacks/{correlationId}         uno en particular
Caso especial: un correlationId que empieza con "callback-falla" responde siempre 503
(sirve para probar los reintentos y GET /api/operaciones/{correlationId}).
"""
import base64
import hashlib
import hmac
import json
import os
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import unquote, urlparse

SECRET = os.environ.get("CALLBACK_HMAC_SECRET", "").encode("utf-8")
MAX_AGE_SECONDS = 300
PREFIX = "/helpia/callback/"

RECIBIDOS = []
LOCK = threading.Lock()


def valid_signature(timestamp, body, signature):
    expected = base64.b64encode(hmac.new(SECRET, timestamp.encode("utf-8") + b"." + body, hashlib.sha256).digest())
    return hmac.compare_digest(b"v1=" + expected, (signature or "").encode("utf-8"))


class Handler(BaseHTTPRequestHandler):

    def send_json(self, status, payload):
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, fmt, *args):
        print("[bot] " + (fmt % args), flush=True)

    def do_POST(self):
        path = urlparse(self.path).path
        if not path.startswith(PREFIX):
            return self.send_json(404, {"error": "ruta no simulada"})
        correlation_id = unquote(path[len(PREFIX):])
        body = self.rfile.read(int(self.headers.get("Content-Length", 0)))
        timestamp = self.headers.get("X-HelpIA-Timestamp") or ""

        if not timestamp.isdigit() or abs(time.time() - int(timestamp)) > MAX_AGE_SECONDS:
            print(f"[bot] RECHAZADO {correlation_id}: timestamp fuera de rango", flush=True)
            return self.send_json(401, {"error": "timestamp"})
        if not valid_signature(timestamp, body, self.headers.get("X-HelpIA-Signature")):
            print(f"[bot] RECHAZADO {correlation_id}: firma inválida", flush=True)
            return self.send_json(401, {"error": "firma"})
        if correlation_id.startswith("callback-falla"):
            print(f"[bot] simulando caída para {correlation_id}", flush=True)
            return self.send_json(503, {"error": "bot no disponible (simulado)"})

        payload = json.loads(body)
        with LOCK:
            RECIBIDOS.insert(0, {"recibidoEn": time.strftime("%Y-%m-%dT%H:%M:%S"), "correlationId": correlation_id,
                                 "transaccionId": self.headers.get("Transaccion-Id"), "payload": payload})
        print(f"[bot] callback OK {correlation_id}: {payload.get('estado')} {payload.get('codigo')} "
              f"-> mensaje al colaborador: {payload.get('mensaje')}", flush=True)
        self.send_json(200, {"recibido": True})

    def do_GET(self):
        path = urlparse(self.path).path.rstrip("/")
        if path in ("", "/callbacks"):
            with LOCK:
                return self.send_json(200, {"status": "OK", "simulador": "bot", "callbacks": RECIBIDOS})
        if path.startswith("/callbacks/"):
            correlation_id = unquote(path[len("/callbacks/"):])
            with LOCK:
                found = [c for c in RECIBIDOS if c["correlationId"] == correlation_id]
            return self.send_json(200 if found else 404, found[0] if found else {"error": "sin callback"})
        self.send_json(404, {"error": "ruta no simulada"})


if __name__ == "__main__":
    if len(SECRET) < 32:
        raise SystemExit("[bot] CALLBACK_HMAC_SECRET debe tener al menos 32 caracteres")
    print("[bot] simulador escuchando en :8082", flush=True)
    ThreadingHTTPServer(("0.0.0.0", 8082), Handler).serve_forever()

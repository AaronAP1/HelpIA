"""
Simulador de Microsoft Graph para probar la Function en local (nunca para Azure).
Responde las consultas que hace el servicio:
  GET /graph/v1.0/users/{upn}                                   búsqueda directa
  GET /graph/v1.0/users?$filter=userPrincipalName eq '..' or mail eq '..'   filtro (ConsistencyLevel: eventual)
  GET /graph/v1.0/users?$filter=onPremisesSamAccountName eq '..'           usuario de red (Etapa 3)
Casos especiales: throttle@pacifico.com.pe responde 429 con Retry-After.
"""
import json
import os
import re
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, unquote, urlparse

with open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "usuarios.json"), encoding="utf-8") as f:
    USERS = json.load(f)

FILTER_EMAIL = re.compile(r"^userPrincipalName eq '(.+)' or mail eq '(.+)'$")
FILTER_SAM = re.compile(r"^onPremisesSamAccountName eq '(.+)'$")


class Handler(BaseHTTPRequestHandler):

    def send_json(self, status, payload, headers=None):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        for key, value in (headers or {}).items():
            self.send_header(key, value)
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):
        print("[graph] " + (fmt % args), flush=True)

    def do_GET(self):
        url = urlparse(self.path)
        if url.path == "/":
            return self.send_json(200, {"status": "OK", "simulador": "graph", "usuarios": len(USERS)})
        if not url.path.startswith("/graph/v1.0/users"):
            return self.send_json(404, {"error": "ruta no simulada"})
        if not (self.headers.get("Authorization") or "").startswith("Bearer "):
            return self.send_json(401, {"error": {"code": "InvalidAuthenticationToken"}})

        if url.path.rstrip("/") == "/graph/v1.0/users":
            if self.headers.get("ConsistencyLevel") != "eventual":
                return self.send_json(400, {"error": {"code": "Request_UnsupportedQuery"}})
            query = parse_qs(url.query)
            top = int(query.get("$top", ["100"])[0])
            sam = FILTER_SAM.match(query.get("$filter", [""])[0])
            if sam:
                name = sam.group(1).replace("''", "'").lower()
                found = [u for u in USERS if (u.get("onPremisesSamAccountName") or "").lower() == name]
                return self.send_json(200, {"@odata.count": len(found), "value": found[:top]})
            match = FILTER_EMAIL.match(query.get("$filter", [""])[0])
            if not match:
                return self.send_json(400, {"error": {"code": "Request_UnsupportedQuery"}})
            email = match.group(1).replace("''", "'").lower()
            if email == "throttle@pacifico.com.pe":
                return self.send_json(429, {"error": {"code": "TooManyRequests"}}, {"Retry-After": "1"})
            found = [u for u in USERS if (u.get("userPrincipalName") or "").lower() == email
                     or (u.get("mail") or "").lower() == email]
            return self.send_json(200, {"@odata.count": len(found), "value": found[:top]})

        upn = unquote(url.path.split("/users/", 1)[1]).lower()
        for user in USERS:
            if (user.get("userPrincipalName") or "").lower() == upn:
                return self.send_json(200, user)
        self.send_json(404, {"error": {"code": "Request_ResourceNotFound"}})


if __name__ == "__main__":
    print(f"[graph] simulador escuchando en :8080 con {len(USERS)} usuarios", flush=True)
    ThreadingHTTPServer(("0.0.0.0", 8080), Handler).serve_forever()

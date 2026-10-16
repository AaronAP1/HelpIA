"""
Simulador de Azure Automation (API REST de Azure Resource Manager) para probar la Function en local (nunca para Azure).
Responde las tres llamadas de AutomationClient:
  PUT .../automationAccounts/{cuenta}/jobs/{job}          crea el job (201; 409 si ya existe)
  GET .../automationAccounts/{cuenta}/jobs/{job}          properties.status
  GET .../automationAccounts/{cuenta}/jobs/{job}/output   salida del runbook (última línea JSON, ver CONTRATO-RUNBOOKS.md)
Escenarios según el parámetro Usuario:
  cgonzalez  tarda 15 s     → la Function responde 202 y entrega el resultado por callback
  jrios      el runbook responde ERROR (SIN_PERMISOS)
  lsoto      el job termina en Failed
  otro       termina OK en 1 s → la Function responde 200
GET / lista los jobs creados.
"""
import json
import re
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse

JOB_PATH = re.compile(
    r"^/subscriptions/[^/]+/resourceGroups/[^/]+/providers/Microsoft\.Automation/"
    r"automationAccounts/[^/]+/jobs/([^/]+)(/output)?$", re.IGNORECASE)

ESCENARIOS = {
    "cgonzalez": {"segundos": 15},
    "jrios": {"segundos": 1, "resultado": "ERROR", "codigo": "SIN_PERMISOS",
              "mensaje": "No se pudo completar la operación; comunícate con Mesa de Ayuda."},
    "lsoto": {"segundos": 2, "estado": "Failed"},
}
EXITO = {
    "DESBLOQUEO": ("DESBLOQUEADO", "Tu cuenta fue desbloqueada."),
    "RESETEO": ("RESETEADO", "Tu contraseña fue reseteada; recibirás la temporal por SMS."),
    "ACTUALIZACION_CELULAR": ("CELULAR_ACTUALIZADO", "Tu número celular fue actualizado."),
}

JOBS = {}
LOCK = threading.Lock()


def status_of(job):
    escenario = ESCENARIOS.get(job["parameters"].get("Usuario", ""), {})
    if time.time() - job["creado"] < escenario.get("segundos", 1):
        return "Running"
    return escenario.get("estado", "Completed")


def output_of(job):
    params = job["parameters"]
    escenario = ESCENARIOS.get(params.get("Usuario", ""), {})
    codigo, mensaje = EXITO.get(params.get("Operacion", ""), ("OK", "Operación realizada."))
    resultado = {
        "resultado": escenario.get("resultado", "OK"),
        "codigo": escenario.get("codigo", codigo),
        "mensaje": escenario.get("mensaje", mensaje),
        "correlationId": params.get("CorrelationId"),
    }
    return "\n".join([
        f"Iniciando {params.get('Operacion')} para {params.get('Usuario')} en {params.get('Dominios')}",
        "Conectando con el controlador de dominio (simulado)",
        json.dumps(resultado, ensure_ascii=False),
    ])


class Handler(BaseHTTPRequestHandler):

    def send_body(self, status, body, content_type="application/json; charset=utf-8"):
        data = body.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def send_json(self, status, payload):
        self.send_body(status, json.dumps(payload, ensure_ascii=False))

    def log_message(self, fmt, *args):
        print("[automation] " + (fmt % args), flush=True)

    def authorized(self):
        if (self.headers.get("Authorization") or "").startswith("Bearer "):
            return True
        self.send_json(401, {"error": {"code": "AuthenticationFailed"}})
        return False

    def do_PUT(self):
        match = JOB_PATH.match(urlparse(self.path).path)
        if not match or match.group(2):
            return self.send_json(404, {"error": "ruta no simulada"})
        if not self.authorized():
            return
        body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"{}")
        props = body.get("properties", {})
        name = match.group(1)
        with LOCK:
            if name in JOBS:
                return self.send_json(409, {"code": "Conflict", "message": "El job ya existe."})
            JOBS[name] = {
                "runbook": props.get("runbook", {}).get("name"),
                "runOn": props.get("runOn"),
                "parameters": props.get("parameters", {}),
                "creado": time.time(),
            }
        params = props.get("parameters", {})
        print(f"[automation] job {name}: {props.get('runbook', {}).get('name')} {params.get('Operacion')} "
              f"{params.get('Usuario')} correlationId={params.get('CorrelationId')}", flush=True)
        self.send_json(201, {"name": name, "properties": {"status": "New"}})

    def do_GET(self):
        path = urlparse(self.path).path
        if path == "/":
            with LOCK:
                jobs = [{"job": k, "runbook": v["runbook"], "status": status_of(v),
                         "operacion": v["parameters"].get("Operacion"), "usuario": v["parameters"].get("Usuario"),
                         "correlationId": v["parameters"].get("CorrelationId")} for k, v in JOBS.items()]
            return self.send_json(200, {"status": "OK", "simulador": "automation", "jobs": jobs})
        match = JOB_PATH.match(path)
        if not match:
            return self.send_json(404, {"error": "ruta no simulada"})
        if not self.authorized():
            return
        with LOCK:
            job = JOBS.get(match.group(1))
        if job is None:
            return self.send_json(404, {"code": "NotFound"})
        if match.group(2):
            return self.send_body(200, output_of(job), "text/plain; charset=utf-8")
        self.send_json(200, {"name": match.group(1), "properties": {"status": status_of(job)}})


if __name__ == "__main__":
    print("[automation] simulador escuchando en :8081", flush=True)
    ThreadingHTTPServer(("0.0.0.0", 8081), Handler).serve_forever()

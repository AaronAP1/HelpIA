# helpia-functions (Etapas 2 y 3)

Function App Java de **HelpIA** según el diagrama de arquitectura HelpIA v4.1.

- **Etapa 2**: migración a Java de la Function App Python anterior (health + validar existencia).
- **Etapa 3**: desbloqueo, reseteo y cambio de celular con **Durable Functions**: crea el job del runbook en Azure
  Automation (Hybrid Worker → AD), espera ≈3 × 2 s y responde **200** con el resultado o **202** y lo entrega por
  **callback** firmado con HMAC.

| Diagrama | Ruta | Función | Permiso JWT |
|---|---|---|---|
| `GET /api/health (real)` | `GET /api/health` | `Health` | `JWT_REQUIRED_PERMISSION` |
| `POST /usuarios/validar-existencia (real · enrolamiento)` | `POST /api/usuarios/validar-existencia` | `ValidarExistenciaUsuario` | `JWT_REQUIRED_PERMISSION` |
| — (ruta actual del Python) | `POST /api/v1/entra/users/exists` | `UserExists` | `JWT_REQUIRED_PERMISSION` |
| `POST /usuario/desbloquear → 202 (Etapa 3)` | `POST /api/usuario/desbloquear` | `DesbloquearUsuario` | `JWT_REQUIRED_PERMISSION_OPERACIONES` |
| `POST /api/usuarios/resetear → 202 (Etapa 3)` | `POST /api/usuarios/resetear` | `ResetearUsuario` | `JWT_REQUIRED_PERMISSION_OPERACIONES` |
| `PUT /usuarios/celular → 202 (Etapa 3)` | `PUT /api/usuarios/celular` | `ActualizarCelular` | `JWT_REQUIRED_PERMISSION_OPERACIONES` |
| — (respaldo del callback, proponer en el diagrama) | `GET /api/operaciones/{correlationId}` | `ConsultarOperacion` | `JWT_REQUIRED_PERMISSION_OPERACIONES` |

Las rutas de Etapa 3 replican el diagrama tal cual (mezcla `usuario`/`usuarios`); homologar con Arquitectura antes de
publicar el contrato. Detalle paso a paso del diagrama → código en [docs/MAPEO-ARQUITECTURA.md](docs/MAPEO-ARQUITECTURA.md);
contrato con los runbooks en [docs/CONTRATO-RUNBOOKS.md](docs/CONTRATO-RUNBOOKS.md); despliegue y permisos en
[docs/DESPLIEGUE.md](docs/DESPLIEGUE.md); contrato OpenAPI en [openapi/helpia-api.yaml](openapi/helpia-api.yaml).

## JWT: dos emisores y dos permisos

- `JWT_TENANT_ID` acepta varios tenants separados por coma: Teams (producto de APIM con token de usuario) y WhatsApp
  (producto de APIM con token de aplicación). Cada token debe traer `iss` y `tid` del mismo tenant permitido.
- Las operaciones de Etapa 3 exigen `JWT_REQUIRED_PERMISSION_OPERACIONES`, distinto del permiso de consulta: un token
  que solo valida correos no puede desbloquear ni resetear. Si el setting falta, esas operaciones responden 503
  `JWT_CONFIGURATION_ERROR` y la Etapa 2 sigue funcionando.
- Con token de usuario (Teams) solo se puede operar sobre la propia cuenta (`USER_MISMATCH`).

## Diferencias con Python (correcciones intencionales)

| Caso | Python | Java |
|---|---|---|
| `{"email": 123}` o lista | 500 `INTERNAL_ERROR` | 400 `INVALID_EMAIL` |
| `ALLOWED_EMAIL_DOMAINS` vacío | Acepta cualquier dominio | 503 `CONFIGURATION_ERROR` |
| Reintentos a Graph | Hasta ~60 s | Máximo ~8 s; `Retry-After` con tope de 2 s |
| Búsqueda directa con 400 | 502 `GRAPH_ERROR` | Sigue con el filtro |
| Correlación | `x-correlation-id` | También acepta `Transaccion-Id` (lineamientos) y responde ambos headers |
| Respuesta | 7 campos | Mismos 7 campos y orden + `cuentaHabilitada` (aditivo) |

Todo lo demás (códigos de error, orden de validaciones, reglas JWT, headers de seguridad) se mantiene.

## Probar en local

Los simuladores reemplazan a los servicios externos (solo fuera de Azure; en Azure la app ignora esos settings):

| Servicio | Simulador | Puerto | Qué simula |
|---|---|---|---|
| Microsoft Graph | `docker/simulador-graph` | 8080 | Usuarios de `usuarios.json` |
| Azure Automation (ARM) | `docker/simulador-automation` | 8081 | Jobs de los runbooks; el resultado depende del usuario |
| Bot (Agente HelpIA) | `docker/simulador-bot` | 8082 | Recibe el callback y verifica la firma HMAC (`/callbacks` lista lo recibido) |
| Storage (Task hub, límites) | Azurite | 127.0.0.1:10010-10012 | Blob, Queue y Table |

### Opción A: todo en Docker

```powershell
gradle test
gradle azureFunctionsPackage --no-daemon
docker compose up -d --build
```

Luego ejecutar `docker/pruebas.http` (27 escenarios). Apagar con `docker compose down`.

### Opción B: Function con Core Tools (depurable con F5)

```powershell
docker compose up -d --build azurite simulador-graph simulador-automation simulador-bot
docker compose stop functions                          # libera el 7071 si el contenedor estaba arriba
gradle azureFunctionsPackage --no-daemon
cd build/azure-functions/helpia-functions; func start
```

`local.settings.json` (no se versiona; partir de `local.settings.example.json`) debe apuntar a los simuladores con los
mismos valores de `docker/local.env`, cambiando los hosts por `localhost` y Azurite por `127.0.0.1:10010-10012`.
El paquete copia ese archivo de la raíz. En VS Code, F5 ("Attach to Java Functions") levanta los simuladores,
empaqueta, arranca el host y conecta el depurador en el puerto 5005.

### Notas

- `--no-daemon`: en Windows el plugin de Azure Functions deja el `.jar` bloqueado en el daemon de Gradle y el segundo
  empaquetado falla con `Unable to delete file`.
- `JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8`: Java 17 usa el charset del sistema (Windows-1252 en Windows, ASCII en la
  imagen Linux de Functions) y las tildes de los mensajes del runbook llegan rotas al bot. También va en Azure.
- Core Tools no exige function key en local: el escenario 16 de `pruebas.http` responde 200 (en Docker, 401).
- Los límites (3 por día; celular cada 24 h) y las operaciones quedan en Azurite. Para empezar de cero:
  `docker compose up -d --force-recreate azurite`.

## App settings en Azure

Ver [docs/DESPLIEGUE.md](docs/DESPLIEGUE.md). La Managed Identity necesita `User.Read.All` en Microsoft Graph
(el mismo permiso que usa hoy el servicio Python), "Automation Job Operator" sobre la cuenta de Automation y
acceso de datos al storage del Task hub y de los límites.

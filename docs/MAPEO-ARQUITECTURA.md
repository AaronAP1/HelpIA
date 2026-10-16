# Mapeo: arquitectura HelpIA v4 y servicio Python → proyecto Java

Referencias: diagrama "HelpIA v4.1 · Callback Asíncrono + Trazabilidad", acta de la Fase 3
y repositorio Python del servicio anterior.

## 1. Elementos del diagrama (zona Cómputo / Function App) → código

| Diagrama | Implementación Java |
|---|---|
| Azure Function App (NUEVA) | Este proyecto (`build.gradle`, `host.json`) |
| Managed Identity | `infrastructure/identity/AzureTokenProvider` (Graph, Azure Resource Manager, Table Storage) |
| "genera y propaga correlationId" | `common/CorrelationIdResolver` → instanceId de Durable → parámetro `CorrelationId` del runbook → callback |
| "persiste en Task hub" | `host.json` → `extensions.durableTask.hubName = %DURABLE_HUB_NAME%` |
| "crea job en Automation" (paso 5) | `function/activity/StartRunbookActivity` + `infrastructure/automation/AutomationClient.createJob` |
| "polling corto (≈3 intentos x 2 s) → 200" | `function/http/OperationStarter.waitShortly` (`SYNC_WAIT_POLLS`, `SYNC_WAIT_INTERVAL_MS`) |
| "si no completa en 3 vueltas → 202 → async (PENDIENTE)" | `OperationResponses.pending` + evento `ModoEntrega=ASINCRONO` |
| "RetryOptions" | `function/orchestration/RetryOptions` (Automation y callback) |
| "CreateTimer → wait-with-timeout" | `AccountOperationOrchestrator.execute` (timers + plazo `JOB_MAX_WAIT_SECONDS`) |
| "resultado (async)" (paso 8) | `function/activity/GetRunbookJobStatusActivity` + `RunbookResultParser` |
| CALLBACK POST `/helpia/callback/{correlationId}` (paso 10) | `function/activity/SendCallbackActivity` + `infrastructure/callback/CallbackClient` |
| Secreto HMAC del callback en Key Vault | App setting `CALLBACK_HMAC_SECRET` como referencia `@Microsoft.KeyVault(...)` + `HmacSigner` |
| `validate-jwt` (productos de APIM de Teams y WhatsApp) | Se valida en APIM **y** en la Function: `auth/JwtValidator` (defensa en profundidad, igual que Python). `JWT_TENANT_ID` acepta los dos emisores (tenant de colaboradores para Teams, tenant de apps OAuth de APIM para WhatsApp) |
| Productos de distinta confianza | Permiso propio para operaciones (`JWT_REQUIRED_PERMISSION_OPERACIONES`, `JwtValidator.validateForOperations`); con token de usuario (Teams) solo la propia cuenta |
| Validaciones del acta: existencia, habilitación, alcance, dos dominios | `application/AccountResolver` + `DefaultAccountScopePolicy` |
| Límites: 3 usos/día, 1 cambio de celular cada 24 h | `application/RateLimitService` + `infrastructure/storage/TableRateLimitStore` |

## 2. Endpoints

| Diagrama | Etapa | Ruta implementada | Origen |
|---|---|---|---|
| `GET /api/health` | Real | `GET /api/health` | Migrado de Python |
| `POST /usuarios/validar-existencia` | Real (enrolamiento) | `POST /api/usuarios/validar-existencia` | Migrado de Python |
| — (ruta actual del servicio Python) | — | `POST /api/v1/entra/users/exists` | Se mantiene para no romper al consumidor; retirar luego |
| `POST /usuario/desbloquear` → 202 | Etapa 3 | `POST /api/usuario/desbloquear` | Nuevo (Durable) |
| `POST /api/usuarios/resetear` → 202 | Etapa 3 | `POST /api/usuarios/resetear` | Nuevo (Durable) |
| `PUT /usuarios/celular` → 202 | Etapa 3 | `PUT /api/usuarios/celular` | Nuevo (Durable) |
| — | — | `GET /api/operaciones/{correlationId}` | Nuevo: respaldo si el callback falla |

Las rutas de Etapa 3 replican el diagrama tal cual (mezcla `usuario`/`usuarios`). Homologar con Arquitectura antes
de publicar el contrato en SwaggerHub; APIM puede reescribir la ruta pública sin tocar el backend.

## 3. Funciones de Azure registradas

| Nombre de función | Tipo | Clase |
|---|---|---|
| `Health` | HTTP | `function/http/HealthFunction` |
| `UserExists` | HTTP (ruta Python) | `function/http/UserExistsFunction` |
| `ValidarExistenciaUsuario` | HTTP (ruta diagrama) | `function/http/UserExistsFunction` |
| `DesbloquearUsuario` | HTTP + cliente Durable | `function/http/AccountOperationFunctions` |
| `ResetearUsuario` | HTTP + cliente Durable | `function/http/AccountOperationFunctions` |
| `ActualizarCelular` | HTTP + cliente Durable | `function/http/AccountOperationFunctions` |
| `ConsultarOperacion` | HTTP + cliente Durable | `function/http/AccountOperationFunctions` |
| `AccountOperationOrchestrator` | Orquestador | `function/orchestration/AccountOperationOrchestrator` |
| `StartRunbookActivity` | Activity | `function/activity/StartRunbookActivity` |
| `GetRunbookJobStatusActivity` | Activity | `function/activity/GetRunbookJobStatusActivity` |
| `SendCallbackActivity` | Activity | `function/activity/SendCallbackActivity` |

**No renombrar** el orquestador ni las activities una vez desplegados.

## 4. Servicio Python → Java (archivo por archivo)

| Python | Java |
|---|---|
| `function_app.py` (FunctionApp + blueprint) | Anotaciones `@FunctionName` en `function/http/*` |
| `host.json` | `host.json` (+ `routePrefix` explícito y configuración de Durable) |
| `local.settings.json` (versionado por error) | `local.settings.example.json` (plantilla) + `.gitignore` corregido |
| `requirements.txt` | `build.gradle` + `gradle.properties` |
| `app/health_logic.py` | `HealthFunction` |
| `router.py` → `_get_correlation_id` | `common/CorrelationIdResolver` (agrega `Transaccion-Id`) |
| `router.py` → `_json_response` | `function/http/ResponseFactory` |
| `router.py` → `_read_email_from_request` | `HttpSupport.readJsonObject` + `EmailValidator` |
| `router.py` → `_known_error_response` / `_internal_error_response` | `HttpSupport.handle` |
| `router.py` → `health`, `user_exists` | `HealthFunction`, `UserExistsFunction` |
| `authentication.py` → `_load_jwt_configuration` | `config/JwtSettings` |
| `authentication.py` → `require_jwt`, `_validate_authorization_claims` | `auth/JwtValidator` (nimbus-jose-jwt) |
| `authentication.py` → `_build_graph_credential`, `get_graph_access_token` | `infrastructure/identity/AzureTokenProvider` |
| `user_find_logic.py` → `validate_email` | `application/EmailValidator` |
| `user_find_logic.py` → `_graph_get`, `_create_http_session` | `infrastructure/graph/GraphUserDirectory` |
| `user_find_logic.py` → `_get_exact_user`, `find_user` | `application/UserLookupService` + `UserExistsResult` |
| `AuthenticationError`, `RouterError`, `UserFindError` | `common/ApiException` + `common/ErrorCatalog` |
| `tools/generate_local_token.py` + `.local-jwt/*.pem` | Eliminado: los tests firman tokens en memoria; para local existe `JWT_LOCAL_BYPASS` (bloqueado en Azure) |

## 5. Cambios de comportamiento respecto a Python (intencionales)

| Caso | Python | Java |
|---|---|---|
| `{"email": 123}` o `{"email": [..]}` | 500 `INTERNAL_ERROR` | 400 `INVALID_EMAIL` |
| `{"email": false}` | 400 `EMAIL_REQUIRED` | 400 `INVALID_EMAIL` |
| `ALLOWED_EMAIL_DOMAINS` presente pero vacío | Acepta cualquier dominio | 503 `CONFIGURATION_ERROR` (falla cerrado) |
| Reintentos a Graph | Hasta ~60 s, `Retry-After` sin tope | Presupuesto de 8 s, `Retry-After` con tope de 2 s |
| Búsqueda directa responde 400 | 502 `GRAPH_ERROR` | Se trata como no encontrado y se usa el filtro |
| Configuración | Se leía en cada request | Se carga una vez; si es inválida, cada request recibe el mismo 503 |
| Respuesta de users/exists | 7 campos | Mismos 7 campos y orden + `cuentaHabilitada` (aditivo) |
| Header de correlación | `x-correlation-id` | Acepta `Transaccion-Id` o `x-correlation-id`; responde ambos |

Todo lo demás (códigos de error, orden de validaciones, headers de seguridad, reglas JWT) se mantiene.

## 6. Pendientes y dependencias

| Tema | Responsable | Impacto en el código |
|---|---|---|
| Criterio confiable de cuenta nominal (acuerdo 6 del acta) | IAM / Seguridad | `DefaultAccountScopePolicy` + `$select` de Graph |
| ¿Entrada por usuario de red o email en validar-existencia? | Producto / Proveedor del bot | `UserExistsFunction` |
| Quién genera y envía la contraseña temporal (SMS) | Proveedor del bot / Seguridad | Runbook de reseteo (ver CONTRATO-RUNBOOKS.md) |
| ¿`mobilePhone` se sincroniza a Entra ID? | Infraestructura | Permitiría resolver "¿tiene celular?" sin ir a on-premise |
| Formato de lineamientos (`datos`, headers obligatorios) para endpoints nuevos | Chapter Lead | `ResponseFactory` (cambio centralizado) |
| Contar intentos aceptados vs. operaciones exitosas | Producto | `RateLimitService` |
| Homologar rutas de Etapa 3 y agregar `GET /operaciones/{correlationId}` al diagrama | Arquitectura | Anotaciones `route` + OpenAPI |
| Nombres reales de runbooks y del Hybrid Worker Group | Infraestructura | App settings |
| Tenant IDs reales de los dos emisores y nombre del permiso de operaciones en el App Registration | IAM / Seguridad | `JWT_TENANT_ID`, `JWT_REQUIRED_PERMISSION_OPERACIONES` |
| Verificación de identidad en WhatsApp (token de aplicación: la Function no sabe quién escribe) | Proveedor del bot / Seguridad | Bot (OTP / celular registrado) antes de llamar a la API |
| Storage Account del Task hub y plan de la Function App (VNet + PE: Premium o Flex) no figuran en el diagrama ni en el costo | Cloud / Arquitectura | Infraestructura |

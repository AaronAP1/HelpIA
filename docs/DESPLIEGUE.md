# Despliegue, identidades y reemplazo del servicio Python

## 1. App settings por ambiente (nunca en el repo)

| Setting | Dev / Cert / Prod |
|---|---|
| `FUNCTIONS_WORKER_RUNTIME` | `java` |
| `FUNCTIONS_EXTENSION_VERSION` | `~4` |
| `AzureWebJobsStorage__accountName` | Cuenta de storage del ambiente (conexión por identidad) |
| `DURABLE_HUB_NAME` | `HelpiaDev`, `HelpiaCert`, `HelpiaProd` (distinto por ambiente y por app paralela) |
| `APPLICATIONINSIGHTS_CONNECTION_STRING` | Del recurso de Application Insights |
| `APPLICATIONINSIGHTS_ENABLE_AGENT` | `true` (captura los logs de java.util.logging) |
| `AZURE_CLIENT_ID` | Solo si se usa identidad user-assigned |
| `JAVA_TOOL_OPTIONS` | `-Dfile.encoding=UTF-8` (Java 17 en Linux arranca en ASCII y rompe las tildes de los mensajes) |
| `JWT_TENANT_ID` | Tenants emisores separados por coma (Teams y WhatsApp), los mismos que valida APIM |
| `JWT_ALLOWED_AUDIENCES`, `JWT_ALLOWED_CLIENT_IDS`, `JWT_REQUIRED_PERMISSION` | Mismos valores que valida APIM |
| `JWT_REQUIRED_PERMISSION_OPERACIONES` | Permiso de desbloqueo/reseteo/celular (distinto de `EmailValidation.Read`); sin él esas operaciones responden 503 |
| `ALLOWED_EMAIL_DOMAINS` | `pacifico.com.pe,epps.com.pe,pacificovida.com.pe` (confirmar) |
| `AUTOMATION_ACCOUNT_RESOURCE_ID` | `/subscriptions/.../automationAccounts/<cuenta>` |
| `AUTOMATION_HYBRID_WORKER_GROUP` | Grupo del ambiente (Dev/Cert usa la VM de pruebas) |
| `AUTOMATION_RUNBOOK_DESBLOQUEO` / `_RESETEO` / `_CELULAR` | Nombres publicados |
| `CALLBACK_BASE_URL` | URL del Agente HelpIA del ambiente |
| `CALLBACK_HMAC_SECRET` | `@Microsoft.KeyVault(SecretUri=https://<key-vault>.vault.azure.net/secrets/<nombre>)` |
| `RATE_LIMIT_TABLE_ENDPOINT` | `https://<cuenta>.table.core.windows.net` |
| `JWT_LOCAL_BYPASS` | No definir (si se define en Azure, la app responde 503) |

## 2. Permisos de la Managed Identity

| Recurso | Permiso | Lo otorga |
|---|---|---|
| Microsoft Graph | `User.Read.All` (aplicación, con consentimiento de administrador) | IAM |
| Cuenta de Automation | Automation Job Operator (crear y leer jobs) | Cloud / DevOps |
| Key Vault | Key Vault Secrets User | Cloud / DevOps |
| Storage (Task hub) | Storage Blob Data Owner, Storage Queue Data Contributor, Storage Table Data Contributor | Cloud / DevOps |
| Storage (límites) | Storage Table Data Contributor (misma cuenta u otra) | Cloud / DevOps |

Si el storage tiene private endpoints, Durable necesita los de **blob, queue y table** con sus zonas DNS privadas.
El Task hub no se crea a mano: Durable lo crea al arrancar.

Para el Hybrid Worker (on-premise): cuenta con permisos delegados solo sobre las OUs nominales
(desbloquear, resetear contraseña, escribir `mobile`).

## 3. Reemplazo del servicio Python sin corte

Una Function App ejecuta un solo lenguaje y los slots no permiten swap entre lenguajes distintos.

**Opción A: app nueva.** Desplegar la app Java en paralelo → probar con los mismos requests →
cambiar el backend en APIM → retirar Python. Rollback: volver a apuntar APIM al backend anterior.

**Opción B: conservar la Function App actual.** App puente temporal con Java → APIM apunta al puente →
convertir la app actual a Java y desplegar → APIM vuelve a la app actual → eliminar el puente.
Con identidad user-assigned los permisos sobreviven si hubiera que recrear la app.

En ambos casos: nueva function key (actualizar el named value de APIM) y `DURABLE_HUB_NAME` distinto por app.

Orden recomendado:
1. Desplegar Java con `Health` y validar-existencia; validar que la respuesta coincide con Python.
2. Cambiar el backend en APIM Dev → validación del proveedor del bot → Cert → Prod.
3. Publicar en APIM las operaciones de Etapa 3 solo cuando Automation, runbooks y callback estén listos.
   Mientras no estén en APIM, nadie puede invocarlas aunque estén desplegadas.

## 4. Cuidados propios de Durable

- No renombrar orquestador ni activities desplegados.
- Cambios incompatibles en el orquestador: desplegar sin instancias en curso o crear `...V2`.
- Purga del historial: el input incluye el celular en la operación de cambio de número. Programar una purga
  periódica de instancias terminadas (por ejemplo, mayores a 30 días) según la política de datos personales.
- Plan Premium o Flex Consumption con al menos una instancia siempre lista (arranque en frío de Java).

## 5. Pipeline

El workflow `.github/workflows/ci.yml` compila, ejecuta tests y empaqueta (`azureFunctionsPackage`).
El despliegue a ambientes debe usar los workflows del chapter (release → rc → Certify Release → Production),
con login OIDC a Azure (sin secretos en GitHub). El paquete a desplegar queda en `build/azure-functions/<appName>`.

# Contrato entre la Function y los runbooks (Azure Automation + Hybrid Worker)

## Parámetros que envía la Function

| Parámetro | Ejemplo | Notas |
|---|---|---|
| `CorrelationId` | `7f3c...` | Registrar en todos los logs del runbook |
| `Operacion` | `DESBLOQUEO`, `RESETEO`, `ACTUALIZACION_CELULAR` | |
| `Usuario` | `mjperez` | sAMAccountName |
| `Dominios` | `pacificoseguros.com,pacificovida.local` | Separados por coma. Desbloqueo: todos los del usuario. Reseteo/celular: uno |
| `Celular` | `987654321` | Solo en `ACTUALIZACION_CELULAR` |

## Salida esperada

La **última línea JSON** del stream Output debe ser:

```json
{"resultado":"OK","codigo":"DESBLOQUEADO","mensaje":"Cuenta desbloqueada","correlationId":"7f3c..."}
```

- `resultado`: `OK` o `ERROR`.
- `codigo`: código de negocio (`DESBLOQUEADO`, `RESETEADO`, `CELULAR_ACTUALIZADO`, `SIN_PERMISOS`, `USUARIO_NO_ENCONTRADO`...).
- `mensaje`: texto apto para mostrar al colaborador.

Reglas:
- **Nunca** escribir la contraseña temporal en la salida ni en los logs: la salida del job queda almacenada en Automation
  y su resultado viaja por el historial de Durable y por el callback.
- El reseteo debe desbloquear y resetear en una sola ejecución (acordado en el acta).
- Ser idempotente: Durable puede consultar el mismo job varias veces; el job solo se crea una vez (nombre determinista).
- Si el job termina en `Failed`/`Stopped` o sin JSON válido, la Function lo informa como `FALLIDO`.

## Esqueleto de referencia (PowerShell)

```powershell
param(
    [Parameter(Mandatory)] [string] $CorrelationId,
    [Parameter(Mandatory)] [string] $Operacion,
    [Parameter(Mandatory)] [string] $Usuario,
    [Parameter(Mandatory)] [string] $Dominios,
    [string] $Celular
)
$ErrorActionPreference = 'Stop'
try {
    foreach ($dominio in $Dominios.Split(',')) {
        Unlock-ADAccount -Identity $Usuario -Server $dominio
    }
    @{ resultado = 'OK'; codigo = 'DESBLOQUEADO'; mensaje = 'Cuenta desbloqueada'; correlationId = $CorrelationId } |
        ConvertTo-Json -Compress | Write-Output
}
catch {
    @{ resultado = 'ERROR'; codigo = 'ERROR_AD'; mensaje = 'No se pudo completar la operación'; correlationId = $CorrelationId } |
        ConvertTo-Json -Compress | Write-Output
}
```

El módulo ActiveDirectory usa ADWS (TCP 9389) hacia los controladores de dominio; confirmar con Infraestructura
los puertos abiertos desde los Hybrid Workers.

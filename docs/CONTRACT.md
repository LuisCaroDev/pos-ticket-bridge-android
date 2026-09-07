# Contrato con desktop

Fuente de referencia para la compatibilidad HTTP/JSON de POS Ticket Bridge Android. Humanos y la skill `pos-ticket-bridge-contract` consultan este documento; las reglas se actualizan aquí sin copiarlas al README ni a las skills.

La web POS no debe distinguir entre el bridge desktop y el Android. La implementación observable del desktop es la autoridad inicial del contrato de red. Si contradice este documento, resolver explícitamente la discrepancia antes de introducir un cambio incompatible.

Consultar desktop en modo lectura salvo solicitud expresa para modificar ambos proyectos. Para decisiones internas de Android, consultar [Arquitectura Android](ARCHITECTURE.md).

## Referencias

La referencia inicial está en `D:\Develop\ventysfy\pos-ticket-bridge`:

- `src/core/server.ts`: rutas, autenticación, CORS, códigos y respuestas.
- `src/core/print-job-contract.ts`: `PrintJobV1` y schemas de bloques.
- `src/core/types.ts`: impresoras, configuración y diagnósticos.
- `tests/bridge.test.ts`: casos observables.

Si esa ruta no está disponible, usar fixtures Android versionados y documentar las suposiciones en el cambio correspondiente.

### Compatibilidad de red

- Preservar las rutas públicas del desktop para health, estado/configuración, diagnósticos, CRUD/descubrimiento/prueba de impresoras, `/print`, `/open-drawer` y `/test/:printerId`.
- Preservar `x-agent-token`, semántica CORS, envelopes, errores estables, códigos HTTP, nombres de campos y comportamiento de ausencia frente a `null`. CORS no sustituye autenticación.
- Decodificar JSON no confiable estrictamente con `kotlinx.serialization` y discriminador `type`; rechazar campos desconocidos salvo que el desktop los acepte intencionalmente.
- Separar decodificación de validación de dominio. Añadir límites de seguridad de recursos sin invalidar silenciosamente payloads válidos de versión 1.
- Mantener `PrintJobV1.version == 1`, anchos, nombres y bloques `image`, `text`, `table-row`, `separator`, `feed`, `qr`, `barcode`, `cut` y `open-drawer`.
- Tratar `jobId`, cuando exista, como clave de idempotencia durable sin cambiar el JSON de la petición.
- Preservar el comportamiento síncrono de `/print` salvo que se diseñe y pruebe una extensión compatible.
- Si Android no implementa una capacidad, conservar la forma de la ruta y devolver un error estable de soportado/no soportado, no un payload específico de plataforma.
- Añadir campos de respuesta sólo si los clientes anteriores pueden ignorarlos. Un cambio incompatible necesita aprobación explícita y una versión o endpoint nuevo.

### Evidencia contractual

- Mantener fixtures JSON independientes del lenguaje para peticiones válidas, inválidas y respuestas representativas.
- Probar exactamente método, ruta, headers, status, campos, ausencia/null y discriminadores.
- Ejecutar los mismos fixtures contra Kotlin y, cuando sea práctico, contra desktop.


## Verificación

- Tests de rutas: autenticación, CORS, JSON inválido, códigos HTTP y cuerpos de respuesta.
- Ejecutar las comprobaciones aplicables siguiendo [Verificación de Android](ARCHITECTURE.md#verificación).

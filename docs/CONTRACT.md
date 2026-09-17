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

### Alcance implementado de impresión V1

- Se implementan las cuatro rutas consumidas actualmente por Ventysfy POS:
  `GET /health`, `POST /print`, `POST /open-drawer` y
  `POST /test/:printerId`.
- `/health` anuncia las impresoras persistidas en Room con la forma pública
  `{ id, nombre, tipo }`.
- Los IDs publicados se generan al crear desde el nombre con la normalización y
  sufijos de unicidad del desktop. No son una entrada editable en Android y no
  cambian al renombrar una impresora existente.
- Las otras tres rutas validan token e identidad y realizan impresión física
  síncrona por TCP, Bluetooth Classic o Android USB Host. `/health` conserva
  `{ id, nombre, tipo }` y publica `tipo: "usb"` como desktop.
- Los bloqueos nativos se conservan en el mismo envelope y se distinguen como
  `local_network_permission_required`, `bluetooth_permission_required` o
  `usb_permission_required`; no se solicita permiso desde una petición HTTP.
- `/print` valida el contrato completo `PrintJobV1`, incluidos los nueve tipos
  de bloque. El bloque `text` conserva su rechazo de campos desconocidos; los
  objetos que Zod acepta de forma no estricta conservan esa tolerancia.
- Los bloques `image` se contienen dentro del ancho imprimible y se centran de
  forma implícita, igual que desktop; el payload no necesita un campo `align`.
- CORS admite solicitudes sin `Origin`, refleja únicamente localhost del bridge
  u orígenes persistidos por el usuario, y permite preflight sin token.
- Los orígenes configurables se validan como orígenes base `http` o `https`, sin
  credenciales, ruta, query ni fragmento; se normalizan y deduplican antes de
  persistir. La representación en DataStore y el comportamiento HTTP existente
  no cambian.
- Status/configuración/diagnósticos/CRUD/descubrimiento HTTP permanecen
  aplazados; el CRUD de impresoras sólo está disponible en la app Android.
- V1 no persiste trabajos ni aplica idempotencia durable a `jobId`.
- Como `src/core/print-queue.ts` de desktop, Android comparte una cola en memoria
  por destino entre impresión, cajón y pruebas, incluyendo borradores de UI.
  `/print`, `/open-drawer` y `/test/:printerId` mantienen sus respuestas síncronas
  y esperan su turno hasta completar el transporte. No se agregan endpoints ni
  campos de estado; Bluetooth se identifica por MAC en Android y USB se agrupa
  conservadoramente por VID/PID, incluso si existe un número de serie.
- `/test/:printerId` y la prueba de una configuración sin guardar usan el mismo
  ticket de prueba que desktop. Android añade únicamente la línea centrada
  `mobile` debajo del encabezado.

### HTTPS local

- El transporte seleccionable HTTP/HTTPS usa el puerto persistido por la app, las cuatro
  rutas V1, `x-agent-token`, payloads y respuestas síncronas existentes.
- Con HTTPS activo, `suggestedHosts` en `/health` contiene únicamente la IPv4
  certificada con esquema `https`. Los clientes deben usar esa URL, sin sustituirla
  por localhost ni otra interfaz. No se agrega una API remota para administrar CA.
- CORS incluye las variantes HTTPS de localhost/127.0.0.1 y refleja
  `Access-Control-Allow-Private-Network: true` en preflight que lo solicite sólo si
  el origen está autorizado o ausente, igual que desktop.
- Descarga temporal separada en el puerto reservado por variante (`9978` release,
  `9988` debug y `9998` releaseCheck): `GET`/`HEAD` de `/setup/android.cer`,
  `/setup/windows.cer`, `/setup/macos.cer` y `/setup/ios.mobileconfig`. Sólo material
  público, misma subred, `no-store`, `nosniff`, diez minutos por defecto (configurable con `POS_BRIDGE_HTTPS_SETUP_TTL_MS`). No contiene token ni
  permite impresión. La CA y el nombre del perfil terminan en `mobile`.
- Los estados de recuperación de certificados/red se presentan en la app; no se
  cambia el envelope de las operaciones de impresión. Android guía la instalación
  y retirada de confianza del sistema sin reproducir la automatización de desktop.

### Evidencia contractual

- Mantener fixtures JSON independientes del lenguaje para peticiones válidas, inválidas y respuestas representativas.
- Mantener una captura ESC/POS generada por desktop para comprobar byte a byte
  los bloques nativos de recibo con XPrinter XP-E260L en español. El texto
  compatible con CP858, incluidas las tildes, debe permanecer nativo y no usar
  el fallback raster.
- Probar exactamente método, ruta, headers, status, campos, ausencia/null y discriminadores.
- Ejecutar los mismos fixtures contra Kotlin y, cuando sea práctico, contra desktop.


## Verificación

- Tests de rutas: autenticación, CORS, JSON inválido, códigos HTTP y cuerpos de respuesta.
- Ejecutar las comprobaciones aplicables siguiendo [Verificación de Android](ARCHITECTURE.md#verificación).

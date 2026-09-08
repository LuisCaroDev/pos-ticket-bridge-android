# Arquitectura Android

Fuente de referencia para la arquitectura y las prácticas de implementación de POS Ticket Bridge Android. Humanos y la skill `pos-ticket-bridge-android` consultan este documento; las decisiones se actualizan aquí sin copiarlas al README ni a las skills.

Las versiones y valores instalados se consultan en `gradle/libs.versions.toml`, `app/build.gradle.kts`, `settings.gradle.kts` y `app/src/main/AndroidManifest.xml`. El stack siguiente expresa decisiones objetivo: comprobar los build files y el código antes de asumir que una dependencia o funcionalidad existe.

Para cambios en comportamiento HTTP o payloads compartidos, consultar también [Contrato con desktop](CONTRACT.md).

## Invariantes del proyecto

- Mantener la aplicación 100 % nativa en Kotlin; no introducir React Native, Flutter, Capacitor ni un runtime Node embebido.
- Conservar el application ID, namespace, SDK mínimo y SDK objetivo configurados, salvo que el usuario solicite una migración explícita.
- Comenzar con un único módulo `:app` y paquetes cohesivos. Dividir módulos sólo cuando exista una necesidad demostrada.
- Declarar dependencias mediante `gradle/libs.versions.toml` y confirmar que están instaladas antes de usarlas.

## Stack objetivo

| Responsabilidad | Decisión |
|---|---|
| UI | Jetpack Compose y Material 3 |
| Estado | `ViewModel`, `StateFlow` y flujo unidireccional |
| Concurrencia | Kotlin Coroutines y Flow |
| Navegación | Navigation Compose tipada |
| JSON | `kotlinx.serialization` |
| Datos estructurados | Room |
| Preferencias pequeñas | DataStore |
| Inyección | Hilt |
| Ejecución persistente | `ForegroundService` de tipo `connectedDevice` |
| HTTP | Interfaz propia; Ktor Server CIO es candidato sujeto a validación |
| Impresión | Encoder ESC/POS independiente de los transportes |
| Transportes | TCP, Bluetooth Classic y Android USB Host; BLE después |

## Estado implementado de impresión V1

- `BridgeForegroundService` arranca desde la actividad visible, es dueño de Ktor
  Server CIO y permanece foreground con el tipo `connectedDevice`. En Android 17
  no puede arrancar ni reiniciarse sin `ACCESS_LOCAL_NETWORK` concedido.
- El runtime publica `Starting`, `Running`, `Failed` y `Stopped` mediante un
  repositorio de aplicación con `StateFlow`.
- DataStore conserva el token y una lista de múltiples orígenes CORS; el puerto
  permanece fijo en `9977`.
- `BridgeHttpServer` aísla Ktor de las rutas. Las operaciones síncronas generan
  ESC/POS y escriben por TCP, Bluetooth Classic o USB Host; no existe aún cola durable.
- Room conserva múltiples impresoras y respalda el CRUD de la app. DataStore
  sigue conservando token y orígenes CORS.
- El encoder implementa los nueve bloques `PrintJobV1`, fuentes A/B/C, perfiles
  de caracteres y fallback raster. Los transportes sólo reciben bytes.
- Los perfiles automáticos verificados viven en un catálogo tipado compartido
  por UI y encoder, equivalente al catálogo desktop. IDs, anchos admitidos,
  coincidencias USB, encoding, code table y política nativa no se duplican en
  composables ni en ramas específicas del encoder.
- Para recibos nativos, texto, estilos, tablas e imágenes usan las mismas
  secuencias y métricas que el encoder desktop; una captura desktop versionada
  protege la paridad byte a byte del perfil XPrinter XP-E260L en español.
- El bloque `cut` avanza tres líneas antes de accionar el cortador para sacar el
  último contenido de la distancia física entre el cabezal y la cuchilla.
- Guardar CORS reinicia el servidor de forma controlada. No existe receiver de
  arranque del dispositivo y el cierre del servicio libera el servidor y scopes.
- La pantalla principal presenta host y token en una sola tarjeta compacta. La
  URL dinámica prioriza la red Wi-Fi o Ethernet activa, luego un hotspot local y
  finalmente localhost. Las alternativas permanecen en el menú contextual del
  host y las interfaces móviles o VPN no se anuncian como utilizables.
- La pantalla principal advierte antes del estado del runtime cuando el ahorro
  de batería global está activo o la app no está exenta de las optimizaciones de
  batería. Cada aviso abre el ajuste oficial correspondiente y se recalcula al
  volver a primer plano; las restricciones adicionales de fabricantes no se
  infieren porque Android no ofrece una API común para ellas.
- Las impresoras guardadas aparecen inmediatamente debajo de la conexión, con
  búsqueda local, prueba directa y edición o eliminación desde su menú. Un FAB
  abre el alta de impresoras sin introducir una pantalla intermedia.
- El usuario no edita identificadores de impresora. Al crear, el repositorio
  genera desde el nombre un slug compatible con desktop y resuelve colisiones
  con sufijos `-2`, `-3`, etc.; al editar, el ID permanece estable.
- El alta usa un asistente de dos pasos: descubrimiento o entrada manual de la
  conexión, seguido por datos operativos. Red escanea el `/24` activo bajo
  acción explícita, Bluetooth sólo enumera emparejadas y USB sólo ofrece
  dispositivos con endpoint Bulk OUT. Los perfiles predefinidos son una opción
  básica visible. El idioma del ticket y la combinación efectiva de encoding y
  code table también permanecen visibles en modo automático; los presets
  conocidos parten de español para evitar que el idioma del sistema active un
  fallback raster inesperado. Sólo encoding, code table y Unicode personalizados
  se pliegan.
- El editor de impresora conserva sus campos en un único `PrinterEditUiState`
  propiedad del `BridgeViewModel`; antes de editar espera a cargar la entidad
  de Room y Compose mantiene localmente sólo el estado visual del asistente. En
  edición, Guardar exige que el estado válido difiera de la configuración
  inicial. Ajustes modela los orígenes CORS como una lista en
  `AllowedOriginsUiState`: el `ViewModel` agrega, elimina, valida y conserva el
  borrador, mientras Compose sólo representa el campo y los chips. Guardar se
  habilita únicamente si la lista o el texto pendiente difieren del estado
  persistido.
- El segundo paso mantiene feedback propio y permite probar la configuración
  aún no guardada reutilizando el ticket, encoder y transporte reales, sin crear
  una impresora temporal ni una ruta HTTP adicional.
- El ticket de prueba móvil conserva contenido, orden, estilos, avance y corte
  del ticket desktop; añade únicamente `mobile` centrado inmediatamente debajo
  del encabezado para identificar el origen de la prueba.
- Room v2 conserva el perfil automático anterior y añade USB, idioma, modo de
  perfil, encoding, code table y estrategia Unicode personalizada.
- Los permisos caducables se reportan por transporte. Una prueba USB iniciada
  por el usuario vuelve a solicitar autorización tras reconectar el dispositivo;
  el servidor HTTP nunca intenta abrir diálogos de permisos en segundo plano.
- Bluetooth Classic no inicia descubrimiento activo. En Android 12 o posterior
  omite `cancelDiscovery()` si no existe `BLUETOOTH_SCAN`, porque esa API exige
  el permiso de escaneo aun cuando la conexión RFCOMM use un equipo emparejado.
- El selector Bluetooth usa la clase pública reportada por Android y pistas de
  nombres conocidos para mostrar iconos y priorizar posibles impresoras. La
  clasificación es informativa: nunca oculta dispositivos genéricos.
- Navigation Compose tipada separa la operación diaria de una pantalla de
  Ajustes; la edición de múltiples orígenes CORS vive únicamente en Ajustes.
  El `NavHost` declara el mismo fade para avance, retroceso y retroceso
  predictivo, sin depender de transiciones predeterminadas de la librería.
- La primera ejecución explica red local y notificaciones antes de abrir cada
  diálogo del sistema. Red local bloquea sólo el runtime; notificaciones son
  opcionales. Bluetooth y USB conservan solicitudes contextuales.
- La distribución directa usa una APK release firmada, R8 y reducción de
  recursos. El keystore y sus credenciales permanecen fuera de Git y son un
  respaldo obligatorio para poder actualizar instalaciones existentes.
- La variante `debug` usa el sufijo de aplicación `.debug`, el sufijo de versión
  `-debug` y la etiqueta `POS Ticket Bridge Dev`. Desarrollo y producción pueden
  convivir instalados y sus firmas y datos permanecen aislados.

## Estructura y responsabilidades

```text
Compose Screen
    | eventos                    ^ UiState
    v                            |
ViewModel ----------------- StateFlow
    |                            ^
    v                            |
Repository <- Room/DataStore ---+
    ^
    |
BridgeForegroundService
    |-- HTTP server
    |-- durable print dispatcher
    |-- runtime state
    `-- PrinterTransport
        |-- TCP
        |-- Android USB Host
        `-- Bluetooth Classic
```

Los paquetes iniciales viven bajo el namespace configurado y se organizan por responsabilidad: `app`, `contract`, `domain`, `data`, `bridge`, `printing` y `ui`.

### UI, estado y validación

- Las pantallas se construyen con Compose y Material 3.
- Una pantalla no trivial tiene un `ViewModel` de pantalla. Expone `StateFlow` de un `UiState` inmutable y recibe eventos explícitos; la UI lo recolecta respetando el ciclo de vida.
- El estado puramente visual de un widget permanece en el composable con `rememberSaveable`.
- Un formulario usa un solo estado inmutable, métodos explícitos para entradas, validación síncrona y estados explícitos de envío y resultado.
- Los validadores de dominio retornan códigos de error estables. Compose los traduce a recursos; el dominio no contiene tipos Android ni texto destinado al usuario.
- Los composables no llaman directamente DAOs, transportes ni el servidor HTTP.

### Servicio y runtime

- `BridgeForegroundService` es dueño del servidor HTTP y la coordinación síncrona de impresión; la notificación lo mantiene foreground. Activities, composables y ViewModels no son dueños de sockets.
- El servicio se ejecuta inicialmente en el proceso principal. El arranque al iniciar el dispositivo es una opción de configuración, no una obligación implícita.
- El estado de salud se publica mediante un repositorio de alcance de aplicación con `StateFlow`.
- Inicio y parada son idempotentes. Sockets, interfaces USB, conexiones Bluetooth y scopes de corrutinas se liberan de forma determinista.

### Datos y concurrencia

- Los repositorios son la fuente compartida de verdad para servicio y UI.
- Room almacena impresoras en V1. La futura cola durable almacenará trabajos, registros de idempotencia y diagnósticos acotados. DataStore almacena preferencias pequeñas.
- V1 no recupera trabajos interrumpidos ni reintenta `jobId`; la solicitud HTTP permanece abierta hasta terminar la escritura o devolver error.
- Usar corrutinas estructuradas; nunca `GlobalScope`. El I/O bloqueante no corre en el hilo principal.
- Serializar trabajos por impresora física con un worker o mutex indexado, permitiendo que impresoras distintas trabajen en paralelo.
- Modelar ciclos de vida del runtime y de los trabajos con estados sellados o enums, no con booleanos independientes.

### Impresión

- El dominio depende de `PrinterTransport`; TCP, Android USB Host, Bluetooth Classic y BLE quedan aislados en implementaciones.
- El encoder ESC/POS no depende de APIs Android y se prueba mediante fixtures de bytes.
- Distinguir escritura completada en el transporte de impresión confirmada. Cuando no exista acknowledgement, exponer la advertencia en diagnósticos.
- Aplicar timeouts, límites de tamaño, cancelación, reintentos acotados y etapas de diagnóstico en las fronteras de I/O.

El flujo objetivo es:

```text
POST /print
  -> autenticar y decodificar JSON
  -> validar dominio y comprobar jobId
  -> serializar por impresora
  -> generar ESC/POS
  -> enviar por PrinterTransport
  -> guardar resultado/diagnóstico
  -> responder de forma compatible
```

### Motor HTTP

El motor queda detrás de una interfaz propia. No escribir un parser HTTP artesanal. Ktor Server CIO sólo queda adoptado después de que un build release supere pruebas de solicitudes sostenidas, pantalla apagada/background, reinicio, memoria y R8 en hardware físico.

## Verificación

- Tests unitarios: validadores, ViewModels, bytes ESC/POS, transiciones de cola y casos de uso sin transporte.
- Instrumentación: ciclo de vida del servicio, migración Room, permisos, USB, Bluetooth y comportamiento Compose.
- Finalizar cambios materiales con las comprobaciones aplicables: `./gradlew.bat test`, `./gradlew.bat lint` y `./gradlew.bat assembleDebug`.
- `./gradlew.bat connectedAndroidTest` requiere emulador o dispositivo. TCP, Bluetooth, USB, reinicio y background prolongado requieren hardware real; informar claramente lo que quede pendiente.

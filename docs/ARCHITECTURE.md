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
| HTTP/HTTPS | Interfaz propia; Ktor Server Netty con JSSE de Android |
| Impresión | Encoder ESC/POS independiente de los transportes |
| Transportes | TCP, Bluetooth Classic y Android USB Host; BLE después |

## Estado implementado de impresión V1

- `BridgeForegroundService` arranca desde la actividad visible, es dueño de Ktor
  Server Netty y permanece foreground con el tipo `connectedDevice`. En Android 17
  no puede arrancar ni reiniciarse sin `ACCESS_LOCAL_NETWORK` concedido.
- El runtime publica `Starting`, `Running`, `Failed` y `Stopped` mediante un
  repositorio de aplicación con `StateFlow`.
- DataStore conserva el token, el puerto y una lista de múltiples orígenes CORS.
  Las instalaciones nuevas usan `9977` en release, `9987` en debug y `9997` en
  releaseCheck; cada instalación puede cambiarlo desde Ajustes.
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
- Cada bloque de imagen establece centrado ESC/POS explícitamente antes de
  imprimir, sin depender de la alineación heredada del bloque anterior.
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
- La notificación ofrece «Detener» mediante un PendingIntent explícito e inmutable
  al servicio no exportado. Retira la notificación y termina el servicio con
  `START_NOT_STICKY`; el cierre libera HTTP/HTTPS y la descarga temporal de CA.
  El arranque automático de la actividad ocurre en `onStart`, no en `onResume`,
  para que cerrar el panel de notificaciones no deshaga la parada. Volver a abrir
  la app desde segundo plano inicia de nuevo el bridge conservando su configuración.

### Datos y concurrencia

- Los repositorios son la fuente compartida de verdad para servicio y UI.
- Room almacena impresoras en V1. La futura cola durable almacenará trabajos, registros de idempotencia y diagnósticos acotados. DataStore almacena preferencias pequeñas.
- V1 no recupera trabajos interrumpidos ni reintenta `jobId`; la solicitud HTTP permanece abierta hasta terminar la escritura o devolver error.
- `PrintCoordinator`, compartido por aplicación entre HTTP/HTTPS y pruebas de UI,
  usa `PrintQueue` en memoria para serializar generación, escritura y cierre por
  destino: host normalizado/puerto TCP, dirección Bluetooth o VID/PID USB. Los
  alias y borradores comparten cola; USB agrupa conservadoramente el modelo para
  cubrir configuraciones con y sin número de serie. Otros destinos avanzan en
  paralelo. Los waiters del mutex son FIFO; errores y cancelaciones liberan su
  turno, y las entradas sin usuarios se eliminan atómicamente. La cancelación
  conserva su tipo y no se convierte en un error de impresora inaccesible.
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

El motor queda detrás de `BridgeHttpServer`. Netty sirve HTTP o HTTPS en el puerto
persistido;
CIO 3.5.2 no admite TLS y se conserva únicamente para la descarga temporal de la
CA pública. Netty usa JSSE de Android, TLS 1.2/1.3 según disponibilidad, grupos de
hilos acotados y HTTP/1.1. No se empaqueta OpenSSL nativo ni se escribe un parser HTTP.

### HTTPS local mobile

- `LocalHttpsController` pertenece al servicio foreground y serializa activación,
  pausa, recuperación, cambio de red y restablecimiento junto con sus listeners.
  `HttpsRepository` publica estado y canaliza órdenes; los ViewModels no son dueños
  de sockets. El servicio comprueba red y vigencia cada cinco segundos.
- Cada instalación genera una CA ECDSA P-256 de diez años con CN
  `POS Ticket Bridge <UUID> mobile`. El certificado de servidor incluye la IPv4 en
  SAN y `serverAuth`, dura como máximo 365 días y se renueva a treinta días de
  vencer. Cambiar de IP conserva la CA. Si desaparece la interfaz, HTTPS se detiene
  y se recupera al regresar; no degrada automáticamente a HTTP.
- El registro se cifra con AES-GCM y una clave no exportable de Android Keystore,
  y se escribe con `AtomicFile` en `noBackupFilesDir`. Un archivo corrupto no se
  reemplaza silenciosamente. Desactivar conserva la CA; restablecer elimina el
  material del registro y requiere instalar una CA nueva en los clientes.
- El listener HTTPS se liga sólo a la IPv4 privada elegida. La UI y `/health`
  anuncian esa dirección real; no ofrecen localhost u otras IP fuera del SAN.
  `LocalBridgeClient` confía sólo en la CA propia para la prueba local y mantiene
  la validación de hostname; no depende de instalar confianza en el sistema.
- El asistente Material ocupa una pantalla con dos pasos: sistema operativo y
  descarga/instalación. El QR contiene sólo la URL pública de la CA; el listener
  HTTP de inscripción se abre durante diez minutos por defecto, restringe la subred y se cierra al salir,
  cambiar de sistema/red, vencer o detener el servicio. iOS recibe `.mobileconfig`;
  Android, Windows y macOS reciben `.cer` DER.
- La instalación de confianza de una CA en Android es manual desde Ajustes. Servir
  HTTPS no requiere instalarla en el propio teléfono; usar el navegador de ese
  teléfono como POS sí requiere completar el flujo de confianza. Restablecer no
  desinstala automáticamente las CA que el usuario instaló en sus dispositivos.
- Ajustes mantiene un borrador en `HttpsViewModel` y Guardar conexión aplica una
  transición con rollback del listener y configuración previa si falla. La pantalla
  principal conserva host/token compactos y añade acceso al asistente y detalles
  desplegables del certificado.

La validación de distribución debe incluir R8 y solicitudes TLS verificadas en
hardware, además de pantalla apagada, reinicio y cambios de Wi-Fi/hotspot. La firma
release requiere el keystore privado configurado en la máquina.

Netty crea `NioServerSocketChannel` por reflexión: la regla R8 conserva su
constructor público sin argumentos, además de los métodos que
`ResourceLeakDetector.addExclusions` busca por nombre en los allocators y
utilidades de buffers, y el fallback de `MethodHandles`/field updaters de
`ConcurrentSkipListIntObjMultimap` usado en Android. Compilar no basta para validar esta ruta;
hay que arrancar la APK optimizada y consultar `/health` en hardware. La variante
`releaseCheck` hereda R8 y reducción de recursos de release, usa firma debug e ID
`.releasecheck` para reproducir problemas sin reemplazar producción. Release,
debug y releaseCheck usan por defecto pares de puertos separados (`9977/9978`,
`9987/9988` y `9997/9998`) para poder ejecutarse a la vez.
Los fallos de arranque se registran con la etiqueta `BridgeRuntime`. El runtime
conserva el error mientras el listener esté detenido, informa conflictos de puerto
y reintenta también HTTP durante la reconciliación periódica.

Cambiar el puerto desde Ajustes valida el rango `1..65535` y excluye el puerto de
inscripción de la variante. El servicio abre primero el listener candidato; sólo
después persiste el valor y cierra el listener anterior. Un fallo de enlace o de
persistencia conserva el puerto y listener previos. La operación comparte el mutex
de las transiciones HTTPS, cierra cualquier descarga temporal activa y conserva la
CA y el certificado. La UI recuerda que debe actualizarse la URL configurada en el POS.

R8 también debe conservar la anotación heredada `ChannelHandler.Sharable` y las
clases anotadas, además de los nombres de callbacks que `ChannelHandlerMask`
resuelve por reflexión y su anotación `Skip`. Sin ello, el listener puede aparecer
activo y aceptar TCP sin responder HTTP; conexiones posteriores fallan con
`NettyChannelInitializer is not a @Sharable handler`. La verificación de release
incluye varias conexiones independientes, no sólo arrancar el servicio. Ejecutar
`scripts/test-release-endpoints.ps1 -BaseUrl http://<IP>:9977` contra la APK
optimizada; también admite HTTPS con una CA confiada por el cliente. Comprueba
health repetido, autenticación de las tres rutas POST y preflight sin imprimir.

## Verificación

- Tests unitarios: validadores, ViewModels, bytes ESC/POS, transiciones de cola y casos de uso sin transporte.
- Instrumentación: ciclo de vida del servicio, migración Room, permisos, USB, Bluetooth y comportamiento Compose.
- Finalizar cambios materiales con las comprobaciones aplicables: `./gradlew.bat test`, `./gradlew.bat lint` y `./gradlew.bat assembleDebug`.
- `./gradlew.bat connectedAndroidTest` requiere emulador o dispositivo. TCP, Bluetooth, USB, reinicio y background prolongado requieren hardware real; informar claramente lo que quede pendiente.

### Configuración pública del asistente HTTPS

Copia `.env.example` a `.env` en la raíz del proyecto y recompila la APK. Se
conservan los nombres del desktop: `POS_BRIDGE_HTTPS_VIDEO_IOS`,
`POS_BRIDGE_HTTPS_VIDEO_ANDROID`, `POS_BRIDGE_HTTPS_VIDEO_WINDOWS`,
`POS_BRIDGE_HTTPS_VIDEO_MACOS`, sus equivalentes `POS_BRIDGE_HTTPS_GUIDE_*` y
`POS_BRIDGE_HTTPS_SETUP_TTL_MS`. La precedencia es variable de entorno del proceso
Gradle, propiedad Gradle `-P`, archivo `.env`, valor predeterminado. Android los
incorpora al compilar; cambiar el entorno después de instalar no modifica la app.
El lector `.env` admite asignaciones de una línea con comillas opcionales, sin
expansión de variables ni comentarios al final del valor.

Los videos vacíos o inválidos se ocultan. Las guías vacías o inválidas usan la
referencia oficial del sistema operativo. Los enlaces personalizados deben ser
HTTPS, con host y sin credenciales. El asistente también conserva un enlace
explícito a la guía oficial cuando hay una guía personalizada. La duración debe
ser un número entero de milisegundos entre 1 y 2147483647; si no lo es, se usan
600000 ms. La UI y el servidor usan la misma configuración validada. Sólo estas
nueve opciones públicas se incorporan a BuildConfig; no se empaqueta el archivo
`.env` ni otras variables. No coloques secretos en enlaces de ayuda.

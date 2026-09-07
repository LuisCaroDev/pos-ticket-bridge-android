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
| Transportes | TCP primero; Android USB Host y Bluetooth después |

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
        `-- Bluetooth
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

- `BridgeForegroundService` es dueño del servidor HTTP, el dispatcher durable de impresión, los recursos de transporte y la notificación foreground. Activities, composables y ViewModels no son dueños de esos recursos.
- El servicio se ejecuta inicialmente en el proceso principal. El arranque al iniciar el dispositivo es una opción de configuración, no una obligación implícita.
- El estado de salud se publica mediante un repositorio de alcance de aplicación con `StateFlow`.
- Inicio y parada son idempotentes. Sockets, interfaces USB, conexiones Bluetooth y scopes de corrutinas se liberan de forma determinista.

### Datos y concurrencia

- Los repositorios son la fuente compartida de verdad para servicio y UI.
- Room almacena impresoras, perfiles, trabajos durables, registros de idempotencia y diagnósticos acotados. DataStore almacena preferencias pequeñas.
- Un trabajo se persiste antes de enviarse. La recuperación tras interrupción es deliberada y `jobId` evita repetir salida física.
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
  -> persistir en Room
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
- Instrumentación: ciclo de vida del servicio, integración Room, permisos, USB, Bluetooth y comportamiento Compose.
- Finalizar cambios materiales con las comprobaciones aplicables: `./gradlew.bat test`, `./gradlew.bat lint` y `./gradlew.bat assembleDebug`.
- `./gradlew.bat connectedAndroidTest` requiere emulador o dispositivo. USB, Bluetooth, reinicio y background prolongado requieren hardware real; informar claramente lo que quede pendiente.

# POS Ticket Bridge Android

Aplicación Android nativa prevista para exponer localmente el contrato HTTP de POS Ticket Bridge desktop y enviar trabajos ESC/POS a impresoras de red, USB o Bluetooth.

## Estado

La V1 de impresión real usa un servicio foreground en el puerto `9977`, conserva
impresoras en Room y envía ESC/POS por red TCP, Bluetooth Classic o USB Host. La app permite
crear, editar, probar y eliminar impresoras; BLE y cola durable quedan para
iteraciones posteriores.

## Compilar una APK release

La firma es privada y nunca se guarda en Git. En la primera preparación ejecuta:

```powershell
.\scripts\generate-release-keystore.ps1
```

El script crea el keystore fuera del repositorio y un `keystore.properties`
local con las credenciales. Respalda ambos archivos: sin la misma clave no es
posible instalar futuras actualizaciones sobre una versión ya distribuida.

Para producir la APK firmada y su checksum SHA-256:

```powershell
.\gradlew.bat packageDistribution
```

Los archivos quedan en `app/build/outputs/distribution/`. Verifica la firma con
`apksigner verify --verbose --print-certs <apk>`. En cada teléfono Android se
debe permitir una vez la instalación desde la aplicación usada para abrir la APK.

## Permisos de Android

- Red local: necesaria en Android 17/API 37 para activar el servidor y las impresoras LAN.
- Notificaciones: recomendadas en Android 13 o posterior, pero no bloquean el bridge.
- Bluetooth: se solicita sólo cuando el usuario elige una impresora emparejada.
- USB: Android autoriza cada dispositivo al seleccionarlo o volverlo a conectar.
- Internet, estado de red y foreground service se conceden al instalar y no muestran popup.

## Documentación

- [Arquitectura Android](docs/ARCHITECTURE.md): stack, estado, persistencia, servicio, transportes y verificación. Referencia de la skill `pos-ticket-bridge-android`.
- [Contrato con desktop](docs/CONTRACT.md): compatibilidad HTTP/JSON, referencia desktop y pruebas contractuales. Referencia de la skill `pos-ticket-bridge-contract`.

Cada decisión se mantiene en su documento correspondiente. Las skills locales de `.codex/skills/` indican cuándo consultarlo y cómo aplicarlo; humanos y agentes consumen las mismas fuentes.

## Orden del MVP

1. ~~Arquitectura, estado, foreground service y notificación.~~
2. ~~Servidor mock con `/health`, `/print`, `/open-drawer`, `/test/:printerId`, token y CORS.~~
3. Cola durable, idempotencia y diagnósticos.
4. ~~Configuración e impresión TCP, Bluetooth Classic y USB Host con contrato completo.~~
5. ~~Resto de bloques ESC/POS.~~
6. ~~Android USB Host.~~
7. BLE y endurecimiento adicional para POS dedicado.

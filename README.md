# POS Ticket Bridge Android

Aplicación Android nativa prevista para exponer localmente el contrato HTTP de POS Ticket Bridge desktop y enviar trabajos ESC/POS a impresoras de red, USB o Bluetooth.

## Estado

El repositorio contiene el template de Android Studio; el bridge aún no está implementado.

## Documentación

- [Arquitectura Android](docs/ARCHITECTURE.md): stack, estado, persistencia, servicio, transportes y verificación. Referencia de la skill `pos-ticket-bridge-android`.
- [Contrato con desktop](docs/CONTRACT.md): compatibilidad HTTP/JSON, referencia desktop y pruebas contractuales. Referencia de la skill `pos-ticket-bridge-contract`.

Cada decisión se mantiene en su documento correspondiente. Las skills locales de `.codex/skills/` indican cuándo consultarlo y cómo aplicarlo; humanos y agentes consumen las mismas fuentes.

## Orden del MVP

1. Arquitectura, estado y navegación.
2. Foreground service y notificación.
3. Servidor con `/health`, token y CORS.
4. `PrintJobV1` y fixtures.
5. Configuración de impresora TCP.
6. Cola durable, idempotencia y diagnósticos.
7. Impresión TCP real: texto, feed y corte.
8. Resto de bloques ESC/POS.
9. Android USB Host.
10. Bluetooth y endurecimiento para POS dedicado.

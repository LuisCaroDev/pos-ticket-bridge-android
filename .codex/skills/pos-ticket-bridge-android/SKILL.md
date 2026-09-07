---
name: pos-ticket-bridge-android
description: Apply the native Android architecture of POS Ticket Bridge when changing Kotlin, Compose UI, app state, persistence, foreground-service lifecycle, print queues, or printer transports in this repository. Also use the contract skill when HTTP routes or shared payloads are affected.
---

# POS Ticket Bridge Android

Read [Arquitectura Android](../../../docs/ARCHITECTURE.md) completely before implementation or architectural review.

Inspect the current Gradle catalog, build files, manifest, and relevant code to distinguish installed functionality from the planned stack. Apply the document's verification guidance to the affected components.

When HTTP behavior or shared payloads are affected, also use `pos-ticket-bridge-contract`. Update architectural decisions in the referenced document; keep this skill focused on routing and workflow.

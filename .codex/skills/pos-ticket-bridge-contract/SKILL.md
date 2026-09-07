---
name: pos-ticket-bridge-contract
description: Preserve wire compatibility with desktop POS Ticket Bridge when changing Android HTTP routes, JSON DTOs, validation, authentication, printer configuration, print jobs, drawer commands, health responses, diagnostics, or contract tests.
---

# POS Ticket Bridge Contract

Read [Contrato con desktop](../../../docs/CONTRACT.md) completely before changing or reviewing wire behavior.

Use its reference locations to compare the affected desktop routes, DTOs, and tests with Android code and fixtures. Check observable request and response behavior using the document's compatibility and verification guidance.

When the change also affects Android runtime ownership, persistence, UI, or transports, also use `pos-ticket-bridge-android`. Update compatibility decisions in the referenced document; keep this skill focused on routing and workflow.

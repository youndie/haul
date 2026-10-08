---
id: haul-shared
title: Haul shared contract
type: service
module: shared
tech_stack: [Kotlin Multiplatform, kotlinx.serialization, kompot-core]
owner: unassigned
depends_on: []
publishes:
  - nothing — a module of this build
---

# Haul shared contract

> Drafted before the module exists: every path below is planned.

## 1. Responsibility

The one place the server and the client agree: the Haul components on the wire (`HaulHeader`,
`ProductCard`, `CartLine`, `OrderProgress`, … — each `@Serializable`, `@KompotComponentMarker`,
wire type `haul_<snake_case>`), the route classes, the request bodies of commands, the closed
`ErrorCode` enum, and money and time types.

Deliberately does **not**: hold logic, defaults that a screen depends on, or anything only one side
reads.

## 2a. Code anchors

| File (planned) | What is there |
|---|---|
| `shared/src/commonMain/kotlin/io/github/youndie/haul/shared/ui/` | the Haul components |
| `shared/src/commonMain/kotlin/io/github/youndie/haul/shared/ErrorCode.kt` | every error code |
| `shared/build.gradle.kts` | targets: jvm, wasmJs; the KSP registry processor |

## 4. Dependencies

| Kind | Name | What for |
|---|---|---|
| Library | kompot-core, kompot-standard, kompot-forms | the tree, the standard components, forms |

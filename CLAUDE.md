# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this
repository.

## Critical Rules

- **Never change security-sensitive code** (crypto, keystore, biometrics, auth, migration) without
  explicit instruction
- **Use `Result<S, E>`** from `core:util` for error handling — never exceptions for expected
  failures
- **Mirror sibling modules** when unsure — consistency over novelty
- **Keep business logic out of composables** and Android framework classes

## Build & Test

```bash
./gradlew build                        # Full build
./gradlew test                         # All unit tests
./gradlew :app:test                    # Module-specific tests (preferred for small changes)
./gradlew assemblePlayStoreDebug       # APK build
```

- Flavors: `playStore` (default), `fdroid`. Types: `debug`, `release`.
- Rust: `./gradlew :rust:buildRust -PbuildRust=true` (disabled by default)
- CI branch: `v2`

## Tech Stack

Kotlin 2.3.20 · AGP 9.1.0 · JVM 17 · Compile SDK 36 · Min SDK 26 · `-Xcontext-parameters`

## Project Structure

Android password manager using Clean Architecture per module:
`domain/` → `data/` → `presentation/` → `di/`

| Module                     | Purpose                                                                  |
|----------------------------|--------------------------------------------------------------------------|
| `:app`                     | Navigation, auth/session flow, autofill service                          |
| `:core:security`           | Crypto, biometrics, Android Keystore                                     |
| `:core:identity`           | Key wrapping, auth data, proto schemas (`core/identity/src/main/proto/`) |
| `:core:item`               | Room database, password/item entities                                    |
| `:core:ui`                 | Shared composables and UI utilities                                      |
| `:core:util`               | Shared utilities, `Result` type                                          |
| `:feature:*`               | `list_screen`, `item:{core,create,view}`, `credentials`, `totp`          |
| `:automation`              | Automation support + annotation processor                                |
| `:migration-create-access` | v1 → v2 data migration (high risk)                                       |
| `:rust`                    | Passkey operations via Rust JNI                                          |

## Key Patterns

**Result type** — Sealed `Result<S, E>` with `Success`/`Failure`. Use helpers: `onSuccess()`,
`mapSuccess()`, `mapFailure()`, `zip()` (2-4 way), `getOrNull()`, `asUnitResult()`,
`Boolean.asResult()`, `S?.asResult()`.

**Koin DI** — `@Single`, `@Factory`, `@KoinViewModel`, `@Module`, `@ComponentScan`.
Composition root: `app/di/Koin.kt`. Wire dependencies in the most local owning module.

**Navigation** — Type-safe `@Serializable` route objects implementing `RouteDestination`.

**ViewModels** — `StateFlow` state, event/action/state pattern. Orchestrate use cases only.

## Security

`KeyStoreManager`, `BiometricCryptoController`, `Session` (active DEK). Wrapped keys in proto
DataStore: `biometric_key_data.pb`, `password_key_data.pb`. Do not change key lifecycle, wrapping,
prompt flow, or persistence semantics without explicit instruction.

## Sensitive Areas

- **Migration** — preserve backward compat, smallest safe change
- **Autofill** (`app/.../autofill/`) — constrained by Android framework, keep conservative
- **Rust FFI** — preserve type/memory-safety across JNI boundary
- **Room schema** — check migration implications before changing entities

## Testing

- kotlin-test + MockK + kotlinx-coroutines-test; Compose UI tests with Espresso
- Use `runTest { }`, `mockk(relaxed = true)`, `coEvery { }`, assert against `Result`
- Run broader tests for cross-module or security changes

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
make -C rust/rust-code test            # Rust unit tests
make -C rust/rust-code bindgen         # Generate uniffi bindings
./gradlew build                        # Full build
./gradlew test                         # All unit tests
./gradlew :app:test                    # Module-specific tests (preferred for small changes)
./gradlew assemblePlayStoreDebug       # APK build
```

- Flavors: `playStore` (default), `fdroid`. Types: `debug`, `staging`, `release`.
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
| `:core:item`               | Room database, login/item entities                                       |
| `:core:ui`                 | Shared composables and UI utilities                                      |
| `:core:util`               | Shared utilities, `Result` type                                          |
| `:feature:*`               | `list_screen`, `item:{core,create,view}`, `credentials`, `totp`, `vault` |
| `:automation`              | Automation support + annotation processor                                |
| `:migration-create-access` | v1 → v2 data migration (high risk)                                       |
| `:rust`                    | Rust crypto/passkey ops via UniFFI-generated Kotlin bindings             |

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

## Key Hierarchy

```
Password / Biometric
      ↓ derive / unlock
   RootKek ───────────────────── never persisted
      ↓ unwrap
   ARK (Account Root Key) ────── Session (in-memory); wrapped in account_registry.pb;
                                 optionally escrowed in backup_ark_data.pb (see Backup Escrow)
      ↓ unwrap (one per vault)
   VaultKey ──────────────────── wrapped in VaultEntity.keyInformation (Room)
      ↓ unwrap (one per item)
   ItemKey ───────────────────── wrapped in ItemEntity.keyInformation (Room)
      ↓ encrypt (AAD = itemId + vaultId)
   SecretData (ciphertext) ───── stored in login/passkey/totp entity fields
```

- **RootKek** — password path: Argon2 over `(password, salt)`; biometric path: hardware
  cipher from Android Keystore. Never stored.
- **ARK** — wrapped twice: `PasswordWrappedArk` and optionally `BiometricWrappedArk`,
  both inside `ProtoAccount` in `account_registry.pb`.
- **VaultKey / ItemKey** — wrapped with AES-256-GCM; moving items between vaults
  re-wraps only the ItemKey, not the ciphertext.
- **AAD** (`itemId + vaultId`) — bound to every ciphertext; prevents transplant attacks.
- **Rust FFI** (`de.davis.keygo.rust`) implements all wrap/unwrap/derive operations.

## Backup Escrow

A scheduled backup runs with no user present, so it cannot reach the ARK the normal way. Scheduling
one therefore escrows a second copy of the ARK, and the export passphrase alongside it, under
Keystore aliases that deliberately do **not** require user authentication (`KeyId.BackupArkKey`,
`KeyId.BackupPassphraseKey`; see `BackupArkUnlocker`).

This is the one place the "ARK is never readable without authenticating" rule is relaxed, so it
carries its own rules:

- The escrow exists **only while a job is scheduled**. `CleanupBackupResourcesUseCase` releases it
  the moment no live job remains, and `reconcile()` sweeps up jobs the scheduler dropped without a
  run. `BackupWorker.MAX_ATTEMPTS` bounds retries so a deferring job cannot hold it open forever.
- `reconcile()` runs once per process start (`BackupEscrowReconciler`, an eager Koin singleton), not
  on entry to the backup screen. A dropped job produces no run to clean up after it, so the trigger
  must not depend on the user navigating anywhere; process start bounds the escrow's stale lifetime
  to a single process. Do not move it back behind a UI event.
- Both aliases set `setUnlockedDeviceRequired(true)` on API 28+. On API 26-27 that constraint does
  not exist, so on those levels the escrow is readable whenever the process runs.
- A passphrase-sealed scheduled backup is not stronger than an ARK-sealed one: both keys sit under
  the same auth-free policy.
- Do not widen the escrow's lifetime, its auth policy, or the set of callers that can read it
  without explicit instruction.

## Sensitive Areas

- **Migration** — preserve backward compat, smallest safe change
- **Autofill** (`app/.../autofill/`) — constrained by Android framework, keep conservative
- **UniFFI** — preserve memory and type safety across the FFI boundary.
- **Room schema** — check migration implications before changing entities

## Code Style

- **Brace-less `if`/`else`** for single-expression branches — even if the expression spans multiple
  lines (e.g. a `viewModelScope.launch { … }` block). Only use braces when a branch contains
  multiple statements.
- **Trailing commas** on multi-line parameter lists and collection literals.

## Testing

- kotlin-test + kotlinx-coroutines-test; Compose UI tests with Espresso
- Prefer **testFixtures-provided fakes** and concrete fake implementations as the default testing
  strategy
- Use `runTest { }` and assert against `Result`
- Prefer behavior/state assertions over interaction verification
- Use **MockK only in rare cases** where interaction verification is the actual behavior under test
  (for example, validating that a side-effecting API was invoked)
- Do not use mocks as the default way to model dependencies when a fake or testFixture exists
- Run broader tests for cross-module or security changes
- **Rust fakes** — `:rust` uses UniFFI (not raw JNI) to generate Kotlin bindings. UniFFI emits
  `KeyDeriverInterface`/`KeyWrapperInterface`/`AccountManagerInterface`/`ItemManagerInterface`/
  `VaultManagerInterface` for test seams; fakes live in `:rust` testFixtures (
  `de.davis.keygo.rust`).
  Never instantiate `KeyDeriver()`/`KeyWrapper()`/`AccountManager()`/`ItemManager()`/
  `VaultManager()`
  in JVM unit tests — their default constructors require the native Rust library at runtime.
- **testFixtures + Compose plugin** — Any module with `kotlin.compose` that enables testFixtures
  must add `testFixturesImplementation(libs.androidx.compose.runtime)` to avoid "Compose Runtime
  not on classpath" compile errors. See `:core:item` for the canonical pattern.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and
cross-file relationships.

Rules:

- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json
  exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for
  focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw
  grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do
  not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).

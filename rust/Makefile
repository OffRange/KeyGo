# KeyGo Rust build orchestrator. Run `make help` for usage.

ABIS         ?= arm64-v8a armeabi-v7a x86_64
MIN_SDK      ?= 26
PROFILE      ?= release
CRATE        := keygo-uniffi
LIB_NAME     := libkeygo_bindings.so
JNI_DIR      ?= ../core/rust/build/generated/source/uniffi/jniLibs
KOTLIN_OUT   ?= ../core/rust/build/generated/source/uniffi/kotlin/
BINDGEN_ABI  := arm64-v8a
BINDGEN_TRIPLE := aarch64-linux-android

# Dev server. The credentials are fixed on purpose: the container only listens on 127.0.0.1 and
# holds throwaway data. `make server-check` flags the weak password as a reminder of that.
SERVER_DIR   := crates/keygo-server
DB_CONTAINER ?= keygo-db
DB_IMAGE     ?= postgres:18-trixie
DB_PORT      ?= 5432
DB_URL       := postgres://keygo:keygo@localhost:$(DB_PORT)/keygo
DEV_SECRETS  := $(CURDIR)/dev-secrets
DB_READY_SECS := 30

# Rustup targets matching the Android ABIs above.
RUST_TARGETS := aarch64-linux-android armv7-linux-androideabi x86_64-linux-android

CARGO_NDK_FLAGS := $(foreach abi,$(ABIS),-t $(abi)) --platform $(MIN_SDK)

ifdef NDK
  export ANDROID_NDK_HOME := $(NDK)
endif

.PHONY: help all build bindgen tools check fmt test clean \
	dev-tools db db-stop db-reset db-logs psql migration serve server-check prepare

##@ General

help: ## Show this help
	@printf "Usage: make [target] [VAR=value]\n"
	@awk -F ':.*?## ' '/^##@ / {printf "\n\033[1m%s\033[0m\n", substr($$0, 5)} \
	  /^[a-zA-Z_-]+:.*?## / {printf "  \033[36m%-13s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)
	@printf "\nVariables:\n"
	@awk '/^# [A-Z_]+ .*= / {sub(/^# /, "  "); print}' $(MAKEFILE_LIST)

## Variables
# ABIS       ?= arm64-v8a armeabi-v7a x86_64   Android ABIs to build
# MIN_SDK    ?= 26                             Android API level for the NDK linker
# PROFILE    ?= release                        Cargo profile (release|debug)
# NDK        ?= $$ANDROID_NDK_HOME             Path to the Android NDK
# JNI_DIR    ?= ../core/rust/build/...         Output dir for the per-ABI cdylibs
# KOTLIN_OUT ?= ../core/rust/build/...         Output dir for the generated Kotlin
# DB_PORT    ?= 5432                           Host port of the dev Postgres (then `make db-reset`)
# DB_IMAGE   ?= postgres:18-trixie             Image of the dev Postgres

##@ Android library

all: build bindgen ## Build cdylibs and generate Kotlin bindings

tools: ## Install cargo-ndk and missing rustup targets (idempotent)
	@command -v cargo >/dev/null || { echo "cargo not found; install Rust via rustup" >&2; exit 1; }
	@command -v cargo-ndk >/dev/null || cargo install cargo-ndk
	@installed="$$(rustup target list --installed)"; \
	for t in $(RUST_TARGETS); do \
	  echo "$$installed" | grep -qx "$$t" || rustup target add "$$t"; \
	done

build: tools ## Build cdylibs per ABI into JNI_DIR
	cargo ndk $(CARGO_NDK_FLAGS) -o $(JNI_DIR) build --$(PROFILE) -p $(CRATE)

bindgen: build ## Generate Kotlin UniFFI bindings
	cargo run -p uniffi-bindgen -- generate \
	  --library target/$(BINDGEN_TRIPLE)/$(PROFILE)/$(LIB_NAME) \
	  --language kotlin \
	  --no-format \
	  --out-dir $(KOTLIN_OUT)

##@ Workspace
check: ## cargo check the workspace
	cargo check --workspace

fmt: ## Format Rust code with cargo fmt
	cargo fmt --all

test: ## cargo test the workspace
	cargo test --workspace

clean: ## Remove build artifacts and generated jniLibs
	cargo clean
	rm -rf $(JNI_DIR)

##@ Dev server (needs docker and sqlx-cli)

# Dev config for the server targets only. Make passes it to their prerequisites too, so `serve`
# reaches `db` with it set. DATABASE_URL is the one sqlx-cli and the query! macros read, so builds
# made here check queries against the live database; plain cargo builds use the committed .sqlx.
DEV_TARGETS := db serve server-check prepare
$(DEV_TARGETS): export KEYGO_DATABASE_URL := $(DB_URL)
$(DEV_TARGETS): export KEYGO_SECRETS_DIR := $(DEV_SECRETS)
$(DEV_TARGETS): export DATABASE_URL := $(DB_URL)
$(DEV_TARGETS): export KEYGO_ENVIRONMENT := development

dev-tools: ## Verify docker and sqlx-cli are installed
	@command -v docker >/dev/null || { echo "docker not found" >&2; exit 1; }
	@command -v sqlx >/dev/null || { echo "sqlx-cli not found: cargo install sqlx-cli --no-default-features --features postgres" >&2; exit 1; }

db: dev-tools ## Start the dev Postgres, apply migrations, create dev secrets
	@docker start $(DB_CONTAINER) >/dev/null 2>&1 || docker run -d --name $(DB_CONTAINER) \
	  -p 127.0.0.1:$(DB_PORT):5432 \
	  -e POSTGRES_USER=keygo -e POSTGRES_PASSWORD=keygo -e POSTGRES_DB=keygo \
	  -v $(DB_CONTAINER):/var/lib/postgresql $(DB_IMAGE) >/dev/null
	@# The init phase serves only the unix socket, so TCP readiness means the real server is up.
	@n=0; until docker exec $(DB_CONTAINER) pg_isready -q -h 127.0.0.1 -U keygo -d keygo; do \
	  n=$$((n + 1)); \
	  [ $$n -lt $(DB_READY_SECS) ] || { echo "Postgres not ready after $(DB_READY_SECS)s, see: make db-logs" >&2; exit 1; }; \
	  sleep 1; \
	done
	@sqlx migrate run --source $(SERVER_DIR)/migrations
	@cargo run -q -p keygo-server -- init

db-stop: ## Stop the dev Postgres, keeping its data
	-@docker stop $(DB_CONTAINER) >/dev/null 2>&1

db-reset: ## Delete the dev Postgres and its data, then recreate it
	-@docker rm -f $(DB_CONTAINER) >/dev/null 2>&1
	-@docker volume rm $(DB_CONTAINER) >/dev/null 2>&1
	@$(MAKE) --no-print-directory db

db-logs: ## Follow the dev Postgres logs
	docker logs -f $(DB_CONTAINER)

psql: ## Open a psql shell in the dev Postgres
	docker exec -it $(DB_CONTAINER) psql -U keygo -d keygo

migration: dev-tools ## Add a migration: make migration name=add_users
	@[ -n "$(name)" ] || { echo "usage: make migration name=<description>" >&2; exit 1; }
	sqlx migrate add --source $(SERVER_DIR)/migrations $(name)

serve: db ## Run the server against the dev Postgres
	cargo run -p keygo-server -- serve

server-check: db ## Run `keygo-server check` against the dev config
	cargo run -q -p keygo-server -- check

prepare: db ## Refresh the committed sqlx query data after changing a query
	cd $(SERVER_DIR) && cargo sqlx prepare

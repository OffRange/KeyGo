# KeyGo Rust build orchestrator. Run `make help` for usage.

ABIS         ?= arm64-v8a armeabi-v7a x86_64
MIN_SDK      ?= 26
PROFILE      ?= release
CRATE        := keygo-bindings
LIB_NAME     := libkeygo_bindings.so
JNI_DIR      := ../build/generated/source/uniffi/jniLibs
KOTLIN_OUT   := ../build/generated/source/uniffi/kotlin/
BINDGEN_ABI  := arm64-v8a
BINDGEN_TRIPLE := aarch64-linux-android

# Rustup targets matching the Android ABIs above.
RUST_TARGETS := aarch64-linux-android armv7-linux-androideabi x86_64-linux-android

CARGO_NDK_FLAGS := $(foreach abi,$(ABIS),-t $(abi)) --platform $(MIN_SDK)

ifdef NDK
  export ANDROID_NDK_HOME := $(NDK)
endif

.PHONY: help all build bindgen tools check fmt test clean

help: ## Show this help
	@printf "Usage: make [target] [VAR=value]\n\nTargets:\n"
	@awk -F ':.*?## ' '/^[a-zA-Z_-]+:.*?## / {printf "  \033[36m%-10s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)
	@printf "\nVariables:\n"
	@awk '/^# [A-Z_]+ .*= / {sub(/^# /, "  "); print}' $(MAKEFILE_LIST)

## Variables
# ABIS       ?= arm64-v8a armeabi-v7a x86_64   Android ABIs to build
# MIN_SDK    ?= 26                             Android API level for the NDK linker
# PROFILE    ?= release                        Cargo profile (release|debug)
# NDK        ?= $$ANDROID_NDK_HOME             Path to the Android NDK

all: build bindgen ## Build cdylibs and generate Kotlin bindings

tools: ## Install cargo-ndk and missing rustup targets (idempotent)
	@command -v cargo >/dev/null || { echo "cargo not found; install Rust via rustup" >&2; exit 1; }
	@command -v cargo-ndk >/dev/null || cargo install cargo-ndk
	@installed="$$(rustup target list --installed)"; \
	for t in $(RUST_TARGETS); do \
	  echo "$$installed" | grep -qx "$$t" || rustup target add "$$t"; \
	done

build: tools ## Build cdylibs per ABI into ../src/main/jniLibs
	cargo ndk $(CARGO_NDK_FLAGS) -o $(JNI_DIR) build --$(PROFILE) -p $(CRATE)

bindgen: build ## Generate Kotlin UniFFI bindings
	cargo run -p $(CRATE) --bin uniffi-bindgen -- generate \
	  --library target/$(BINDGEN_TRIPLE)/$(PROFILE)/$(LIB_NAME) \
	  --language kotlin \
	  --no-format \
	  --out-dir $(KOTLIN_OUT)

check: ## cargo check the workspace
	cargo check --workspace

fmt: ## Format Rust code with cargo fmt
	cargo fmt --all

test: ## cargo test the workspace
	cargo test --workspace

clean: ## Remove build artifacts and generated jniLibs
	cargo clean
	rm -rf $(JNI_DIR)

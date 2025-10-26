#!/usr/bin/env bash
set -euo pipefail

cd -- "$(dirname -- "${BASH_SOURCE[0]}" )"
script_name=$(basename "$0")

ensure_cargo_ndk() {
  if command -v cargo-ndk >/dev/null 2>&1; then
    return 0
  fi

  echo "[$script_name] cargo-ndk not found. Attempting install via 'cargo install cargo-ndk'..."

  if ! command -v cargo >/dev/null 2>&1; then
    echo "[$script_name] Error: 'cargo' is not installed. Install Rust (e.g., via rustup) and re-run." >&2
    exit 1
  fi

  # Install cargo-ndk
  if ! cargo install cargo-ndk; then
    echo "[$script_name] Error: failed to install cargo-ndk." >&2
    exit 1
  fi

  # Ensure ~/.cargo/bin is in PATH for the current shell
  CARGO_BIN_DIR="${CARGO_HOME:-$HOME/.cargo}/bin"
  if [[ ":$PATH:" != *":$CARGO_BIN_DIR:"* ]]; then
    export PATH="$CARGO_BIN_DIR:$PATH"
  fi

  # Verify it’s available now
  if ! command -v cargo-ndk >/dev/null 2>&1; then
    echo "[$script_name] Error: cargo-ndk installed but not found in PATH. Add '$CARGO_BIN_DIR' to your PATH." >&2
    exit 1
  fi

  echo "[$script_name] cargo-ndk installed successfully: $(cargo-ndk --version)"
}

ensure_android_rust_targets() {
  local -a targets=(aarch64-linux-android armv7-linux-androideabi x86_64-linux-android)

  # Need rustup
  if ! command -v rustup >/dev/null 2>&1; then
    echo "[$script_name] Error: rustup is not installed. Install Rust (e.g., via rustup) and re-run." >&2
    exit 1
  fi

  local installed missing=()
  installed="$(rustup target list --installed)"

  for t in "${targets[@]}"; do
    if ! grep -qx "$t" <<<"$installed"; then
      missing+=("$t")
    fi
  done

  if ((${#missing[@]})); then
    echo "[$script_name] Installing missing Rust Android targets: ${missing[*]}"
    # Install into the active toolchain (or specify one with: --toolchain <name>)
    rustup target add "${missing[@]}"
  else
    echo "[$script_name] All requested Rust Android targets already installed: ${targets[*]}"
  fi
}

usage() {
  cat << EOF
Usage:: $script_name [--ndk /path/to/android/and/root] [--min-platform INT_VALUE]

Builds the rust code and and exports the libraries for each ABI in the right jniLibs/<abi> folder.
This may install cargo-ndk and all required targets.

Options:
  --ndk PATH                  Tells the cargo-ndk tool where the Android NDK folder is situated
  --min-platform INT_VALUE    Tells the cargo-ndk tool what the min platform is
  -h, --help                  Show this help
EOF
}

ndk_folder=""
min_platform=""

# Parse args
while [[ $# -gt 0 ]]; do
  case "$1" in
    --ndk)
      [[ $# -ge 2 && ${2:0:1} != "-" ]] || { echo "Error: --ndk requires a value"; exit 1; }
      ndk_folder="$2"; shift 2 ;;

    --min-platform)
      [[ $# -ge 2 && ${2:0:1} != "-" ]] || { echo "Error: --min-platform requires a value"; exit 1; }
      min_platform="$2"; shift 2 ;;

    -h|--help)
      usage; exit 0 ;;
    --) shift; break ;;
    *)
      echo "Unknown argument: $1"
      usage; exit 1 ;;
  esac
done

ensure_cargo_ndk
ensure_android_rust_targets

if [[ -n "$ndk_folder" ]]; then
  echo "[$script_name] Setting ANDROID_NDK_HOME to $ndk_folder"
  export ANDROID_NDK_HOME="$ndk_folder"
fi

cmd=(cargo ndk -t armeabi-v7a -t arm64-v8a -t x86_64)
if [[ -n "$min_platform" ]]; then
  cmd+=(--platform "$min_platform")
fi
cmd+=(-o ../src/main/jniLibs build --release)

echo "[$script_name] Executing build command: ${cmd[*]}"

exec "${cmd[@]}"
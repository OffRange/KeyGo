#!/usr/bin/env bash
set -euo pipefail

# ---------------------------------------------------------------------------
# new-module.sh — scaffold a new Gradle module in the KeyGo Android project.
# Usage: scripts/new-module.sh [location] [name]
# ---------------------------------------------------------------------------

REPO_ROOT="$(git rev-parse --show-toplevel)"

usage() {
    echo "Usage: $(basename "$0") [location] [name]" >&2
    echo "  location  — parent directory (e.g. core, feature, feature/item)" >&2
    echo "  name      — module name, lowercase, e.g. my-module" >&2
    exit 1
}

# ---------------------------------------------------------------------------
# choose_menu <default_index> <item1> <item2> ...
# Renders an arrow-key menu on stderr; echoes chosen value to stdout.
# Falls back to a numbered prompt when stdin is not a TTY.
# ---------------------------------------------------------------------------
choose_menu() {
    local default_idx=$1
    shift
    local items=("$@")
    local count=${#items[@]}
    local current=$default_idx

    if [ ! -t 0 ]; then
        # Non-TTY fallback: numbered list
        local i
        for (( i=0; i<count; i++ )); do
            echo "  $((i+1))) ${items[$i]}" >&2
        done
        printf "Choose [1-%d] (default %d): " "$count" "$((default_idx+1))" >&2
        local choice
        read -r choice
        if [[ -z "$choice" ]]; then
            choice=$((default_idx+1))
        fi
        if ! [[ "$choice" =~ ^[0-9]+$ ]] || (( choice < 1 || choice > count )); then
            echo "Invalid choice." >&2
            exit 1
        fi
        echo "${items[$((choice-1))]}"
        return
    fi

    # TTY path: arrow-key menu drawn to stderr
    local render
    render() {
        local i
        for (( i=0; i<count; i++ )); do
            if (( i == current )); then
                printf "\033[1;32m  ▶ %s\033[0m\n" "${items[$i]}" >&2
            else
                printf "    %s\n" "${items[$i]}" >&2
            fi
        done
    }

    # Hide cursor, save position
    printf "\033[?25l" >&2
    render

    local key esc_buf
    while true; do
        # Move cursor back to top of menu
        printf "\033[%dA" "$count" >&2

        IFS= read -rsn1 key
        if [[ "$key" == $'\x1b' ]]; then
            # Arrow keys arrive as a 3-byte burst (ESC [ A/B); grab the next two
            # bytes together. Reading without a fractional timeout keeps this
            # compatible with bash 3.2 (macOS default), whose `read -t` only
            # accepts integer seconds.
            IFS= read -rsn2 esc_buf
            case "$esc_buf" in
                '[A') (( current > 0 )) && (( current-- )) || true ;;
                '[B') (( current < count-1 )) && (( current++ )) || true ;;
            esac
        elif [[ "$key" == "" ]]; then
            # Enter pressed — clear the menu lines
            printf "\033[%dA" "$count" >&2
            local i
            for (( i=0; i<count; i++ )); do
                printf "\033[2K\n" >&2
            done
            printf "\033[%dA" "$count" >&2
            printf "\033[?25h" >&2   # restore cursor
            echo "${items[$current]}"
            return
        fi

        render
    done
}

# ---------------------------------------------------------------------------
# Positional args
# ---------------------------------------------------------------------------
ARG_LOCATION="${1-}"
ARG_NAME="${2-}"

# ---------------------------------------------------------------------------
# Resolve location
# ---------------------------------------------------------------------------
if [[ -z "$ARG_LOCATION" ]]; then
    # Offer the known top-level module parents that exist in this repo.
    # Nested locations (e.g. feature/item) are reached via the positional arg.
    LOCATION_OPTS=()
    for d in core feature; do
        if [[ -d "$REPO_ROOT/$d" ]]; then
            LOCATION_OPTS+=("$d")
        fi
    done

    if [[ ${#LOCATION_OPTS[@]} -eq 0 ]]; then
        echo "Error: could not detect any module-parent directories." >&2
        exit 1
    fi

    echo "Select module location:" >&2
    LOCATION="$(choose_menu 0 "${LOCATION_OPTS[@]}")"
else
    LOCATION="$ARG_LOCATION"
fi

# Validate location directory exists
if [[ ! -d "$REPO_ROOT/$LOCATION" ]]; then
    echo "Error: location '$LOCATION' does not exist under repo root." >&2
    usage
fi

# ---------------------------------------------------------------------------
# Resolve name
# ---------------------------------------------------------------------------
if [[ -z "$ARG_NAME" ]]; then
    if [ ! -t 0 ]; then
        echo "Error: 'name' argument required in non-interactive mode." >&2
        usage
    fi
    printf "Module name (e.g. my-module): " >&2
    read -r ARG_NAME
fi

NAME="$ARG_NAME"

# Validate name
if ! [[ "$NAME" =~ ^[a-z][a-z0-9_-]*$ ]]; then
    echo "Error: name '$NAME' is invalid. Must match ^[a-z][a-z0-9_-]*\$." >&2
    usage
fi

# ---------------------------------------------------------------------------
# Resolve plugin
# ---------------------------------------------------------------------------
PLUGIN_OPTS=(
    "keygo.android.compose"
    "keygo.android.library"
    "keygo.kotlin.jvm"
)

echo "Select convention plugin:" >&2
CHOSEN_PLUGIN="$(choose_menu 0 "${PLUGIN_OPTS[@]}")"

# Map plugin choice to alias reference (dashes → dots already; just format)
PLUGIN_ALIAS="alias(libs.plugins.${CHOSEN_PLUGIN})"

# ---------------------------------------------------------------------------
# Derivation
# ---------------------------------------------------------------------------
# Package segment: replace - with _
PKG_SEGMENT="${NAME//-/_}"

# Namespace: de.davis.keygo.<location (/ → .)>.<pkg_segment>
LOCATION_PKG="${LOCATION//\//.}"
NAMESPACE="de.davis.keygo.${LOCATION_PKG}.${PKG_SEGMENT}"

# Source package path
PKG_PATH="${NAMESPACE//.//}"

# Module directory
MODULE_DIR="$REPO_ROOT/$LOCATION/$NAME"

# Gradle project path: :<location (/ → :)>:<name>
GRADLE_PATH=":${LOCATION//\//:}:${NAME}"

# ---------------------------------------------------------------------------
# Validation: target must not already exist
# ---------------------------------------------------------------------------
if [[ -d "$MODULE_DIR" ]]; then
    echo "Error: module directory '$MODULE_DIR' already exists." >&2
    exit 1
fi

# ---------------------------------------------------------------------------
# Phase 3 — file generation
# ---------------------------------------------------------------------------
SRC_BASE="$MODULE_DIR/src/main/kotlin/$PKG_PATH"

# Create Clean-Architecture package subdirs with .gitkeep
for subdir in domain data presentation di; do
    mkdir -p "$SRC_BASE/$subdir"
    touch "$SRC_BASE/$subdir/.gitkeep"
done

# Determine whether to include the android {} block
# (keygo.kotlin.jvm modules don't use android {})
if [[ "$CHOSEN_PLUGIN" == "keygo.kotlin.jvm" ]]; then
    cat > "$MODULE_DIR/build.gradle.kts" <<GRADLE
plugins {
    ${PLUGIN_ALIAS}
}

dependencies {
}
GRADLE
else
    cat > "$MODULE_DIR/build.gradle.kts" <<GRADLE
plugins {
    ${PLUGIN_ALIAS}
}

android {
    namespace = "${NAMESPACE}"
}

dependencies {
}
GRADLE
fi

# ---------------------------------------------------------------------------
# Phase 4 — settings wiring
# ---------------------------------------------------------------------------
SETTINGS="$REPO_ROOT/settings.gradle.kts"
INCLUDE_LINE="include(\"${GRADLE_PATH}\")"

if grep -qF "$INCLUDE_LINE" "$SETTINGS"; then
    echo "Note: $INCLUDE_LINE already present in settings.gradle.kts — skipping." >&2
else
    # Find the line number of the last include( line
    LAST_INCLUDE_LINE="$(grep -n '^include(' "$SETTINGS" | tail -1 | cut -d: -f1)"
    if [[ -z "$LAST_INCLUDE_LINE" ]]; then
        # No existing include lines; append at end
        printf '\n%s\n' "$INCLUDE_LINE" >> "$SETTINGS"
    else
        # Insert after the last include line using ed (available on macOS/Linux)
        ed -s "$SETTINGS" <<ED_SCRIPT
${LAST_INCLUDE_LINE}a
${INCLUDE_LINE}
.
w
q
ED_SCRIPT
    fi
fi

# ---------------------------------------------------------------------------
# Remove obsolete Makefile
# ---------------------------------------------------------------------------
MAKEFILE="$REPO_ROOT/Makefile"
if git -C "$REPO_ROOT" ls-files --error-unmatch Makefile &>/dev/null 2>&1; then
    git -C "$REPO_ROOT" rm -f "$MAKEFILE"
elif git -C "$REPO_ROOT" diff --name-only --cached 2>/dev/null | grep -q '^Makefile$'; then
    git -C "$REPO_ROOT" rm -f "$MAKEFILE"
elif [[ -f "$MAKEFILE" ]]; then
    rm -f "$MAKEFILE"
fi

# ---------------------------------------------------------------------------
# Success output
# ---------------------------------------------------------------------------
echo ""
echo "Module created successfully!"
echo "  Directory   : $LOCATION/$NAME"
echo "  Gradle path : $GRADLE_PATH"
echo "  Namespace   : $NAMESPACE"
echo ""
echo "Next steps:"
echo "  ./gradlew ${GRADLE_PATH}:help   (or trigger an IDE Gradle sync)"

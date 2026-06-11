#!/usr/bin/env bash
set -euo pipefail

# ---------------------------------------------------------------------------
# new-module.sh — scaffold a new Gradle module in the KeyGo Android project.
#
# Interactive (arrow-key menus):
#   scripts/new-module.sh
#
# One-liner (no prompts once location, name and --plugin are given):
#   scripts/new-module.sh feature payments -p compose
#   scripts/new-module.sh core crypto-extras -p library --protobuf --packages domain,data
#   scripts/new-module.sh feature/item edit -p compose --packages domain,data,presentation
#
# Generates:
#   <location>/<name>/build.gradle.kts                       convention plugin applied
#   <location>/<name>/src/main/kotlin/<pkg>/di/<X>Module.kt  Koin DI module
#   <location>/<name>/src/main/kotlin/<pkg>/{domain,data,presentation}/
#   include(":<location>:<name>") in settings.gradle.kts
# ---------------------------------------------------------------------------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="${NEW_MODULE_ROOT:-$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel 2>/dev/null || dirname "$SCRIPT_DIR")}"

IS_TTY=0
[[ -t 0 && -t 2 ]] && IS_TTY=1

if [[ $IS_TTY -eq 1 ]]; then
    C_RESET=$'\033[0m'
    C_BOLD=$'\033[1m'
    C_DIM=$'\033[2m'
    C_CYAN=$'\033[1;36m'
    C_GREEN=$'\033[1;32m'
    C_RED=$'\033[1;31m'
else
    C_RESET="" C_BOLD="" C_DIM="" C_CYAN="" C_GREEN="" C_RED=""
fi

# Always restore the cursor, even on Ctrl-C mid-menu.
cleanup() { [[ $IS_TTY -eq 1 ]] && printf '\033[?25h' >&2 || true; }
trap cleanup EXIT

die() {
    printf '%sError:%s %s\n' "$C_RED" "$C_RESET" "$1" >&2
    exit 1
}

usage() {
    cat >&2 <<USAGE
Usage: $(basename "$0") [location] [name] [options]

Arguments (prompted interactively when omitted):
  location              parent directory, e.g. core, feature, feature/item
  name                  module name, lowercase, e.g. my-module

Options:
  -p, --plugin <id>     convention plugin: compose | library | jvm
                        (or full id, e.g. keygo.android.compose)
      --protobuf        additionally apply keygo.android.protobuf (Android only)
      --packages <csv>  packages to create besides di: any of domain,data,presentation
                        (use --packages none for di only)
  -h, --help            show this help

Providing location, name and --plugin makes the script fully non-interactive;
--protobuf/--packages then fall back to sensible defaults instead of prompting.
USAGE
    exit "${1:-1}"
}

# ---------------------------------------------------------------------------
# Interactive widgets — drawn on stderr, result in $MENU_RESULT.
# ---------------------------------------------------------------------------
MENU_RESULT=""

menu_abort() {
    printf '\033[?25h\n%sAborted.%s\n' "$C_DIM" "$C_RESET" >&2
    exit 130
}

# read_key — reads one keypress into $KEY: up|down|enter|space|quit|other
read_key() {
    local key rest
    IFS= read -rsn1 key
    case "$key" in
        $'\x1b')
            rest=""
            IFS= read -rsn2 -t 0.05 rest || true
            case "$rest" in
                '[A') KEY=up ;;
                '[B') KEY=down ;;
                '')   KEY=quit ;;
                *)    KEY=other ;;
            esac ;;
        k) KEY=up ;;
        j) KEY=down ;;
        q) KEY=quit ;;
        ' ') KEY=space ;;
        '') KEY=enter ;;
        *) KEY=other ;;
    esac
}

# erase_menu <line_count> — erase the last <line_count> drawn lines.
erase_menu() {
    local n=$1 i
    printf '\033[%dA' "$n" >&2
    for (( i=0; i<n; i++ )); do printf '\033[2K\n' >&2; done
    printf '\033[%dA' "$n" >&2
}

# choose_one <title> <default_idx> <item>... — single-select arrow menu.
choose_one() {
    local title=$1 current=$2
    shift 2
    local items=("$@") count=$#

    if [[ $IS_TTY -eq 0 ]]; then
        local i choice
        printf '%s\n' "$title" >&2
        for (( i=0; i<count; i++ )); do
            printf '  %d) %s\n' "$((i+1))" "${items[$i]}" >&2
        done
        printf 'Choose [1-%d] (default %d): ' "$count" "$((current+1))" >&2
        read -r choice
        [[ -z "$choice" ]] && choice=$((current+1))
        [[ "$choice" =~ ^[0-9]+$ ]] && (( choice >= 1 && choice <= count )) \
            || die "invalid choice '$choice'"
        MENU_RESULT="${items[$((choice-1))]}"
        return
    fi

    printf '%s%s%s\n' "$C_BOLD" "$title" "$C_RESET" >&2
    printf '\033[?25l' >&2

    local total=$((count+1))
    draw() {
        local i
        for (( i=0; i<count; i++ )); do
            printf '\033[2K' >&2
            if (( i == current )); then
                printf '  %s▶ %s%s\n' "$C_CYAN" "${items[$i]}" "$C_RESET" >&2
            else
                printf '    %s\n' "${items[$i]}" >&2
            fi
        done
        printf '\033[2K%s  ↑/↓ move · Enter select · q quit%s\n' "$C_DIM" "$C_RESET" >&2
    }
    draw

    while true; do
        read_key
        case "$KEY" in
            up)    (( current > 0 )) && (( current-- )) || true ;;
            down)  (( current < count-1 )) && (( current++ )) || true ;;
            quit)  menu_abort ;;
            enter)
                erase_menu "$total"
                printf '\033[1A\033[2K%s%s%s %s%s%s\n' \
                    "$C_BOLD" "$title" "$C_RESET" "$C_GREEN" "${items[$current]}" "$C_RESET" >&2
                printf '\033[?25h' >&2
                MENU_RESULT="${items[$current]}"
                return ;;
        esac
        printf '\033[%dA' "$total" >&2
        draw
    done
}

# choose_multi <title> <checked_csv> <item>... — multi-select; result is CSV.
choose_multi() {
    local title=$1 checked_csv=$2
    shift 2
    local items=("$@") count=$# current=0
    local -a checked=()
    local i
    for (( i=0; i<count; i++ )); do
        if [[ ",$checked_csv," == *",${items[$i]},"* ]]; then
            checked[i]=1
        else
            checked[i]=0
        fi
    done

    collect() {
        local out="" j
        for (( j=0; j<count; j++ )); do
            (( checked[j] )) && out+="${items[$j]},"
        done
        MENU_RESULT="${out%,}"
    }

    if [[ $IS_TTY -eq 0 ]]; then
        printf '%s\n' "$title" >&2
        printf 'Comma-separated list of [%s] (default: %s, "none" for none): ' \
            "$(IFS=,; echo "${items[*]}")" "${checked_csv:-none}" >&2
        local answer
        read -r answer
        [[ -z "$answer" ]] && answer="$checked_csv"
        [[ "$answer" == "none" ]] && answer=""
        MENU_RESULT="$answer"
        return
    fi

    printf '%s%s%s\n' "$C_BOLD" "$title" "$C_RESET" >&2
    printf '\033[?25l' >&2

    local total=$((count+1))
    draw() {
        local j box
        for (( j=0; j<count; j++ )); do
            if (( checked[j] )); then box="[x]"; else box="[ ]"; fi
            printf '\033[2K' >&2
            if (( j == current )); then
                printf '  %s▶ %s %s%s\n' "$C_CYAN" "$box" "${items[$j]}" "$C_RESET" >&2
            else
                printf '    %s %s\n' "$box" "${items[$j]}" >&2
            fi
        done
        printf '\033[2K%s  ↑/↓ move · Space toggle · Enter confirm · q quit%s\n' "$C_DIM" "$C_RESET" >&2
    }
    draw

    while true; do
        read_key
        case "$KEY" in
            up)    (( current > 0 )) && (( current-- )) || true ;;
            down)  (( current < count-1 )) && (( current++ )) || true ;;
            space) (( checked[current] = !checked[current] )) || true ;;
            quit)  menu_abort ;;
            enter)
                erase_menu "$total"
                collect
                printf '\033[1A\033[2K%s%s%s %s%s%s\n' \
                    "$C_BOLD" "$title" "$C_RESET" "$C_GREEN" "${MENU_RESULT:-none}" "$C_RESET" >&2
                printf '\033[?25h' >&2
                return ;;
        esac
        printf '\033[%dA' "$total" >&2
        draw
    done
}

# confirm <question> <default y|n> — result in $MENU_RESULT (1 or 0).
confirm() {
    local question=$1 default=$2 hint answer
    if [[ "$default" == y ]]; then hint="Y/n"; else hint="y/N"; fi
    printf '%s%s%s [%s] ' "$C_BOLD" "$question" "$C_RESET" "$hint" >&2
    read -r answer
    [[ -z "$answer" ]] && answer=$default
    case "$answer" in
        y|Y|yes) MENU_RESULT=1 ;;
        n|N|no)  MENU_RESULT=0 ;;
        *) die "invalid answer '$answer'" ;;
    esac
}

# ---------------------------------------------------------------------------
# Argument parsing
# ---------------------------------------------------------------------------
LOCATION=""
NAME=""
PLUGIN_ARG=""
PROTOBUF=""
PACKAGES_ARG=""

while (( $# )); do
    case "$1" in
        -h|--help) usage 0 ;;
        -p|--plugin) [[ $# -ge 2 ]] || die "$1 requires a value"; PLUGIN_ARG=$2; shift 2 ;;
        --plugin=*) PLUGIN_ARG=${1#*=}; shift ;;
        --protobuf) PROTOBUF=1; shift ;;
        --no-protobuf) PROTOBUF=0; shift ;;
        --packages) [[ $# -ge 2 ]] || die "$1 requires a value"; PACKAGES_ARG=$2; shift 2 ;;
        --packages=*) PACKAGES_ARG=${1#*=}; shift ;;
        -*) die "unknown option '$1' (see --help)" ;;
        *)
            if [[ -z "$LOCATION" ]]; then LOCATION=$1
            elif [[ -z "$NAME" ]]; then NAME=$1
            else die "unexpected argument '$1'"
            fi
            shift ;;
    esac
done

# Fully non-interactive once the three required inputs are present.
ONE_LINER=0
[[ -n "$LOCATION" && -n "$NAME" && -n "$PLUGIN_ARG" ]] && ONE_LINER=1

# ---------------------------------------------------------------------------
# Resolve location
# ---------------------------------------------------------------------------
if [[ -z "$LOCATION" ]]; then
    [[ $IS_TTY -eq 0 ]] && die "'location' is required in non-interactive mode (see --help)"
    LOCATION_OPTS=()
    for d in core feature; do
        [[ -d "$REPO_ROOT/$d" ]] && LOCATION_OPTS+=("$d")
    done
    LOCATION_OPTS+=("custom…")
    choose_one "Module location:" 0 "${LOCATION_OPTS[@]}"
    LOCATION="$MENU_RESULT"
    if [[ "$LOCATION" == "custom…" ]]; then
        printf '%sLocation path (e.g. feature/item):%s ' "$C_BOLD" "$C_RESET" >&2
        read -r LOCATION
    fi
fi

[[ "$LOCATION" =~ ^[a-z][a-z0-9_/-]*$ ]] || die "location '$LOCATION' is invalid (lowercase path, e.g. core or feature/item)"
[[ -d "$REPO_ROOT/$LOCATION" ]] || die "location '$LOCATION' does not exist under $REPO_ROOT"

# ---------------------------------------------------------------------------
# Resolve name
# ---------------------------------------------------------------------------
if [[ -z "$NAME" ]]; then
    [[ $IS_TTY -eq 0 ]] && die "'name' is required in non-interactive mode (see --help)"
    printf '%sModule name (e.g. my-module):%s ' "$C_BOLD" "$C_RESET" >&2
    read -r NAME
fi

[[ "$NAME" =~ ^[a-z][a-z0-9_-]*$ ]] || die "name '$NAME' is invalid (must match ^[a-z][a-z0-9_-]*\$)"

MODULE_DIR="$REPO_ROOT/$LOCATION/$NAME"
[[ -e "$MODULE_DIR" ]] && die "module directory '$LOCATION/$NAME' already exists"

# ---------------------------------------------------------------------------
# Resolve convention plugin
# ---------------------------------------------------------------------------
PLUGIN_LABELS=(
    "keygo.android.compose — Android library + Compose"
    "keygo.android.library — Android library (no Compose)"
    "keygo.kotlin.jvm — pure Kotlin/JVM"
)

if [[ -z "$PLUGIN_ARG" ]]; then
    [[ $IS_TTY -eq 0 ]] && die "'--plugin' is required in non-interactive mode (see --help)"
    choose_one "Convention plugin:" 0 "${PLUGIN_LABELS[@]}"
    PLUGIN="${MENU_RESULT%% *}"
else
    case "$PLUGIN_ARG" in
        compose|keygo.android.compose) PLUGIN="keygo.android.compose" ;;
        library|keygo.android.library) PLUGIN="keygo.android.library" ;;
        jvm|keygo.kotlin.jvm)          PLUGIN="keygo.kotlin.jvm" ;;
        *) die "unknown plugin '$PLUGIN_ARG' (compose | library | jvm)" ;;
    esac
fi

IS_ANDROID=1
[[ "$PLUGIN" == "keygo.kotlin.jvm" ]] && IS_ANDROID=0

if [[ -n "$PROTOBUF" && $PROTOBUF -eq 1 && $IS_ANDROID -eq 0 ]]; then
    die "--protobuf requires an Android plugin (keygo.android.protobuf extends the Android setup)"
fi

if [[ -z "$PROTOBUF" ]]; then
    if [[ $ONE_LINER -eq 1 || $IS_TTY -eq 0 || $IS_ANDROID -eq 0 ]]; then
        PROTOBUF=0
    else
        confirm "Also apply keygo.android.protobuf?" n
        PROTOBUF=$MENU_RESULT
    fi
fi

# ---------------------------------------------------------------------------
# Resolve packages (di is always created)
# ---------------------------------------------------------------------------
DEFAULT_PACKAGES="domain,data"
[[ "$PLUGIN" == "keygo.android.compose" ]] && DEFAULT_PACKAGES="domain,data,presentation"

if [[ -n "$PACKAGES_ARG" ]]; then
    [[ "$PACKAGES_ARG" == "none" ]] && PACKAGES_ARG=""
    IFS=',' read -ra PKG_LIST <<<"$PACKAGES_ARG"
    for p in "${PKG_LIST[@]+"${PKG_LIST[@]}"}"; do
        case "$p" in
            domain|data|presentation|di) ;;
            *) die "unknown package '$p' (domain | data | presentation)" ;;
        esac
    done
    PACKAGES="$PACKAGES_ARG"
elif [[ $ONE_LINER -eq 1 || $IS_TTY -eq 0 ]]; then
    PACKAGES="$DEFAULT_PACKAGES"
else
    choose_multi "Packages to create (di is always created):" "$DEFAULT_PACKAGES" \
        domain data presentation
    PACKAGES="$MENU_RESULT"
fi

# ---------------------------------------------------------------------------
# Derivations
# ---------------------------------------------------------------------------
LOCATION_PKG="${LOCATION//\//.}"
NAMESPACE="de.davis.keygo.${LOCATION_PKG}.${NAME//-/_}"
PKG_PATH="${NAMESPACE//.//}"
GRADLE_PATH=":${LOCATION//\//:}:${NAME}"
PLUGIN_ACCESSOR="libs.plugins.${PLUGIN//-/.}"

# DI class: PascalCase of location + name, e.g. feature/item + create → FeatureItemCreateModule
pascal_case() {
    local out="" part
    IFS='/-_' read -ra parts <<<"$1"
    for part in "${parts[@]}"; do
        out+="${part^}"
    done
    printf '%s' "$out"
}
DI_CLASS="$(pascal_case "$LOCATION/$NAME")Module"

# ---------------------------------------------------------------------------
# Generation
# ---------------------------------------------------------------------------
SRC_BASE="$MODULE_DIR/src/main/kotlin/$PKG_PATH"

mkdir -p "$SRC_BASE/di"
IFS=',' read -ra PKG_LIST <<<"$PACKAGES"
for p in "${PKG_LIST[@]+"${PKG_LIST[@]}"}"; do
    [[ -z "$p" || "$p" == "di" ]] && continue
    mkdir -p "$SRC_BASE/$p"
done

# Koin DI module
cat > "$SRC_BASE/di/${DI_CLASS}.kt" <<KOTLIN
package ${NAMESPACE}.di

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module

@Module
@Configuration
@ComponentScan("${NAMESPACE}")
object ${DI_CLASS}
KOTLIN

# build.gradle.kts
PLUGINS_BLOCK="    alias(${PLUGIN_ACCESSOR})"
(( PROTOBUF )) && PLUGINS_BLOCK+=$'\n    alias(libs.plugins.keygo.android.protobuf)'

if (( IS_ANDROID )); then
    cat > "$MODULE_DIR/build.gradle.kts" <<GRADLE
plugins {
${PLUGINS_BLOCK}
}

android {
    namespace = "${NAMESPACE}"
}

dependencies {
}
GRADLE
else
    # keygo.kotlin.jvm doesn't wire Koin — add what the DI module needs.
    cat > "$MODULE_DIR/build.gradle.kts" <<GRADLE
plugins {
${PLUGINS_BLOCK}
    alias(libs.plugins.koin.compiler)
}

dependencies {
    implementation(project.dependencies.platform(libs.koin.bom))
    implementation(libs.koin.core)
}
GRADLE
fi

# ---------------------------------------------------------------------------
# settings.gradle.kts wiring
# ---------------------------------------------------------------------------
SETTINGS="$REPO_ROOT/settings.gradle.kts"
INCLUDE_LINE="include(\"${GRADLE_PATH}\")"

if [[ ! -f "$SETTINGS" ]]; then
    die "settings.gradle.kts not found at $SETTINGS"
elif grep -qF "$INCLUDE_LINE" "$SETTINGS"; then
    printf '%sNote:%s %s already present in settings.gradle.kts — skipping.\n' \
        "$C_DIM" "$C_RESET" "$INCLUDE_LINE" >&2
else
    awk -v line="$INCLUDE_LINE" '
        { buf[NR] = $0; if ($0 ~ /^include\(/) last = NR }
        END {
            for (i = 1; i <= NR; i++) {
                print buf[i]
                if (i == last) print line
            }
            if (!last) print line
        }
    ' "$SETTINGS" > "$SETTINGS.tmp"
    mv "$SETTINGS.tmp" "$SETTINGS"
fi

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
PKG_SUMMARY="di"
[[ -n "$PACKAGES" ]] && PKG_SUMMARY="$PACKAGES,di"

printf '\n%s✔ Module created%s\n' "$C_GREEN" "$C_RESET" >&2
printf '  Directory   : %s/%s\n' "$LOCATION" "$NAME" >&2
printf '  Gradle path : %s\n' "$GRADLE_PATH" >&2
printf '  Namespace   : %s\n' "$NAMESPACE" >&2
printf '  Plugin      : %s%s\n' "$PLUGIN" "$( (( PROTOBUF )) && echo ' + keygo.android.protobuf' )" >&2
printf '  Packages    : %s\n' "$PKG_SUMMARY" >&2
printf '  Koin module : %s\n' "$DI_CLASS" >&2
printf '\nNext steps:\n' >&2
printf '  · add implementation(projects.%s) where the module is consumed\n' \
    "$(echo "${LOCATION//\//.}.${NAME}" | sed -E 's/[-_]([a-z])/\U\1/g')" >&2
printf '  · sync Gradle (./gradlew %s:help)\n' "$GRADLE_PATH" >&2

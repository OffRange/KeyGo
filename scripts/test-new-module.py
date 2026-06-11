#!/usr/bin/env python3
"""Visual + functional tests for scripts/new-module.sh.

Runs the script against a throwaway fake repo root (via NEW_MODULE_ROOT) and
drives the interactive arrow-key UI through a pty, interpreting the ANSI
output with a minimal terminal emulator to assert on what the user sees.
"""

import codecs
import os
import re
import select
import shutil
import subprocess
import sys
import tempfile
import time

SCRIPT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "new-module.sh")

FAILURES = []
CHECKS = 0


def check(name, condition, detail=""):
    global CHECKS
    CHECKS += 1
    status = "PASS" if condition else "FAIL"
    print(f"  [{status}] {name}")
    if not condition:
        FAILURES.append(name)
        if detail:
            print("         " + detail.replace("\n", "\n         "))


class Term:
    """Minimal ANSI terminal emulator for the escape codes the script emits."""

    def __init__(self):
        self.lines = [""]
        self.row = 0
        self.col = 0
        self.cursor_visible = True
        self.raw = b""
        self._decoder = codecs.getincrementaldecoder("utf-8")("replace")

    def feed(self, data):
        self.raw += data
        text = self._decoder.decode(data)
        i = 0
        while i < len(text):
            ch = text[i]
            if ch == "\x1b":
                m = re.match(r"\x1b\[(\??)([0-9;]*)([A-Za-z])", text[i:])
                if m:
                    priv, params, final = m.groups()
                    n = int(params.split(";")[0]) if params else 1
                    if priv == "?" and params == "25":
                        self.cursor_visible = final == "h"
                    elif final == "A":
                        self.row = max(0, self.row - n)
                    elif final == "B":
                        self.row += n
                        self._ensure()
                    elif final == "K":
                        self._erase_line(params or "0")
                    # SGR (m) and anything else: no screen effect
                    i += m.end()
                    continue
                i += 1
                continue
            if ch == "\r":
                self.col = 0
            elif ch == "\n":
                self.row += 1
                self._ensure()
            elif ch == "\x08":
                self.col = max(0, self.col - 1)
            elif ch == "\x07":
                pass
            else:
                self._ensure()
                line = self.lines[self.row]
                if self.col >= len(line):
                    line = line + " " * (self.col - len(line)) + ch
                else:
                    line = line[: self.col] + ch + line[self.col + 1 :]
                self.lines[self.row] = line
                self.col += 1
            i += 1

    def _ensure(self):
        while self.row >= len(self.lines):
            self.lines.append("")

    def _erase_line(self, mode):
        self._ensure()
        line = self.lines[self.row]
        if mode == "2":
            self.lines[self.row] = ""
        elif mode == "1":
            self.lines[self.row] = " " * min(self.col, len(line)) + line[self.col :]
        else:
            self.lines[self.row] = line[: self.col]

    def screen(self):
        return "\n".join(l.rstrip() for l in self.lines).rstrip("\n")


class Session:
    def __init__(self, args, root):
        import pty

        env = dict(os.environ, NEW_MODULE_ROOT=root, TERM="xterm-256color")
        self.master, slave = pty.openpty()
        self.term = Term()
        self.proc = subprocess.Popen(
            ["bash", SCRIPT] + args,
            stdin=slave,
            stdout=slave,
            stderr=slave,
            env=env,
            close_fds=True,
        )
        os.close(slave)

    def drain(self, quiet=0.30, timeout=8):
        """Read pty output until it goes quiet."""
        deadline = time.time() + timeout
        last = time.time()
        while time.time() < deadline:
            r, _, _ = select.select([self.master], [], [], 0.05)
            if r:
                try:
                    data = os.read(self.master, 4096)
                except OSError:
                    return
                if not data:
                    return
                self.term.feed(data)
                last = time.time()
            elif time.time() - last > quiet:
                return

    def send(self, data):
        os.write(self.master, data)
        self.drain()

    def finish(self):
        self.drain()
        code = self.proc.wait(timeout=10)
        try:
            os.close(self.master)
        except OSError:
            pass
        return code


def make_root():
    root = tempfile.mkdtemp(prefix="newmod-test-")
    os.makedirs(os.path.join(root, "core"))
    os.makedirs(os.path.join(root, "feature"))
    os.makedirs(os.path.join(root, "feature", "item"))
    with open(os.path.join(root, "settings.gradle.kts"), "w") as f:
        f.write(
            'rootProject.name = "KeyGoV2"\n'
            'include(":app")\n'
            'include(":core:util")\n'
            "\n"
            "// trailing content after includes\n"
        )
    return root


def run_cli(args, root):
    env = dict(os.environ, NEW_MODULE_ROOT=root)
    return subprocess.run(
        ["bash", SCRIPT] + args, env=env, capture_output=True, text=True, timeout=30
    )


def read_file(*parts):
    with open(os.path.join(*parts)) as f:
        return f.read()


# ---------------------------------------------------------------------------
# Functional tests (one-liner mode)
# ---------------------------------------------------------------------------
def test_one_liner_compose():
    print("\n== one-liner: feature payments -p compose ==")
    root = make_root()
    try:
        r = run_cli(["feature", "payments", "-p", "compose"], root)
        check("exits 0", r.returncode == 0, r.stderr)
        mod = os.path.join(root, "feature", "payments")
        build = read_file(mod, "build.gradle.kts")
        check("applies compose convention plugin",
              "alias(libs.plugins.keygo.android.compose)" in build, build)
        check("sets namespace",
              'namespace = "de.davis.keygo.feature.payments"' in build, build)
        check("no protobuf by default", "protobuf" not in build, build)
        pkg = os.path.join(mod, "src", "main", "kotlin", "de", "davis", "keygo",
                           "feature", "payments")
        di = read_file(pkg, "di", "FeaturePaymentsModule.kt")
        check("Koin module annotated",
              "@Module" in di and "@Configuration" in di
              and '@ComponentScan("de.davis.keygo.feature.payments")' in di, di)
        check("Koin module object named",
              "object FeaturePaymentsModule" in di, di)
        for sub in ("domain", "data", "presentation"):
            check(f"{sub}/ created",
                  os.path.isdir(os.path.join(pkg, sub)))
            check(f"{sub}/ has no .gitkeep",
                  not os.path.exists(os.path.join(pkg, sub, ".gitkeep")))
        settings = read_file(root, "settings.gradle.kts")
        lines = settings.splitlines()
        check("settings include added",
              'include(":feature:payments")' in lines, settings)
        check("include inserted after last include",
              lines.index('include(":feature:payments")')
              == lines.index('include(":core:util")') + 1, settings)
        check("summary mentions gradle path", ":feature:payments" in r.stderr, r.stderr)
    finally:
        shutil.rmtree(root, ignore_errors=True)


def test_one_liner_jvm():
    print("\n== one-liner: core toolkit -p jvm --packages none ==")
    root = make_root()
    try:
        r = run_cli(["core", "toolkit", "-p", "jvm", "--packages", "none"], root)
        check("exits 0", r.returncode == 0, r.stderr)
        mod = os.path.join(root, "core", "toolkit")
        build = read_file(mod, "build.gradle.kts")
        check("applies jvm convention plugin",
              "alias(libs.plugins.keygo.kotlin.jvm)" in build, build)
        check("no android block for jvm", "android {" not in build, build)
        check("jvm module wires Koin compiler plugin",
              "alias(libs.plugins.koin.compiler)" in build, build)
        check("jvm module adds koin deps",
              "platform(libs.koin.bom)" in build and "libs.koin.core" in build, build)
        pkg = os.path.join(mod, "src", "main", "kotlin", "de", "davis", "keygo",
                           "core", "toolkit")
        check("di module generated",
              os.path.isfile(os.path.join(pkg, "di", "CoreToolkitModule.kt")))
        check("packages none → no domain/data",
              not os.path.isdir(os.path.join(pkg, "domain"))
              and not os.path.isdir(os.path.join(pkg, "data")))
    finally:
        shutil.rmtree(root, ignore_errors=True)


def test_one_liner_protobuf_and_naming():
    print("\n== one-liner: core proto-store -p library --protobuf ==")
    root = make_root()
    try:
        r = run_cli(["core", "proto-store", "-p", "library", "--protobuf"], root)
        check("exits 0", r.returncode == 0, r.stderr)
        mod = os.path.join(root, "core", "proto-store")
        build = read_file(mod, "build.gradle.kts")
        check("applies library plugin",
              "alias(libs.plugins.keygo.android.library)" in build, build)
        check("applies protobuf add-on",
              "alias(libs.plugins.keygo.android.protobuf)" in build, build)
        check("dash → underscore in namespace",
              'namespace = "de.davis.keygo.core.proto_store"' in build, build)
        pkg = os.path.join(mod, "src", "main", "kotlin", "de", "davis", "keygo",
                           "core", "proto_store")
        check("PascalCase DI class from dashed name",
              os.path.isfile(os.path.join(pkg, "di", "CoreProtoStoreModule.kt")))
        check("library default packages = domain,data (no presentation)",
              os.path.isdir(os.path.join(pkg, "domain"))
              and os.path.isdir(os.path.join(pkg, "data"))
              and not os.path.isdir(os.path.join(pkg, "presentation")))
        settings = read_file(root, "settings.gradle.kts")
        check("gradle path keeps dash",
              'include(":core:proto-store")' in settings, settings)
    finally:
        shutil.rmtree(root, ignore_errors=True)


def test_nested_location():
    print("\n== one-liner: feature/item edit -p compose ==")
    root = make_root()
    try:
        r = run_cli(["feature/item", "edit", "-p", "compose"], root)
        check("exits 0", r.returncode == 0, r.stderr)
        pkg = os.path.join(root, "feature", "item", "edit", "src", "main", "kotlin",
                           "de", "davis", "keygo", "feature", "item", "edit")
        check("nested DI class name",
              os.path.isfile(os.path.join(pkg, "di", "FeatureItemEditModule.kt")))
        settings = read_file(root, "settings.gradle.kts")
        check("nested gradle path", 'include(":feature:item:edit")' in settings, settings)
    finally:
        shutil.rmtree(root, ignore_errors=True)


def test_errors():
    print("\n== error handling ==")
    root = make_root()
    try:
        os.makedirs(os.path.join(root, "feature", "existing"))
        r = run_cli(["feature", "existing", "-p", "compose"], root)
        check("existing module dir rejected",
              r.returncode != 0 and "already exists" in r.stderr, r.stderr)

        r = run_cli(["feature", "Bad_Name!", "-p", "compose"], root)
        check("invalid name rejected", r.returncode != 0 and "invalid" in r.stderr, r.stderr)

        r = run_cli(["nowhere", "thing", "-p", "compose"], root)
        check("missing location rejected",
              r.returncode != 0 and "does not exist" in r.stderr, r.stderr)

        r = run_cli(["core", "thing", "-p", "jvm", "--protobuf"], root)
        check("--protobuf + jvm rejected",
              r.returncode != 0 and "protobuf" in r.stderr, r.stderr)

        r = run_cli(["core", "thing"], root)
        check("non-interactive without --plugin rejected",
              r.returncode != 0 and "--plugin" in r.stderr, r.stderr)

        r = run_cli(["core", "thing", "-p", "nope"], root)
        check("unknown plugin rejected", r.returncode != 0, r.stderr)
    finally:
        shutil.rmtree(root, ignore_errors=True)


# ---------------------------------------------------------------------------
# Visual tests (interactive pty)
# ---------------------------------------------------------------------------
ARROW_DOWN = b"\x1b[B"
ARROW_UP = b"\x1b[A"
ENTER = b"\r"
SPACE = b" "


def selected_line(term):
    for line in term.lines:
        if "▶" in line:
            return line
    return None


def test_interactive_visuals():
    print("\n== interactive pty: full arrow-key flow ==")
    root = make_root()
    s = None
    try:
        s = Session([], root)
        s.drain()

        screen = s.term.screen()
        check("location menu rendered",
              "Module location:" in screen and "core" in screen and "feature" in screen,
              screen)
        check("first item highlighted initially",
              (selected_line(s.term) or "").strip().startswith("▶ core"), screen)
        check("hint line shown", "↑/↓ move" in screen, screen)
        check("cursor hidden while menu open",
              b"\x1b[?25l" in s.term.raw and not s.term.cursor_visible)
        check("highlight uses color",
              b"\x1b[1;36m" in s.term.raw)

        s.send(ARROW_DOWN)
        sel = selected_line(s.term) or ""
        check("arrow down moves highlight to feature",
              sel.strip().startswith("▶ feature"), s.term.screen())
        check("only one item highlighted",
              sum("▶" in l for l in s.term.lines) == 1, s.term.screen())

        s.send(ARROW_UP)
        sel = selected_line(s.term) or ""
        check("arrow up moves highlight back to core",
              sel.strip().startswith("▶ core"), s.term.screen())
        s.send(ARROW_UP)
        sel = selected_line(s.term) or ""
        check("arrow up clamps at top",
              sel.strip().startswith("▶ core"), s.term.screen())

        s.send(ARROW_DOWN)
        s.send(ENTER)
        screen = s.term.screen()
        check("menu collapses to inline answer",
              "Module location: feature" in screen, screen)
        check("menu items cleared after select",
              "▶" not in screen and "↑/↓ move" not in screen, screen)
        check("cursor restored after select", s.term.cursor_visible)

        s.send(b"demo\r")
        screen = s.term.screen()
        check("name prompt echoed", "Module name" in screen and "demo" in screen, screen)
        check("plugin menu rendered", "Convention plugin:" in screen, screen)

        s.send(ENTER)  # keygo.android.compose
        screen = s.term.screen()
        check("plugin answer inline",
              "Convention plugin: keygo.android.compose" in screen, screen)

        check("protobuf confirm shown", "keygo.android.protobuf" in s.term.screen())
        s.send(b"n\r")

        screen = s.term.screen()
        check("package multi-select rendered",
              "Packages to create" in screen and "[x] domain" in screen, screen)
        check("compose defaults preselect presentation",
              "[x] presentation" in screen, screen)

        s.send(ARROW_DOWN)  # → data
        s.send(SPACE)       # untoggle
        sel = selected_line(s.term) or ""
        check("space untoggles item under cursor",
              "[ ] data" in sel, s.term.screen())
        s.send(SPACE)       # retoggle
        sel = selected_line(s.term) or ""
        check("space retoggles item",
              "[x] data" in sel, s.term.screen())

        s.send(ENTER)
        code = s.finish()
        screen = s.term.screen()
        check("exits 0 after interactive flow", code == 0, screen)
        check("success banner shown", "✔ Module created" in screen, screen)
        check("summary shows gradle path", ":feature:demo" in screen, screen)
        check("summary shows Koin module", "FeatureDemoModule" in screen, screen)
        check("final screen has no menu artifacts",
              "▶" not in screen and "Enter select" not in screen, screen)
        check("cursor visible at exit", s.term.cursor_visible)

        di = os.path.join(root, "feature", "demo", "src", "main", "kotlin", "de",
                          "davis", "keygo", "feature", "demo", "di",
                          "FeatureDemoModule.kt")
        check("interactive run generated module files", os.path.isfile(di))
    finally:
        if s and s.proc.poll() is None:
            s.proc.kill()
        shutil.rmtree(root, ignore_errors=True)


def test_interactive_quit():
    print("\n== interactive pty: q aborts cleanly ==")
    root = make_root()
    s = None
    try:
        s = Session([], root)
        s.drain()
        s.send(b"q")
        code = s.finish()
        check("q exits non-zero", code != 0)
        check("abort message shown", "Aborted" in s.term.screen(), s.term.screen())
        check("cursor restored on abort", s.term.cursor_visible)
        check("no module dirs created",
              not os.listdir(os.path.join(root, "core"))
              and os.listdir(os.path.join(root, "feature")) == ["item"])
    finally:
        if s and s.proc.poll() is None:
            s.proc.kill()
        shutil.rmtree(root, ignore_errors=True)


def main():
    test_one_liner_compose()
    test_one_liner_jvm()
    test_one_liner_protobuf_and_naming()
    test_nested_location()
    test_errors()
    test_interactive_visuals()
    test_interactive_quit()

    print(f"\n{CHECKS - len(FAILURES)}/{CHECKS} checks passed")
    if FAILURES:
        print("Failed: " + ", ".join(FAILURES))
        sys.exit(1)
    print("All visual + functional tests passed.")


if __name__ == "__main__":
    main()

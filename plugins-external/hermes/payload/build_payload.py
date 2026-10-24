#!/usr/bin/env python3
"""Builds the full portable Hermes Agent payload for the OS it runs on.

Hermes ships no binaries and refuses wheel builds, so this follows its own installer and desktop payload:
sources + a separate dependency tree + a bundled CPython, and the managed tools (ripgrep, node, npm, ffmpeg)
installed by Hermes' own `pm` into the payload store named by manifest.json.
Must run natively: `pm` verifies every tool by executing it.

    usage: build_payload.py <hermes-version> <out-dir>
    result: <out-dir>/hermes-payload-<version>-<platform>.tar.gz with hermes/{bin,python,venv,app,tools,pm-runtime,...}
"""
import hashlib
import json
import os
import platform
import shutil
import subprocess
import sys
import tarfile
import tempfile
from pathlib import Path

PYTHON_VERSION = "3.14.6"  # Hermes' uv.lock targets Python 3.14
SKIPPED_SOURCES = (".git", "tests", "tests-js", "website", "apps", "contributors", "node_modules")
WINDOWS = sys.platform == "win32"
# uv's `python3` link is dropped by the dereferencing copy; the versioned binary is a real file
PYTHON_EXE = "python.exe" if WINDOWS else "bin/python" + PYTHON_VERSION.rsplit(".", 1)[0]
# dependencies sit in a venv layout named by manifest.json: Hermes processes started without our PYTHONPATH
# (the TUI gateway) select it themselves; the payload's own Python runs it, the venv has no interpreter
SITE = "venv/Lib/site-packages" if WINDOWS else "venv/lib/python" + PYTHON_VERSION.rsplit(".", 1)[0] + "/site-packages"


def host_platform() -> str:
  machine = platform.machine().lower()
  arch = {"arm64": "arm64", "aarch64": "arm64", "x86_64": "x64", "amd64": "x64"}[machine]
  return {"darwin": "macos", "linux": "linux", "win32": "windows"}[sys.platform] + "-" + arch


def run(*cmd, **kwargs) -> subprocess.CompletedProcess:
  print("+", " ".join(str(c) for c in cmd), flush=True)
  return subprocess.run([str(c) for c in cmd], check=True, **kwargs)


def main(version: str, out_dir: Path) -> None:
  plat = host_platform()
  work = Path(tempfile.mkdtemp(prefix="hermes-payload-"))
  src, dest = work / "src", work / "hermes"

  run("git", "-c", "advice.detachedHead=false", "clone", "-q", "--depth", "1", "--branch", f"v{version}",
      "https://github.com/nousresearch/hermes-agent.git", src)
  commit = subprocess.run(["git", "-C", str(src), "rev-parse", "HEAD"], check=True, capture_output=True, text=True).stdout.strip()
  # exact locked versions; --no-deps later because the lock carries overrides a fresh resolve would reject
  requirements = work / "requirements.txt"
  with requirements.open("w") as f:
    run("uv", "export", "--frozen", "--no-hashes", "--no-dev", "--no-emit-project", "--format", "requirements-txt", cwd=src, stdout=f)

  run("uv", "python", "install", "--quiet", PYTHON_VERSION, env={**os.environ, "UV_PYTHON_INSTALL_DIR": str(work / "pythons")})
  python_home = next(p for p in (work / "pythons").glob(f"cpython-{PYTHON_VERSION}-*") if p.is_dir() and not p.is_symlink())
  shutil.copytree(python_home, dest / "python", symlinks=False, ignore_dangling_symlinks=True)
  python = dest / "python" / PYTHON_EXE
  run("uv", "pip", "install", "--quiet", "--no-deps", "--no-build", "--target", dest / SITE, "--python", python, "-r", requirements)
  (dest / "venv/pyvenv.cfg").write_text(f"version = {PYTHON_VERSION}\n")
  # like Hermes' own payloads: the venv resolves the sources through a relative .pth
  (dest / SITE / "hermes-agent.pth").write_text(Path(os.path.relpath(dest / "app", dest / SITE)).as_posix() + "\n")
  shutil.copytree(src, dest / "app", ignore=shutil.ignore_patterns(*SKIPPED_SOURCES))
  shutil.copy2(src / "LICENSE", dest / "LICENSE")

  env = {
    **os.environ,
    "PYTHONPATH": os.pathsep.join([str(dest / "app"), str(dest / SITE)]),
    "PYTHONNOUSERSITE": "1",
    "HERMES_HOME": str(work / "home"),  # never the builder's own ~/.hermes
  }
  # official build-time stamp: `hermes --version` reports the release and `hermes update` defers to the IDE plugin
  run(python, src / "scripts/write_install_stamp.py", "--output", dest / "app/install-stamp.json", "--commit", commit,
      "--base-version", version, "--display-version", version, "--source", "build", "--update-mechanism", "external", env=env)
  # tools go into the payload store before manifest.json exists: with it the store reads as sealed and pm writes to ~/.hermes
  run(python, "-m", "pm.cli", "install", "--tools-only", "--without", "agent-browser", "--without", "cua-driver",
      cwd=dest / "app", env={**env, "HERMES_RUNTIME_DIR": str(dest / "tools")})
  # the resident PM runtime Hermes' own payloads carry next to manifest.json; without it `pm.ensure("node")` fails,
  # e.g. when `hermes --tui` looks for node
  run(python, "-c", "import sys; from pathlib import Path; from pm import stage_manager_runtime; "
      "from scripts.bundles.payload import seal_pm_runtime; root, py = Path(sys.argv[1]), Path(sys.argv[2]); "
      "stage_manager_runtime(python=py, destination=root / 'pm-runtime', project=root / 'app' / 'pm'); "
      "seal_pm_runtime(root, py)", dest, python, cwd=dest / "app", env={**env, "HERMES_RUNTIME_DIR": str(dest / "tools")})
  build_tui(src, dest, work, env)
  (dest / "manifest.json").write_text(json.dumps({"repo": "app", "store": "tools", "venv": "venv", "runtime": {
    "commands": {"hermes": "bin/hermes"}, "storePython": "python/" + PYTHON_EXE}}))
  write_launcher(dest)
  verify(dest, version, env)

  out_dir.mkdir(parents=True, exist_ok=True)
  archive = out_dir / f"hermes-payload-{version}-{plat}.tar.gz"
  with tarfile.open(archive, "w:gz") as tar:
    tar.add(dest, arcname="hermes", filter=lambda info: info.replace(uid=0, gid=0, uname="", gname="", deep=False))
  print(f"{hashlib.sha256(archive.read_bytes()).hexdigest()}  {archive}")
  shutil.rmtree(work, ignore_errors=True)


def build_tui(src: Path, dest: Path, work: Path, env: dict) -> None:
  """Prebuilds the Ink TUI (`display.interface: tui`) with Hermes' own scripts and the payload's node, into
  hermes_cli/tui_dist like Hermes' packaged builds: otherwise the first `hermes --tui` runs npm install from the network."""
  # pm's node keeps npm beside it (bin/node + bin/npm; the Windows zip has node.exe + npm.cmd, maybe without bin/)
  node = next(p for pattern in ("node-*/bin/node", "node-*/bin/node.exe", "node-*/node.exe") for p in (dest / "tools").glob(pattern))
  node_env = {**env, "PATH": str(node.parent) + os.pathsep + os.environ["PATH"], "CI": "1"}
  run(node, "scripts/build/node-deps.mjs", "--source", src, "--workspace", "ui-tui", cwd=src, env=node_env)
  run(node, "scripts/build/tui.mjs", "--source", src, "--out", work / "tui", cwd=src, env=node_env)
  shutil.copytree(work / "tui" / "dist", dest / "app/hermes_cli/tui_dist")
  shutil.copy2(work / "tui" / "package.json", dest / "app/hermes_cli/tui_dist/package.json")


def write_launcher(dest: Path) -> None:
  if WINDOWS:
    (dest / "bin").mkdir()
    (dest / "bin/hermes.cmd").write_text(
      "@echo off\r\n"
      "rem Portable Hermes launcher: bundled CPython + vendored dependencies + Hermes sources\r\n"
      'set "HERMES_PORTABLE=%~dp0.."\r\n'
      'set "PYTHONPATH=%HERMES_PORTABLE%\\app;%HERMES_PORTABLE%\\venv\\Lib\\site-packages"\r\n'
      'set "PYTHONNOUSERSITE=1"\r\n'
      '"%HERMES_PORTABLE%\\python\\python.exe" -m hermes_cli.main %*\r\n')
  else:
    (dest / "bin").mkdir()
    launcher = dest / "bin/hermes"
    launcher.write_text(
      "#!/bin/sh\n"
      "# Portable Hermes launcher: bundled CPython + vendored dependencies + Hermes sources\n"
      'HERE="$(cd "$(dirname "$0")/.." && pwd)"\n'
      'PYTHONPATH="$HERE/app:$HERE/' + SITE + '" PYTHONNOUSERSITE=1 exec "$HERE/python/' + PYTHON_EXE + '" -m hermes_cli.main "$@"\n')
    launcher.chmod(0o755)


def verify(dest: Path, version: str, env: dict) -> None:
  """The payload must report its release and must not be "out of sync" for the managed tools."""
  launcher = [os.environ.get("COMSPEC", "cmd.exe"), "/c", str(dest / "bin/hermes.cmd")] if WINDOWS else [str(dest / "bin/hermes")]
  clean_env = {k: v for k, v in env.items() if k not in ("PYTHONPATH", "PYTHONNOUSERSITE")}
  out = subprocess.run([*launcher, "--version"], env=clean_env, capture_output=True, text=True)
  print(out.stdout, out.stderr, flush=True)
  assert f"v{version}" in out.stdout, f"hermes --version did not report v{version}"
  verdict = subprocess.run(
    [str(dest / "python" / PYTHON_EXE), "-c",
     "from pathlib import Path; from hermes_cli.venv_sync import check_runtime; "
     f"print(check_runtime(Path(r'{dest / 'app'}')))"],
    env=env, capture_output=True, text=True, check=True).stdout.strip().splitlines()[-1]
  assert verdict == "None", f"payload is not complete: {verdict}"
  # the TUI gateway starts with only app/ on PYTHONPATH and must find the dependencies itself
  subprocess.run(
    [str(dest / "python" / PYTHON_EXE), "-c", "from pathlib import Path; from pm.environments import activate_dependencies; "
     f"activate_dependencies(Path(r'{dest / 'app'}')); import ruamel.yaml"],
    env={**env, "PYTHONPATH": str(dest / "app")}, check=True)
  assert (dest / "app/hermes_cli/tui_dist/entry.js").is_file(), "prebuilt TUI missing"
  # the TUI resolves node through the sealed PM runtime
  node = subprocess.run(
    [str(dest / "python" / PYTHON_EXE), "-c", "import shutil; from pm import ensure; print(shutil.which('node', path=ensure('node').env['PATH']))"],
    env=env, capture_output=True, text=True, check=True).stdout.strip().splitlines()[-1]
  assert Path(node).resolve().is_relative_to(dest.resolve()), f"node outside the payload: {node}"
  print("payload verified:", version, host_platform(), flush=True)


if __name__ == "__main__":
  main(sys.argv[1], Path(sys.argv[2]).resolve())

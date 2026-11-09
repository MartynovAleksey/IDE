#!/usr/bin/env python3
"""Adds the Claude Code CLI binary to the plugin zip from `./gradlew buildPlugin`, one zip per OS.

The binary goes to bin/claude[.exe] with mode 0755, which the IDE's plugin installer restores. It comes from the native
installer's download host, is cached in build/claude-binaries/ and checked against the SHA-256 below.

    usage: package_plugin.py <claude-code-intellij-<ver>.zip> <out-dir> [<os>...]
    result: <out-dir>/claude-code-intellij-<ver>-<os>.zip
"""
import hashlib
import sys
import urllib.request
import zipfile
from pathlib import Path

VERSION = "2.1.319"
BASE_URL = "https://downloads.claude.ai/claude-code-releases"

# os -> installer platform; shortcut: glibc Linux only, add linux-*-musl here for Alpine
PLATFORMS = {
  "macos-arm64": "darwin-arm64",
  "macos-x64": "darwin-x64",
  "linux-x64": "linux-x64",
  "linux-arm64": "linux-arm64",
  "windows-x64": "win32-x64",
  "windows-arm64": "win32-arm64",
}

# os -> SHA-256 of the binary, from <BASE_URL>/<VERSION>/manifest.json
SHA256 = {
  "macos-arm64": "1f53587e92dee8e089fa349188a4cda64fa34ace6e7b0ca2ee278ddaa3cc2ebd",
  "macos-x64": "af6f1f5510ae98d56a155bf8f007aa9d5b44efccc9c10c8e10ef35039e95a940",
  "linux-x64": "dabafec82ca1319fa43d8be36a10fc61b1d11821cb98ad5df04ad95e199b8eed",
  "linux-arm64": "a7e687fd1cb054d651c7588223de3786c99d4c682365e3c3ae1e59ce7f3a591f",
  "windows-x64": "2e055732c9d03d22700d9046251a8370b673607bfcc9e4d16a1b0e8fb1345cbd",
  "windows-arm64": "59b1ace11fad7f95c17a4ba01cf50eb48af20cd133df93129b34aa8e633bd6b1",
}

CACHE = Path(__file__).parent / "build" / "claude-binaries"


def main(plugin_zip: Path, out_dir: Path, os_names: list[str]) -> None:
  out_dir.mkdir(parents=True, exist_ok=True)
  for os_name in os_names or PLATFORMS:
    data = binary(os_name)
    exe = "claude.exe" if os_name.startswith("windows") else "claude"
    out = out_dir / f"{plugin_zip.stem}-{os_name}.zip"
    with zipfile.ZipFile(plugin_zip) as src, zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as dst:
      root = src.namelist()[0].split("/")[0]  # claude-code-intellij
      for info in src.infolist():
        dst.writestr(info, src.read(info))
      info = zipfile.ZipInfo(f"{root}/bin/{exe}", (1980, 1, 1, 0, 0, 0))
      info.create_system = 3  # Unix: external_attr carries the mode
      info.external_attr = 0o100755 << 16
      info.compress_type = zipfile.ZIP_DEFLATED
      dst.writestr(info, data)
      dst.writestr(f"{root}/bin/version.txt", VERSION)
    print(out)


def binary(os_name: str) -> bytes:
  """The claude binary of [VERSION] for [os_name], after checking its SHA-256."""
  exe = "claude.exe" if os_name.startswith("windows") else "claude"
  cached = CACHE / VERSION / os_name / exe
  if not cached.exists():
    cached.parent.mkdir(parents=True, exist_ok=True)
    with urllib.request.urlopen(f"{BASE_URL}/{VERSION}/{PLATFORMS[os_name]}/{exe}") as response:
      cached.with_suffix(".part").write_bytes(response.read())
    cached.with_suffix(".part").rename(cached)
  data = cached.read_bytes()
  actual = hashlib.sha256(data).hexdigest()
  if actual != SHA256[os_name]:
    raise SystemExit(f"SHA-256 mismatch for {cached}: {actual}, expected {SHA256[os_name]}")
  return data


if __name__ == "__main__":
  main(Path(sys.argv[1]), Path(sys.argv[2]), sys.argv[3:])

#!/usr/bin/env python3
"""Adds opencode binaries to the plugin zip from `./gradlew buildPlugin`, one zip per OS.

The default version goes to bin/opencode[.exe], each other one to bin/versions/<version>/opencode[.exe]. Archives come from
the opencode GitHub releases, are cached in build/opencode-archives/ and checked against the SHA-256 below; binaries are
written with mode 0755, which the IDE's plugin installer restores.

    usage: package_plugin.py <opencode-intellij-<ver>.zip> <out-dir> [<os>...]
    result: <out-dir>/opencode-intellij-<ver>-<os>.zip
"""
import hashlib
import io
import sys
import tarfile
import urllib.request
import zipfile
from pathlib import Path

DEFAULT_VERSION = "1.19.5"

# os -> release archive name
ARCHIVES = {
  "macos-arm64": "opencode-darwin-arm64.zip",
  "macos-x64": "opencode-darwin-x64.zip",
  "linux-x64": "opencode-linux-x64.tar.gz",
  "linux-arm64": "opencode-linux-arm64.tar.gz",
  "windows-x64": "opencode-windows-x64.zip",
  "windows-arm64": "opencode-windows-arm64.zip",
}

# version -> os -> SHA-256 of the archive, from the `digest` of the GitHub release assets
SHA256 = {
  "1.19.5": {
    "macos-arm64": "c1c9e62521192192ef4c3ed1355ada2144055bddb61682217e997b03c55203c1",
    "macos-x64": "b0212efa6db347187225a17fa3a31c6b261bf4c9f1589a290b0ca902d720d250",
    "linux-x64": "588310a336fa3b9df36b3f0225b26f679d453efaad0cf3d66c3651ef3ec1647a",
    "linux-arm64": "fae5be262d693b7c5e562856cc473e59e741772936db0c0600ac0d5ed5bd088b",
    "windows-x64": "09a6e693157f0887e504f2cf4357a8cbddc5ead5d0dffb9a269dcc47c48640f5",
    "windows-arm64": "5d49139bd917511ff35a3c06065c9f47918346668f05ea020e778286f302fbb7",
  },
  "2.2.5": {
    "macos-arm64": "25ba9908979fa7d764792c82589b4b5768ab1511154e0317d98aa77d216ce767",
    "macos-x64": "4d4419022e6ffbd48afc97c5101bdf001d38b0499f516169b9a80a87a340fd56",
    "linux-x64": "09f44302c10fd036912f59c81fd1aef5df45dd06f649197f9dfc9bbe2a79614e",
    "linux-arm64": "5a528f42caf576c6cdece6a191eb273d3db54effa5518e0402684d8f8026aeb6",
    "windows-x64": "2f3bfd6b3527c60c2a882557b20ddc9ba6bdff4b5a8fe61c853da8fcfb3076e8",
    "windows-arm64": "4e426a39d68516b036cd28b574c5ce5f3b50846ab460521f83f2458fe7f242d8",
  },
}

CACHE = Path(__file__).parent / "build" / "opencode-archives"


def main(plugin_zip: Path, out_dir: Path, os_names: list[str]) -> None:
  out_dir.mkdir(parents=True, exist_ok=True)
  for os_name in os_names or ARCHIVES:
    binaries = {version: binary(version, os_name) for version in SHA256}  # all checked before the zip is written
    exe = "opencode.exe" if os_name.startswith("windows") else "opencode"
    out = out_dir / f"{plugin_zip.stem}-{os_name}.zip"
    with zipfile.ZipFile(plugin_zip) as src, zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as dst:
      root = src.namelist()[0].split("/")[0]  # opencode-intellij
      for info in src.infolist():
        dst.writestr(info, src.read(info))
      for version, data in binaries.items():
        bin_dir = "bin" if version == DEFAULT_VERSION else f"bin/versions/{version}"
        info = zipfile.ZipInfo(f"{root}/{bin_dir}/{exe}", (1980, 1, 1, 0, 0, 0))
        info.create_system = 3  # Unix: external_attr carries the mode
        info.external_attr = 0o100755 << 16
        info.compress_type = zipfile.ZIP_DEFLATED
        dst.writestr(info, data)
      dst.writestr(f"{root}/bin/version.txt", DEFAULT_VERSION)  # the version of bin/opencode, shown in the IDE
    print(out)


def binary(version: str, os_name: str) -> bytes:
  """The opencode binary from the release archive of [version] for [os_name], after checking the archive's SHA-256."""
  name = ARCHIVES[os_name]
  archive = CACHE / version / name
  if not archive.exists():
    archive.parent.mkdir(parents=True, exist_ok=True)
    url = f"https://github.com/anomalyco/opencode/releases/download/v{version}/{name}"
    with urllib.request.urlopen(url) as response:
      archive.with_suffix(".part").write_bytes(response.read())
    archive.with_suffix(".part").rename(archive)
  data = archive.read_bytes()
  actual = hashlib.sha256(data).hexdigest()
  if actual != SHA256[version][os_name]:
    raise SystemExit(f"SHA-256 mismatch for {archive}: {actual}, expected {SHA256[version][os_name]}")
  exe = "opencode.exe" if os_name.startswith("windows") else "opencode"
  if name.endswith(".zip"):
    with zipfile.ZipFile(io.BytesIO(data)) as z:
      member = next((m for m in z.namelist() if m.rsplit("/", 1)[-1] == exe), None)
      if member: return z.read(member)
  else:
    with tarfile.open(fileobj=io.BytesIO(data)) as t:
      member = next((m for m in t.getmembers() if m.isfile() and m.name.rsplit("/", 1)[-1] == exe), None)
      if member: return t.extractfile(member).read()
  raise SystemExit(f"no {exe} in {archive}")


if __name__ == "__main__":
  main(Path(sys.argv[1]), Path(sys.argv[2]), sys.argv[3:])

#!/usr/bin/env python3
"""Adds blackbird binaries to the plugin zip from `./gradlew buildPlugin`, one zip per OS.

The default version goes to bin/blackbird[.exe], each other one to bin/versions/<version>/blackbird[.exe]. Archives come from
the blackbird GitHub releases (MartynovAleksey/blackbird), are cached in build/blackbird-archives/ and checked against the SHA-256 below; binaries are
written with mode 0755, which the IDE's plugin installer restores.

    usage: package_plugin.py <blackbird-intellij-<ver>.zip> <out-dir> [<os>...]
    result: <out-dir>/blackbird-intellij-<ver>-<os>.zip
"""
import hashlib
import io
import sys
import tarfile
import urllib.request
import zipfile
from pathlib import Path

DEFAULT_VERSION = "1.19.5.5"

# os -> release archive name; the version is filled in
ARCHIVES = {
  "macos-arm64": "blackbird-v{version}-darwin-arm64.tar.gz",
  "macos-x64": "blackbird-v{version}-darwin-x64.tar.gz",
  "linux-x64": "blackbird-v{version}-linux-x64.tar.gz",
  "linux-arm64": "blackbird-v{version}-linux-arm64.tar.gz",
  "windows-x64": "blackbird-v{version}-windows-x64.zip",
  "windows-arm64": "blackbird-v{version}-windows-arm64.zip",
}

# version -> os -> SHA-256 of the archive, from SHA256SUMS of the GitHub release
SHA256 = {
  "1.19.5.5": {
    "macos-arm64": "a595525714b1247f78df3532a8a4718f77fbf7c082243316fc9ea6963c0aea23",
    "macos-x64": "cb241a96ce60607d11c4411da1c49cc2abf0cfbdf2e3946ea6b9c40035ca38e6",
    "linux-x64": "f4db6bfcb399b3f5eabe5eeb92b63dfb6e9e2c99a51e3ae15ffbc6b4ce605530",
    "linux-arm64": "dd020c7270a96ed86a0bc3c890dc220cb6632e4d6ec1e2bc0dec02e185266e3a",
    "windows-x64": "3a2db24d0cc45851178849af371e9e2ef79b0f4ca304f71d24c5066240c5d73f",
    "windows-arm64": "b3df3b0452a1e6fff90b10a3fd823901003aecb793a5b98cc3e82d248723bcee",
  },
  "2.2.5.5": {
    "macos-arm64": "0ad6d3086b5561775592345caa61e5398bca0c3ceb010aa3a5fb14b09902508b",
    "macos-x64": "47ee0ab43872a6a98e0b460fa1e8c598f57f041b445c1b617f47cf1c3625b1b2",
    "linux-x64": "e99914aa6fe617cd6f9b80879db5e8888d3f9efdea50e66be5ace6d2360f12ce",
    "linux-arm64": "16e2a8669558816ecd159cb4104998e301190353c1ae2d7f250ee7eba9ab1d04",
    "windows-x64": "18f372c53fbbf350022c011d125ff12195271671d8fd343f4af66f73e41a8bdb",
    "windows-arm64": "c9e71c94de2f25be85a7d615f1cfb3f76ef4b9e9537f69d4d4e707af68ed9f08",
  },
}

CACHE = Path(__file__).parent / "build" / "blackbird-archives"


def main(plugin_zip: Path, out_dir: Path, os_names: list[str]) -> None:
  out_dir.mkdir(parents=True, exist_ok=True)
  for os_name in os_names or ARCHIVES:
    binaries = {version: binary(version, os_name) for version in SHA256}  # all checked before the zip is written
    exe = "blackbird.exe" if os_name.startswith("windows") else "blackbird"
    out = out_dir / f"{plugin_zip.stem}-{os_name}.zip"
    with zipfile.ZipFile(plugin_zip) as src, zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as dst:
      root = src.namelist()[0].split("/")[0]  # blackbird-intellij
      for info in src.infolist():
        dst.writestr(info, src.read(info))
      for version, data in binaries.items():
        bin_dir = "bin" if version == DEFAULT_VERSION else f"bin/versions/{version}"
        info = zipfile.ZipInfo(f"{root}/{bin_dir}/{exe}", (1980, 1, 1, 0, 0, 0))
        info.create_system = 3  # Unix: external_attr carries the mode
        info.external_attr = 0o100755 << 16
        info.compress_type = zipfile.ZIP_DEFLATED
        dst.writestr(info, data)
      dst.writestr(f"{root}/bin/version.txt", DEFAULT_VERSION)  # the version of bin/blackbird, shown in the IDE
    print(out)


def binary(version: str, os_name: str) -> bytes:
  """The blackbird binary from the release archive of [version] for [os_name], after checking the archive's SHA-256."""
  name = ARCHIVES[os_name].format(version=version)
  archive = CACHE / version / name
  if not archive.exists():
    archive.parent.mkdir(parents=True, exist_ok=True)
    url = f"https://github.com/MartynovAleksey/blackbird/releases/download/v{version}/{name}"
    with urllib.request.urlopen(url) as response:
      archive.with_suffix(".part").write_bytes(response.read())
    archive.with_suffix(".part").rename(archive)
  data = archive.read_bytes()
  actual = hashlib.sha256(data).hexdigest()
  if actual != SHA256[version][os_name]:
    raise SystemExit(f"SHA-256 mismatch for {archive}: {actual}, expected {SHA256[version][os_name]}")
  exe = "blackbird.exe" if os_name.startswith("windows") else "blackbird"
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

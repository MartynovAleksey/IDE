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

DEFAULT_VERSION = "1.19.5.2"

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
  "1.19.5.2": {
    "macos-arm64": "e86e5a9f5a6d8f16f941f68c8c4842778dbed5196c84e1432519367a0dd48c98",
    "macos-x64": "6f028a9967dbf0153a01a8379be34bc59f6dd3948f724cda161c2ed72189573f",
    "linux-x64": "82a744dffd5dd87aa8bc7caadb728095f1be114f1621daac38268cf83f6b558e",
    "linux-arm64": "388618757b203ddc5a3d4bdeb49498f3b82527c9d73f16921f0d5d49dd02a5e2",
    "windows-x64": "201cce74505a87ad9e1b00d14d85efb0a04fc354913d16ebb6d505c26777905e",
    "windows-arm64": "addb89894fdcde7b53faa182c18cef874ea8b88dca3ed0fb1e036829141e4887",
  },
  "2.2.5.2": {
    "macos-arm64": "ffd8a0d99f627a50cb88e00495ed4495d827c980890885944868e1b4d47364de",
    "macos-x64": "9967f108569a68f2e718a0684f6dbda16b78d9fcc09d2273810e246e13079ca6",
    "linux-x64": "654cfdfc8e803d50dc249554a0c5e838b1a00c39490e55af8ecd72c1638378e9",
    "linux-arm64": "c9888a6266257d0041697c5867994aade49707249ec6676363f132e15a14e659",
    "windows-x64": "4afb97e97784bac604b40a685d4b55d8413b8cf38b644fad99286ad90fd7fec9",
    "windows-arm64": "5b729214db770dbdf2c3b5eccb3ae7c0007ef17a0cb9d5245f515c8a6a708eaf",
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

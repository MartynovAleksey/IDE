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

DEFAULT_VERSION = "1.19.5.3"

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
  "1.19.5.3": {
    "macos-arm64": "278df0d6f4eaab384a0f12adbf44ca709e9ab3467f9c301767cad65447b96bcb",
    "macos-x64": "1956da24fdcaf99127c5c93392171e7e2ffc6e6558661006ab5ec18b2ce08f35",
    "linux-x64": "78496c63fbb2d3c2680666b1f92b33b2bb55f73ababc226d6774675b53cc3575",
    "linux-arm64": "036f6652cfd1572ef2c30314bc96d0669b725036d4fc13aa76429e44d8c42627",
    "windows-x64": "5223d24587b9f5ab948e688679f0a53d8b8e56db9de8e0751dd4efb831e5099e",
    "windows-arm64": "bd1374360b84875bafbab0e4ac09467960a3d5b5b6bc19e7f114d9d54d006088",
  },
  "2.2.5.3": {
    "macos-arm64": "e511c704fa97ec60548a062fb380a9cdcaf74d032e34e7edbf5f19b7f91a4dc0",
    "macos-x64": "4b66e1fc74a1a7f3d249b2f47bbbe618748bfe88ad696e7b781c85622f59a79d",
    "linux-x64": "dea1d9aa77807e633cf0bbd7cb9fb60b24ffef05be0f505f51b93ca26abc58ac",
    "linux-arm64": "77297a8315eec462393affecf15410ca15ec5dbf63d7273c17af2bf7fe10d8f1",
    "windows-x64": "4a4dd561dac872c78462b0588479c962ee65a480a8cb6a62ab549fae6f1f18a0",
    "windows-arm64": "2d9a68bad3a26e6f7097a2a07db38f1f60aaf9981235c8699b1fe87e715756aa",
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

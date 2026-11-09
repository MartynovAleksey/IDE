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

DEFAULT_VERSION = "1.19.5.4"

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
  "1.19.5.4": {
    "macos-arm64": "9ac679bf8d4b7ff043e8397665b1e1c9c4b10b1de198f2c44d4e3243237cc6bf",
    "macos-x64": "bf3b9a181aacc1ccf595a1d0470f26bc38935454bb60df85943b093a3af5e4c5",
    "linux-x64": "0112b828e291d37a034ded4620c8092e7dd3d13093ec38a42b4a2a551d1fa1e4",
    "linux-arm64": "318c59f4a7882f2d2a5382dc0be9097f1f99af53fac5c74345eb876c30b92a68",
    "windows-x64": "11f8574137cc2e4d0e9d20e283a14f7df91ce0b8b2df5df1489d353c0fc4a7cf",
    "windows-arm64": "4b897d25179ad44c7fa01b1dd1f1be6e15a176225c458818d1f33984b7b54ba9",
  },
  "2.2.5.4": {
    "macos-arm64": "2dee283736ddda1489aed83120d5ad73808e52d0242cac5c4159a52c47db3861",
    "macos-x64": "af4b731c7fb3b5677f2708c4a8a7a6520065eb0444097bc4cf1bd7c4efb84b24",
    "linux-x64": "32bcd6e533f750cf3f11a291b9547f936323e7a50cc47498f5b35eb8c15c5e23",
    "linux-arm64": "f00870b10d02e63a1c90f8ef5ef42aa546360c6642d583ccb62495c132aeb6e2",
    "windows-x64": "042b38f517ae17f92a1263aee79d34f6baae3084eeef2494b36ce9c267ebb493",
    "windows-arm64": "fb41ad85ee543087430104bb782ea1730acf6547990714159bec7f50259daea5",
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

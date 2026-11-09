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

DEFAULT_VERSION = "1.19.5.6"

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
  "1.19.5.6": {
    "macos-arm64": "9ce661753d0395ebd81cb2b2dc6efba13eb08bf5f1c8e727fc92460a07b38705",
    "macos-x64": "75927b0054e3af65946f50ad95a6ea22d287dfe85fa32c60c2046287267921e5",
    "linux-x64": "99da7841b67e59d2004b620462df12bdf0157d7e17b78fb40553b610342094a9",
    "linux-arm64": "f73e1f14b78f7996e9156c40085eb681a38472b4b34cd11db7d9cecc924b49b4",
    "windows-x64": "bf55f9024eb76266e35cbf39607adf6eb414188615ec098ec6071ee971941c50",
    "windows-arm64": "f7fc365467bb77f71e38631ae9e86956740e54dd3e7af9810f06ce1d4374b952",
  },
  "2.2.5.6": {
    "macos-arm64": "2f89cf95f669d49101a4145c0eb78e3a34e59a69ec9794bd3b7163baad410d89",
    "macos-x64": "d233473f0e4b7ee8ad5c7778ddc441603eb2a26f3b06e2298aec29fc2c498442",
    "linux-x64": "5490fc0ff8ae3be14fd3f3be3db7c4d59386843f166662e95793678d3b030a32",
    "linux-arm64": "ef92b184f4157ef1b3c62a3bca900bc27a90276f4cf4bec8f0acb78fddd13c22",
    "windows-x64": "08fd01d563c03b109cba80f356aee94e1c7772ce0ddfbbdf84047c4c827e6631",
    "windows-arm64": "b41a7f8cbc24ac9051cf11aec90425537869bd02c0375d9a527a43f9ff012da1",
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

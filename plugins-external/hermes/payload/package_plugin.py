#!/usr/bin/env python3
"""Adds a Hermes payload to the plugin zip from `./gradlew buildPlugin`, one zip per OS.

Copies tar entries straight into the zip with their Unix modes and symlinks (npm/npx are symlinks), which the IDE's plugin
installer restores; Gradle's Zip task would follow the links, and unpacking a Linux payload on macOS would hit case clashes.

    usage: package_plugin.py <hermes-intellij-<ver>.zip> <hermes-payload-<hermes-ver>-<os>.tar.gz> <out-dir>
    result: <out-dir>/hermes-intellij-<ver>-<os>.zip
"""
import shutil
import sys
import tarfile
import time
import zipfile
from pathlib import Path


def main(plugin_zip: Path, payload: Path, out_dir: Path) -> None:
  os_name = payload.name.removesuffix(".tar.gz").split("-", 3)[3]  # hermes-payload-<ver>-<os>
  out_dir.mkdir(parents=True, exist_ok=True)
  out = out_dir / f"{plugin_zip.stem}-{os_name}.zip"
  with zipfile.ZipFile(plugin_zip) as src, zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as dst:
    root = src.namelist()[0].split("/")[0]  # hermes-intellij
    for info in src.infolist():
      dst.writestr(info, src.read(info))
    with tarfile.open(payload) as tar:
      for member in tar:
        name = f"{root}/{member.name}"
        info = zipfile.ZipInfo(name + ("/" if member.isdir() else ""), zip_time(member.mtime))
        info.create_system = 3  # Unix: external_attr carries the mode
        if member.isdir():
          info.external_attr = (0o040000 | member.mode) << 16 | 0x10
          dst.writestr(info, b"")
        elif member.issym():
          info.external_attr = (0o120000 | 0o777) << 16
          dst.writestr(info, member.linkname)
        elif member.isfile():
          info.external_attr = (0o100000 | member.mode) << 16
          info.compress_type = zipfile.ZIP_DEFLATED
          with tar.extractfile(member) as data, dst.open(info, "w") as target:
            shutil.copyfileobj(data, target, 1 << 20)
        else:
          raise SystemExit(f"unsupported tar entry: {member.name}")
  with zipfile.ZipFile(out) as check:
    names = set(check.namelist())
    assert {f"{root}/hermes/bin/hermes", f"{root}/hermes/bin/hermes.cmd"} & names, "payload launcher missing"
    assert f"{root}/hermes/manifest.json" in names, "payload manifest missing"
  print(out)


def zip_time(mtime: float) -> tuple:
  return time.localtime(max(mtime, 315532800))[:6]  # zip cannot store dates before 1980


if __name__ == "__main__":
  main(Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3]))

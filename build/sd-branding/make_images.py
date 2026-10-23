# Generates the SD-OpenIDE splash logo and app icons from sd-openide-source.png (already transparent).
# Run: uv run --with pillow --with numpy python -I make_images.py <repo_root>
# then: iconutil -c icns build/sd-branding/generated/product.iconset -o build/idea-community-images/mac/product.icns
import base64
import io
import shutil
import sys
from pathlib import Path

import numpy as np
from PIL import Image


def fit(img: Image.Image, width: int, height: int, margin: float) -> Image.Image:
  scale = min(width * (1 - 2 * margin) / img.width, height * (1 - 2 * margin) / img.height)
  resized = img.resize((round(img.width * scale), round(img.height * scale)), Image.LANCZOS)
  canvas = Image.new("RGBA", (width, height), (0, 0, 0, 0))
  canvas.paste(resized, ((width - resized.width) // 2, (height - resized.height) // 2))
  return canvas


def main(root: Path) -> None:
  branding = root / "build/sd-branding"
  source = Image.open(branding / "sd-openide-source.png").convert("RGBA")
  master = source.crop(source.getchannel("A").getbbox())
  a = np.asarray(master)[..., 3]
  assert a[0, 0] == 0 and a[-1, -1] == 0, "corners must be transparent"
  assert (a > 250).mean() > 0.5, "icon body must be opaque"

  generated = branding / "generated"
  shutil.rmtree(generated, ignore_errors=True)
  generated.mkdir()
  resources = root / "community-resources/resources"
  images = root / "build/idea-community-images"

  fit(master, 640, 400, 0.05).save(resources / "idea_community_logo.png")
  fit(master, 1280, 800, 0.05).save(resources / "idea_community_logo@2x.png")

  icon = fit(master, 1024, 1024, 0.0)  # the source is already a padded macOS-style icon

  def sized(px: int) -> Image.Image:
    return icon.resize((px, px), Image.LANCZOS)

  iconset = generated / "product.iconset"
  iconset.mkdir()
  for px in (16, 32, 128, 256, 512):
    sized(px).save(iconset / f"icon_{px}x{px}.png")
    sized(px * 2).save(iconset / f"icon_{px}x{px}@2x.png")
  sized(128).save(images / "linux/product_128.png")
  icon.save(images / "win/product.ico", sizes=[(s, s) for s in (16, 20, 24, 32, 40, 48, 64, 256)])

  # the platform needs SVG; embed a 2x raster to stay sharp on HiDPI
  for name, size, scaled in (("idea-ce.svg", 64, False), ("idea-ce_16.svg", 16, False), ("idea-ce_16@2x.svg", 32, True)):
    buf = io.BytesIO()
    sized(size * 2).save(buf, "PNG")
    data = base64.b64encode(buf.getvalue()).decode()
    extra = ' data-scaled="true"' if scaled else ""
    (resources / name).write_text(
      f'<svg width="{size}" height="{size}" viewBox="0 0 {size} {size}" xmlns="http://www.w3.org/2000/svg"'
      f' xmlns:xlink="http://www.w3.org/1999/xlink"{extra}>\n'
      f'<image width="{size}" height="{size}" href="data:image/png;base64,{data}" xlink:href="data:image/png;base64,{data}"/>\n</svg>\n'
    )
  print("ok")


if __name__ == "__main__":
  main(Path(sys.argv[1]))

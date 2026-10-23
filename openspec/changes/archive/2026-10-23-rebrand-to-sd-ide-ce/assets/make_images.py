# Generates transparent SD logo images from sd-logo-source.png.
# Run: uv run --with pillow --with numpy python -I make_images.py <assets_dir>
import base64
import io
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

YELLOW_MAX_DIFF = 223.0  # max(255 - c) of the yellow outline (232,178,32) that borders the white background
WHITE_TOLERANCE = 12.0


def remove_white_background(src: Image.Image) -> Image.Image:
  rgb = np.asarray(src.convert("RGB"), dtype=np.float32)
  h, w, _ = rgb.shape
  max_diff = (255.0 - rgb).max(axis=2)

  # only near-white pixels connected to the image border are background; white text and tusks stay
  mask = Image.fromarray(np.where(max_diff < 40, 255, 0).astype(np.uint8), "L").copy()  # copy: floodfill silently ignores array-backed images
  for x, y in [(x, 0) for x in range(w)] + [(x, h - 1) for x in range(w)] + [(0, y) for y in range(h)] + [(w - 1, y) for y in range(h)]:
    if mask.getpixel((x, y)) == 255:
      ImageDraw.floodfill(mask, (x, y), 128)
  bg = np.asarray(mask) == 128

  band = bg.copy()
  for _ in range(3):  # anti-aliased fringe next to the background
    band = band | np.roll(band, 1, 0) | np.roll(band, -1, 0) | np.roll(band, 1, 1) | np.roll(band, -1, 1)

  alpha = np.ones((h, w), dtype=np.float32)
  # the source "white" is slightly off-white, so values up to WHITE_TOLERANCE count as fully transparent
  alpha[band] = np.clip((max_diff[band] - WHITE_TOLERANCE) / (YELLOW_MAX_DIFF - WHITE_TOLERANCE), 0.0, 1.0)
  alpha[bg] = 0.0
  out = rgb.copy()
  a = alpha[..., None]
  unmix = band & (alpha > 0)
  out[unmix] = np.clip((rgb[unmix] - 255.0 * (1.0 - a[unmix])) / a[unmix], 0, 255)
  rgba = np.dstack([out, alpha * 255.0]).round().astype(np.uint8)
  return Image.fromarray(rgba, "RGBA")


def fit(img: Image.Image, width: int, height: int, margin: float) -> Image.Image:
  scale = min(width * (1 - 2 * margin) / img.width, height * (1 - 2 * margin) / img.height)
  resized = img.resize((round(img.width * scale), round(img.height * scale)), Image.LANCZOS)
  canvas = Image.new("RGBA", (width, height), (0, 0, 0, 0))
  canvas.paste(resized, ((width - resized.width) // 2, (height - resized.height) // 2))
  return canvas


def main(assets: Path) -> None:
  master = remove_white_background(Image.open(assets / "sd-logo-source.png"))
  a = np.asarray(master)[..., 3]
  assert a[0, 0] == 0 and a[-1, -1] == 0, "corners must be transparent"
  assert a[1120, 580] == 255, "white HADOOP letters must stay opaque"
  assert (a == 0).mean() > 0.3, "background must be removed"
  master = master.crop(master.getchannel("A").getbbox())  # drop the now-empty margins, the logo itself is untouched
  master.save(assets / "sd-logo-transparent.png")

  generated = assets / "generated"
  generated.mkdir(exist_ok=True)
  fit(master, 640, 400, 0.05).save(generated / "idea_community_logo.png")
  fit(master, 1280, 800, 0.05).save(generated / "idea_community_logo@2x.png")
  icon = fit(master, 1024, 1024, 0.04)
  icon.save(assets / "sd-icon-1024.png")

  def sized(px: int) -> Image.Image:
    return icon.resize((px, px), Image.LANCZOS)

  iconset = generated / "product.iconset"  # packed into product.icns by `iconutil -c icns`
  iconset.mkdir(exist_ok=True)
  for px in (16, 32, 128, 256, 512):
    sized(px).save(iconset / f"icon_{px}x{px}.png")
    sized(px * 2).save(iconset / f"icon_{px}x{px}@2x.png")
  sized(128).save(generated / "product_128.png")
  icon.save(generated / "product.ico", sizes=[(s, s) for s in (16, 20, 24, 32, 40, 48, 64, 256)])

  # the platform needs SVG; embed a 2x raster to stay sharp on HiDPI
  for name, size, scaled in (("idea-ce.svg", 64, False), ("idea-ce_16.svg", 16, False), ("idea-ce_16@2x.svg", 32, True)):
    buf = io.BytesIO()
    sized(size * 2).save(buf, "PNG")
    data = base64.b64encode(buf.getvalue()).decode()
    extra = ' data-scaled="true"' if scaled else ""
    (generated / name).write_text(
      f'<svg width="{size}" height="{size}" viewBox="0 0 {size} {size}" xmlns="http://www.w3.org/2000/svg"'
      f' xmlns:xlink="http://www.w3.org/1999/xlink"{extra}>\n'
      f'<image width="{size}" height="{size}" href="data:image/png;base64,{data}" xlink:href="data:image/png;base64,{data}"/>\n</svg>\n'
    )
  print("ok")


if __name__ == "__main__":
  main(Path(sys.argv[1]))

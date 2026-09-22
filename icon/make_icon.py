"""Builds the mod icon: Flashback's camera with a chest sitting in the corner.

The camera comes out of an installed Flashback jar rather than being committed here — it is
Moulberry's artwork and its licence says not to redistribute it.

    python icon/make_icon.py "C:/.../Flashback-0.39.1-for-MC1.21.8.jar"

The chest is drawn here rather than lifted out of the game's textures. The vanilla chest is an
entity texture wrapped round a model, and the flat front face cut out of it reads as mush at this
size. Sixteen by sixteen, nearest-neighbour up, keeps the pixels square the way an item icon looks.
"""

import sys
import zipfile
from pathlib import Path

from PIL import Image, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
CAMERA = ROOT / "icon" / "flashback-icon.png"
OUT = ROOT / "src" / "main" / "resources" / "assets" / "flashbackinspector" / "icon.png"

OUTLINE = (58, 40, 22, 255)
WOOD_LIGHT = (172, 128, 74, 255)
WOOD = (146, 107, 61, 255)
WOOD_DARK = (112, 80, 44, 255)
SEAM = (82, 57, 31, 255)
LATCH = (196, 196, 196, 255)
LATCH_DARK = (104, 104, 104, 255)


def extract_camera(jar: Path) -> None:
    with zipfile.ZipFile(jar) as zf:
        CAMERA.write_bytes(zf.read("assets/flashback/icon.png"))
    print("extracted", CAMERA)


def chest_sprite() -> Image.Image:
    """A sixteen pixel chest front: lid, seam, body, latch over the middle of the seam."""
    px = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    put = px.putpixel

    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                put((x, y), OUTLINE)
            elif y <= 4:
                put((x, y), WOOD_LIGHT if y <= 3 else WOOD)
            elif y in (5, 6):
                put((x, y), SEAM)
            elif y >= 13:
                put((x, y), WOOD_DARK)
            else:
                put((x, y), WOOD)

    # The latch straddles the seam, which is what makes a brown box read as a chest.
    for y in range(4, 10):
        for x in range(7, 9):
            put((x, y), LATCH_DARK if y in (4, 9) else LATCH)

    return px


def main() -> None:
    if len(sys.argv) > 1:
        extract_camera(Path(sys.argv[1]))
    if not CAMERA.exists():
        raise SystemExit("нет %s — запусти с путём к установленному jar Flashback" % CAMERA)

    camera = Image.open(CAMERA).convert("RGBA")
    size = camera.size[0]
    canvas = Image.new("RGBA", camera.size, (0, 0, 0, 0))
    canvas.alpha_composite(camera)

    # Just over a third of the icon, tucked into the bottom right. The red recording dot lives in
    # the opposite corner, so the two never crowd each other.
    chest = chest_sprite().resize((44, 44), Image.NEAREST)
    margin = 8
    x = size - chest.width - margin
    y = size - chest.height - margin

    # A shadow, so the brown does not float on the white camera body.
    shadow = Image.new("RGBA", camera.size, (0, 0, 0, 0))
    shadow.paste((0, 0, 0, 90), (x - 2, y - 2, x + chest.width + 2, y + chest.height + 2))
    shadow = shadow.filter(ImageFilter.GaussianBlur(3))
    canvas.alpha_composite(shadow)
    canvas.alpha_composite(chest, (x, y))

    canvas.save(OUT)
    print("written", OUT, canvas.size)


if __name__ == "__main__":
    main()

"""Фон окна dmg: стрелка между иконкой приложения и ярлыком Applications. Запуск: make_background.py <каталог>."""
import sys
from PIL import Image, ImageDraw, ImageFont

OUT = sys.argv[1]
FONT = "/System/Library/Fonts/Helvetica.ttc"


def make(scale: int, name: str) -> None:
    w, h = 640 * scale, 400 * scale
    im = Image.new("RGB", (w, h), (0x1C, 0x1D, 0x22))
    d = ImageDraw.Draw(im)
    y, x0, x1, green = 190 * scale, 255 * scale, 385 * scale, (0x7A, 0xC9, 0x6B)
    d.line([(x0, y), (x1, y)], fill=green, width=4 * scale)
    d.polygon([(x1 + 14 * scale, y), (x1 - 4 * scale, y - 12 * scale), (x1 - 4 * scale, y + 12 * scale)], fill=green)
    font = ImageFont.truetype(FONT, 15 * scale)
    text = "Drag Logcat to Applications"
    d.text(((w - d.textlength(text, font=font)) / 2, 330 * scale), text, font=font, fill=(0x9A, 0x9D, 0xA6))
    im.save(f"{OUT}/{name}")


make(1, "bg.png")
make(2, "bg@2x.png")

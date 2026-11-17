"""Efface les icônes de notification de la barre d'état d'une capture du Fairphone 6 (1116 x 2484), entre l'heure et
le Wi-Fi, quand le mode démo d'Android ne les masque pas.

    python3 docs/store/nettoyer-barre.py docs/store/brutes/1-aujourdhui.png
"""
import sys

from PIL import Image, ImageDraw

for f in sys.argv[1:]:
    im = Image.open(f).convert("RGB")
    fond = im.getpixel((5, 5))
    ImageDraw.Draw(im).rectangle((165, 0, 520, 105), fill=fond)  # après « 16:00 », avant le Wi-Fi
    im.save(f, optimize=True)

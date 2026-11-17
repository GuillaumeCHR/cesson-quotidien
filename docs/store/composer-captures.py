"""Captures de la fiche Play Store : une légende en haut, l'écran du téléphone encadré en dessous.

    python3 docs/store/composer-captures.py DOSSIER_DES_CAPTURES [--sortie docs/store/captures]

Lit `legendes.json` (capture, titre, texte) ; les captures absentes du dossier sont ignorées. Chaque image fait
1440 x 2560 (9:16, accepté par Google Play et au-dessus du minimum de 1080 px pour la mise en avant), aux couleurs
de l'app. Rendu par Chrome sans interface (google-chrome --headless).
"""
import argparse
import html
import json
import subprocess
import tempfile
from pathlib import Path

ICI = Path(__file__).resolve().parent
L, H = 1440, 2560

GABARIT = """<!doctype html><html lang="fr"><head><meta charset="utf-8"><style>
html,body{{margin:0;width:{l}px;height:{h}px;overflow:hidden}}
body{{background:#2F6B4F;font-family:"Atkinson Hyperlegible","Inter","Segoe UI",Roboto,sans-serif;color:#F6F3EC;
  display:flex;flex-direction:column;align-items:center;position:relative}}
.vague{{position:absolute;left:0;top:520px}}
h1{{margin:150px 90px 0;font-size:104px;line-height:1.05;font-weight:800;text-align:center;letter-spacing:-1px}}
p{{margin:34px 120px 0;font-size:50px;line-height:1.3;text-align:center;color:#D6EBDF}}
.tel{{position:relative;z-index:1;margin-top:110px;width:1000px;padding:22px;border-radius:96px;background:#1B2A23;
  box-shadow:0 30px 80px rgba(0,0,0,.35)}}
.tel img{{display:block;width:100%;border-radius:76px}}
</style></head><body>
<svg class="vague" width="{l}" height="160" viewBox="0 0 1440 160"><path d="M-20,110 C170,40 300,150 520,100 C740,50 860,150 1080,98 C1220,66 1320,90 1460,70"
 fill="none" stroke="#5AA7D9" stroke-width="18" stroke-linecap="round" opacity=".45"/></svg>
<h1>{titre}</h1><p>{texte}</p>
<div class="tel"><img src="{image}"></div>
</body></html>"""

p = argparse.ArgumentParser()
p.add_argument("dossier", type=Path)
p.add_argument("--sortie", type=Path, default=ICI / "captures")
a = p.parse_args()
a.sortie.mkdir(parents=True, exist_ok=True)
with tempfile.TemporaryDirectory() as tmp:
    for leg in json.loads((ICI / "legendes.json").read_text()):
        src = (a.dossier / leg["capture"]).resolve()
        if not src.is_file():
            print(f"absente : {leg['capture']}")
            continue
        page = Path(tmp) / (src.stem + ".html")
        page.write_text(GABARIT.format(l=L, h=H, titre=html.escape(leg["titre"]), texte=html.escape(leg["texte"]),
                                       image=src.as_uri()))
        sortie = (a.sortie / leg["capture"]).resolve()
        subprocess.run(["google-chrome", "--headless=new", "--disable-gpu", "--hide-scrollbars", "--allow-file-access-from-files",
                        f"--window-size={L},{H}", f"--screenshot={sortie}", page.as_uri()],
                       check=True, capture_output=True)
        print(f"{leg['capture']} -> {sortie}")

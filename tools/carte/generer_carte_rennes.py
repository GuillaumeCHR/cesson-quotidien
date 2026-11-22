#!/usr/bin/env python3
"""Génère le fond du plan de la circulation à l'échelle de Rennes et de sa première couronne (CarteRennes.kt) :
limites des communes, Vilaine et Ille, depuis OpenStreetMap. Le cadre englobe toute la rocade et toute la commune
de Cesson-Sévigné ; les communes qui le débordent sont coupées au dessin.

    curl -s -A "CessonApp-carte/1.0" --data-urlencode data@tools/carte/requete_rennes.overpassql https://overpass-api.de/api/interpreter > osm.json
    python3 tools/carte/generer_carte_rennes.py osm.json android/core/src/main/java/com/yogistudio/cesson/core/carte/CarteRennes.kt web/carte-rennes.js

Données © contributeurs OpenStreetMap, ODbL. Même projection que generer_carte.py (équirectangulaire locale,
normalisée sur la plus grande dimension du cadre, y vers le bas).
"""

from __future__ import annotations

import json
import math
import sys

from generer_carte import assembler, douglas_peucker

# Cadre (degrés) : rocade de Rennes entière et commune de Cesson, avec un peu de marge.
OUEST, EST, SUD, NORD = -1.758, -1.532, 48.064, 48.166
TOLERANCE = 0.0015  # part de la plus grande dimension du cadre : 25 m, invisible à cette échelle
LONGUEUR_MIN_RIVIERE = 0.02  # bras trop courts pour être vus à cette échelle
INSEE_CESSON, INSEE_RENNES = "35051", "35238"


def main(entree: str, sortie: str, sortie_web: str | None = None) -> None:
    elements = json.load(open(entree, encoding="utf-8"))["elements"]
    k = math.cos(math.radians((SUD + NORD) / 2))
    x0, y0 = OUEST * k, NORD
    etendue = max((EST - OUEST) * k, NORD - SUD)
    largeur, hauteur = (EST - OUEST) * k / etendue, (NORD - SUD) / etendue

    def proj(lon, lat):
        return ((lon * k - x0) / etendue, (y0 - lat) / etendue)

    def dedans(p, marge=0.0):
        return -marge <= p[0] <= largeur + marge and -marge <= p[1] <= hauteur + marge

    communes = []
    for e in elements:
        t = e.get("tags", {})
        if e["type"] != "relation" or t.get("boundary") != "administrative" or t.get("admin_level") != "8":
            continue
        anneaux = assembler([[(p["lon"], p["lat"]) for p in m["geometry"]]
                             for m in e["members"] if m["type"] == "way" and m.get("role") == "outer" and m.get("geometry")])
        anneau = max(anneaux, key=len)
        p = douglas_peucker([proj(*q) for q in anneau], TOLERANCE)
        if len(p) >= 4 and any(dedans(q) for q in p):
            communes.append((t.get("ref:INSEE", ""), t["name"], p[:-1] if p[0] == p[-1] else p))
    # Cesson en dernier : sa limite se dessine par-dessus celles de ses voisines.
    communes.sort(key=lambda c: (c[0] == INSEE_CESSON, c[0] == INSEE_RENNES, c[1]))

    vus, morceaux = set(), []
    for e in elements:
        t = e.get("tags", {})
        if t.get("waterway") != "river" or t.get("name") not in ("La Vilaine", "L'Ille"):
            continue
        voies = [(e["id"], e["geometry"])] if e["type"] == "way" else \
            [(m["ref"], m["geometry"]) for m in e.get("members", []) if m["type"] == "way" and m.get("geometry")]
        for ident, g in voies:
            if ident in vus:
                continue
            vus.add(ident)
            morceau = []
            for q in g:
                p = proj(q["lon"], q["lat"])
                if dedans(p, 0.03):
                    morceau.append(p)
                elif morceau:
                    morceaux.append(morceau)
                    morceau = []
            if len(morceau) > 1:
                morceaux.append(morceau)

    def longueur(m):
        return sum(math.hypot(b[0] - a[0], b[1] - a[1]) for a, b in zip(m, m[1:]))

    rivieres = [douglas_peucker(m, TOLERANCE / 2) for m in assembler(morceaux) if len(m) > 1]
    rivieres = [m for m in rivieres if longueur(m) >= LONGUEUR_MIN_RIVIERE]

    def tableau(pts):
        return "floatArrayOf(" + ", ".join(f"{v:.4f}f" for p in pts for v in p) + ")"

    nb = sum(len(c[2]) for c in communes) + sum(map(len, rivieres))
    kt = f'''package com.yogistudio.cesson.core.carte

// Fichier généré par tools/carte/generer_carte_rennes.py : ne pas modifier à la main.
// Données © contributeurs OpenStreetMap (ODbL). {nb} points.

/** Une commune du fond de plan : contour x, y à plat, comme les tracés de [CarteRennes]. */
class CommuneCarte(val insee: String, val nom: String, val contour: FloatArray)

/**
 * Fond du plan de la circulation à l'échelle de Rennes et de sa première couronne : limites des communes, Vilaine et
 * Ille. Coordonnées x, y normalisées sur la plus grande dimension du cadre (y vers le bas) ; [LARGEUR] × [HAUTEUR] est
 * le cadre, qui englobe toute la rocade et toute la commune de Cesson. Les communes qui le débordent sont à couper
 * au dessin. Un point (lon, lat) se place par [projeter].
 */
object CarteRennes {{
    const val LARGEUR = {largeur:.4f}f
    const val HAUTEUR = {hauteur:.4f}f
    const val INSEE_CESSON = "{INSEE_CESSON}"
    const val INSEE_RENNES = "{INSEE_RENNES}"

    private const val COS_LAT0 = {k:.8f}
    private const val X0 = {x0:.8f}
    private const val Y0 = {y0:.8f}
    private const val ETENDUE = {etendue:.8f}

    /** Coordonnées x, y sur la carte d'un point en degrés. */
    fun projeter(lon: Double, lat: Double): Pair<Float, Float> =
        ((lon * COS_LAT0 - X0) / ETENDUE).toFloat() to ((Y0 - lat) / ETENDUE).toFloat()

    /** Cesson en dernier, pour que sa limite passe par-dessus celles de ses voisines. */
    val communes = listOf(
{"".join(f'        CommuneCarte("{i}", "{n}", {tableau(p)}),{chr(10)}' for i, n, p in communes)}    )

    val rivieres = listOf(
{"".join(f"        {tableau(m)},{chr(10)}" for m in rivieres)}    )
}}
'''
    open(sortie, "w", encoding="utf-8").write(kt)
    if sortie_web:
        r = lambda pts: [[round(x, 4), round(y, 4)] for x, y in pts]  # noqa: E731
        donnees = json.dumps({
            "source": "© contributeurs OpenStreetMap (ODbL)",
            "largeur": round(largeur, 4), "hauteur": round(hauteur, 4), "insee_cesson": INSEE_CESSON, "insee_rennes": INSEE_RENNES,
            "projection": {"cos_lat0": round(k, 8), "x0": round(x0, 8), "y0": round(y0, 8), "etendue": round(etendue, 8)},
            "communes": [{"insee": i, "nom": n, "contour": r(p)} for i, n, p in communes],
            "rivieres": [r(m) for m in rivieres],
        }, ensure_ascii=False, separators=(",", ":"))
        open(sortie_web, "w", encoding="utf-8").write(
            "// Fichier généré par tools/carte/generer_carte_rennes.py : ne pas modifier à la main.\n"
            f"export default {donnees};\n")
    print(f"{nb} points : {len(communes)} communes, {len(rivieres)} tracés de rivière ; cadre {largeur:.3f} × {hauteur:.3f}")


if __name__ == "__main__":
    main(*sys.argv[1:4])

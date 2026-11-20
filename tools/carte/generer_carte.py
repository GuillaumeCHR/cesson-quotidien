#!/usr/bin/env python3
"""Génère la carte vectorielle de Cesson-Sévigné (CarteCesson.kt) depuis OpenStreetMap : fond de l'intro et du plan
de la circulation. Avec un troisième argument, écrit aussi le contour et la Vilaine pour la version web (module JS).

    curl -s -A "CessonApp-carte/1.0" --data-urlencode data@tools/carte/requete.overpassql https://overpass-api.de/api/interpreter > osm.json
    python3 tools/carte/generer_carte.py osm.json android/core/src/main/java/com/yogistudio/cesson/core/carte/CarteCesson.kt web/carte-cesson.js

 Données © contributeurs OpenStreetMap, ODbL.
Coordonnées projetées (équirectangulaire locale), normalisées dans [0, 1] sur la plus grande dimension
de la commune, simplifiées (Douglas-Peucker) : quelques milliers de points, dessinés en Canvas.
"""

from __future__ import annotations

import json
import math
import sys

TOLERANCE = 0.0025      # simplification, en part de la plus grande dimension
AIRE_MIN_VERT = 0.0004  # surfaces trop petites pour être vues à l'écran : écartées
AIRE_MIN_URBAIN = 0.0003
AIRE_MIN_EAU = 0.00005

# Lieux où l'intro pose ses repères (lat, lon), relevés dans OpenStreetMap (identifiants en commentaire)
# et dans les jeux de données de l'app.
LIEUX = {
    "viasilva": (48.13150, -1.62004),      # way 699390793, station de métro Cesson - Viasilva
    "atalante": (48.12714, -1.62804),      # way 669388591, station de métro Atalante
    "halte": (48.11258, -1.60255),         # node 8986308784, halte SNCF
    "piscine": (48.11925, -1.60230),       # way 81781277
    "mediatheque": (48.11688, -1.60283),   # node 13606929252
    "decheterie": (48.10674, -1.60222),    # way 183340947
    "pont_briand": (48.12366, -1.56368),   # station Hub'Eau J709063002
    "carrefour": (48.11263, -1.58941),     # flux des carburants, station 35510001
    "golf": (48.13095, -1.58476),          # way 118940855
}


def assembler(chemins: list[list[tuple[float, float]]]) -> list[list[tuple[float, float]]]:
    """Recolle des voies bout à bout en anneaux (contours de relations)."""
    restants = [c[:] for c in chemins if len(c) > 1]
    anneaux = []
    while restants:
        anneau = restants.pop(0)
        change = True
        while change and anneau[0] != anneau[-1]:
            change = False
            for i, c in enumerate(restants):
                if c[0] == anneau[-1]:
                    anneau += c[1:]
                elif c[-1] == anneau[-1]:
                    anneau += c[-2::-1]
                elif c[-1] == anneau[0]:
                    anneau = c[:-1] + anneau
                elif c[0] == anneau[0]:
                    anneau = c[:0:-1] + anneau
                else:
                    continue
                restants.pop(i)
                change = True
                break
        anneaux.append(anneau)
    return anneaux


def douglas_peucker(pts, tol):
    if len(pts) < 3:
        return pts
    (x1, y1), (x2, y2) = pts[0], pts[-1]
    dx, dy = x2 - x1, y2 - y1
    n = math.hypot(dx, dy) or 1e-12
    dmax, imax = 0.0, 0
    for i in range(1, len(pts) - 1):
        x, y = pts[i]
        d = abs(dy * x - dx * y + x2 * y1 - y2 * x1) / n if n > 1e-12 else math.hypot(x - x1, y - y1)
        if d > dmax:
            dmax, imax = d, i
    if dmax <= tol:
        return [pts[0], pts[-1]]
    return douglas_peucker(pts[: imax + 1], tol)[:-1] + douglas_peucker(pts[imax:], tol)


def aire(p):
    return abs(sum(p[i][0] * p[i - 1][1] - p[i - 1][0] * p[i][1] for i in range(len(p)))) / 2


def main(entree: str, sortie: str, sortie_web: str | None = None) -> None:
    elements = json.load(open(entree, encoding="utf-8"))["elements"]
    commune = next(e for e in elements if e["type"] == "relation" and e.get("tags", {}).get("boundary") == "administrative")
    contour_geo = max(assembler([[(p["lon"], p["lat"]) for p in m["geometry"]]
                                 for m in commune["members"] if m["type"] == "way" and m.get("role") == "outer"]), key=len)
    lons, lats = [p[0] for p in contour_geo], [p[1] for p in contour_geo]
    lat0 = (min(lats) + max(lats)) / 2
    k = math.cos(math.radians(lat0))
    x0, y0 = min(lons) * k, max(lats)
    etendue = max((max(lons) - min(lons)) * k, max(lats) - min(lats))
    largeur, hauteur = (max(lons) - min(lons)) * k / etendue, (max(lats) - min(lats)) / etendue

    def proj(lon, lat):
        return ((lon * k - x0) / etendue, (y0 - lat) / etendue)

    def dedans(p, marge=0.08):
        return -marge <= p[0] <= largeur + marge and -marge <= p[1] <= hauteur + marge

    def polygones(filtre, aire_min):
        res = []
        for e in elements:
            t = e.get("tags", {})
            if not filtre(t):
                continue
            if e["type"] == "way" and e.get("geometry") and e["nodes"][0] == e["nodes"][-1]:
                anneaux = [[(p["lon"], p["lat"]) for p in e["geometry"]]]
            elif e["type"] == "relation":
                anneaux = assembler([[(p["lon"], p["lat"]) for p in m["geometry"]]
                                     for m in e.get("members", []) if m["type"] == "way" and m.get("role") == "outer" and m.get("geometry")])
            else:
                continue
            for a in anneaux:
                p = douglas_peucker([proj(*q) for q in a], TOLERANCE)
                if len(p) >= 4 and aire(p) >= aire_min and any(dedans(q, 0) for q in p):
                    res.append(p[:-1] if p[0] == p[-1] else p)
        return res

    vert = polygones(lambda t: t.get("landuse") in {"forest", "meadow", "grass", "recreation_ground", "village_green"}
                     or t.get("leisure") in {"park", "golf_course", "nature_reserve"}
                     or t.get("natural") in {"wood", "scrub", "wetland", "heath"}, AIRE_MIN_VERT)
    urbain = polygones(lambda t: t.get("landuse") in {"residential", "commercial", "industrial", "retail"}, AIRE_MIN_URBAIN)
    eau = polygones(lambda t: t.get("natural") == "water", AIRE_MIN_EAU)

    # La Vilaine : voies de la rivière, coupées au cadre de la commune (avec une marge).
    vilaine = []
    for e in elements:
        t = e.get("tags", {})
        if t.get("waterway") != "river" or "Vilaine" not in t.get("name", ""):
            continue
        voies = [e["geometry"]] if e["type"] == "way" else [m["geometry"] for m in e.get("members", []) if m.get("geometry")]
        for g in voies:
            morceau = []
            for q in g:
                p = proj(q["lon"], q["lat"])
                if dedans(p):
                    morceau.append(p)
                elif morceau:
                    vilaine.append(morceau)
                    morceau = []
            if len(morceau) > 1:
                vilaine.append(morceau)
    vilaine = [douglas_peucker(m, TOLERANCE / 2) for m in assembler(vilaine) if len(m) > 1]
    vilaine = sorted(vilaine, key=len, reverse=True)[:3]  # le lit principal ; les bras secondaires sont trop fins à cette échelle

    def position(e):
        if "center" in e:
            return e["center"]["lon"], e["center"]["lat"]
        return (e["lon"], e["lat"]) if "lon" in e else None

    mairie = next((position(e) for e in elements if e.get("tags", {}).get("amenity") == "townhall" and position(e)), None)
    centre = proj(*mairie) if mairie else (largeur / 2, hauteur / 2)
    contour = douglas_peucker([proj(*q) for q in contour_geo], TOLERANCE)

    lieux = {nom: proj(lon, lat) for nom, (lat, lon) in LIEUX.items()}

    def tableau(pts):
        return "floatArrayOf(" + ", ".join(f"{v:.4f}f" for p in pts for v in p) + ")"

    def liste(polys):
        return "listOf(\n" + "".join(f"        {tableau(p)},\n" for p in polys) + "    )"

    nb = len(contour) + sum(map(len, vert + urbain + eau + vilaine))
    kt = f'''package com.yogistudio.cesson.core.carte

// Fichier généré par tools/carte/generer_carte.py : ne pas modifier à la main.
// Données © contributeurs OpenStreetMap (ODbL). {nb} points.

/**
 * Carte de Cesson-Sévigné (intro, plan de la circulation) : coordonnées x, y à plat dans des FloatArray, normalisées sur la
 * plus grande dimension de la commune (y vers le bas). [LARGEUR] × [HAUTEUR] est le cadre de la commune.
 * Un point (lon, lat) se place par [projeter] : équirectangulaire locale, comme les tracés ci-dessous.
 */
object CarteCesson {{
    const val LARGEUR = {largeur:.4f}f
    const val HAUTEUR = {hauteur:.4f}f
    const val CENTRE_X = {centre[0]:.4f}f
    const val CENTRE_Y = {centre[1]:.4f}f

    private const val COS_LAT0 = {k:.8f}
    private const val X0 = {x0:.8f}
    private const val Y0 = {y0:.8f}
    private const val ETENDUE = {etendue:.8f}

    /** Coordonnées x, y sur la carte d'un point en degrés. */
    fun projeter(lon: Double, lat: Double): Pair<Float, Float> =
        ((lon * COS_LAT0 - X0) / ETENDUE).toFloat() to ((Y0 - lat) / ETENDUE).toFloat()

    val contour = {tableau(contour)}

    val vilaine = {liste(vilaine)}

    val eau = {liste(eau)}

    val vert = {liste(vert)}

    val urbain = {liste(urbain)}

    /** Lieux des repères de l'intro (x, y). */
    val lieux: Map<String, FloatArray> = mapOf(
{"".join(f'        "{n}" to floatArrayOf({x:.4f}f, {y:.4f}f),{chr(10)}' for n, (x, y) in lieux.items())}    )
}}
'''
    open(sortie, "w", encoding="utf-8").write(kt)
    if sortie_web:
        r = lambda pts: [[round(x, 4), round(y, 4)] for x, y in pts]  # noqa: E731
        donnees = json.dumps({
            "source": "© contributeurs OpenStreetMap (ODbL)",
            "largeur": round(largeur, 4), "hauteur": round(hauteur, 4),
            "projection": {"cos_lat0": round(k, 8), "x0": round(x0, 8), "y0": round(y0, 8), "etendue": round(etendue, 8)},
            "contour": r(contour), "vilaine": [r(m) for m in vilaine],
        }, ensure_ascii=False, separators=(",", ":"))
        open(sortie_web, "w", encoding="utf-8").write(
            "// Fichier généré par tools/carte/generer_carte.py : ne pas modifier à la main.\n"
            f"export default {donnees};\n")
    print(f"{nb} points : contour {len(contour)}, Vilaine {len(vilaine)} tracés, eau {len(eau)}, vert {len(vert)}, urbain {len(urbain)}"
          f" ; cadre {largeur:.3f} × {hauteur:.3f}, mairie {centre[0]:.3f}, {centre[1]:.3f}")


if __name__ == "__main__":
    main(*sys.argv[1:4])

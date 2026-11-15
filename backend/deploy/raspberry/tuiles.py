"""Génère tuiles-http.conf : le cache nginx des tuiles OpenStreetMap, limité à l'emprise de Cesson-Sévigné.

Le Pi relaie les tuiles de tile.openstreetmap.org pour la carte de l'app : OpenStreetMap n'est sollicité
qu'une fois par tuile (cache de longue durée) et ne voit jamais les utilisateurs. Seules les tuiles de la
commune (avec une marge) et des zooms 12 à 18 sont servies : le Pi n'est pas un relais ouvert.

    python3 tuiles.py > tuiles-http.conf
"""

import math

# Emprise de la commune (geo.api.gouv.fr, INSEE 35051) avec une marge d'environ 500 m.
OUEST, EST, SUD, NORD = -1.645, -1.541, 48.081, 48.166
ZOOMS = range(12, 19)


def tuile(lat: float, lon: float, z: int) -> tuple[int, int]:
    n = 2 ** z
    x = int((lon + 180) / 360 * n)
    y = int((1 - math.asinh(math.tan(math.radians(lat))) / math.pi) / 2 * n)
    return x, y


def main() -> None:
    print("# Généré par tuiles.py : ne pas modifier à la main. Contexte http de nginx (conf.d).")
    print("proxy_cache_path /var/cache/nginx/cesson-tuiles levels=1:2 keys_zone=tuiles:10m max_size=1g")
    print("                 inactive=365d use_temp_path=off;")
    print("# Derrière Tailscale Funnel, toutes les requêtes arrivent de 127.0.0.1 : limite globale, large.")
    print("limit_req_zone $server_name zone=tuiles_debit:1m rate=40r/s;")
    xs, ys = [], []
    for z in ZOOMS:
        x0, y0 = tuile(NORD, OUEST, z)
        x1, y1 = tuile(SUD, EST, z)
        xs += [f"{z}/{x}" for x in range(x0, x1 + 1)]
        ys += [f"{z}/{y}" for y in range(y0, y1 + 1)]
    for nom, cles, var in (("x", xs, "$tuile_tz/$tuile_tx"), ("y", ys, "$tuile_tz/$tuile_ty")):
        print(f'map "{var}" $tuile_{nom}_ok {{')
        print("    default 0;")
        for c in cles:
            print(f'    "{c}" 1;')
        print("}")
    print('map "$tuile_x_ok$tuile_y_ok" $tuile_dans_cesson {')
    print('    default 0;')
    print('    "11" 1;')
    print("}")


if __name__ == "__main__":
    main()

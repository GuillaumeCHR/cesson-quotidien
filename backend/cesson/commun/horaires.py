"""Horaires au format OSM `opening_hours`, dans le sous-ensemble utilisé par nos fichiers de saisie.

Pris en charge : jours (`Mo`…`Su`, plages `Mo-Fr`, listes `Sa,Su`), plages horaires `09:00-12:30`
séparées par des virgules, `off`, plusieurs règles séparées par `;` (une règle plus loin remplace
les précédentes pour ses jours, comme dans la spécification OSM). Tout le reste est refusé :
mieux vaut une erreur à la saisie qu'un horaire faux affiché.

Les saisons, vacances et fermetures ne passent pas par cette syntaxe : elles sont décrites dans
le YAML par des périodes datées et des exceptions, plus lisibles pour qui met à jour le fichier.
"""

from __future__ import annotations

import re
from datetime import date, datetime, time, timedelta
from zoneinfo import ZoneInfo

PARIS = ZoneInfo("Europe/Paris")
JOURS = ["Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"]
_PLAGE = re.compile(r"^(\d{2}):(\d{2})-(\d{2}):(\d{2})$")


class HoraireInvalide(ValueError):
    pass


Semaine = dict[int, list[tuple[time, time]]]  # 0 = lundi


def _jours(selecteur: str) -> list[int]:
    jours: list[int] = []
    for morceau in selecteur.split(","):
        if "-" in morceau:
            a, b = morceau.split("-", 1)
            if a not in JOURS or b not in JOURS:
                raise HoraireInvalide(f"jours inconnus : {morceau!r}")
            i, j = JOURS.index(a), JOURS.index(b)
            if j < i:
                raise HoraireInvalide(f"plage de jours à l'envers : {morceau!r}")
            jours += range(i, j + 1)
        elif morceau in JOURS:
            jours.append(JOURS.index(morceau))
        else:
            raise HoraireInvalide(f"jour inconnu : {morceau!r}")
    return jours


def _plages(texte: str) -> list[tuple[time, time]]:
    if texte == "off":
        return []
    plages = []
    for morceau in texte.split(","):
        m = _PLAGE.match(morceau.strip())
        if not m:
            raise HoraireInvalide(f"plage horaire illisible : {morceau!r} (attendu 09:00-12:30)")
        h1, m1, h2, m2 = (int(x) for x in m.groups())
        if not (0 <= h1 < 24 and 0 <= h2 <= 24 and m1 < 60 and m2 < 60):
            raise HoraireInvalide(f"heure impossible : {morceau!r}")
        debut, fin = time(h1, m1), time(23, 59, 59) if h2 == 24 else time(h2, m2)
        if fin <= debut:
            raise HoraireInvalide(f"plage qui finit avant de commencer : {morceau!r}")
        if plages and debut < plages[-1][1]:
            raise HoraireInvalide(f"plages qui se chevauchent ou dans le désordre : {texte!r}")
        plages.append((debut, fin))
    return plages


def lire(opening_hours: str) -> Semaine:
    """Semaine type : pour chaque jour (0 = lundi), ses plages d'ouverture (vide = fermé)."""
    semaine: Semaine = {j: [] for j in range(7)}
    regles = [r.strip() for r in opening_hours.split(";") if r.strip()]
    if not regles:
        raise HoraireInvalide("horaire vide")
    for regle in regles:
        if regle == "24/7":
            for j in range(7):
                semaine[j] = [(time(0), time(23, 59, 59))]
            continue
        parties = regle.split(None, 1)
        if len(parties) != 2:
            raise HoraireInvalide(f"règle sans jours ou sans heures : {regle!r}")
        plages = _plages(parties[1].strip())
        for j in _jours(parties[0]):
            semaine[j] = plages
    return semaine


def plages_du_jour(semaine: Semaine, jour: date) -> list[tuple[datetime, datetime]]:
    return [(datetime.combine(jour, a, PARIS), datetime.combine(jour, b, PARIS))
            for a, b in semaine[jour.weekday()]]


def jours(debut: date, nombre: int) -> list[date]:
    return [debut + timedelta(days=i) for i in range(nombre)]


def heure_de_pointe(t: datetime) -> bool:
    """En semaine, de 6h à 10h et de 17h à 21h (heure de Paris) : les collectes de « Bouger » s'y resserrent."""
    local = t.astimezone(PARIS)
    return local.weekday() < 5 and (6 <= local.hour < 10 or 17 <= local.hour < 21)

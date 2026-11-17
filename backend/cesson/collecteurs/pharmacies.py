"""Pharmacies de Cesson-Sévigné et leurs horaires d'ouverture (OpenStreetMap).

Une requête Overpass par jour : les officines de la commune (code INSEE), leur nom, adresse, téléphone et
`opening_hours`. Comme pour les équipements, le backend publie les plages d'ouverture des 14 prochains jours :
l'app n'a plus qu'à comparer à l'heure qu'il est.

Les jours fériés, une officine est fermée sauf si elle est de garde ; la garde n'est pas dans OSM (elle se
consulte sur ameli ou au 3237), le jour est donc publié fermé avec son motif. Un horaire qu'on ne sait pas lire
(syntaxe OSM hors du sous-ensemble de `horaires.py`) donne des jours « inconnus », jamais un horaire faux.
"""

from __future__ import annotations

import re
from datetime import date, datetime, timedelta
from typing import Any

import httpx

from ..commun.collecteur import Collecteur, ErreurSource, ErreurValidation, obtenir
from ..commun.feries import ferie
from ..commun.horaires import PARIS, HoraireInvalide, Semaine, jours, lire, plages_du_jour
from ..commun.modele import Coord, Lieu, Modele
from .equipements import JourHoraire, Plage

# Serveur principal, puis un miroir : Overpass est un service bénévole, parfois saturé.
OVERPASS = ("https://overpass-api.de/api/interpreter", "https://overpass.private.coffee/api/interpreter")
# Une requête par zone administrative peut prendre une minute quand le serveur est chargé.
DELAI_OVERPASS = httpx.Timeout(90.0, connect=10.0)
INSEE = "35051"
REQUETE = (f'[out:json][timeout:60];area["ref:INSEE"="{INSEE}"]["boundary"="administrative"]->.a;'
           'nwr["amenity"="pharmacy"](area.a);out tags center;')
NB_JOURS = 14
# Règle des jours fériés : on applique de toute façon « fermé sauf garde », elle n'apporte rien.
_REGLE_FERIES = re.compile(r"^\s*PH\s+(off|closed)\s*$", re.I)


class Pharmacie(Modele):
    lieu: Lieu
    telephone: str | None = None
    horaires: str | None = None  # `opening_hours` d'OSM, tel quel
    horaires_verifies_le: date | None = None  # `check_date:opening_hours` : dernier contrôle sur place
    jours: list[JourHoraire]


class DonneesPharmacies(Modele):
    pharmacies: list[Pharmacie]


def telephone_lisible(brut: str | None) -> str | None:
    """« +33299832221 », « +33 2 99 83 23 18 » → « 02 99 83 22 21 ». Le premier numéro si OSM en liste plusieurs."""
    if not brut:
        return None
    chiffres = re.sub(r"\D", "", brut.split(";")[0])
    if chiffres.startswith("33") and len(chiffres) == 11:
        chiffres = "0" + chiffres[2:]
    if len(chiffres) != 10 or not chiffres.startswith("0"):
        return brut.strip()
    return " ".join(chiffres[i:i + 2] for i in range(0, 10, 2))


def semaine_type(opening_hours: str | None) -> Semaine | None:
    """Semaine type lue dans OSM, ou None si absente ou illisible."""
    if not opening_hours:
        return None
    regles = [r for r in opening_hours.split(";") if r.strip() and not _REGLE_FERIES.match(r)]
    try:
        return lire(";".join(regles))
    except HoraireInvalide:
        return None


def nom_pharmacie(tags: dict[str, str]) -> str:
    nom = (tags.get("name") or "").strip()
    if not nom:
        return "Pharmacie"
    return nom if nom.lower().startswith("pharmacie") else f"Pharmacie {nom}"


def date_controle(tags: dict[str, str]) -> date | None:
    for cle in ("check_date:opening_hours", "check_date"):
        try:
            return date.fromisoformat((tags.get(cle) or "")[:10])
        except ValueError:
            continue
    return None


def adresse(tags: dict[str, str]) -> str | None:
    rue = (tags.get("addr:street") or "").strip()
    if not rue:
        return None
    numero = (tags.get("addr:housenumber") or "").strip()
    return f"{numero} {rue}".strip()


class CollecteurPharmacies(Collecteur[DonneesPharmacies]):
    module = "pharmacies"
    source = "OpenStreetMap (contributeurs OSM), via l'API Overpass"
    licence = "ODbL"
    url_source = "https://www.openstreetmap.org/"
    fraicheur = timedelta(hours=24)
    modele_donnees = DonneesPharmacies

    def telecharger(self, client: httpx.Client) -> Any:
        erreurs = []
        for serveur in OVERPASS:
            try:
                r = obtenir(client, serveur, params={"data": REQUETE}, delai=DELAI_OVERPASS)
                return r.json()
            except (ErreurSource, ValueError) as e:
                erreurs.append(f"{serveur} : {e}")
        raise ErreurSource(" ; ".join(erreurs))

    def valider(self, brut: Any) -> None:
        elements = brut.get("elements") if isinstance(brut, dict) else None
        if not isinstance(elements, list):
            raise ErreurValidation("champ « elements » absent")
        # Cinq officines à Cesson en novembre 2026 : moins de trois, c'est la requête ou la zone qui a cassé.
        if len(elements) < 3:
            raise ErreurValidation(f"seulement {len(elements)} pharmacie(s) à Cesson")

    def convertir(self, brut: Any, maintenant: datetime) -> DonneesPharmacies:
        aujourdhui = maintenant.astimezone(PARIS).date()
        pharmacies = []
        for e in brut["elements"]:
            tags = e.get("tags") or {}
            point = e.get("center") or e
            coord = Coord(lat=point["lat"], lon=point["lon"]) if "lat" in point and "lon" in point else None
            horaires = " ".join((tags.get("opening_hours") or "").split()) or None
            semaine = semaine_type(horaires)
            jours_publies = []
            for jour in jours(aujourdhui, NB_JOURS):
                if semaine is None:
                    jours_publies.append(JourHoraire(date=jour, connu=False))
                elif ferie(jour):
                    jours_publies.append(JourHoraire(date=jour, connu=True, motif=f"Jour férié ({ferie(jour)})"))
                else:
                    jours_publies.append(JourHoraire(date=jour, connu=True, plages=[
                        Plage(debut=a, fin=b) for a, b in plages_du_jour(semaine, jour)]))
            pharmacies.append(Pharmacie(
                lieu=Lieu(id=f"osm:{e['type']}/{e['id']}", nom=nom_pharmacie(tags), type="pharmacie", coord=coord,
                          adresse=adresse(tags), source="OpenStreetMap"),
                telephone=telephone_lisible(tags.get("phone") or tags.get("contact:phone")),
                horaires=horaires,
                horaires_verifies_le=date_controle(tags),
                jours=jours_publies,
            ))
        pharmacies.sort(key=lambda p: p.lieu.nom)
        return DonneesPharmacies(pharmacies=pharmacies)

    def valide_jusqu_a(self, donnees: DonneesPharmacies, maintenant: datetime) -> datetime:
        # Les plages couvrent 14 jours ; au-delà de 36 h sans recalcul, « aujourd'hui » glisse.
        return maintenant + timedelta(hours=36)

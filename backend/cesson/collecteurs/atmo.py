"""Qualité de l'air, pollens et épisodes de pollution (Atmo Data, données d'Air Breizh).

Trois modules, trois cartes dans l'app, une seule connexion à l'API Atmo Data :

- `qualite_air` : indice ATMO du jour et du lendemain. Air Breizh ne le calcule pas commune par
  commune mais pour Rennes Métropole (EPCI 243500139) : c'est l'indice qui vaut pour Cesson.
  Publication vers 15h45.
- `pollens` : indice pollinique de Cesson-Sévigné même (INSEE 35051), du jour au surlendemain,
  espèce par espèce. Publication vers 16h15.
- `episodes_pollution` : procédures préfectorales d'Ille-et-Vilaine, par polluant, d'hier à demain.
  Lu en CSV : le GeoJSON porte les contours du département (30 Mo pour 12 lignes).

L'API demande un compte (gratuit). Les identifiants restent sur le serveur, dans le fichier
désigné par CESSON_ATMO_IDENTIFIANTS (par défaut /srv/cesson/secrets/atmo.env, lisible par
l'utilisateur cesson seul) : deux lignes « utilisateur=… » et « mot_de_passe=… ». Jamais dans le
dépôt, jamais dans l'app. Le jeton obtenu vaut 24 h ; chaque collecte en redemande un.

Licence ODbL, mention obligatoire « Source : Atmo France / AASQA ».
"""

from __future__ import annotations

import csv
import io
import os
import re
from datetime import date, datetime, timedelta
from enum import StrEnum
from pathlib import Path
from typing import Any
from zoneinfo import ZoneInfo

import httpx

from ..commun.collecteur import Collecteur, ErreurSource, ErreurValidation, obtenir
from ..commun.modele import Alerte, Modele, NiveauAlerte

BASE = "https://admindata.atmo-france.org"
IDENTIFIANTS_DEFAUT = "/srv/cesson/secrets/atmo.env"
# Mention imposée par les conditions d'utilisation d'Atmo Data, à reprendre telle quelle partout.
SOURCE = "Atmo France / AASQA"
LICENCE = "ODbL"
URL_AIR_BREIZH = "https://www.airbreizh.asso.fr/"
CODE_EPCI = "243500139"  # Rennes Métropole
CODE_COMMUNE = "35051"  # Cesson-Sévigné
CODE_DEPARTEMENT = "35"
PARIS = ZoneInfo("Europe/Paris")
COULEUR = re.compile(r"^#[0-9a-fA-F]{6}$")


# --- Connexion -------------------------------------------------------------------------------

def lire_identifiants(chemin: str | None = None) -> tuple[str, str]:
    p = Path(chemin or os.environ.get("CESSON_ATMO_IDENTIFIANTS") or IDENTIFIANTS_DEFAUT)
    try:
        lignes = p.read_text(encoding="utf-8").splitlines()
    except OSError as e:
        raise ErreurSource(f"identifiants Atmo illisibles ({p}) : {e.strerror}") from e
    valeurs = dict(l.split("=", 1) for l in lignes if "=" in l)
    utilisateur, mot_de_passe = valeurs.get("utilisateur", "").strip(), valeurs.get("mot_de_passe", "").strip()
    if not utilisateur or not mot_de_passe:
        raise ErreurSource(f"identifiants Atmo incomplets dans {p}")
    return utilisateur, mot_de_passe


def connexion(client: httpx.Client) -> dict[str, str]:
    """En-têtes d'autorisation pour l'API. Le message d'erreur ne reprend jamais les identifiants."""
    utilisateur, mot_de_passe = lire_identifiants()
    try:
        r = client.post(f"{BASE}/api/login", json={"username": utilisateur, "password": mot_de_passe})
    except httpx.TransportError as e:
        raise ErreurSource(f"connexion Atmo impossible : {e}") from e
    if r.status_code == 401:
        raise ErreurSource("connexion Atmo refusée : identifiants invalides")
    if r.status_code != 200:
        raise ErreurSource(f"connexion Atmo : HTTP {r.status_code}")
    try:
        jeton = r.json()["token"]
    except (ValueError, KeyError, TypeError) as e:
        raise ErreurValidation("connexion Atmo : jeton absent de la réponse") from e
    return {"Authorization": f"Bearer {jeton}"}


def _geojson(client: httpx.Client, chemin: str, params: dict[str, str]) -> list[dict]:
    r = obtenir(client, BASE + chemin, params=params, headers=connexion(client))
    try:
        brut = r.json()
    except ValueError as e:
        raise ErreurValidation(f"{chemin} : réponse non JSON : {e}") from e
    if not isinstance(brut, dict) or not isinstance(brut.get("features"), list):
        raise ErreurValidation(f"{chemin} : champ « features » absent")
    return [f["properties"] for f in brut["features"] if isinstance(f, dict) and isinstance(f.get("properties"), dict)]


def _jour(valeur: Any) -> date:
    """« 2026-11-16 » ou « 2026/11/16 » (le CSV des épisodes)."""
    return date.fromisoformat(str(valeur)[:10].replace("/", "-"))


def _horodatage(valeur: Any) -> datetime:
    """« 2026-11-16T15:47:19.614+01:00 » ou « 2026/11/16 15:21:40.674+01 »."""
    s = str(valeur).replace("/", "-").replace(" ", "T")
    if re.search(r"[+-]\d\d$", s):
        s += ":00"
    d = datetime.fromisoformat(s)
    if d.tzinfo is None:
        raise ValueError(f"horodatage sans fuseau : {valeur!r}")
    return d


def _aujourdhui(maintenant: datetime) -> date:
    return maintenant.astimezone(PARIS).date()


# --- Indice ATMO -----------------------------------------------------------------------------

LIBELLES_ATMO = {0: "Absent", 1: "Bon", 2: "Moyen", 3: "Dégradé", 4: "Mauvais", 5: "Très mauvais",
                 6: "Extrêmement mauvais", 7: "Événement"}
SOUS_INDICES = {"no2": "NO₂", "o3": "O₃", "pm10": "PM10", "pm25": "PM2,5", "so2": "SO₂"}


class SousIndice(Modele):
    polluant: str  # no2, o3, pm10, pm25, so2
    nom: str  # NO₂, O₃…
    code: int


class JourAtmo(Modele):
    date: date
    code: int  # 0 absent, 1 bon … 6 extrêmement mauvais, 7 événement
    libelle: str
    couleur: str  # couleur officielle de l'indice, #rrggbb
    sous_indices: list[SousIndice]
    # Polluants dont le sous-indice fait l'indice (le plus élevé), vides si l'indice est absent.
    responsables: list[str]


class DonneesQualiteAir(Modele):
    zone: str
    mis_a_jour: datetime
    jours: list[JourAtmo]


class CollecteurQualiteAir(Collecteur[DonneesQualiteAir]):
    module = "qualite_air"
    source = SOURCE
    licence = LICENCE
    url_source = URL_AIR_BREIZH
    fraicheur = timedelta(hours=24)
    modele_donnees = DonneesQualiteAir

    def telecharger(self, client: httpx.Client) -> Any:
        auj = datetime.now(PARIS).date()
        return _geojson(client, "/api/v2/data/indices/atmo", {
            "format": "geojson", "code_zone": CODE_EPCI,
            "date": (auj + timedelta(days=1)).isoformat(), "date_historique": auj.isoformat()})

    def valider(self, brut: Any) -> None:
        if not brut:
            raise ErreurValidation("aucun indice ATMO pour Rennes Métropole")
        for p in brut:
            if p.get("code_zone") != CODE_EPCI:
                raise ErreurValidation(f"indice d'une autre zone : {p.get('code_zone')!r}")
            if p.get("code_qual") not in LIBELLES_ATMO:
                raise ErreurValidation(f"indice ATMO inattendu : {p.get('code_qual')!r}")
            if not COULEUR.match(str(p.get("coul_qual"))):
                raise ErreurValidation(f"couleur d'indice inattendue : {p.get('coul_qual')!r}")
            for cle in SOUS_INDICES:
                if p.get(f"code_{cle}") not in LIBELLES_ATMO:
                    raise ErreurValidation(f"sous-indice {cle} inattendu : {p.get(f'code_{cle}')!r}")

    def convertir(self, brut: Any, maintenant: datetime) -> DonneesQualiteAir:
        jours = {}
        for p in brut:
            sous = [SousIndice(polluant=c, nom=n, code=int(p[f"code_{c}"])) for c, n in SOUS_INDICES.items()]
            code = int(p["code_qual"])
            responsables = [s.polluant for s in sous if s.code == code] if 1 <= code <= 6 else []
            jours[_jour(p["date_ech"])] = JourAtmo(
                date=_jour(p["date_ech"]), code=code, libelle=LIBELLES_ATMO[code],
                couleur=str(p["coul_qual"]).lower(), sous_indices=sous, responsables=responsables)
        return DonneesQualiteAir(
            zone=brut[0].get("lib_zone") or "Rennes Métropole",
            mis_a_jour=max(_horodatage(p["date_maj"]) for p in brut),
            jours=[jours[d] for d in sorted(jours) if d >= _aujourdhui(maintenant)] or [jours[max(jours)]],
        )

    def valide_jusqu_a(self, donnees: DonneesQualiteAir, maintenant: datetime) -> datetime:
        # Une collecte par jour, plus un rattrapage : au-delà de 26 h, l'indice du jour peut manquer.
        return maintenant + timedelta(hours=26)


# --- Pollens ---------------------------------------------------------------------------------

LIBELLES_POLLEN = {0: "Indisponible", 1: "Très faible", 2: "Faible", 3: "Modéré", 4: "Élevé", 5: "Très élevé",
                   6: "Extrêmement élevé"}
# Champ de l'API -> (nom affiché, nom latin tel que l'écrit « pollen_resp »).
ESPECES = {"aul": ("Aulne", "ALNUS"), "boul": ("Bouleau", "BETULA"), "oliv": ("Olivier", "OLEA"),
           "gram": ("Graminées", "GRAMINEE"), "arm": ("Armoise", "ARTEMISI"), "ambr": ("Ambroisie", "AMBROSIA")}


class IndiceEspece(Modele):
    espece: str  # aul, boul…
    nom: str
    code: int


class JourPollen(Modele):
    date: date
    code: int  # 0 indisponible, 1 très faible … 6 extrêmement élevé
    libelle: str
    alerte: bool  # alerte pollinique d'Air Breizh
    especes: list[IndiceEspece]
    # Espèces qui font l'indice, d'après Air Breizh ; vides tant que l'indice reste très faible.
    responsables: list[str]


class DonneesPollens(Modele):
    commune: str
    mis_a_jour: datetime
    jours: list[JourPollen]


class CollecteurPollens(Collecteur[DonneesPollens]):
    module = "pollens"
    source = SOURCE
    licence = LICENCE
    url_source = URL_AIR_BREIZH
    fraicheur = timedelta(hours=24)
    modele_donnees = DonneesPollens

    def telecharger(self, client: httpx.Client) -> Any:
        auj = datetime.now(PARIS).date()
        return _geojson(client, "/api/v2/data/indices/pollens", {
            "format": "geojson", "code_zone": CODE_COMMUNE,
            "date": (auj + timedelta(days=2)).isoformat(), "date_historique": auj.isoformat()})

    def valider(self, brut: Any) -> None:
        if not brut:
            raise ErreurValidation("aucun indice pollen pour Cesson-Sévigné")
        for p in brut:
            if p.get("code_zone") != CODE_COMMUNE:
                raise ErreurValidation(f"indice d'une autre commune : {p.get('code_zone')!r}")
            if p.get("code_qual") not in LIBELLES_POLLEN:
                raise ErreurValidation(f"indice pollen inattendu : {p.get('code_qual')!r}")
            if not isinstance(p.get("alerte"), bool):
                raise ErreurValidation(f"alerte pollen inattendue : {p.get('alerte')!r}")
            for cle in ESPECES:
                v = p.get(f"code_{cle}")
                if not isinstance(v, (int, float)) or int(v) != v or int(v) not in LIBELLES_POLLEN:
                    raise ErreurValidation(f"indice {cle} inattendu : {v!r}")

    def convertir(self, brut: Any, maintenant: datetime) -> DonneesPollens:
        jours = {}
        for p in brut:
            code = int(p["code_qual"])
            latins = str(p.get("pollen_resp") or "").split()
            jours[_jour(p["date_ech"])] = JourPollen(
                date=_jour(p["date_ech"]), code=code, libelle=LIBELLES_POLLEN[code], alerte=p["alerte"],
                especes=[IndiceEspece(espece=c, nom=n, code=int(p[f"code_{c}"])) for c, (n, _) in ESPECES.items()],
                # Au niveau « très faible », l'API cite toutes les espèces : rien à mettre en avant.
                responsables=[c for c, (_, latin) in ESPECES.items() if latin in latins] if code >= 2 else [],
            )
        return DonneesPollens(
            commune=brut[0].get("lib_zone") or "Cesson-Sévigné",
            mis_a_jour=max(_horodatage(p["date_maj"]) for p in brut),
            jours=[jours[d] for d in sorted(jours) if d >= _aujourdhui(maintenant)] or [jours[max(jours)]],
        )

    def valide_jusqu_a(self, donnees: DonneesPollens, maintenant: datetime) -> datetime:
        return maintenant + timedelta(hours=26)


# --- Épisodes de pollution ---------------------------------------------------------------------

class EtatEpisode(StrEnum):
    AUCUN = "aucun"  # PAS DE DEPASSEMENT
    INFORMATION = "information"  # INFORMATION ET RECOMMANDATION
    ALERTE_PERSISTANCE = "alerte_persistance"  # ALERTE SUR PERSISTANCE
    ALERTE = "alerte"  # ALERTE


PAR_ETAT = {"PAS DE DEPASSEMENT": EtatEpisode.AUCUN, "INFORMATION ET RECOMMANDATION": EtatEpisode.INFORMATION,
            "ALERTE SUR PERSISTANCE": EtatEpisode.ALERTE_PERSISTANCE, "ALERTE": EtatEpisode.ALERTE}
GRAVITE = list(EtatEpisode)
NIVEAU_ALERTE = {EtatEpisode.INFORMATION: NiveauAlerte.JAUNE, EtatEpisode.ALERTE_PERSISTANCE: NiveauAlerte.ORANGE,
                 EtatEpisode.ALERTE: NiveauAlerte.ORANGE}
LIBELLE_PROCEDURE = {EtatEpisode.INFORMATION: "procédure d'information et de recommandation",
                     EtatEpisode.ALERTE_PERSISTANCE: "procédure d'alerte sur persistance",
                     EtatEpisode.ALERTE: "procédure d'alerte"}
# Noms courts des polluants (code_pol de l'API).
POLLUANTS = {"8": "NO₂", "7": "O₃", "5": "PM10", "6001": "PM2,5", "1": "SO₂"}
COLONNES = {"aasqa", "date_maj", "etat", "lib_zone", "lib_pol", "date_ech", "date_dif", "code_zone", "code_pol"}


class EpisodePolluant(Modele):
    polluant: str  # NO₂, O₃, PM10, PM2,5, SO₂
    nom: str  # « Dioxyde d'azote »…
    etat: EtatEpisode


class JourEpisodes(Modele):
    date: date
    etat: EtatEpisode  # le plus grave des polluants
    polluants: list[EpisodePolluant]


class DonneesEpisodes(Modele):
    zone: str
    mis_a_jour: datetime
    jours: list[JourEpisodes]  # d'hier à demain
    # Dès qu'une procédure vise aujourd'hui ou demain, au format commun des alertes.
    alerte: Alerte | None = None


class CollecteurEpisodes(Collecteur[DonneesEpisodes]):
    module = "episodes_pollution"
    source = SOURCE
    licence = LICENCE
    url_source = URL_AIR_BREIZH
    fraicheur = timedelta(hours=12)
    modele_donnees = DonneesEpisodes

    def telecharger(self, client: httpx.Client) -> Any:
        r = obtenir(client, BASE + "/api/v2/data/episodes/3jours",
                    params={"format": "csv", "code_zone": CODE_DEPARTEMENT}, headers=connexion(client))
        texte = r.content.decode("utf-8-sig")
        lecteur = csv.DictReader(io.StringIO(texte))
        if not COLONNES <= set(lecteur.fieldnames or []):
            raise ErreurValidation(f"colonnes inattendues : {lecteur.fieldnames!r}")
        return list(lecteur)

    def valider(self, brut: Any) -> None:
        if not brut:
            raise ErreurValidation("aucune ligne d'épisode pour l'Ille-et-Vilaine")
        for l in brut:
            if l.get("code_zone") != CODE_DEPARTEMENT:
                raise ErreurValidation(f"épisode d'une autre zone : {l.get('code_zone')!r}")
            if l.get("etat") not in PAR_ETAT:
                raise ErreurValidation(f"état d'épisode inattendu : {l.get('etat')!r}")
            if not l.get("lib_pol"):
                raise ErreurValidation("polluant sans nom")

    def convertir(self, brut: Any, maintenant: datetime) -> DonneesEpisodes:
        par_jour: dict[date, list[EpisodePolluant]] = {}
        for l in brut:
            par_jour.setdefault(_jour(l["date_ech"]), []).append(EpisodePolluant(
                polluant=POLLUANTS.get(l["code_pol"], l["lib_pol"]), nom=l["lib_pol"], etat=PAR_ETAT[l["etat"]]))
        jours = [JourEpisodes(date=d, polluants=p, etat=max((x.etat for x in p), key=GRAVITE.index))
                 for d, p in sorted(par_jour.items())]
        zone = "Ille-et-Vilaine"
        auj = _aujourdhui(maintenant)
        visees = [j for j in jours if j.date >= auj and j.etat != EtatEpisode.AUCUN]
        alerte = None
        if visees:
            pire = max(visees, key=lambda j: GRAVITE.index(j.etat))
            polluants = ", ".join(p.polluant for p in pire.polluants if p.etat == pire.etat)
            quand = "aujourd'hui" if pire.date == auj else "demain"
            debut = datetime.combine(pire.date, datetime.min.time(), PARIS)
            alerte = Alerte(
                id=f"episodes:{CODE_DEPARTEMENT}:{pire.date}:{pire.etat}",
                niveau=NIVEAU_ALERTE[pire.etat],
                titre=f"Pollution {polluants} {quand} : {LIBELLE_PROCEDURE[pire.etat]}",
                zone=zone, debut=debut, fin=debut + timedelta(days=1), source="Air Breizh",
            )
        return DonneesEpisodes(zone=zone, mis_a_jour=max(_horodatage(l["date_maj"]) for l in brut),
                               jours=jours, alerte=alerte)

    def valide_jusqu_a(self, donnees: DonneesEpisodes, maintenant: datetime) -> datetime:
        # Sur une procédure préfectorale, on ne laisse pas croire qu'un état d'hier vaut encore.
        return maintenant + self.fraicheur

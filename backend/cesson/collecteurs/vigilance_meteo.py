"""Vigilance météorologique en Ille-et-Vilaine (Météo-France, API « Bulletin Vigilance »).

Deux fichiers de la diffusion en cours :
- `cartevigilance` : pour chaque département et chaque phénomène, la couleur du jour (J) et du lendemain (J1),
  avec la chronologie heure par heure (« timelaps ») ;
- `textesvigilance` : les bulletins de suivi, dont celui du département quand il en a un (aggravation possible,
  vigilance orange ou rouge : situation, conseils).

On ne garde que le département 35. Sa zone côtière (« 3510 », vagues-submersion) ne concerne pas Cesson.

L'API demande un compte (gratuit). Le serveur échange l'identifiant de l'application (fichier
/srv/cesson/secrets/meteofrance.env, ligne « identifiants_application=… ») contre un jeton d'une heure, à chaque
collecte. Jamais dans le dépôt, jamais dans l'app.

Cadence : toutes les 15 min. Météo-France publie au moins deux fois par jour (6h et 16h), et à chaque changement.
"""

from __future__ import annotations

from datetime import datetime, timedelta
from enum import StrEnum
from typing import Any
from zoneinfo import ZoneInfo

import httpx

from ..commun.collecteur import Collecteur, ErreurSource, ErreurValidation, obtenir
from ..commun.modele import Alerte, Modele, NiveauAlerte
from ..commun.secrets import lire_secrets

URL_JETON = "https://portail-api.meteofrance.fr/token"
BASE = "https://public-api.meteofrance.fr/public/DPVigilance/v1"
DEPARTEMENT = "35"
NOM_DEPARTEMENT = "Ille-et-Vilaine"
URL_VIGILANCE = "https://vigilance.meteofrance.fr/fr/ille-et-vilaine"
PARIS = ZoneInfo("Europe/Paris")


class Couleur(StrEnum):
    VERT = "vert"
    JAUNE = "jaune"
    ORANGE = "orange"
    ROUGE = "rouge"


PAR_CODE = {1: Couleur.VERT, 2: Couleur.JAUNE, 3: Couleur.ORANGE, 4: Couleur.ROUGE}
ORDRE = list(PAR_CODE.values())
NIVEAU_ALERTE = {Couleur.JAUNE: NiveauAlerte.JAUNE, Couleur.ORANGE: NiveauAlerte.ORANGE, Couleur.ROUGE: NiveauAlerte.ROUGE}

# Phénomènes de la vigilance V6 (identifiants de Météo-France).
PHENOMENES = {"1": "vent violent", "2": "pluie-inondation", "3": "orages", "4": "crues", "5": "neige-verglas",
              "6": "canicule", "7": "grand froid", "8": "avalanches", "9": "vagues-submersion"}
# Les crues de la Vilaine ont leur propre source (Vigicrues) et leur bandeau : la vigilance « crues » de
# Météo-France reste affichée dans la carte, mais ne déclenche ni l'alerte ni le bandeau de ce module.
SANS_ALERTE = {"4"}


class Plage(Modele):
    debut: datetime
    fin: datetime
    couleur: Couleur


class Phenomene(Modele):
    id: str
    nom: str
    couleur: Couleur  # la plus forte de la journée
    plages: list[Plage]  # seulement celles en jaune ou plus


class JourVigilance(Modele):
    echeance: str  # J ou J1
    debut: datetime
    fin: datetime
    couleur: Couleur
    phenomenes: list[Phenomene]  # jaune ou plus, du plus fort au plus faible


class TexteVigilance(Modele):
    titre: str  # « Aggravation possible du niveau de Vigilance », « Situation »…
    alea: str | None = None  # « tous aléas », « vent violent »…
    texte: str


class DonneesVigilanceMeteo(Modele):
    departement: str
    mis_a_jour: datetime
    jours: list[JourVigilance]
    textes: list[TexteVigilance] = []
    # Dès le jaune (hors crues), au format commun des alertes.
    alerte: Alerte | None = None


def _horodatage(valeur: Any) -> datetime:
    d = datetime.fromisoformat(str(valeur).replace("Z", "+00:00"))
    if d.tzinfo is None:
        raise ValueError(f"horodatage sans fuseau : {valeur!r}")
    return d


def connexion(client: httpx.Client) -> dict[str, str]:
    """En-têtes d'autorisation. Le message d'erreur ne reprend jamais l'identifiant de l'application."""
    ident = lire_secrets("Météo-France", "CESSON_METEOFRANCE_IDENTIFIANTS", "meteofrance.env",
                         ("identifiants_application",))["identifiants_application"]
    try:
        r = client.post(URL_JETON, data={"grant_type": "client_credentials"}, headers={"Authorization": f"Basic {ident}"})
    except httpx.TransportError as e:
        raise ErreurSource(f"jeton Météo-France impossible à obtenir : {e}") from e
    if r.status_code in (400, 401):
        raise ErreurSource(f"jeton Météo-France refusé (HTTP {r.status_code}) : identifiant d'application invalide")
    if r.status_code != 200 or "json" not in r.headers.get("content-type", ""):
        raise ErreurSource(f"jeton Météo-France : HTTP {r.status_code}, {r.headers.get('content-type')}")
    try:
        return {"Authorization": f"Bearer {r.json()['access_token']}"}
    except (ValueError, KeyError, TypeError) as e:
        raise ErreurValidation("jeton Météo-France absent de la réponse") from e


def _domaine(periode: dict) -> dict | None:
    return next((d for d in periode.get("timelaps", {}).get("domain_ids", []) if d.get("domain_id") == DEPARTEMENT), None)


def _textes(textes: dict) -> list[TexteVigilance]:
    resultat = []
    for bloc in textes.get("product", {}).get("text_bloc_items", []):
        if bloc.get("domain_id") != DEPARTEMENT:
            continue
        for item in bloc.get("bloc_items", []):
            for t in item.get("text_items", []):
                lignes = [l for term in t.get("term_items", []) for sub in term.get("subdivision_text", [])
                          for l in [sub.get("bold_text"), *sub.get("text", [])] if l]
                if lignes:
                    resultat.append(TexteVigilance(titre=item.get("type_name") or "Bulletin", alea=t.get("hazard_name"),
                                                   texte="\n".join(lignes)))
    return resultat


class CollecteurVigilanceMeteo(Collecteur[DonneesVigilanceMeteo]):
    module = "vigilance_meteo"
    source = "Météo-France"
    licence = "Licence Ouverte 2.0"
    url_source = URL_VIGILANCE
    fraicheur = timedelta(minutes=15)
    modele_donnees = DonneesVigilanceMeteo

    def telecharger(self, client: httpx.Client) -> Any:
        entetes = connexion(client)
        brut = {}
        for nom in ("cartevigilance", "textesvigilance"):
            r = obtenir(client, f"{BASE}/{nom}/encours", headers=entetes)
            try:
                brut[nom] = r.json()
            except ValueError as e:
                raise ErreurValidation(f"{nom} : réponse non JSON : {e}") from e
        return brut

    def valider(self, brut: Any) -> None:
        produit = brut.get("cartevigilance", {}).get("product") if isinstance(brut.get("cartevigilance"), dict) else None
        if not isinstance(produit, dict) or not isinstance(produit.get("periods"), list):
            raise ErreurValidation("carte de vigilance : « product.periods » absent")
        if sorted(p.get("echeance") for p in produit["periods"]) != ["J", "J1"]:
            raise ErreurValidation(f"échéances inattendues : {[p.get('echeance') for p in produit['periods']]}")
        for p in produit["periods"]:
            d = _domaine(p)
            if d is None:
                raise ErreurValidation(f"Ille-et-Vilaine absente de l'échéance {p['echeance']}")
            for ph in d.get("phenomenon_items", []):
                if ph.get("phenomenon_id") not in PHENOMENES:
                    raise ErreurValidation(f"phénomène inconnu : {ph.get('phenomenon_id')!r}")
                for t in [ph, *ph.get("timelaps_items", [])]:
                    code = t.get("phenomenon_max_color_id", t.get("color_id"))
                    if code not in PAR_CODE:
                        raise ErreurValidation(f"couleur de vigilance inattendue : {code!r}")
        if not isinstance(brut.get("textesvigilance"), dict):
            raise ErreurValidation("textes de vigilance absents")

    def convertir(self, brut: Any, maintenant: datetime) -> DonneesVigilanceMeteo:
        produit = brut["cartevigilance"]["product"]
        jours = []
        for p in sorted(produit["periods"], key=lambda p: p["echeance"]):
            d = _domaine(p)
            phenomenes = []
            for ph in d.get("phenomenon_items", []):
                couleur = PAR_CODE[ph["phenomenon_max_color_id"]]
                if couleur == Couleur.VERT:
                    continue
                plages = [Plage(debut=_horodatage(t["begin_time"]), fin=_horodatage(t["end_time"]), couleur=PAR_CODE[t["color_id"]])
                          for t in ph.get("timelaps_items", []) if t["color_id"] >= 2]
                phenomenes.append(Phenomene(id=ph["phenomenon_id"], nom=PHENOMENES[ph["phenomenon_id"]],
                                            couleur=couleur, plages=sorted(plages, key=lambda x: x.debut)))
            phenomenes.sort(key=lambda x: (-ORDRE.index(x.couleur), int(x.id)))
            jours.append(JourVigilance(
                echeance=p["echeance"], debut=_horodatage(p["begin_validity_time"]), fin=_horodatage(p["end_validity_time"]),
                couleur=max((x.couleur for x in phenomenes), key=ORDRE.index, default=Couleur.VERT), phenomenes=phenomenes))
        return DonneesVigilanceMeteo(
            departement=NOM_DEPARTEMENT, mis_a_jour=_horodatage(produit["update_time"]), jours=jours,
            textes=_textes(brut["textesvigilance"]), alerte=self._alerte(jours, maintenant))

    @staticmethod
    def _alerte(jours: list[JourVigilance], maintenant: datetime) -> Alerte | None:
        """La vigilance la plus forte d'aujourd'hui ou de demain, hors crues (Vigicrues s'en charge)."""
        candidats = [(j, ph) for j in jours for ph in j.phenomenes if ph.id not in SANS_ALERTE and j.fin > maintenant]
        if not candidats:
            return None
        couleur = max((ph.couleur for _, ph in candidats), key=ORDRE.index)
        retenus = [(j, ph) for j, ph in candidats if ph.couleur == couleur]
        noms = list(dict.fromkeys(ph.nom for _, ph in retenus))
        plages = [pl for _, ph in retenus for pl in ph.plages if pl.couleur == couleur]
        debut, fin = min(pl.debut for pl in plages), max(pl.fin for pl in plages)
        return Alerte(
            id=f"vigilance:{DEPARTEMENT}:{debut.astimezone(PARIS).date()}:{couleur}:{'-'.join(sorted({ph.id for _, ph in retenus}))}",
            niveau=NIVEAU_ALERTE[couleur],
            titre=f"Vigilance {couleur} {', '.join(noms)}",
            zone=NOM_DEPARTEMENT, debut=debut, fin=fin, source="Météo-France",
        )

    def valide_jusqu_a(self, donnees: DonneesVigilanceMeteo, maintenant: datetime) -> datetime:
        # Comme la vigilance crues : trois collectes manquées et la carte se dit ancienne.
        return maintenant + 3 * self.fraicheur

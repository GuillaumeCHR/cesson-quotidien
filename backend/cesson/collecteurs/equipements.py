"""Horaires des équipements municipaux (piscine, médiathèque, golf, Base Sports Nature).

Source : `saisie/equipements.yaml`, recopié du site de la ville et versionné (l'historique Git
garde chaque changement d'horaire). Aucun appel réseau.

Le backend fait le calcul : il publie, pour chaque espace, les plages d'ouverture des 14 prochains
jours, vacances et fermetures déjà appliquées. L'app n'a plus qu'à comparer à l'heure qu'il est.
"""

from __future__ import annotations

from datetime import date, datetime, timedelta
from pathlib import Path
from typing import Any

import yaml

from ..commun.collecteur import Collecteur, ErreurValidation
from ..commun.feries import ferie
from ..commun.horaires import PARIS, HoraireInvalide, jours, lire, plages_du_jour
from ..commun.modele import ExceptionHoraire, Horaire, Lieu, Modele

SAISIE = Path(__file__).resolve().parents[2] / "saisie" / "equipements.yaml"
# Équipements municipaux fermés les jours fériés, sauf quand la source dit autre chose.
FERIES_FERMES, FERIES_COMME_DIMANCHE, FERIES_NORMAUX = "fermes", "comme_le_dimanche", "normaux"
# La source ne dit rien et l'équipement ouvre parfois (piscine l'été) : jour publié « à vérifier ».
FERIES_A_VERIFIER = "a_verifier"
MODES_FERIES = (FERIES_FERMES, FERIES_COMME_DIMANCHE, FERIES_NORMAUX, FERIES_A_VERIFIER)
NB_JOURS = 14


class Plage(Modele):
    debut: datetime
    fin: datetime


class JourHoraire(Modele):
    date: date
    connu: bool  # faux : aucun horaire publié pour ce jour (ce n'est pas « fermé »)
    plages: list[Plage] = []
    motif: str | None = None  # fermeture exceptionnelle, jour férié…
    periode: str | None = None


class Espace(Modele):
    id: str
    nom: str
    jours: list[JourHoraire]
    remarque: str | None = None  # remarque de la période en cours


class Equipement(Modele):
    lieu: Lieu
    telephone: str | None = None
    verifie_le: date
    remarque: str | None = None
    espaces: list[Espace]
    horaires: list[Horaire]  # saisie brute, au format du modèle commun


class DonneesEquipements(Modele):
    equipements: list[Equipement]


def _date(v: Any, champ: str) -> date | None:
    if v is None:
        return None
    if isinstance(v, date):
        return v
    raise ErreurValidation(f"{champ} : date attendue (AAAA-MM-JJ), reçu {v!r}")


def _periode_du_jour(periodes: list[dict], jour: date) -> dict | None:
    """La période la plus courte qui couvre le jour (les vacances l'emportent sur l'année)."""
    candidates = [p for p in periodes if (p["du"] or date.min) <= jour <= (p["au"] or date.max)]
    return min(candidates, key=lambda p: (p["au"] or date.max) - (p["du"] or date.min), default=None)


class CollecteurEquipements(Collecteur[DonneesEquipements]):
    module = "equipements"
    source = "Ville de Cesson-Sévigné (site internet), saisie manuelle"
    licence = "Informations publiques de la Ville de Cesson-Sévigné"
    url_source = "https://www.ville-cesson-sevigne.fr/"
    fraicheur = timedelta(hours=24)
    modele_donnees = DonneesEquipements

    def __init__(self, chemin: Path = SAISIE) -> None:
        self.chemin = chemin

    def telecharger(self, client: Any) -> Any:
        try:
            return yaml.safe_load(self.chemin.read_text(encoding="utf-8"))
        except (OSError, yaml.YAMLError) as e:
            raise ErreurValidation(f"{self.chemin.name} illisible : {e}") from e

    def valider(self, brut: Any) -> None:
        if not isinstance(brut, dict) or not isinstance(brut.get("equipements"), dict) or not brut["equipements"]:
            raise ErreurValidation("clé « equipements » absente ou vide")
        for eid, e in brut["equipements"].items():
            if not e.get("nom") or not isinstance(e.get("espaces"), dict) or not e["espaces"]:
                raise ErreurValidation(f"{eid} : nom ou espaces manquants")
            _date(e.get("verifie_le"), f"{eid}.verifie_le") or self._manque(f"{eid}.verifie_le")
            if e.get("jours_feries", FERIES_FERMES) not in MODES_FERIES:
                raise ErreurValidation(f"{eid}.jours_feries : {e['jours_feries']!r} inconnu ({', '.join(MODES_FERIES)})")
            for x in e.get("exceptions") or []:
                du, au = _date(x.get("du"), f"{eid} exception"), _date(x.get("au"), f"{eid} exception")
                if not du or not au or au < du or not x.get("motif"):
                    raise ErreurValidation(f"{eid} : exception incomplète {x!r}")
                if x.get("horaires"):
                    self._lire(x["horaires"], f"{eid} exception {du}")
            for sid, s in e["espaces"].items():
                if not s.get("nom") or not s.get("periodes"):
                    raise ErreurValidation(f"{eid}.{sid} : nom ou périodes manquants")
                for p in s["periodes"]:
                    du, au = _date(p.get("du"), f"{eid}.{sid}"), _date(p.get("au"), f"{eid}.{sid}")
                    if du and au and au < du:
                        raise ErreurValidation(f"{eid}.{sid} : période {p.get('nom')} à l'envers")
                    self._lire(p.get("horaires") or "", f"{eid}.{sid} ({p.get('nom')})")

    @staticmethod
    def _manque(champ: str) -> None:
        raise ErreurValidation(f"{champ} manquant")

    @staticmethod
    def _lire(texte: str, ou: str):
        try:
            return lire(" ".join(texte.split()))
        except HoraireInvalide as e:
            raise ErreurValidation(f"{ou} : {e}") from e

    def convertir(self, brut: Any, maintenant: datetime) -> DonneesEquipements:
        aujourdhui = maintenant.astimezone(PARIS).date()
        equipements = []
        for eid, e in brut["equipements"].items():
            exceptions = [ExceptionHoraire(du=x["du"], au=x["au"], motif=x["motif"],
                                           opening_hours=" ".join(x["horaires"].split()) if x.get("horaires") else None)
                          for x in e.get("exceptions") or []]
            feries_mode = e.get("jours_feries", FERIES_FERMES)
            if feries_mode == FERIES_FERMES:
                # Fériés calculés, ajoutés après les exceptions saisies : celles-ci restent prioritaires.
                exceptions += [ExceptionHoraire(du=j, au=j, motif=f"Jour férié ({ferie(j)})")
                               for j in jours(aujourdhui, NB_JOURS) if ferie(j)]
            espaces, horaires = [], []
            for sid, s in e["espaces"].items():
                periodes = [{**p, "du": p.get("du"), "au": p.get("au"), "semaine": self._lire(p["horaires"], sid)}
                            for p in s["periodes"]]
                for p in periodes:
                    horaires.append(Horaire(lieu_id=f"equipement:{eid}", sous_espace=sid, periode=p.get("nom"),
                                            opening_hours=" ".join(p["horaires"].split()), exceptions=exceptions))
                jours_publies = []
                for jour in jours(aujourdhui, NB_JOURS):
                    exc = next((x for x in exceptions if x.du <= jour <= x.au), None)
                    p = _periode_du_jour(periodes, jour)
                    if exc is not None:
                        semaine = lire(exc.opening_hours) if exc.opening_hours else None
                        plages = plages_du_jour(semaine, jour) if semaine else []
                        jours_publies.append(JourHoraire(date=jour, connu=True, motif=exc.motif,
                                                         plages=[Plage(debut=a, fin=b) for a, b in plages]))
                    elif p is None:
                        jours_publies.append(JourHoraire(date=jour, connu=False))
                    elif feries_mode == FERIES_A_VERIFIER and ferie(jour):
                        jours_publies.append(JourHoraire(date=jour, connu=False, periode=p.get("nom"),
                                                         motif=f"Jour férié ({ferie(jour)})"))
                    elif feries_mode == FERIES_COMME_DIMANCHE and ferie(jour):
                        dimanche = [(datetime.combine(jour, a, PARIS), datetime.combine(jour, b, PARIS))
                                    for a, b in p["semaine"][6]]
                        jours_publies.append(JourHoraire(
                            date=jour, connu=True, periode=p.get("nom"),
                            motif=f"Jour férié ({ferie(jour)}) : horaires du dimanche",
                            plages=[Plage(debut=a, fin=b) for a, b in dimanche]))
                    else:
                        jours_publies.append(JourHoraire(
                            date=jour, connu=True, periode=p.get("nom"),
                            plages=[Plage(debut=a, fin=b) for a, b in plages_du_jour(p["semaine"], jour)]))
                en_cours = _periode_du_jour(periodes, aujourdhui)
                espaces.append(Espace(id=sid, nom=s["nom"], jours=jours_publies,
                                      remarque=en_cours.get("remarque") if en_cours else None))
            equipements.append(Equipement(
                lieu=Lieu(id=f"equipement:{eid}", nom=e["nom"], type="equipement", adresse=e.get("adresse"),
                          source="Ville de Cesson-Sévigné", url=e.get("url")),
                telephone=e.get("telephone"), verifie_le=e["verifie_le"], remarque=e.get("remarque"),
                espaces=espaces, horaires=horaires,
            ))
        return DonneesEquipements(equipements=equipements)

    def valide_jusqu_a(self, donnees: DonneesEquipements, maintenant: datetime) -> datetime:
        # Les plages couvrent 14 jours ; au-delà de 36 h sans recalcul, « aujourd'hui » glisse.
        return maintenant + timedelta(hours=36)

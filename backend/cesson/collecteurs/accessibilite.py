"""Accessibilité des lieux recevant du public à Cesson-Sévigné (acceslibre, ministère de la Transition écologique).

L'API acceslibre demande une clé (« Authorization: Api-Key … »), lue dans le fichier désigné par
CESSON_ACCESLIBRE_IDENTIFIANTS (par défaut /srv/cesson/secrets/acceslibre.env, ligne « cle_api=… »).
Les téléphones n'appellent jamais l'API : ils lisent le JSON publié ici.

Chaque fiche compte une centaine de champs, presque tous vides. On n'en garde que ce qui aide à préparer un
déplacement (stationnement, chemin depuis la rue, entrée, accueil, sanitaires) et on laisse à None ce qui n'est
pas renseigné : « non renseigné » n'est pas « non ». Aucune note globale n'est calculée, l'app décrit les faits
et renvoie à la fiche complète. Données contributives qui bougent peu : collecte hebdomadaire.
"""

from __future__ import annotations

from datetime import date, datetime, timedelta
from typing import Any, Literal

import httpx

from ..commun.collecteur import Collecteur, ErreurValidation, obtenir
from ..commun.modele import Coord, Lieu, Modele
from ..commun.secrets import lire_secrets

API = "https://acceslibre.beta.gouv.fr/api/erps/"
COMMUNE = "Cesson-Sévigné"

Famille = Literal["services_publics", "sante", "loisirs", "restauration", "commerces"]

# Familles d'activités (slugs acceslibre) ; tout le reste va dans « commerces » (commerces et services).
FAMILLES: dict[str, Famille] = {
    **dict.fromkeys((
        "mairie", "administration-publique", "bureau-de-poste", "commissariat-de-police", "gendarmerie",
        "office-du-tourisme", "emploi-formation", "toilettes-publiques", "cimetiere", "salle-des-fetes",
        "creche", "ecole", "ecole-primaire", "ecole-maternelle", "college", "lycee", "centre-de-loisirs",
        "association", "caf", "cpam", "centre-des-finances-publiques",
    ), "services_publics"),
    **dict.fromkeys((
        "pharmacie", "medecin-generaliste", "cabinet-medical", "centre-medical", "chirurgien-dentiste",
        "infirmier", "laboratoire-danalyse-medicale", "hopital", "clinique", "kinesitherapeute",
        "osteopathie", "psychologie-psychiatrie", "sophrologue", "herboristerie-naturopathie", "ehpad",
        "optique", "audioprothesiste", "orthophoniste", "pedicure-podologue", "sage-femme", "veterinaire",
    ), "sante"),
    **dict.fromkeys((
        "bibliotheque-mediatheque", "cinema", "theatre", "musee", "art", "piscine", "stade", "club-de-sport",
        "sports-et-loisirs", "salles-de-remise-en-forme", "golf", "salle-de-spectacle", "gymnase",
    ), "loisirs"),
    **dict.fromkeys(("restaurant", "restauration-rapide", "cafeteria", "hotel", "boulangerie-patisserie"),
                    "restauration"),
}
ORDRE_FAMILLES = ("services_publics", "sante", "loisirs", "restauration", "commerces")


class Accessibilite(Modele):
    # Stationnement : place réservée dans l'établissement, ou à moins de 200 m.
    stationnement_pmr: bool | None = None
    stationnement_pmr_proche: bool | None = None
    arret_transport: bool | None = None  # arrêt de transport en commun à moins de 200 m
    # Chemin entre la rue (ou le parking) et l'entrée, quand il existe.
    chemin_plain_pied: bool | None = None
    # Entrée
    entree_plain_pied: bool | None = None
    entree_marches: int | None = None
    entree_rampe: Literal["fixe", "amovible"] | None = None
    entree_ascenseur: bool | None = None
    entree_largeur_cm: int | None = None
    entree_appel: bool | None = None  # sonnette, interphone ou aide humaine à l'entrée
    entree_specifique: bool | None = None  # entrée dédiée aux personnes à mobilité réduite
    # Accueil
    accueil_plain_pied: bool | None = None  # de l'entrée à l'accueil
    personnel_forme: bool | None = None  # personnel formé à l'accueil des personnes handicapées
    malentendants: list[str] = []  # « boucle à induction magnétique », « langue des signes »…
    # Sanitaires : None si non renseigné ; False si absents.
    sanitaires: bool | None = None
    sanitaires_adaptes: bool | None = None


class Etablissement(Modele):
    lieu: Lieu  # lieu.url : la fiche complète sur acceslibre, où l'on peut aussi la compléter
    activite: str | None = None
    famille: Famille
    acces: Accessibilite
    commentaire: str | None = None
    mis_a_jour: date


class DonneesAccessibilite(Modele):
    etablissements: list[Etablissement]


# Codes acceslibre des équipements pour personnes sourdes ou malentendantes.
MALENTENDANTS = {
    "bim": "boucle à induction magnétique", "bim_portative": "boucle magnétique portative",
    "lsf": "langue des signes", "lpc": "langue française parlée complétée", "sts": "sous-titrage ou transcription",
    "autres": "autre équipement",
}


def _section(a: dict, nom: str) -> dict:
    s = a.get(nom)
    return s if isinstance(s, dict) else {}


def _bool(v: Any) -> bool | None:
    return v if isinstance(v, bool) else None


def _entier(v: Any) -> int | None:
    return v if isinstance(v, int) and not isinstance(v, bool) and v >= 0 else None


def accessibilite(a: dict) -> Accessibilite:
    t, c, e, ac, s = (_section(a, n) for n in ("transport", "cheminement_ext", "entree", "accueil", "sanitaires"))
    plain_pied = _bool(e.get("entree_plain_pied"))
    rampe = e.get("entree_marches_rampe")
    appel = [_bool(e.get("entree_dispositif_appel")), _bool(e.get("entree_aide_humaine"))]
    sanitaires = _bool(s.get("sanitaires_presence"))
    personnels = ac.get("accueil_personnels")
    return Accessibilite(
        stationnement_pmr=_bool(t.get("stationnement_pmr")) if t.get("stationnement_presence") is not False else False,
        stationnement_pmr_proche=_bool(t.get("stationnement_ext_pmr")),
        arret_transport=_bool(t.get("transport_station_presence")),
        chemin_plain_pied=_bool(c.get("cheminement_ext_plain_pied")) if c.get("cheminement_ext_presence") else None,
        entree_plain_pied=plain_pied,
        # Les marches, la rampe et l'ascenseur ne sont décrits que si l'entrée n'est pas de plain-pied.
        entree_marches=_entier(e.get("entree_marches")) if plain_pied is False else None,
        entree_rampe=rampe if plain_pied is False and rampe in ("fixe", "amovible") else None,
        entree_ascenseur=_bool(e.get("entree_ascenseur")) if plain_pied is False else None,
        entree_largeur_cm=_entier(e.get("entree_largeur_mini")) or None,
        entree_appel=True if True in appel else (False if False in appel else None),
        entree_specifique=True if e.get("entree_pmr") is True else None,
        accueil_plain_pied=_bool(ac.get("accueil_cheminement_plain_pied")),
        personnel_forme={"formés": True, "non-formés": False, "aucun": False}.get(personnels),
        malentendants=[MALENTENDANTS.get(x, x) for x in (ac.get("accueil_equipements_malentendants") or [])
                       if ac.get("accueil_equipements_malentendants_presence") is not False],
        sanitaires=sanitaires,
        sanitaires_adaptes=_bool(s.get("sanitaires_adaptes")) if sanitaires else None,
    )


def adresse_courte(adresse: str | None) -> str | None:
    """« 1 Place de l'Eglise 35510 Cesson-Sévigné » → « 1 Place de l'Eglise » (tout est à Cesson)."""
    a = (adresse or "").strip()
    for fin in (" 35510 Cesson-Sévigné", " 35510 Cesson-Sevigne", " 35510 Cesson Sévigné", " 35510"):
        if a.lower().endswith(fin.lower()):
            a = a[: -len(fin)].strip(" ,")
            break
    return a or None


def nom_lisible(nom: str) -> str:
    n = " ".join(nom.split())
    return n.title() if n.isupper() and len(n) > 4 else n


class CollecteurAccessibilite(Collecteur[DonneesAccessibilite]):
    module = "accessibilite"
    source = "acceslibre (ministère de la Transition écologique)"
    licence = "Licence Ouverte 2.0"
    url_source = "https://acceslibre.beta.gouv.fr/"
    fraicheur = timedelta(days=7)
    modele_donnees = DonneesAccessibilite

    def telecharger(self, client: httpx.Client) -> Any:
        cle = lire_secrets("acceslibre", "CESSON_ACCESLIBRE_IDENTIFIANTS", "acceslibre.env", ("cle_api",))["cle_api"]
        entetes = {"Authorization": f"Api-Key {cle}", "Accept": "application/json"}
        url, params, res = API, {"commune": COMMUNE, "page_size": 200}, []
        while url:
            r = obtenir(client, url, params=params, headers=entetes)
            try:
                page = r.json()
            except ValueError as e:
                raise ErreurValidation(f"réponse non JSON : {e}") from e
            if not isinstance(page, dict) or not isinstance(page.get("results"), list):
                raise ErreurValidation("champ « results » absent")
            res += page["results"]
            url, params = page.get("next"), None
            if len(res) > 5000:
                raise ErreurValidation("pagination sans fin")
        return res

    def valider(self, brut: Any) -> None:
        # 310 fiches en novembre 2026 : en dessous de 100, la réponse est tronquée ou le filtre a changé.
        if len(brut) < 100:
            raise ErreurValidation(f"seulement {len(brut)} établissement(s) à Cesson")
        for x in brut:
            if not x.get("uuid") or not x.get("nom") or not isinstance(x.get("accessibilite"), dict):
                raise ErreurValidation(f"fiche incomplète : {x.get('slug')}")

    def convertir(self, brut: Any, maintenant: datetime) -> DonneesAccessibilite:
        liste = []
        for x in brut:
            if x.get("published") is False or (x.get("commune") or COMMUNE) != COMMUNE:
                continue
            coords = (x.get("geom") or {}).get("coordinates") or []
            activite = x.get("activite") or {}
            commentaire = ((x["accessibilite"].get("commentaire") or {}).get("commentaire") or "").strip()
            liste.append(Etablissement(
                lieu=Lieu(id=f"acceslibre:{x['uuid']}", nom=nom_lisible(x["nom"]), type="etablissement",
                          coord=Coord(lon=coords[0], lat=coords[1]) if len(coords) == 2 else None,
                          adresse=adresse_courte(x.get("adresse")), source="acceslibre",
                          url=x.get("web_url")),
                activite=activite.get("nom"),
                famille=FAMILLES.get(activite.get("slug") or "", "commerces"),
                acces=accessibilite(x["accessibilite"]),
                commentaire=commentaire or None,
                mis_a_jour=datetime.fromisoformat(x["updated_at"]).date(),
            ))
        liste.sort(key=lambda e: (ORDRE_FAMILLES.index(e.famille), e.lieu.nom.casefold()))
        return DonneesAccessibilite(etablissements=liste)

    def valide_jusqu_a(self, donnees: DonneesAccessibilite, maintenant: datetime) -> datetime:
        return maintenant + timedelta(days=30)

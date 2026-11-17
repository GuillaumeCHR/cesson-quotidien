import json
from datetime import datetime, timezone
from pathlib import Path

import pytest

from cesson.collecteurs.decheterie import CollecteurDecheterie
from cesson.commun.collecteur import ErreurValidation

# Réponse réelle de data.rennesmetropole.fr, 17 nov. 2026 (code_insee=35051).
BRUT = json.loads((Path(__file__).parent / "fixtures" / "decheteries_rennes_metropole.json").read_text())
MAINTENANT = datetime(2026, 11, 17, 9, 0, tzinfo=timezone.utc)


def test_dechets_acceptes_et_refus():
    c = CollecteurDecheterie()
    c.valider(BRUT)
    d = c.convertir(BRUT, MAINTENANT)
    assert d.nom == "Déchetterie de Cesson-Sévigné"
    assert d.acceptes[0] == "Végétaux" and "Gravats" in d.acceptes and "Déchets dangereux ou toxiques" in d.acceptes
    assert "Verre" not in d.acceptes and "Tout-venant" not in d.acceptes
    assert d.refuses == ["Verre (colonnes à verre)", "Plastiques rigides"]
    assert d.reemploi and d.fermeture_exceptionnelle is None


def test_aucune_decheterie():
    with pytest.raises(ErreurValidation):
        CollecteurDecheterie().valider({"results": []})


def test_champ_disparu():
    brut = json.loads(json.dumps(BRUT))
    del brut["results"][0]["f_gravats"]
    with pytest.raises(ErreurValidation):
        CollecteurDecheterie().valider(brut)

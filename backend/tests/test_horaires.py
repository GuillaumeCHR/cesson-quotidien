from datetime import date, time

import pytest

from cesson.commun.horaires import HoraireInvalide, lire, plages_du_jour


def test_semaine_type():
    s = lire("Tu 14:30-19:00; We 10:00-12:30,14:30-19:00; Fr 12:00-20:00; Sa 10:00-12:30,14:30-18:00")
    assert s[0] == [] and s[6] == []
    assert s[2] == [(time(10), time(12, 30)), (time(14, 30), time(19))]


def test_plages_de_jours_et_regle_qui_remplace():
    s = lire("Mo-Su 09:30-18:00; Sa,Su 09:30-12:00,13:00-16:45; We off")
    assert s[0] == [(time(9, 30), time(18))]
    assert s[2] == []
    assert len(s[5]) == 2


def test_plages_datees_avec_fuseau():
    (debut, fin), = plages_du_jour(lire("Mo 16:00-19:45"), date(2026, 11, 16))
    assert debut.isoformat() == "2026-11-16T16:00:00+01:00" and fin.hour == 19


@pytest.mark.parametrize("faux", [
    "", "Lu 09:00-12:00", "Mo 9h-12h", "Mo 12:00-09:00", "Mo 09:00-12:00,11:00-13:00",
    "Fr-Mo 09:00-12:00", "09:00-12:00", "Mo 09:00-12:00 PH off", "Mo 25:00-26:00",
])
def test_syntaxe_refusee(faux):
    with pytest.raises(HoraireInvalide):
        lire(faux)

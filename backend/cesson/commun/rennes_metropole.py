"""Appels au portail open data de Rennes Métropole (data.rennesmetropole.fr), avec la clé d'API du compte si le
serveur en a une (/srv/cesson/secrets/rennes.env, « cle_api=… »).

Sans clé, le portail accorde 7 000 appels par jour à chaque adresse IP : le Pi et les postes de développement de la
même box se partagent ce quota (HTTP 429 pendant des essais le 22/11). Avec la clé, les appels comptent sur le
quota du compte (10 000 par jour). Une clé absente, refusée ou à court de quota ne bloque jamais la collecte : on
réessaie sans clé.
"""

from __future__ import annotations

from typing import Any

import httpx

from .collecteur import ErreurSource, obtenir
from .secrets import lire_secrets

REPLI_SANS_CLE = (401, 403, 429)


def entetes_rennes() -> dict[str, str]:
    try:
        cle = lire_secrets("Rennes Métropole", "CESSON_RENNES_IDENTIFIANTS", "rennes.env", ("cle_api",))["cle_api"]
    except ErreurSource:
        return {}
    return {"Authorization": f"Apikey {cle}"}


def obtenir_rennes(client: httpx.Client, url: str, *, params: dict[str, Any] | None = None) -> httpx.Response:
    entetes = entetes_rennes()
    if entetes:
        try:
            return obtenir(client, url, params=params, headers=entetes)
        except ErreurSource as e:
            cause = e.__cause__
            if not (isinstance(cause, httpx.HTTPStatusError) and cause.response.status_code in REPLI_SANS_CLE):
                raise
    return obtenir(client, url, params=params)

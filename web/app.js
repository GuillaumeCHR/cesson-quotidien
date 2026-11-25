// Cesson'App, version web. Mêmes données que l'app Android : les fichiers JSON publiés par le serveur de l'app,
// et les prochains passages du métro et des bus demandés directement à STAR. Aucun traceur, aucun cookie.
// Le navigateur garde le cache hors connexion du service worker et, dans son stockage local, les choix des cartes
// « Mon trajet » (arrêts et gare) : ils ne quittent jamais l'appareil.

import { STAR_CLE } from "./config.js";
import CARTE from "./carte-cesson.js";
import RENNES from "./carte-rennes.js";

const DONNEES = "https://cesson-pi.tail7e6f0d.ts.net/";
const STAR = "https://data.explore.star.fr/api/explore/v2.1/catalog/datasets/";
const POLE_VIASILVA = "Cesson - Viasilva";
const FUSEAU = "Europe/Paris";

// --- Outils ----------------------------------------------------------------------------------------

/** Élément DOM sans innerHTML : les textes viennent de sources externes. */
function el(balise, attributs = {}, ...enfants) {
  const e = document.createElement(balise);
  for (const [k, v] of Object.entries(attributs || {})) {
    if (v == null || v === false) continue;
    if (k === "classe") e.className = v;
    else if (k === "style") e.style.cssText = v; // CSSOM : permis par la CSP, contrairement à l'attribut style
    else if (k.startsWith("on")) e.addEventListener(k.slice(2), v);
    else e.setAttribute(k, v === true ? "" : v);
  }
  for (const c of enfants.flat(Infinity)) if (c != null && c !== false) e.append(c instanceof Node ? c : String(c));
  return e;
}

/** Lien externe, seulement en http(s). */
function lien(url, texte, classe = null) {
  try {
    const u = new URL(url);
    if (u.protocol !== "https:" && u.protocol !== "http:" && u.protocol !== "tel:") return document.createTextNode(texte);
    return el("a", { href: u.href, classe, rel: "noopener noreferrer", target: u.protocol === "tel:" ? null : "_blank" }, texte);
  } catch { return document.createTextNode(texte); }
}
const tel = (numero, texte = numero, classe = "bouton") => lien(`tel:${numero.replace(/\s/g, "")}`, texte, classe);
const pastille = (texte, ton = "neutre") => el("span", { classe: `pastille p-${ton}` }, texte);

const fmt = (options) => new Intl.DateTimeFormat("fr-FR", { timeZone: FUSEAU, ...options });
const FMT_HEURE = fmt({ hour: "numeric", minute: "2-digit" });
const FMT_JOUR = fmt({ weekday: "long", day: "numeric", month: "long" });
const FMT_JOUR_COURT = fmt({ weekday: "short", day: "numeric", month: "short" });
/** « 8h30 », « 20h00 ». */
const heure = (d) => FMT_HEURE.format(new Date(d)).replace(":", "h");
const jourCalendaire = (iso) => new Date(`${iso}T12:00:00`);
/** Date du jour à Cesson, « 2026-11-17 ». */
const aujourdhui = (decalage = 0) => fmt({ year: "numeric", month: "2-digit", day: "2-digit" })
  .formatToParts(new Date(Date.now() + decalage * 86400000))
  .reduce((a, p) => ({ ...a, [p.type]: p.value }), {});
const isoJour = (decalage = 0) => { const p = aujourdhui(decalage); return `${p.year}-${p.month}-${p.day}`; };
/** Jour à Cesson d'un horodatage, « 2026-11-17 ». */
const jourCesson = (d) => new Date(d).toLocaleDateString("sv-SE", { timeZone: FUSEAU });
/** Train encore à afficher : jusqu'à la fin fixée par le serveur (départ réel d'un train en retard, train suivant
 * pour un supprimé), sinon 2 min après son départ. */
const trainVisible = (x) => (x.visible_jusqu_a ? new Date(x.visible_jusqu_a)
  : new Date(new Date(x.heure_reelle || x.heure_theorique).getTime() + 120000)) > Date.now();
/** Les n prochains trains, plus ceux dont l'horaire prévu est passé (en retard ou supprimés), qui ne prennent pas
 * leur place. [liste] triée par horaire prévu. */
const prochainsEtPasses = (liste, n) => {
  const passe = (x) => new Date(x.heure_theorique) < Date.now();
  return [...liste.filter(passe), ...liste.filter((x) => !passe(x)).slice(0, n)];
};
const majuscule = (s) => s ? s[0].toUpperCase() + s.slice(1) : s;
const sansAccents = (s) => (s || "").normalize("NFD").replace(/\p{M}+/gu, "").toLowerCase();
const euros = (n) => n.toLocaleString("fr-FR", { minimumFractionDigits: 3, maximumFractionDigits: 3 }) + " €";

/** « il y a 5 min », « il y a 2 h », « le 15 novembre ». */
function depuis(iso) {
  const min = Math.round((Date.now() - new Date(iso)) / 60000);
  if (min < 1) return "à l'instant";
  if (min < 60) return `il y a ${min} min`;
  if (min < 24 * 60) return `il y a ${Math.round(min / 60)} h`;
  return `le ${fmt({ day: "numeric", month: "long" }).format(new Date(iso))}`;
}
/** « 4 min », « maintenant », puis l'heure au-delà d'une heure. */
function attente(iso) {
  const min = Math.floor((new Date(iso) - Date.now()) / 60000);
  if (min < 1) return "maintenant";
  if (min < 60) return `${min} min`;
  return heure(iso);
}

// --- Données ---------------------------------------------------------------------------------------

const cache = new Map();
/** Un fichier du serveur, avec une nouvelle tentative après une seconde si le réseau échoue. */
async function telecharger(nom) {
  for (let essai = 0; essai < 2; essai++) {
    try {
      const r = await fetch(`${DONNEES}${nom}.json`, { cache: "no-cache" });
      if (r.ok) return await r.json();
      if (r.status === 404) return null;
    } catch { /* réseau ou serveur injoignable : on réessaie */ }
    await new Promise((ok) => setTimeout(ok, 1000));
  }
  return null;
}
/**
 * Fichier d'un module du serveur ; null s'il n'est pas (encore) publié ou injoignable. Un échec n'est pas gardé
 * en mémoire : le prochain affichage d'un onglet réessaie.
 */
function module(nom) {
  if (!cache.has(nom)) {
    const p = telecharger(nom);
    cache.set(nom, p);
    p.then((f) => { if (f == null && cache.get(nom) === p) cache.delete(nom); });
  }
  return cache.get(nom);
}

/**
 * Jeu temps réel STAR filtré sur un arrêt ou un parc ; lève une erreur lisible si STAR refuse. Avec la clé, les appels
 * comptent sur le quota du compte ; clé refusée ou quota épuisé : nouvelle tentative anonyme, comme dans l'app.
 */
async function star(jeu, where) {
  const url = `${STAR}${jeu}/records?` + new URLSearchParams({ where, order_by: jeu.includes("passages") ? "depart" : "idparc", limit: "100" });
  let r = await fetch(url, { cache: "no-store", headers: STAR_CLE ? { Authorization: `Apikey ${STAR_CLE}` } : {} });
  if (STAR_CLE && [401, 403, 429].includes(r.status)) r = await fetch(url, { cache: "no-store" });
  if (r.status === 429) throw new Error("Le service temps réel de STAR est saturé en ce moment. Réessayez dans quelques minutes.");
  if (!r.ok) throw new Error(`STAR a répondu ${r.status}.`);
  return (await r.json()).results || [];
}

// --- Cartes ----------------------------------------------------------------------------------------

/** Carte commune : titre, contenu, puis la ligne de fraîcheur (« Mis à jour il y a 12 min · source »). */
function carte(titre, contenu, fichier = null, { classe = "", source = null } = {}) {
  const pied = [];
  if (fichier?.meta) {
    const m = fichier.meta;
    const perime = m.etat !== "ok" || new Date(m.valide_jusqu_a) < new Date();
    pied.push(el("p", { classe: "fraicheur" },
      perime ? el("span", { classe: "ancienne" }, "Données anciennes") : null, perime ? el("br") : null,
      `Mis à jour ${depuis(m.recupere_le)} · ${m.source}`));
  } else if (source) {
    pied.push(el("p", { classe: "fraicheur" }, source));
  }
  return el("section", { classe: `carte ${classe}`, "aria-label": titre }, el("h2", {}, titre), contenu, pied);
}
const absent = (titre, texte = "Données pas encore reçues.") => carte(titre, el("p", { classe: "vide" }, texte));

/** Petite rangée de puces exclusives (jours, carburants…) ; rend le contenu choisi dans [cible]. */
function puces(choix, initial, rendu) {
  const cible = el("div");
  const rangee = el("div", { classe: "puces", role: "group" });
  const choisir = (cle) => {
    for (const b of rangee.children) b.setAttribute("aria-pressed", String(b.dataset.cle === cle));
    cible.replaceChildren(rendu(cle));
  };
  for (const [cle, libelle] of choix) rangee.append(el("button", { type: "button", "data-cle": cle, onclick: () => choisir(cle) }, libelle));
  choisir(initial);
  return el("div", {}, rangee, cible);
}

// Ouverture d'un lieu à partir des jours publiés (plages déjà calculées par le serveur, jours fériés compris).
function ouverture(jours) {
  const maintenant = new Date();
  for (const j of jours || []) {
    if (!j.connu) continue;
    for (const p of j.plages) {
      const debut = new Date(p.debut), fin = new Date(p.fin);
      if (debut <= maintenant && maintenant < fin) return { ouvert: true, jusqua: fin };
    }
  }
  const prochaine = (jours || []).filter((j) => j.connu).flatMap((j) => j.plages).map((p) => new Date(p.debut))
    .filter((d) => d > maintenant).sort((a, b) => a - b)[0];
  const connu = (jours || []).some((j) => j.connu);
  return { ouvert: false, prochaine, connu };
}
function etiquetteOuverture(o) {
  if (o.ouvert) return [pastille("Ouvert", "ouvert"), ` jusqu'à ${heure(o.jusqua)}`];
  if (!o.connu) return [pastille("Horaires inconnus", "neutre")];
  if (!o.prochaine) return [pastille("Fermé", "ferme")];
  const meme = isoJour() === o.prochaine.toLocaleDateString("sv-SE", { timeZone: FUSEAU });
  const demain = isoJour(1) === o.prochaine.toLocaleDateString("sv-SE", { timeZone: FUSEAU });
  const quand = meme ? `à ${heure(o.prochaine)}` : demain ? `demain à ${heure(o.prochaine)}`
    : `${fmt({ weekday: "long" }).format(o.prochaine)} à ${heure(o.prochaine)}`;
  return [pastille("Fermé", "ferme"), ` · ouvre ${quand}`];
}

// --- Vigilances (bandeaux en tête d'Aujourd'hui) ---------------------------------------------------

const RANG = { vert: 0, jaune: 1, orange: 2, rouge: 3 };
async function bandeaux() {
  const [crues, meteo, pollution] = await Promise.all([module("vigicrues"), module("vigilance_meteo"), module("episodes_pollution")]);
  const res = [];
  const v = crues?.donnees;
  if (v && RANG[v.vigilance] >= 1) {
    res.push(carte(`Vigilance ${v.vigilance} crues`, el("div", {},
      el("p", {}, `La Vilaine (tronçon ${v.troncon.nom}) est en vigilance ${v.vigilance}.`),
      lien(v.troncon.url || "https://www.vigicrues.gouv.fr/", "Voir sur Vigicrues", "bouton plein")), crues,
    { classe: `bandeau ${v.vigilance} large` }));
  }
  const a = meteo?.donnees?.alerte;
  if (a && RANG[a.niveau] >= 1 && new Date(a.fin) > new Date()) {
    res.push(carte(a.titre, el("div", {},
      el("p", { classe: "petit doux" }, `Source : Météo-France, bulletin du ${fmt({ day: "2-digit", month: "2-digit" }).format(new Date(meteo.donnees.mis_a_jour))} à ${heure(meteo.donnees.mis_a_jour)}.`),
      ...(meteo.donnees.textes || []).slice(0, 1).map((t) => el("p", {}, t.texte)),
      lien("https://vigilance.meteofrance.fr/fr/ille-et-vilaine", "Vigilance Météo-France", "bouton")), null,
    { classe: `bandeau ${a.niveau} large` }));
  }
  const ep = pollution?.donnees;
  const jour = ep?.jours?.find((j) => j.date >= isoJour() && j.etat && j.etat !== "aucun");
  if (jour) {
    res.push(carte("Épisode de pollution", el("p", {},
      `${majuscule(FMT_JOUR.format(jourCalendaire(jour.date)))} : ${{ information: "procédure d'information et de recommandation",
        alerte: "procédure d'alerte", alerte_persistance: "alerte sur persistance" }[jour.etat] || jour.etat} (${ep.zone}).`),
    pollution, { classe: "bandeau orange large" }));
  }
  return res;
}

// --- Aujourd'hui ----------------------------------------------------------------------------------

async function carteMeteo() {
  const f = await module("meteo");
  if (!f) return absent("Météo à Cesson");
  const d = f.donnees, a = d.actuelle;
  const maintenant = Date.now();
  const heures = d.heures.filter((h) => new Date(h.horodatage) > maintenant).filter((_, i) => i % 2 === 0).slice(0, 6);
  const jour = d.jours.find((j) => j.date === isoJour()) || d.jours[0];
  const demain = d.jours.find((j) => j.date === isoJour(1));
  return carte("Météo à Cesson", el("div", {},
    el("div", { classe: "ligne" }, el("span", { classe: "grand" }, `${Math.round(a.temperature)}°`),
      el("div", { classe: "etire" }, el("strong", {}, a.libelle), el("br"), el("span", { classe: "doux" }, `Ressenti ${Math.round(a.ressentie)}°`))),
    jour && el("p", {}, el("strong", {}, "Aujourd'hui "), `${Math.round(jour.min)}° / ${Math.round(jour.max)}° · ${jour.libelle}`,
      jour.precipitation_mm >= 0.1 ? ` · ${jour.precipitation_mm.toLocaleString("fr-FR", { maximumFractionDigits: 1 })} mm` : ""),
    jour?.rafales_max_kmh > 50 && el("p", {}, `Rafales jusqu'à ${Math.round(jour.rafales_max_kmh)} km/h`),
    heures.length > 0 && el("div", { classe: "heures" }, heures.map((h) =>
      el("div", {}, el("span", { classe: "doux" }, heure(h.horodatage).replace(/h00$/, "h")), el("strong", {}, `${Math.round(h.temperature)}°`),
        el("span", { classe: "petit" }, h.libelle)))),
    demain && el("p", {}, el("strong", {}, "Demain "), `${Math.round(demain.min)}° / ${Math.round(demain.max)}° · ${demain.libelle}`),
  ), f);
}

async function carteVilaine() {
  const [f, v] = await Promise.all([module("vilaine"), module("vigicrues")]);
  if (!f) return absent("La Vilaine et les crues");
  const m = f.donnees.mesure;
  const vig = v?.donnees?.vigilance;
  const tendance = { hausse: "En hausse", baisse: "En baisse", stable: "Stable" }[m.tendance] || "";
  return carte("La Vilaine et les crues", el("div", {},
    vig && el("p", {}, pastille(vig === "vert" ? "Pas de vigilance crues" : `Vigilance ${vig}`, vig === "vert" ? "ouvert" : vig),
      el("span", { classe: "doux" }, ` Tronçon ${v.donnees.troncon.nom}`)),
    el("div", { classe: "ligne" }, el("span", { classe: "grand" }, `${(m.valeur / 1000).toLocaleString("fr-FR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })} m`),
      el("div", { classe: "etire" }, el("strong", {}, tendance), el("br"),
        el("span", { classe: "doux petit" }, `Mesure à Pont Briand de ${heure(m.horodatage)}`))),
    lien(f.donnees.station.url || "https://www.vigicrues.gouv.fr/", "Voir sur Vigicrues", "bouton"),
  ), f);
}

/** Prochains passages du métro et des bus à l'arrêt [nomarret], 2 par ligne et destination, dans [corps]. */
async function remplirPassages(corps, nomarret) {
  corps.replaceChildren(el("p", { classe: "doux" }, "Interrogation de STAR…"));
  const nom = nomarret.replace(/"/g, "");
  try {
    const [metro, bus] = await Promise.all([
      star("tco-metro-circulation-passages-tr", `nomarret="${nom}"`),
      star("tco-bus-circulation-passages-tr", `nomarret="${nom}"`)]);
    const departs = [
      ...metro.filter((p) => p.depart).map((p) => ({ ligne: p.nomcourtligne, metro: true, destination: p.destination, heure: p.depart, arret: p.nomarret })),
      ...bus.filter((p) => (p.depart || p.departtheorique) && (!p.visibilite || p.visibilite === "Grand public"))
        .map((p) => ({ ligne: p.nomcourtligne, metro: false, destination: p.destination, heure: p.depart || p.departtheorique, arret: p.nomarret,
          theorique: p.precision !== "Temps réel" })),
    ].filter((d) => sansAccents(d.destination).replace(/[^a-z]/g, "") !== sansAccents(d.arret).replace(/[^a-z]/g, ""))
      .filter((d) => new Date(d.heure) > Date.now() - 30000)
      .sort((a, b) => new Date(a.heure) - new Date(b.heure));
    const groupes = new Map();
    for (const d of departs) {
      const k = `${d.ligne}|${d.destination}`;
      if (!groupes.has(k)) groupes.set(k, []);
      if (groupes.get(k).length < 3) groupes.get(k).push(d);
    }
    const lignes = [...groupes.values()].sort((a, b) => (b[0].metro - a[0].metro) || (new Date(a[0].heure) - new Date(b[0].heure)));
    if (lignes.length === 0) { corps.replaceChildren(el("p", { classe: "vide" }, "Aucun départ annoncé pour l'instant.")); return; }
    corps.replaceChildren(el("ul", { classe: "liste" }, lignes.map((g) => el("li", { classe: "ligne" },
      el("span", { classe: "ligne-star", style: null, "data-ligne": g[0].ligne }, g[0].ligne),
      el("span", { classe: "etire" }, g[0].destination),
      el("span", { classe: "chiffre" }, attente(g[0].heure)),
      g[1] && el("span", { classe: "doux" }, attente(g[1].heure)))))
    );
    colorerLignes(corps);
  } catch (e) {
    corps.replaceChildren(el("p", {}, e.message), lien("https://www.star.fr/", "Horaires sur star.fr", "bouton"));
  }
}

async function carteDeparts() {
  const corps = el("div");
  remplirPassages(corps, POLE_VIASILVA);
  return carte("Métro et bus ViaSilva", corps, null, { source: "STAR (Rennes Métropole, Keolis Rennes), temps réel, ODbL" });
}

// Choix des cartes « Mon trajet » (arrêt, destination, gare), gardés dans le stockage local du navigateur, sur
// l'appareil seulement. Stockage bloqué (navigation privée, données effacées) : les choix durent le temps de la visite.
const CLE_CHOIX = "cessonapp-choix";
function lireChoix() {
  try { return JSON.parse(localStorage.getItem(CLE_CHOIX)) || {}; } catch { return {}; }
}
function enregistrerChoix() {
  try {
    localStorage.setItem(CLE_CHOIX, JSON.stringify({ arret: trajet.arret, destination: trajet.destination, gare: trajetTrain.gare }));
  } catch { /* stockage indisponible : le choix vaut pour la visite */ }
}
const apresMidi = () => parseInt(fmt({ hour: "numeric", hourCycle: "h23" }).format(new Date()), 10) >= 13;
/** Remplace le contenu de [noeud] ; les listes sont aplaties et les valeurs vides ignorées, comme dans el(). */
const remplacer = (noeud, ...enfants) => noeud.replaceChildren(...enfants.flat(Infinity).filter((c) => c != null && c !== false));
const choix = lireChoix();
const texte = (v) => typeof v === "string" && v ? v : null;
// Le sens (aller ou retour) n'est pas gardé : il se choisit selon l'heure à chaque visite, comme dans l'app.
const trajet = { arret: texte(choix.arret), destination: texte(choix.destination), retour: null };
const trajetTrain = { gare: texte(choix.gare), retour: null };

/** Rangée Aller / Retour ; [retour] est l'état actuel, [changer] reçoit le nouveau. */
function allerRetour(retour, changer) {
  return el("div", { classe: "puces", role: "group" },
    el("button", { type: "button", "aria-pressed": String(!retour), onclick: () => changer(false) }, "Aller"),
    el("button", { type: "button", "aria-pressed": String(retour), onclick: () => changer(true) }, "Retour"));
}

/**
 * Mon trajet réseau Star : un arrêt à Cesson et, si l'on veut, une destination sur tout le réseau. Aller : les bus et
 * métros en approche de l'arrêt de Cesson ; retour : ceux en approche de la destination (comme l'app).
 */
async function carteMonTrajet() {
  const f = await module("lignes_star");
  if (!f) return absent("Mon trajet réseau Star");
  const d = f.donnees;
  const corps = el("div");
  // Un choix gardé d'une visite précédente qui n'existe plus dans le réseau est oublié.
  if (trajet.arret && !d.arrets.some((a) => a.nom === trajet.arret)) trajet.arret = null;
  if (trajet.destination && !d.reseau.some((a) => a.nom === trajet.destination)) trajet.destination = null;
  const rendre = () => {
    const retour = trajet.destination && (trajet.retour ?? apresMidi());
    const choixArret = el("select", { id: "trajet-arret", onchange: (e) => { trajet.arret = e.target.value || null; enregistrerChoix(); rendre(); } },
      el("option", { value: "" }, "Choisir un arrêt"),
      [...d.arrets].sort((a, b) => a.nom.localeCompare(b.nom, "fr")).map((a) =>
        el("option", { value: a.nom, selected: a.nom === trajet.arret }, a.nom)));
    const noms = new Set(d.reseau.map((a) => a.nom));
    const choixDestination = el("input", {
      id: "trajet-destination", list: "trajet-reseau", value: trajet.destination || "", placeholder: "Rechercher un arrêt", autocomplete: "off",
      onchange: (e) => {
        const v = e.target.value.trim();
        if (v && !noms.has(v)) { e.target.setCustomValidity("Aucun arrêt du réseau STAR ne porte ce nom."); e.target.reportValidity(); return; }
        e.target.setCustomValidity("");
        trajet.destination = v || null; trajet.retour = null; enregistrerChoix(); rendre();
      },
    });
    const passages = el("div");
    const origine = retour ? trajet.destination : trajet.arret;
    remplacer(corps,
      el("div", { classe: "choix" },
        el("label", { for: "trajet-arret" }, "À Cesson"), choixArret,
        el("label", { for: "trajet-destination" }, "Destination"), choixDestination,
        el("datalist", { id: "trajet-reseau" }, d.reseau.map((a) => el("option", { value: a.nom })))),
      trajet.destination && allerRetour(retour, (v) => { trajet.retour = v; rendre(); }),
      origine ? [el("p", { classe: "doux petit" }, `Départs de ${origine}`), passages]
        : el("p", { classe: "vide" }, "Choisissez votre arrêt à Cesson et, si vous voulez, votre destination n'importe où sur le réseau STAR."),
    );
    if (origine) remplirPassages(passages, origine);
  };
  rendre();
  return carte("Mon trajet réseau Star", corps, null, { source: "STAR (Rennes Métropole, Keolis Rennes), temps réel, ODbL" });
}

/** Couleurs officielles des lignes (module lignes_star). */
async function colorerLignes(racine) {
  const f = await module("lignes_star");
  const lignes = new Map((f?.donnees?.lignes || []).map((l) => [l.nom_court, l]));
  for (const s of racine.querySelectorAll(".ligne-star")) {
    const l = lignes.get(s.dataset.ligne);
    if (l) { s.style.background = l.couleur; s.style.color = l.couleur_texte; }
  }
}

const SVG = "http://www.w3.org/2000/svg";
function svg(balise, attributs = {}, ...enfants) {
  const e = document.createElementNS(SVG, balise);
  for (const [k, v] of Object.entries(attributs)) e.setAttribute(k, v);
  for (const c of enfants.flat()) if (c) e.append(c);
  return e;
}

/** Plan de la circulation : limite de Cesson, Vilaine, et chaque tronçon dans la couleur de son état (comme l'app). */
function planCirculation(troncons, compact = false) {
  // Compact (accueil) : plus petit, et seulement les tronçons mesurés.
  if (compact) troncons = troncons.filter((t) => t.etat !== "inconnu");
  const { cos_lat0: k, x0, y0, etendue } = CARTE.projection;
  const E = 1000; // coordonnées de la carte (0 à 1) mises à l'échelle du viewBox
  const ligne = (pts) => pts.map(([x, y]) => `${(x * E).toFixed(1)},${(y * E).toFixed(1)}`).join(" ");
  const projete = (pts) => pts.map(([lon, lat]) => [(lon * k - x0) / etendue, (y0 - lat) / etendue]);
  const n = {};
  for (const t of troncons) n[t.etat] = (n[t.etat] || 0) + 1;
  const resume = [["bloque", "bloqués"], ["sature", "saturés"], ["dense", "denses"], ["fluide", "fluides"]]
    .filter(([e]) => n[e]).map(([e, mot]) => `${n[e]} ${mot}`).join(", ") || "aucun tronçon mesuré";
  const marge = 10;
  const plan = svg("svg", {
    class: `plan-circulation${compact ? " compact" : ""}`, role: "img", "aria-label": `Plan de la circulation à Cesson-Sévigné : ${resume}. Le détail suit en texte.`,
    viewBox: `${-marge} ${-marge} ${CARTE.largeur * E + 2 * marge} ${CARTE.hauteur * E + 2 * marge}`,
  },
    svg("polygon", { class: "plan-commune", points: ligne(CARTE.contour) }),
    CARTE.vilaine.map((m) => svg("polyline", { class: "plan-vilaine", points: ligne(m) })),
    troncons.filter((t) => t.points?.length > 1)
      .map((t) => svg("polyline", { class: `plan-trafic ${t.etat}${t.rocade ? " rocade" : ""}`, points: ligne(projete(t.points)) })),
  );
  return el("figure", { classe: "plan" }, plan);
}

/**
 * Plan de Rennes et de sa première couronne (comme l'app) : limites des communes, Cesson teintée et cernée, Vilaine et
 * Ille, puis la rocade entière, les voies rapides (épaisses), les grands axes, les boulevards de Rennes et les rues de
 * Cesson dans la couleur de leur état.
 */
function planMetropole(troncons, compact = false) {
  if (compact) troncons = troncons.filter((t) => t.etat !== "inconnu");
  const { cos_lat0: k, x0, y0, etendue } = RENNES.projection;
  const E = 1000;
  const ligne = (pts) => pts.map(([x, y]) => `${(x * E).toFixed(1)},${(y * E).toFixed(1)}`).join(" ");
  const projete = (pts) => pts.map(([lon, lat]) => [(lon * k - x0) / etendue, (y0 - lat) / etendue]);
  const n = {};
  for (const t of troncons) n[t.etat] = (n[t.etat] || 0) + 1;
  const resume = [["bloque", "bloqués"], ["sature", "saturés"], ["dense", "denses"], ["fluide", "fluides"]]
    .filter(([e]) => n[e]).map(([e, mot]) => `${n[e]} ${mot}`).join(", ") || "aucun tronçon mesuré";
  const classe = (c) => c.insee === RENNES.insee_cesson ? "plan-cesson" : c.insee === RENNES.insee_rennes ? "plan-rennes" : "plan-voisine";
  const plan = svg("svg", {
    class: `plan-circulation metropole${compact ? " compact" : ""}`, role: "img",
    "aria-label": `Plan de la circulation à Rennes et autour, Cesson-Sévigné mise en évidence : ${resume}. Le détail suit en texte.`,
    viewBox: `0 0 ${RENNES.largeur * E} ${RENNES.hauteur * E}`, preserveAspectRatio: "xMidYMid meet",
  },
    RENNES.communes.map((c) => svg("polygon", { class: classe(c), points: ligne(c.contour) })),
    RENNES.rivieres.map((m) => svg("polyline", { class: "plan-vilaine riviere", points: ligne(m) })),
    troncons.filter((t) => t.points?.length > 1)
      .map((t) => svg("polyline", { class: `plan-trafic ${t.etat}${t.rocade ? " rocade" : ""}`, points: ligne(projete(t.points)) })),
  );
  return el("figure", { classe: "plan" }, plan);
}

/** [complet] : tous les axes de Cesson ; sinon, seulement ceux qui ne sont pas fluides (accueil). */
async function carteCirculation(complet = true) {
  const f = await module("circulation");
  if (!f) return absent("Circulation");
  const d = f.donnees;
  const axes = complet ? d.axes_cesson : d.axes_cesson.filter((a) => a.etat !== "fluide" && a.etat !== "inconnu");
  const ton = (e) => ({ fluide: "ouvert", dense: "jaune", sature: "orange", bloque: "rouge" }[e] || "neutre");
  const nom = (e) => ({ fluide: "Fluide", dense: "Dense", sature: "Saturé", bloque: "Bloqué", inconnu: "Inconnu" }[e] || majuscule(e));
  return carte("Circulation", el("div", {},
    d.troncons_metropole?.length > 0 ? planMetropole(d.troncons_metropole, !complet)
      : d.troncons?.length > 0 && planCirculation(d.troncons, !complet),
    el("h3", {}, "Rocade est"),
    el("ul", { classe: "liste" }, d.rocade_est.map((r) => el("li", { classe: "ligne" },
      el("span", { classe: "etire" }, r.libelle, ...(r.ralentissements || []).map((x) => el("div", { classe: "doux petit" }, `${nom(x.etat)} entre ${x.entre}`))),
      pastille(nom(r.etat), ton(r.etat)), r.vitesse != null && el("span", { classe: "chiffre" }, `${r.vitesse} km/h`)))),
    el("h3", {}, "Dans Cesson"),
    !complet && axes.length === 0 && el("p", {}, pastille("Fluide", "ouvert"), " Pas de ralentissement sur les grands axes."),
    el("ul", { classe: "liste" }, axes.map((a) => el("li", { classe: "ligne" },
      el("span", { classe: "etire" }, a.nom), pastille(nom(a.etat), ton(a.etat))))),
    el("p", { classe: "doux petit" }, `Mesure de ${heure(d.mesure_le)}.`),
  ), f);
}

const nomJour = (iso) => iso === isoJour() ? "Aujourd'hui" : iso === isoJour(1) ? "Demain" : majuscule(fmt({ weekday: "long" }).format(jourCalendaire(iso)));
const SOURCE_ATMO = "Source : Atmo France / AASQA (Air Breizh)";

/** Qualité de l'air : indice ATMO du jour (et du lendemain une fois publié), polluant en cause. */
async function carteAir() {
  const f = await module("qualite_air");
  if (!f) return absent("Qualité de l'air", "Indice ATMO pas encore reçu. Air Breizh le publie chaque jour vers 16h.");
  const d = f.donnees;
  const jours = d.jours.filter((j) => j.date >= isoJour());
  const cause = (j) => (j.responsables || []).map((p) => j.sous_indices?.find((x) => x.polluant === p)?.nom || p).join(", ");
  return carte("Qualité de l'air", el("div", {},
    jours.length === 0 ? el("p", { classe: "vide" }, "Pas d'indice pour aujourd'hui : Air Breizh ne l'a pas encore publié.") :
      el("ul", { classe: "liste" }, jours.map((j) => el("li", { classe: "ligne" },
        el("span", { classe: "etire" }, el("strong", {}, nomJour(j.date)), el("br"),
          el("span", { classe: "doux petit" }, cause(j) ? `Dû à ${cause(j)}` : "Indice ATMO")),
        el("span", { classe: "pastille", style: `background:${/^#[0-9a-f]{6}$/i.test(j.couleur) ? j.couleur : "#888"};color:#14211C` }, j.libelle)))),
    el("p", { classe: "doux petit" }, `Indice calculé pour ${d.zone}, qui vaut pour Cesson.`),
    lien("https://www.airbreizh.asso.fr/", "Conseils et cartes sur Air Breizh", "bouton"),
    el("p", { classe: "doux petit" }, SOURCE_ATMO)), f);
}

/** Indice pollen de Cesson : aujourd'hui et les jours suivants, espèces en cause. */
async function cartePollens() {
  const f = await module("pollens");
  if (!f) return absent("Indice pollen", "Indice pollen pas encore reçu. Air Breizh le publie chaque jour vers 16h.");
  const d = f.donnees;
  const jours = d.jours.filter((j) => j.date >= isoJour());
  const j = jours[0];
  const especes = (x) => (x.especes || []).filter((e) => e.code >= 3).sort((a, b) => b.code - a.code).map((e) => e.nom);
  return carte("Indice pollen", el("div", {},
    !j ? el("p", { classe: "vide" }, "Pas d'indice pour aujourd'hui : Air Breizh ne l'a pas encore publié.") : [
      j.alerte && el("p", {}, pastille("Alerte pollens d'Air Breizh", "orange")),
      el("ul", { classe: "liste" }, jours.map((x) => el("li", { classe: "ligne" },
        el("span", { classe: "etire" }, el("strong", {}, nomJour(x.date)),
          especes(x).length > 0 && [el("br"), el("span", { classe: "doux petit" }, `Surtout ${especes(x).join(", ")}`)]),
        pastille(x.libelle, x.alerte ? "orange" : x.code >= 3 ? "jaune" : "neutre")))),
    ],
    el("p", { classe: "doux petit" }, `Indice calculé pour ${d.commune}.`),
    el("p", { classe: "doux petit" }, SOURCE_ATMO)), f);
}

/** Épisodes de pollution : procédures préfectorales d'aujourd'hui et de demain. */
async function carteEpisodes() {
  const f = await module("episodes_pollution");
  if (!f) return absent("Épisodes de pollution");
  const d = f.donnees;
  const ETATS = { information: ["Information et recommandation", "jaune"], alerte: ["Alerte", "orange"], alerte_persistance: ["Alerte sur persistance", "rouge"] };
  const aVenir = d.jours.filter((j) => j.date >= isoJour() && j.date <= isoJour(1) && j.etat && j.etat !== "aucun");
  return carte("Épisodes de pollution", el("div", {},
    aVenir.length === 0 ? [el("p", {}, pastille("Aucun épisode", "ouvert"), " ni aujourd'hui ni demain"),
      el("p", { classe: "doux petit" }, `Aucune procédure préfectorale en ${d.zone}.`)] :
      [el("ul", { classe: "liste" }, aVenir.map((j) => { const [libelle, ton] = ETATS[j.etat] || [j.etat, "orange"]; return el("li", { classe: "ligne" },
        el("span", { classe: "etire" }, el("strong", {}, nomJour(j.date)), el("br"),
          el("span", { classe: "doux petit" }, (j.polluants || []).filter((p) => p.etat === j.etat).map((p) => p.nom).join(", "))),
        pastille(libelle, ton)); })),
      lien("https://www.airbreizh.asso.fr/", "Recommandations sur Air Breizh", "bouton")],
    el("p", { classe: "doux petit" }, SOURCE_ATMO)), f);
}

async function carteActus(flux = "ville", titre = "Actualités de la ville") {
  const f = await module("actus");
  const fl = f?.donnees?.flux?.find((x) => x.id === flux);
  if (!fl) return absent(titre);
  return carte(titre, el("ul", { classe: "liste" }, fl.articles.slice(0, 6).map((a) => el("li", {},
    lien(a.lien, a.titre), el("div", { classe: "doux petit" }, depuis(a.date))))), f);
}

// --- Bouger ---------------------------------------------------------------------------------------

const DEPARTEMENTS = { 35: "Ille-et-Vilaine", 44: "Loire-Atlantique", 56: "Morbihan", 22: "Côtes-d'Armor", 53: "Mayenne", 50: "Manche", 75: "Paris" };
const statutTrain = (x) => x.statut === "supprime" ? pastille("Supprimé", "ferme") : x.statut === "retard" ? pastille(`+${x.retard_min || "?"} min`, "jaune")
  : x.statut === "a_l_heure" ? pastille("À l'heure", "ouvert") : pastille("Horaire prévu", "neutre");

/**
 * Mon trajet en train : l'aller part de la halte de Cesson (deux sens) ; le retour, de la gare choisie (Ille-et-Vilaine,
 * départements voisins ou Paris), avec seulement les trains qui vont ensuite à Rennes ou à Cesson (comme l'app).
 * [complet] : 3 trains par sens et 4 au retour ; sinon 2.
 */
async function carteTrains(complet = true) {
  const [f, r] = await Promise.all([module("trains"), module("trains_retour")]);
  if (!f) return absent("Mon trajet en train");
  const d = f.donnees, gares = r?.donnees?.gares || [];
  const n = complet ? 3 : 2;
  const corps = el("div");
  const aller = () => {
    const futurs = d.departs.filter((x) => jourCesson(x.heure_theorique) === isoJour() && trainVisible(x))
      .sort((a, b) => new Date(a.heure_theorique) - new Date(b.heure_theorique));
    return d.directions.map((dir) => {
      const liste = prochainsEtPasses(futurs.filter((x) => x.direction === dir.id), n);
      return el("div", {}, el("h3", {}, dir.libelle), liste.length === 0 ? el("p", { classe: "vide" }, "Plus de train aujourd'hui.") :
        el("ul", { classe: "liste" }, liste.map((x) => el("li", { classe: "ligne" },
          el("span", { classe: "chiffre" }, heure(x.heure_reelle || x.heure_theorique)),
          el("span", { classe: "etire" }, `${x.ligne} · ${x.destination}`), statutTrain(x)))));
    });
  };
  const retour = (gare) => {
    const trains = prochainsEtPasses(r.donnees.departs.filter((x) => x.arret === gare
      && jourCesson(x.heure_theorique) === isoJour() && trainVisible(x))
      .sort((a, b) => new Date(a.heure_theorique) - new Date(b.heure_theorique)), complet ? 4 : 2);
    return [el("h3", {}, "Trains vers Rennes ou Cesson uniquement"),
      !r.donnees.temps_reel_disponible && el("p", { classe: "doux petit" }, "Temps réel SNCF indisponible : horaires prévus."),
      trains.length === 0 ? el("p", { classe: "vide" }, "Plus de train vers Rennes ou Cesson aujourd'hui.") :
        el("ul", { classe: "liste" }, trains.map((x) => el("li", { classe: "ligne" },
          el("span", { classe: "chiffre" }, heure(x.statut === "retard" && x.heure_reelle ? x.heure_reelle : x.heure_theorique)),
          el("span", { classe: "etire" }, `${x.ligne} · n° ${x.numero}`, el("br"),
            el("span", { classe: "doux petit" }, `arrivée à ${x.arrivee} à ${heure(x.heure_arrivee)}`)),
          statutTrain(x))))];
  };
  const rendre = () => {
    const gare = gares.find((g) => g.id === trajetTrain.gare);
    const sensRetour = gare && (trajetTrain.retour ?? apresMidi());
    const parDepartement = Object.entries(DEPARTEMENTS).map(([code, nom]) => [nom, code, gares.filter((g) => g.departement === code)])
      .filter(([, , liste]) => liste.length);
    remplacer(corps,
      gares.length > 0 && el("div", { classe: "choix" },
        el("label", { for: "train-gare" }, "Destination"),
        el("select", { id: "train-gare", onchange: (e) => { trajetTrain.gare = e.target.value || null; trajetTrain.retour = null; enregistrerChoix(); rendre(); } },
          el("option", { value: "" }, "Aucune destination"),
          parDepartement.map(([nom, code, liste]) => el("optgroup", { label: `${nom} (${code})` },
            liste.map((g) => el("option", { value: g.id, selected: g.id === trajetTrain.gare }, g.nom)))))),
      gare && allerRetour(sensRetour, (v) => { trajetTrain.retour = v; rendre(); }),
      gare && el("p", { classe: "doux petit" }, sensRetour ? `Départs de ${gare.nom}` : "Départs de la halte de Cesson"),
      sensRetour ? retour(gare.id) : aller(),
      complet && lien(d.halte.url || "https://www.ter.sncf.com/bretagne", "Horaires TER BreizhGo", "bouton"),
    );
  };
  rendre();
  return carte("Mon trajet en train", corps, f);
}

async function carteParcRelais() {
  const corps = el("div", {}, el("p", { classe: "doux" }, "Interrogation de STAR…"));
  const c = carte("Parc relais ViaSilva", corps, null, { source: "STAR, temps réel, ODbL" });
  star("tco-parcsrelais-star-etat-tr", `idparc="CVI"`).then(([p]) => {
    if (!p) { corps.replaceChildren(el("p", { classe: "vide" }, "Parc non trouvé.")); return; }
    const ouvert = p.etatouverture === "OUVERT";
    const pct = p.capacitesoliste ? Math.round(100 * (p.capacitesoliste - p.jrdinfosoliste) / p.capacitesoliste) : 0;
    corps.replaceChildren(
      el("div", { classe: "ligne" }, el("span", { classe: "grand" }, p.jrdinfosoliste),
        el("span", { classe: "etire" }, ` places libres sur ${p.capacitesoliste}`), pastille(ouvert ? "Ouvert" : "Fermé", ouvert ? "ouvert" : "ferme")),
      el("div", { classe: "jauge", role: "img", "aria-label": `Rempli à ${pct} %` }, el("span", { style: `width:${pct}%` })),
      el("p", { classe: "doux petit" }, `Électrique : ${p.jrdinfoelectrique} / ${p.capaciteve} · PMR : ${p.jrdinfopmr} / ${p.capacitepmr}`));
  }).catch((e) => corps.replaceChildren(el("p", {}, e.message)));
  return c;
}

async function carteCarburants() {
  const f = await module("carburants");
  if (!f) return absent("Prix des carburants");
  const stations = f.donnees.stations;
  const types = [...new Map(stations.flatMap((s) => s.prix).map((p) => [p.carburant, p.libelle])).entries()];
  return carte("Prix des carburants", puces(types, types.find(([k]) => k === "gazole")?.[0] || types[0]?.[0], (code) => {
    const lignes = stations.map((s) => ({ s, p: s.prix.find((x) => x.carburant === code) })).filter((x) => x.p).sort((a, b) => a.p.prix - b.p.prix);
    const ruptures = stations.filter((s) => (s.ruptures || []).some((r) => (r.carburant || r) === code));
    return el("ul", { classe: "liste" }, lignes.map(({ s, p }, i) => {
      const ancien = Date.now() - new Date(p.maj) > 3 * 86400000;
      return el("li", { classe: "ligne" },
        el("span", { classe: "etire" }, s.lieu.nom, el("br"), el("span", { classe: `petit ${ancien ? "" : "doux"}` }, `Prix ${depuis(p.maj)}`)),
        i === 0 && !ancien && pastille("Le moins cher", "ouvert"), el("span", { classe: "chiffre" }, euros(p.prix)));
    }), ruptures.map((s) => el("li", { classe: "ligne" }, el("span", { classe: "etire" }, s.lieu.nom), pastille("Rupture", "ferme"))));
  }), f);
}

async function carteBornes() {
  const f = await module("bornes");
  if (!f) return absent("Bornes de recharge");
  return carte("Bornes de recharge", el("div", {}, el("ul", { classe: "liste" }, f.donnees.stations.slice(0, 8).map((s) => el("li", {},
    el("div", { classe: "ligne" }, el("strong", { classe: "etire" }, s.lieu.nom),
      pastille(`${Math.round(s.puissance_max)} kW`, s.puissance_max >= 50 ? "info" : "neutre")),
    el("div", { classe: "doux petit" }, [s.lieu.adresse, `${s.points} point${s.points > 1 ? "s" : ""}`, s.prises.join(", ")].filter(Boolean).join(" · ")),
    el("div", { classe: "pastilles" }, s.h24 && pastille("24 h/24", "ouvert"), s.gratuit && pastille("Gratuit", "ouvert"))))),
  el("p", { classe: "doux petit" }, "Disponibilité en temps réel non publiée en open data.")), f);
}

// --- Sortir ---------------------------------------------------------------------------------------

async function carteSaison() {
  const f = await module("agenda");
  if (!f) return absent("Saison culturelle");
  const ev = f.donnees.evenements.filter((e) => e.saison && e.au >= isoJour()).slice(0, 8);
  return carte("Saison culturelle", el("div", {},
    el("p", {}, "Les spectacles du Carré Sévigné et du Pont des Arts."),
    lien("https://billetterie.ville-cesson-sevigne.fr/", "Réserver des places", "bouton plein"),
    el("ul", { classe: "liste" }, ev.map((e) => el("li", { classe: "ligne film" },
      e.vignette && /^vignettes\/\d+\.jpg$/.test(e.vignette)
        ? el("img", { classe: "vignette", src: DONNEES + e.vignette, alt: "", loading: "lazy", width: 56, height: 56 }) : null,
      el("div", { classe: "etire" },
        el("div", { classe: "doux" }, majuscule(FMT_JOUR_COURT.format(jourCalendaire(e.du)))),
        el("strong", {}, lien(e.lien, e.titre)), " ", e.statut && pastille(e.statut, e.statut === "Nouvelle date" ? "neutre" : "ferme"),
        " ", e.categorie && pastille(e.categorie, "info"),
        el("div", { classe: "doux petit" }, [e.horaires, e.lieu].filter(Boolean).join(" · ")),
        e.vignette && e.credit_photo && el("div", { classe: "doux petit" }, `Photo ${e.credit_photo}`)))))), f);
}

async function carteAgenda() {
  const f = await module("agenda");
  if (!f) return absent("Agenda");
  // Les matchs du club de handball ont leur carte.
  const ev = f.donnees.evenements.filter((e) => !e.saison && !/^Cesson Rennes Métropole Handball/i.test(e.titre) && e.au >= isoJour()).slice(0, 10);
  return carte("Agenda", el("ul", { classe: "liste" }, ev.map((e) => el("li", {},
    el("div", { classe: "doux petit" }, e.du === e.au ? majuscule(FMT_JOUR_COURT.format(jourCalendaire(e.du)))
      : `Du ${FMT_JOUR_COURT.format(jourCalendaire(e.du))} au ${FMT_JOUR_COURT.format(jourCalendaire(e.au))}`),
    el("strong", {}, lien(e.lien, e.titre)),
    el("div", { classe: "doux petit" }, [e.horaires, e.lieu].filter(Boolean).join(" · "))))), f);
}

async function carteHandball() {
  const f = await module("handball");
  if (!f) return absent("Handball à la Glaz Arena");
  const d = f.donnees;
  const matchs = d.a_venir.filter((m) => new Date(m.debut) > Date.now() - 2 * 3600000).slice(0, 6);
  const quand = (m) => m.horaire_a_confirmer ? "horaire à confirmer" : heure(m.debut);
  const jour = (m) => majuscule(FMT_JOUR.format(new Date(m.debut)));
  const [prochain, ...suivants] = matchs;
  const der = d.dernier;
  const issue = der && (der.score_cesson > der.score_adversaire ? "victoire" : der.score_cesson < der.score_adversaire ? "défaite" : "match nul");
  return carte("Handball à la Glaz Arena", el("div", {},
    el("p", {}, "Les matchs à domicile du Cesson Rennes Métropole Handball."),
    !prochain && el("p", { classe: "vide" }, "Pas de match à domicile annoncé pour l'instant."),
    prochain && el("div", {},
      el("div", { classe: "doux" }, `${jour(prochain)} · ${quand(prochain)}`),
      el("strong", {}, `Cesson – ${prochain.adversaire}`), " ", pastille(prochain.tour, "info"),
      prochain.competition?.startsWith("Coupe") && pastille(prochain.competition, "neutre"),
      el("p", {}, prochain.billetterie && /^https:\/\/billetterie\.cesson-handball\.com\//.test(prochain.billetterie)
        ? lien(prochain.billetterie, "Réserver des places", "bouton plein")
        : el("span", { classe: "doux petit" }, "Places pas encore en vente."))),
    suivants.length > 0 && el("ul", { classe: "liste" }, suivants.map((m) => el("li", { classe: "ligne" },
      el("span", { classe: "etire" }, el("strong", {}, `Cesson – ${m.adversaire}`), el("br"),
        el("span", { classe: "doux petit" }, [majuscule(FMT_JOUR_COURT.format(new Date(m.debut))), quand(m), m.tour].join(" · ")))))),
    issue && el("p", { classe: "doux petit" }, `Dernier match à domicile : ${issue} ${der.score_cesson}-${der.score_adversaire} contre ${der.adversaire}.`),
    lien("https://www.cesson-handball.com/pro/saison", "Toute la saison du club", "bouton")), f);
}

const duree = (m) => m < 60 ? `${m} min` : `${Math.floor(m / 60)}h${String(m % 60).padStart(2, "0")}`;
/** « Martin Scorsese · 1h54 · Crime » (fiche TMDB publiée par le serveur). */
const ligneFiche = (f) => [f.realisateurs?.[0], f.duree_min && duree(f.duree_min), f.genres?.[0]].filter(Boolean).join(" · ");

function detailFiche(f) {
  const lignes = [
    f.realisateurs?.length && `Réalisation : ${f.realisateurs.join(", ")}`,
    f.distribution?.length && `Avec ${f.distribution.join(", ")}`,
    [f.annee, f.titre_original && `titre original : ${f.titre_original}`, f.genres?.length > 1 && f.genres.join(", ")].filter(Boolean).join(" · "),
    f.classification && `Interdit aux moins de ${f.classification} ans`,
  ].filter(Boolean);
  return el("details", { classe: "petit" }, el("summary", {}, "À propos du film"),
    f.synopsis && el("p", {}, f.synopsis),
    lignes.map((l) => el("div", { classe: "doux" }, l)),
    el("div", { classe: "pastilles" }, f.wikipedia && lien(f.wikipedia, "Wikipédia", "bouton"),
      f.allocine && lien(f.allocine, "Allociné", "bouton"), lien(f.page_tmdb, "TMDB", "bouton")));
}

async function carteCinema() {
  const f = await module("cinema");
  if (!f) return absent("Cinéma Le Sévigné");
  const d = f.donnees;
  const parJour = new Map();
  for (const film of d.films) for (const s of film.seances) {
    const j = new Date(s.debut).toLocaleDateString("sv-SE", { timeZone: FUSEAU });
    if (j < isoJour() || new Date(s.debut) < Date.now() - 15 * 60000) continue; // séance commencée depuis plus de 15 min
    if (!parJour.has(j)) parJour.set(j, []);
    parJour.get(j).push({ film, s });
  }
  const jours = [...parJour.keys()].sort().slice(0, 10);
  if (jours.length === 0) return carte("Cinéma Le Sévigné", el("p", { classe: "vide" }, "Pas de séance annoncée."), f);
  const libelle = (j) => j === isoJour() ? "Aujourd'hui" : j === isoJour(1) ? "Demain" : majuscule(fmt({ weekday: "long", day: "numeric" }).format(jourCalendaire(j)));
  return carte("Cinéma Le Sévigné", el("div", {},
    puces(jours.map((j) => [j, libelle(j)]), jours[0], (j) => el("ul", { classe: "liste" },
      parJour.get(j).sort((a, b) => new Date(a.s.debut) - new Date(b.s.debut)).map(({ film, s }) => el("li", { classe: "ligne film" },
        el("span", { classe: "chiffre" }, heure(s.debut)),
        film.fiche?.affiche && /^affiches\/\d+\.jpg$/.test(film.fiche.affiche)
          ? el("img", { classe: "affiche", src: DONNEES + film.fiche.affiche, alt: "", loading: "lazy", width: 48, height: 72 }) : null,
        el("div", { classe: "etire" }, el("strong", {}, film.titre),
          film.fiche && ligneFiche(film.fiche) && el("div", { classe: "doux petit" }, ligneFiche(film.fiche)),
          film.fiche?.resume && el("div", { classe: "resume" }, film.fiche.resume),
          film.fiche && detailFiche(film.fiche)),
        film.vo && pastille("VO", "neutre"), s.tarif && el("span", { classe: "doux petit" }, s.tarif))))),
    d.tarifs && el("p", { classe: "doux petit" }, majuscule(d.tarifs)),
    d.grille && lien(d.grille, "Grille du mois", "bouton")), f);
}

async function carteEquipements(garder = (e) => !["equipement:decheterie", "equipement:service_urbanisme", "equipement:police_municipale"].includes(e.lieu.id), titre = "Équipements") {
  const f = await module("equipements");
  if (!f) return absent(titre);
  const eq = f.donnees.equipements.filter(garder);
  return carte(titre, el("ul", { classe: "liste" }, eq.map((e) => el("li", {},
    el("strong", {}, e.lieu.nom),
    e.espaces.map((s) => el("div", { classe: "ligne" }, e.espaces.length > 1 && el("span", { classe: "etire doux" }, s.nom),
      el("span", { classe: e.espaces.length > 1 ? null : "etire" }, ...etiquetteOuverture(ouverture(s.jours))))),
    e.remarque && el("p", { classe: "doux petit" }, e.remarque),
    el("div", { classe: "pastilles" }, e.telephone && tel(e.telephone), e.lieu.url && lien(e.lieu.url, "Page officielle", "bouton")))),
  ), f);
}

// --- Familles -------------------------------------------------------------------------------------

async function carteMenus() {
  const f = await module("familles");
  const menus = (f?.donnees?.menus || []).filter((m) => m.jour >= isoJour()).slice(0, 5);
  if (!menus.length) return absent("Menus de la cantine", "Pas de menu publié pour les prochains jours.");
  const libelle = (j) => j === isoJour() ? "Aujourd'hui" : majuscule(fmt({ weekday: "short", day: "numeric" }).format(jourCalendaire(j)));
  const RUBRIQUES = [["hors_doeuvre", "Entrée"], ["plat", "Plat"], ["laitier", "Laitage"], ["dessert", "Dessert"], ["gouter", "Goûter"]];
  return carte("Menus de la cantine", el("div", {},
    puces(menus.map((m) => [m.jour, libelle(m.jour)]), menus[0].jour, (j) => {
      const m = menus.find((x) => x.jour === j);
      return el("dl", { classe: "detail" }, RUBRIQUES.filter(([k]) => m[k]?.length).map(([k, nom]) => [el("dt", {}, nom), el("dd", {}, m[k].join(", "))]));
    }),
    el("p", { classe: "doux petit" }, `Restaurants scolaires de ${f.donnees.restaurants}. Menu susceptible de changer selon les livraisons.`),
    f.donnees.menu_pdf && lien(f.donnees.menu_pdf, "Menu du mois (PDF)", "bouton")), f);
}

async function carteVacances() {
  const f = await module("vacances_scolaires");
  if (!f) return absent("Vacances scolaires");
  const p = f.donnees.periodes.filter((x) => x.reprise > isoJour()).sort((a, b) => a.du.localeCompare(b.du));
  const prochaine = p[0];
  if (!prochaine) return absent("Vacances scolaires");
  const enCours = prochaine.du <= isoJour();
  const semaines = Math.floor((jourCalendaire(prochaine.du) - jourCalendaire(isoJour())) / (7 * 86400000));
  const veille = new Date(jourCalendaire(prochaine.du) - 86400000);
  return carte("Vacances scolaires", el("div", {},
    pastille(enCours ? "En cours" : semaines <= 0 ? "Bientôt" : `Dans ${semaines} semaine${semaines > 1 ? "s" : ""}`, enCours ? "ouvert" : "neutre"),
    el("h3", {}, prochaine.nom),
    el("p", {}, `Dernier jour de classe ${FMT_JOUR.format(veille)}, reprise ${FMT_JOUR.format(jourCalendaire(prochaine.reprise))}.`),
    el("p", { classe: "doux petit" }, `${f.donnees.zone}, académie de ${f.donnees.academie}.`)), f);
}

async function carteInfosFamilles() {
  const f = await module("familles");
  const a = (f?.donnees?.actualites || []).filter((x) => !x.au || x.au >= isoJour()).slice(0, 6);
  if (!a.length) return absent("Infos du portail familles");
  return carte("Infos du portail familles", el("div", {}, el("ul", { classe: "liste" }, a.map((x) => el("li", {},
    lien(x.lien, x.titre), x.resume && el("div", { classe: "doux petit" }, x.resume))))), f);
}

/** Portail familles (inscriptions, réservations, factures) et pages utiles de la ville. */
function cartePortailFamilles() {
  const LIENS = [
    ["Démarches scolaires (inscriptions)", "https://www.ville-cesson-sevigne.fr/demarches-scolaires/"],
    ["Accueil de loisirs et périscolaire (2-11 ans)", "https://www.ville-cesson-sevigne.fr/accueil_enfant_2_11_ans/"],
    ["Stages pendant les vacances", "https://www.ville-cesson-sevigne.fr/stages-vacances-scolaires/"],
    ["Relais petite enfance et modes de garde", "https://www.ville-cesson-sevigne.fr/relais-petite-enfance/"],
  ];
  return carte("Portail familles", el("div", {},
    el("p", {}, "Inscriptions à la cantine, au périscolaire et à l'accueil de loisirs, réservations et factures : tout se fait sur le portail de la ville, avec votre compte."),
    lien("https://portail-familles.ville-cesson-sevigne.fr/", "Ouvrir le portail familles", "bouton plein"),
    el("ul", { classe: "liste" }, LIENS.map(([nom, url]) => el("li", {}, lien(url, nom))))));
}

// --- Pratique -------------------------------------------------------------------------------------

/**
 * Démarches (comme l'app) : carte d'identité et passeport, signalement, urbanisme, police municipale ; une rubrique
 * dépliée à la fois.
 */
async function carteDemarches() {
  const f = await module("equipements");
  const lieu = (id) => f?.donnees?.equipements?.find((e) => e.lieu.id === id);
  const accueil = (e) => e?.espaces?.[0] && el("p", {}, ...etiquetteOuverture(ouverture(e.espaces[0].jours)));
  const rubriques = [
    ["Carte d'identité et passeport", [
      el("p", {}, "1. Faites la pré-demande en ligne, gratuite, sur le seul site officiel (ANTS)."),
      el("p", {}, "2. Prenez rendez-vous à la mairie (Espace Citoyen) pour déposer le dossier et les empreintes."),
      el("div", { classe: "pastilles" },
        lien("https://www.ville-cesson-sevigne.fr/carte-nationale-didentite-cni-et-passeport/", "Prendre rendez-vous en mairie", "bouton plein"),
        lien("https://passeport.ants.gouv.fr/demarches-en-ligne", "Pré-demande sur ants.gouv.fr", "bouton")),
      el("p", { classe: "doux petit" }, "Les mineurs viennent au rendez-vous ; dès 12 ans, aussi au retrait. Délais plus longs avant l'été. Méfiez-vous des sites payants : la démarche est gratuite."),
    ]],
    ["Faire un signalement", [
      el("h3", {}, "À la ville"),
      el("p", { classe: "petit" }, "Parcs, espaces verts et aires de jeux, élagage, chemins piétons, bâtiments publics, plaques de rue, propreté."),
      lien("https://www.ville-cesson-sevigne.fr/faire-un-signalement/", "Signaler à la ville", "bouton plein"),
      el("h3", {}, "À Rennes Métropole"),
      el("p", { classe: "petit" }, "Voirie (trou, barrière, ralentisseur), éclairage public, eau et assainissement, déchets."),
      el("div", { classe: "pastilles" },
        lien("https://demarches.rennes.fr/RENNES.Guichet/workflow_url?ECITIZ_ACTIVITY_PATH=Citoyen&IDCOLLECTIVITE=RENNES&TS=SVE&TASK=CREATE_SVE&THEMATIQUE=SIGNAL", "Formulaire de la Métropole", "bouton"),
        tel("02 23 62 10 10")),
      el("p", { classe: "doux petit" }, "Par téléphone : 8h30-17h30 en semaine, 9h-12h30 le samedi. Aussi par l'application « Ici Rennes »."),
    ]],
    ["Urbanisme", [
      el("p", {}, "Permis de construire, déclaration préalable : les demandes se déposent en ligne, sur le service de la ville."),
      lien("https://urbanisme.ville-cesson-sevigne.fr/NetADS/sve/CESSON35051", "Déposer une demande en ligne", "bouton plein"),
      el("ul", { classe: "liste" }, [
        ["Déclaration préalable", "Petits travaux (extension modeste, façade, abri…). À Cesson, aussi pour toute clôture."],
        ["Permis de construire", "Construction neuve ou agrandissement important."],
        ["Permis d'aménager", "Lotissement, aménagement de terrain."],
        ["Permis de démolir", "À Cesson, obligatoire pour démolir toute construction."],
      ].map(([nom, quand]) => el("li", {}, el("strong", {}, nom), el("div", { classe: "doux petit" }, quand)))),
      el("details", {}, el("summary", {}, "À savoir"), el("ul", { classe: "liste" }, [
        "L'arrêté s'affiche sur le terrain dès sa réception et pendant tous les travaux ; le délai de recours est de deux mois à partir de cet affichage.",
        "Une autorisation vaut 3 ans pour commencer les travaux, prorogeable deux fois un an ; des travaux arrêtés plus d'un an la rendent caduque.",
        "À renvoyer en mairie : la déclaration d'ouverture de chantier (permis de construire) et l'attestation d'achèvement et de conformité (DAACT).",
      ].map((t) => el("li", { classe: "petit" }, t)))),
      el("h3", {}, "Service urbanisme"),
      el("p", { classe: "petit" }, "Mairie annexe, 1 place de la Chalotais. Dépôt papier possible aux heures d'ouverture."),
      accueil(lieu("equipement:service_urbanisme")),
      el("div", { classe: "pastilles" }, tel("02 99 83 52 00"), lien("https://www.ville-cesson-sevigne.fr/les-autorisations-durbanisme/", "Page de la ville", "bouton")),
    ]],
    ["Police municipale", [
      el("p", {}, el("strong", {}, "En cas d'urgence, appelez le 17 (police) ou le 112.")),
      el("div", { classe: "pastilles" }, tel("17"), tel("112")),
      el("h3", {}, "Hôtel des Polices"),
      el("p", { classe: "petit" }, "6 mail de Bourgchevreuil. Police municipale et police nationale au même endroit."),
      accueil(lieu("equipement:police_municipale")),
      el("p", { classe: "doux petit" }, "Du lundi au vendredi de 9h à 18h ; le samedi de 7h à 12h, police municipale seule."),
      el("div", { classe: "pastilles" }, tel("02 99 83 52 14"), tel("06 09 35 73 92"),
        lien("mailto:police.municipale@ville-cesson-sevigne.fr", "Courriel", "bouton")),
      el("p", { classe: "petit" }, "Police nationale : ", tel("02 57 87 10 05", "02 57 87 10 05", null)),
      el("div", { classe: "pastilles" },
        lien("https://www.ville-cesson-sevigne.fr/operation-tranquilite-vacances/", "Surveillance pendant vos vacances", "bouton"),
        lien("https://www.ville-cesson-sevigne.fr/contacts/lhotel-des-polices-police-municipale-et-police-nationale/", "Page de la ville", "bouton")),
    ]],
  ];
  const blocs = rubriques.map(([titre, contenu]) => el("details", { classe: "rubrique", name: "demarches" }, el("summary", {}, titre), el("div", {}, contenu)));
  // Une rubrique ouverte à la fois, aussi dans les navigateurs qui ignorent l'attribut name des <details>.
  for (const b of blocs) b.addEventListener("toggle", () => { if (b.open) for (const x of blocs) if (x !== b) x.open = false; });
  return carte("Démarches", el("div", {}, blocs), f);
}

async function carteCollecte() {
  const f = await module("collecte");
  if (!f) return absent("Ma collecte de déchets");
  const d = f.donnees;
  const flux = new Map(d.flux.map((x) => [x.id, x]));
  const prochains = d.flux.map((x) => d.passages.filter((p) => p.flux === x.id && p.date >= isoJour()).sort((a, b) => a.date.localeCompare(b.date))[0])
    .filter(Boolean).sort((a, b) => a.date.localeCompare(b.date));
  const avis = (d.avis || []).filter((a) => a.au >= isoJour());
  const quand = (j) => j === isoJour() ? "Aujourd'hui" : j === isoJour(1) ? "Demain" : majuscule(fmt({ weekday: "long", day: "numeric", month: "short" }).format(jourCalendaire(j)));
  return carte("Ma collecte de déchets", el("div", {},
    el("ul", { classe: "liste" }, prochains.map((p) => { const x = flux.get(p.flux); return el("li", { classe: "ligne" },
      el("span", { classe: "etire" }, `Bac ${x.bac} · ${x.nom}`, el("br"), el("span", { classe: "doux petit" }, majuscule(x.regle))),
      el("strong", {}, quand(p.date))); })),
    avis.map((a) => el("p", {}, pastille("Avis", "jaune"), " ", a.texte)),
    d.conseil && el("p", { classe: "doux petit" }, d.conseil),
    el("p", { classe: "doux petit" }, `${d.public}.`),
    d.url && lien(d.url, "Page de la ville", "bouton")), f);
}

async function carteDechetterie() {
  const [eq, dech] = await Promise.all([module("equipements"), module("decheterie")]);
  const e = eq?.donnees?.equipements?.find((x) => x.lieu.id === "equipement:decheterie");
  if (!e) return absent("Déchetterie");
  const d = dech?.donnees;
  return carte("Déchetterie", el("div", {},
    el("p", {}, ...etiquetteOuverture(ouverture(e.espaces[0]?.jours))),
    el("p", { classe: "doux petit" }, e.lieu.adresse),
    e.remarque && el("p", { classe: "doux petit" }, e.remarque),
    d && [el("h3", {}, "Déchets acceptés"), el("div", { classe: "pastilles" }, d.acceptes.map((x) => pastille(x, "neutre"))),
      d.refuses.length > 0 && el("p", {}, `Non acceptés : ${d.refuses.join(", ")}.`),
      d.reemploi && el("p", {}, "Espace réemploi : déposez-y les objets encore en bon état."),
      el("p", { classe: "doux petit" }, "Déchets acceptés : Rennes Métropole (ODbL).")],
    el("div", { classe: "pastilles" }, e.telephone && tel(e.telephone))), eq);
}

async function carteEau() {
  const f = await module("usages_eau");
  if (!f) return absent("Usage de l'eau");
  const d = f.donnees;
  const ton = { vigilance: "jaune", alerte: "orange", alerte_renforcee: "orange", crise: "rouge" }[d.niveau] || "ouvert";
  const libelle = { vigilance: "Vigilance sécheresse", alerte: "Alerte sécheresse", alerte_renforcee: "Alerte renforcée", crise: "Crise sécheresse" }[d.niveau] || "Pas de restriction";
  return carte("Usage de l'eau", el("div", {},
    el("p", {}, pastille(libelle, ton), d.depuis ? ` depuis le ${fmt({ day: "numeric", month: "long" }).format(new Date(d.depuis))}` : ""),
    (d.usages || []).length > 0 && el("details", {}, el("summary", {}, `${d.usages.length} usages limités ou interdits`),
      el("ul", { classe: "liste" }, d.usages.map((u) => el("li", {}, el("strong", {}, u.nom), el("div", { classe: "petit" }, u.regle))))),
    d.arrete && lien(d.arrete, "Arrêté préfectoral", "bouton"),
    lien("https://vigieau.gouv.fr/", "VigiEau", "bouton")), f);
}

async function cartePharmacies() {
  const f = await module("pharmacies");
  if (!f) return absent("Pharmacies");
  const liste = f.donnees.pharmacies.map((p) => ({ p, o: ouverture(p.jours) }))
    .sort((a, b) => (b.o.ouvert - a.o.ouvert) || ((a.o.prochaine || Infinity) - (b.o.prochaine || Infinity)));
  const ouvertes = liste.filter((x) => x.o.ouvert).length;
  return carte("Pharmacies", el("div", {},
    el("p", {}, ouvertes === 0 ? "Aucune pharmacie de Cesson n'est ouverte en ce moment."
      : `${ouvertes} pharmacie${ouvertes > 1 ? "s" : ""} de Cesson ouverte${ouvertes > 1 ? "s" : ""} en ce moment.`),
    ouvertes === 0 && el("p", {}, "Pharmacie de garde : ", lien("https://pharmacie-de-garde.ameli.fr/", "ameli.fr"), " ; la nuit, le ", tel("3237", "3237", null), "."),
    el("ul", { classe: "liste" }, liste.map(({ p, o }) => el("li", {},
      el("strong", {}, p.lieu.nom), el("div", {}, ...etiquetteOuverture(o)),
      el("div", { classe: "doux petit" }, p.lieu.adresse || ""), p.telephone && tel(p.telephone))))), f);
}

function carteUrgences() {
  return carte("Urgences", el("div", {},
    el("p", {}, el("strong", {}, "Urgence vitale : "), tel("15", "15 (SAMU)"), " ", tel("112", "112"), " ", tel("18", "18 (pompiers)"), " ", tel("17", "17 (police)")),
    el("p", {}, "Médecin de garde, le soir et le week-end : ", tel("116117", "116 117")),
    el("h3", {}, "Urgences de l'hôpital privé"),
    el("p", {}, "Ouvertes 24 h/24, 7 j/7 · 3 rue du Chêne Germain, Cesson-Sévigné."),
    el("p", {}, "De 18 h 30 à 8 h, appelez le 15 avant de vous déplacer : c'est obligatoire dans la métropole rennaise."),
    lien("https://www.vivalto-sante.fr/hopitaux-prives-rennais-cesson-sevigne/urgences", "Site de l'hôpital", "bouton")),
  null, { source: "Numéros nationaux d'urgence ; hôpital privé Cesson-Sévigné" });
}

// Accessibilité des lieux (acceslibre) : recherche et détail dépliable, comme dans l'app.
const FAMILLES_LIEUX = [["", "Tous"], ["services_publics", "Services publics"], ["sante", "Santé"], ["loisirs", "Culture et sport"],
  ["restauration", "Restaurants et hôtels"], ["commerces", "Commerces et services"]];

function pastillesAcces(a) {
  const r = [];
  if (a.entree_plain_pied === true) r.push(pastille("Entrée de plain-pied", "ouvert"));
  else if (a.entree_plain_pied === false && (a.entree_rampe || a.entree_ascenseur)) r.push(pastille(a.entree_rampe ? "Rampe à l'entrée" : "Ascenseur à l'entrée", "info"));
  else if (a.entree_plain_pied === false) r.push(pastille(a.entree_marches > 0 ? `${a.entree_marches} marche${a.entree_marches > 1 ? "s" : ""} à l'entrée` : "Marches à l'entrée", "neutre"));
  if (a.stationnement_pmr === true) r.push(pastille("Place réservée sur place", "ouvert"));
  else if (a.stationnement_pmr_proche === true) r.push(pastille("Place réservée à proximité", "info"));
  if (a.sanitaires && a.sanitaires_adaptes) r.push(pastille("Sanitaires adaptés", "ouvert"));
  if ((a.malentendants || []).length) r.push(pastille("Équipement malentendants", "info"));
  if (!r.length) r.push(pastille("Peu renseigné", "neutre"));
  return r;
}

function detailsAcces(e) {
  const a = e.acces, d = [];
  const stat = a.stationnement_pmr ? "Place réservée dans l'établissement." : a.stationnement_pmr_proche ? "Place réservée à moins de 200 m."
    : a.stationnement_pmr === false && a.stationnement_pmr_proche === false ? "Pas de place réservée à proximité." : null;
  if (stat) d.push(["Stationnement", stat]);
  if (a.arret_transport) d.push(["Transports", "Arrêt de bus ou de métro à moins de 200 m."]);
  if (a.chemin_plain_pied != null) d.push(["Depuis la rue", a.chemin_plain_pied ? "Chemin de plain-pied." : "Marches, pente ou obstacle sur le chemin."]);
  const entree = [a.entree_plain_pied === true ? "de plain-pied" : a.entree_plain_pied === false
    ? [a.entree_marches > 0 ? `${a.entree_marches} marche${a.entree_marches > 1 ? "s" : ""}` : "marches ou ressaut", a.entree_rampe && `rampe ${a.entree_rampe}`, a.entree_ascenseur && "ascenseur"].filter(Boolean).join(", ") : null,
  a.entree_largeur_cm && `porte d'au moins ${a.entree_largeur_cm} cm`, a.entree_appel && "sonnette ou aide à l'entrée"].filter(Boolean);
  if (entree.length) d.push(["Entrée", majuscule(entree.join(", ")) + "."]);
  const accueil = [a.accueil_plain_pied != null && (a.accueil_plain_pied ? "De plain-pied jusqu'à l'accueil" : "Marches ou obstacle jusqu'à l'accueil"),
    a.personnel_forme != null && (a.personnel_forme ? "personnel formé à l'accueil des personnes handicapées" : "personnel non formé à l'accueil des personnes handicapées")].filter(Boolean);
  if (accueil.length) d.push(["Accueil", majuscule(accueil.join(", ")) + "."]);
  if ((a.malentendants || []).length) d.push(["Malentendants", majuscule(a.malentendants.join(", ")) + "."]);
  if (a.sanitaires != null) d.push(["Sanitaires", a.sanitaires ? (a.sanitaires_adaptes ? "Adaptés." : a.sanitaires_adaptes === false ? "Non adaptés." : "Adaptation non renseignée.") : "Pas de sanitaires."]);
  if (e.commentaire) d.push(["Commentaire", `« ${e.commentaire} »`]);
  if (e.mis_a_jour) d.push(["Fiche", `Mise à jour le ${fmt({ day: "numeric", month: "long", year: "numeric" }).format(jourCalendaire(e.mis_a_jour))}.`]);
  return d;
}

async function carteAccessibilite() {
  const f = await module("accessibilite");
  if (!f) return absent("Accessibilité des lieux");
  const tous = f.donnees.etablissements;
  const champ = el("input", { type: "search", placeholder: `Chercher parmi ${tous.length} lieux (mairie, piscine…)`, "aria-label": "Chercher un lieu" });
  let famille = "", tout = false;
  const resultats = el("div", { "aria-live": "polite" });
  const afficher = () => {
    const mots = sansAccents(champ.value).split(/[\s,'’-]+/).filter(Boolean);
    const liste = tous.filter((e) => (!famille || e.famille === famille) &&
      mots.every((m) => sansAccents([e.lieu.nom, e.activite, e.lieu.adresse].join(" ")).includes(m)));
    const vus = tout ? liste : liste.slice(0, 6);
    resultats.replaceChildren(
      liste.length === 0 ? el("p", { classe: "vide" }, "Aucun lieu trouvé.") : el("ul", { classe: "liste" }, vus.map((e) => el("li", {},
        el("details", {}, el("summary", {}, el("strong", {}, e.lieu.nom), el("div", { classe: "doux petit" }, [e.activite, e.lieu.adresse].filter(Boolean).join(" · ")),
          el("div", { classe: "pastilles" }, pastillesAcces(e.acces))),
        (() => { const d = detailsAcces(e); return d.some(([k]) => k !== "Fiche") ? el("dl", { classe: "detail" }, d.map(([k, v]) => [el("dt", {}, k), el("dd", {}, v)]))
          : el("p", {}, "La fiche ne décrit pas encore l'accessibilité de ce lieu."); })(),
        e.lieu.url && lien(e.lieu.url, "Fiche acceslibre (à compléter si besoin)", "bouton"))))),
      liste.length > 6 && el("button", { type: "button", classe: "bouton", onclick: () => { tout = !tout; afficher(); } }, tout ? "Afficher moins" : `Tous les lieux (${liste.length})`));
  };
  champ.addEventListener("input", () => { tout = false; afficher(); });
  const filtres = puces(FAMILLES_LIEUX, "", (k) => { famille = k; tout = false; afficher(); return document.createTextNode(""); });
  afficher();
  return carte("Accessibilité des lieux", el("div", {},
    el("p", {}, "Entrée, stationnement, accueil, sanitaires : ce que disent les fiches acceslibre, remplies par les établissements et des contributeurs."),
    champ, filtres, resultats), f, { classe: "large" });
}

async function carteDefibrillateurs() {
  const f = await module("defibrillateurs");
  if (!f) return absent("Défibrillateurs");
  const liste = f.donnees.defibrillateurs;
  const resultats = el("div");
  const afficher = (position) => {
    const avecDist = liste.map((d) => ({ d, m: position && d.lieu.coord ? distance(position, d.lieu.coord) : null }));
    if (position) avecDist.sort((a, b) => (a.m ?? Infinity) - (b.m ?? Infinity));
    resultats.replaceChildren(el("ul", { classe: "liste" }, avecDist.slice(0, 6).map(({ d, m }) => el("li", {},
      el("div", { classe: "ligne" }, el("strong", { classe: "etire" }, d.lieu.nom), m != null && el("span", { classe: "chiffre" }, m < 1000 ? `${Math.round(m / 10) * 10} m` : `${(m / 1000).toLocaleString("fr-FR", { maximumFractionDigits: 1 })} km`)),
      el("div", { classe: "doux petit" }, d.lieu.adresse || ""),
      pastille(d.exterieur && d.libre_acces ? "Dehors, accès libre" : d.libre_acces ? "Accès libre aux heures du lieu" : "Accès restreint",
        d.exterieur && d.libre_acces ? "ouvert" : d.libre_acces ? "info" : "neutre")))));
  };
  const bouton = el("button", { type: "button", classe: "bouton", onclick: () => {
    if (!navigator.geolocation) return;
    bouton.textContent = "Recherche de votre position…";
    navigator.geolocation.getCurrentPosition((p) => { bouton.remove(); afficher({ lat: p.coords.latitude, lon: p.coords.longitude }); },
      () => { bouton.textContent = "Position indisponible"; }, { maximumAge: 600000, timeout: 15000 });
  } }, "Les plus proches de moi");
  afficher(null);
  return carte("Défibrillateurs", el("div", {},
    el("p", {}, el("strong", {}, "Arrêt cardiaque : "), "appelez le 15 (ou le 112), massez, et faites apporter un défibrillateur."),
    tel("15", "Appeler le 15"), " ", bouton, el("p", { classe: "doux petit" }, "Votre position reste dans votre navigateur : elle ne sert qu'à trier la liste."),
    resultats), f);
}
function distance(a, b) {
  return Math.hypot((b.lon - a.lon) * 111320 * Math.cos(a.lat * Math.PI / 180), (b.lat - a.lat) * 110540);
}

// --- À propos -------------------------------------------------------------------------------------

async function carteSources() {
  const m = await fetch(`${DONNEES}manifest.json`, { cache: "no-cache" }).then((r) => r.json()).catch(() => null);
  const noms = Object.keys(m?.modules || {}).sort();
  const fichiers = (await Promise.all(noms.map(module))).filter(Boolean);
  const sources = [...new Map(fichiers.map((f) => [f.meta.source, f.meta])).values()];
  return carte("Sources et licences", el("div", {},
    el("p", {}, "Toutes les données proviennent de sources publiques. Chacune est citée avec sa licence."),
    el("ul", { classe: "liste" }, sources.map((s) => el("li", {}, s.url_source ? lien(s.url_source, s.source) : s.source,
      el("div", { classe: "doux petit" }, `Licence : ${s.licence} · mis à jour ${depuis(s.recupere_le)}`)))),
    el("p", { classe: "doux petit" }, "Prochains passages du métro et des bus, parc relais : STAR (Rennes Métropole, Keolis Rennes), temps réel, ODbL. " +
      "Qualité de l'air et pollens : source Atmo France / AASQA."),
    el("img", { classe: "logo-tmdb", src: "icones/tmdb.svg", alt: "Logo de The Movie Database (TMDB)", width: 96, height: 12 }),
    el("p", { classe: "doux petit" }, "Fiches des films : The Movie Database (TMDB), liens Wikipédia et Allociné par Wikidata (CC0). " +
      "This product uses the TMDB API but is not endorsed or certified by TMDB. Votre navigateur ne contacte jamais TMDB : " +
      "le serveur de l'app récupère fiches et affiches. Vignettes des spectacles : OpenAgenda (Ville de Cesson-Sévigné), " +
      "avec le crédit de chaque photo, récupérées de la même façon.")), null, { classe: "large" });
}

function carteAPropos() {
  return carte("À propos", el("div", {},
    el("p", {}, el("strong", {}, "Cesson'App"), ", version web : l'information pratique de Cesson-Sévigné, à partir de données publiques. Éditée par Yogi Studio."),
    el("p", {}, "Aucun compte, aucune publicité, aucune donnée collectée. Le site ne dépose aucun cookie ; votre navigateur télécharge les mêmes fichiers publics que tout le monde. Vos choix de « Mon trajet » restent dans votre navigateur, sur cet appareil : ils ne nous sont jamais envoyés."),
    el("p", {}, "Application non officielle : elle n'est éditée ni par la Ville de Cesson-Sévigné ni par Rennes Métropole. Elle ne remplace ni les canaux officiels de la ville, ni Vigicrues, ni FR-Alert."),
    el("p", {}, "Sur Android, l'application ajoute les rappels de collecte, les alertes (bus, pollens, vigilance) et le widget."),
    el("div", { classe: "pastilles" }, lien("https://www.ville-cesson-sevigne.fr/", "Ville de Cesson-Sévigné", "bouton"), lien("https://www.vigicrues.gouv.fr/", "Vigicrues", "bouton"),
      lien("https://fr-alert.gouv.fr/", "FR-Alert", "bouton"), lien("https://www.star.fr/", "STAR", "bouton")),
    el("p", {}, "Une idée, une erreur ? ", lien("mailto:yogistudiofr@gmail.com", "yogistudiofr@gmail.com"))), null, { classe: "large" });
}

// --- Onglets --------------------------------------------------------------------------------------

const ONGLETS = {
  // Mêmes cartes, dans le même ordre, que les onglets de l'app (android/app/…/ui/Accueil.kt).
  aujourdhui: { titre: "Aujourd'hui", cartes: [carteMeteo, carteMonTrajet, () => carteTrains(false), () => carteCirculation(false), carteAir, () => carteActus()] },
  bouger: { titre: "Bouger", cartes: [carteDeparts, carteParcRelais, carteTrains, carteMonTrajet, carteCirculation, carteCarburants, carteBornes] },
  sortir: { titre: "Sortir", cartes: [carteSaison, carteAgenda, carteHandball, carteCinema, () => carteEquipements()] },
  familles: { titre: "Familles", cartes: [carteMenus, carteVacances, carteInfosFamilles, cartePortailFamilles] },
  environnement: { titre: "Environnement", cartes: [carteAir, cartePollens, carteEpisodes, carteVilaine, carteEau, carteCollecte, carteDechetterie] },
  pratique: { titre: "Pratique", cartes: [carteDemarches, cartePharmacies, carteUrgences, carteDefibrillateurs, carteAccessibilite] },
  apropos: { titre: "À propos", cartes: [carteAPropos, carteSources] },
};

let rendu = 0;
async function afficher() {
  const id = (location.hash.slice(1) in ONGLETS) ? location.hash.slice(1) : "aujourdhui";
  const onglet = ONGLETS[id];
  document.title = id === "aujourdhui" ? "Cesson'App" : `${onglet.titre} · Cesson'App`;
  for (const a of document.querySelectorAll(".onglets a")) {
    if (a.dataset.onglet === id) { a.setAttribute("aria-current", "page"); a.scrollIntoView({ block: "nearest", inline: "center" }); }
    else a.removeAttribute("aria-current");
  }
  const main = document.getElementById("contenu");
  const numero = ++rendu;
  main.replaceChildren(el("p", { classe: "doux" }, "Chargement…"));
  const cartes = [...(id === "aujourdhui" ? await bandeaux() : []),
    ...await Promise.all(onglet.cartes.map((f) => Promise.resolve().then(f).catch((e) => absent("Carte indisponible", e.message))))];
  if (numero !== rendu) return; // un autre onglet a été choisi entre-temps
  main.replaceChildren(...cartes);
}

window.addEventListener("hashchange", () => { afficher(); window.scrollTo(0, 0); });
document.getElementById("actualiser").addEventListener("click", async (ev) => {
  const b = ev.currentTarget;
  b.classList.add("tourne");
  cache.clear();
  await afficher();
  setTimeout(() => b.classList.remove("tourne"), 400);
});
// Les horaires temps réel vieillissent vite : on recharge en revenant sur l'onglet après plus de 2 minutes.
let quitte = 0;
document.addEventListener("visibilitychange", () => {
  if (document.hidden) quitte = Date.now();
  else if (quitte && Date.now() - quitte > 120000) { cache.clear(); afficher(); }
});
afficher();
if ("serviceWorker" in navigator) navigator.serviceWorker.register("sw.js").catch(() => {});

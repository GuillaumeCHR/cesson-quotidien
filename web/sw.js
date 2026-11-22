// Service worker de Cesson'App (web) : l'appli s'ouvre hors connexion, avec les dernières données reçues.
// Fichiers de l'appli : cache d'abord. Données du serveur : réseau d'abord, cache en secours. STAR (temps réel) : jamais en cache.
const VERSION = "cessonapp-web-14";
const APPLI = ["./", "index.html", "app.css", "app.js", "carte-cesson.js", "carte-rennes.js", "config.js", "manifest.webmanifest", "icones/icone.svg", "icones/icone-192.png", "icones/icone-512.png", "icones/tmdb.svg"];

self.addEventListener("install", (e) => {
  e.waitUntil(caches.open(VERSION).then((c) => c.addAll(APPLI)).then(() => self.skipWaiting()));
});
self.addEventListener("activate", (e) => {
  e.waitUntil(caches.keys().then((cles) => Promise.all(cles.filter((k) => k !== VERSION).map((k) => caches.delete(k))))
    .then(() => self.clients.claim()));
});
self.addEventListener("fetch", (e) => {
  const url = new URL(e.request.url);
  if (e.request.method !== "GET" || url.hostname === "data.explore.star.fr") return;
  if (url.hostname === "cesson-pi.tail7e6f0d.ts.net") {
    e.respondWith(fetch(e.request).then((r) => {
      if (r.ok) { const copie = r.clone(); caches.open(VERSION).then((c) => c.put(url.href, copie)); }
      return r;
    }).catch(() => caches.match(url.href)));
    return;
  }
  if (url.origin === self.location.origin) {
    e.respondWith(caches.match(e.request, { ignoreSearch: true }).then((r) => r || fetch(e.request)));
  }
});

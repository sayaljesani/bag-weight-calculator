/* Offline support: the app is four small files, so cache them all up front and
   serve from the cache first. Bump CACHE when the app changes — the new worker
   deletes the old cache and takes over straight away. */

const CACHE = "bag-weight-v3";

const FILES = [
  "./",
  "./index.html",
  "./manifest.webmanifest",
  "./icon-192.png",
  "./icon-512.png",
  "./icon-512-maskable.png",
  "./apple-touch-icon.png"
];

self.addEventListener("install", event => {
  event.waitUntil(
    caches.open(CACHE)
      .then(cache => cache.addAll(FILES))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener("activate", event => {
  event.waitUntil(
    caches.keys()
      .then(names => Promise.all(names.filter(n => n !== CACHE).map(n => caches.delete(n))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", event => {
  if (event.request.method !== "GET") return;

  event.respondWith(
    caches.match(event.request).then(hit => {
      if (hit) {
        // refresh the copy in the background so the next launch is up to date
        fetch(event.request)
          .then(res => {
            if (res && res.ok) caches.open(CACHE).then(c => c.put(event.request, res.clone()));
          })
          .catch(() => { /* offline: the cached copy is the answer */ });
        return hit;
      }
      return fetch(event.request).catch(() => caches.match("./index.html"));
    })
  );
});

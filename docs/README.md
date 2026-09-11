# The web app (iPhone and Android)

This folder is published by GitHub Pages at

**https://sayaljesani.github.io/bag-weight-calculator/**

It is the same app as the Android one — same screens, same calculations, same
weighment sheet with the logo — written as a web page so it runs on iPhone,
where Apple does not allow installing an app from a downloaded file.

## Turning it on (once)

Repository ▸ **Settings** ▸ **Pages** ▸ under *Build and deployment* set
**Source: Deploy from a branch**, **Branch: main**, **folder: /docs**, then
**Save**. The site is live a minute later at the address above.

## Installing it on a phone

**iPhone** — open the address in **Safari** (it has to be Safari), tap the
Share button, then **Add to Home Screen**. The icon opens the app full screen
with no browser bars, and it works with no internet.

**Android** — open it in Chrome and use **Install app** / **Add to Home
screen** from the ⋮ menu. Either that or the APK; they behave the same.

## What is in here

| file | |
|---|---|
| `index.html` | the whole app — screens, calculations and the .xlsx writer |
| `manifest.webmanifest` | name, colours and icons, so the phone can install it |
| `sw.js` | the service worker; caches the app so it opens offline |
| `icon-*.png`, `apple-touch-icon.png` | home-screen icons, made from the logo |

## Updating it

Edit `index.html`, bump `CACHE` in `sw.js` (e.g. `bag-weight-v4`) and push.
Phones pick the new version up the next time they open the app with a
connection. Without the `CACHE` bump they keep serving the old cached copy.

## Worth knowing

* Loads are stored on the phone itself, per browser. Nothing is uploaded — the
  page is static and there is no server behind it.
* Because of that, **Past loads ▸ Backup all** is the only safety net. iOS
  clears storage for web apps left unused for a long stretch, so take a backup
  after any load you would hate to lose.
* The .xlsx is generated on the phone. On iPhone use **Share** to put it into
  Files, WhatsApp or mail; **Save to Files** does the same through the
  download route.

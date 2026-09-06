# Bag Weight Calculator — Android app

Enter the load details once, weigh each bag, and the app writes a weighment
workbook in exactly the layout of `RS_002_03.04.2026_385_Bags.xlsx` — savable
to Downloads and shareable through WhatsApp / Gmail / Drive.

## What it does

**Screen 1 — Load details (entered once)**
Sr. No · Count · Lot No · Vehicle No · No of Bags · Tare weight per bag · Date.
The Sr. No is filled in automatically — one more than the highest used in the
current year, so numbering restarts at 1 each January — and can be overwritten.

**Screen 2 — Weighing**
One gross weight per bag. Net (`gross − tare`) is shown live as you type and
again in the list. Tap any row to correct or delete it, or use *Undo last*.
Running totals (weighed / gross / tare / net) sit at the bottom. Every entry is
written to the phone immediately, so a dead battery mid-load costs nothing —
reopen the app and it offers to resume.

**Screen 3 — Summary**
Total gross, total tare, total net, then *Save to Downloads* and *Share*, plus
*Continue editing*, *Duplicate* (same details, fresh weights) and *Delete*.

**Past loads**
Every load stays on the phone. The list shows each one newest first with its
date, bag count, tare and net, and whether it is finished. Tap a load to open
its summary — from there you can re-download or re-share the .xlsx, edit any bag
weight and regenerate it, duplicate it, or delete it. ✕ on a row deletes it
outright.

*Backup all* writes one JSON file holding every load to Downloads and offers to
send it (WhatsApp, Drive, email); *Restore* reads such a file back on a new
phone. Restoring merges by load: loads the phone has not seen are added, and a
load that exists on both is replaced only if the backup's copy is newer, so
restoring twice is harmless.

## The generated sheet

Every sheet opens with the RAMDHAN spintex logo, the title, and the load
details spelled out — `SR NO : 1`, `COUNT : 30`, `LOT NO : 900`,
`VEHICLE NO : …`, `TOTAL BAGS : 385`, `TARE / BAG : 1.40`.

One worksheet per 100 bags (`1-100`, `101-200`, …). Each sheet holds two blocks
of 50 bags under a `NO | GROSS | NET` header, laid out as five column groups of
ten rows:

```
A bag-no | B gross | C net      D | E | F      G | H | I      J | K | L      M | N | O
```

* Bag numbers run through the whole load (301…400 on the fourth sheet), so a
  number on the sheet is the number on the bag.
* Row 16 and row 29 carry `SUM()` subtotals for every group; a group nobody
  weighed stays blank rather than showing 0.00.
* Columns Q–U hold the summary panel: the ten group subtotals, the sheet total,
  then **GROSS WEIGHT** (cumulative across sheets), **TARE** (bags × tare) and
  **NET WEIGHT**, with a small "net from subtotals" cross-check underneath.
* Gridlines are off and the page is set to print landscape, one sheet per page.

The logo lives at `app/src/main/res/raw/logo.png` — replace that file to change
it; anything with roughly 3.4 : 1 proportions drops straight in.
* Net cells are live formulas (`=+B4-1.4`) with the value cached, so the numbers
  show up in Excel, Google Sheets, WPS and any phone viewer, and still recalc if
  someone edits a gross weight.
* File name: `SrNo_dd.MM.yyyy_N_Bags.xlsx` — e.g. `1_06.09.2026_4_Bags.xlsx`.

The workbook is written by `xlsx/XlsxWriter.java` + `xlsx/BagSheetGenerator.java`,
which use nothing but `java.util.zip` — no Apache POI, no third-party library,
nothing to keep up to date.

## Building the APK

**No computer? Build it on GitHub** — upload the project to a free GitHub
repository and its build machines produce the APK for you; see
[`GITHUB_BUILD.md`](GITHUB_BUILD.md). The workflow lives in
`.github/workflows/build-apk.yml` and publishes the APK as a Release you can
download and install straight from the phone.

**On your own machine**, requires Android Studio (Ladybug or newer) or the
Android SDK.

```bash
# in Android Studio: File ▸ Open ▸ this folder, then Run ▸ Run 'app'
# or from a terminal:
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug           # build and push to a connected phone
```

Gradle downloads the Android Gradle Plugin and the SDK bits on first build, so
the machine needs internet once.

To sideload the debug APK: copy `app-debug.apk` to the phone, tap it, and allow
"Install unknown apps" for the file manager when prompted.

For a Play Store build, create a signing key and run `./gradlew assembleRelease`.

### Versions used

| | |
|---|---|
| Gradle wrapper | 8.14.3 |
| Android Gradle Plugin | 8.10.1 |
| compileSdk / targetSdk | 35 |
| minSdk | 24 (Android 7.0) |
| Language | Java 17 |
| Dependencies | androidx appcompat, core, recyclerview |

If your Android Studio ships an older AGP, change the version in `build.gradle`
and let Studio sync — nothing in the code depends on a specific AGP release.

## Project layout

```
app/src/main/java/com/toddle/bagweigher/
  MainActivity.java        load details, resume/discard
  EntryActivity.java       bag-by-bag weight entry
  SummaryActivity.java     totals, save, share, duplicate, delete
  HistoryActivity.java     past loads, backup and restore
  BagAdapter.java          the entered-bags list
  LoadAdapter.java         the past-loads list
  WeighSession.java        one load: details + every gross weight
  LoadStore.java           every load, in one JSON file on the phone
  FileExporter.java        MediaStore save + FileProvider share
  xlsx/XlsxWriter.java     minimal .xlsx writer (zip + OOXML)
  xlsx/BagSheetGenerator.java  the weighment layout
app/src/main/res/          layouts, strings, colours, launcher icons
app/src/main/res/raw/logo.png  the logo printed on every sheet
```

## Verification

`BagSheetGenerator` was run against the 385 gross weights from the reference
workbook. The output matches it cell for cell — same sheet names, same formulas,
same subtotals — and LibreOffice recalculates every formula to the same numbers:

| | |
|---|---|
| Bags | 385 |
| Total gross | 16136.613 |
| Total tare (385 × 1.4) | 539.00 |
| Total net | 15597.613 |

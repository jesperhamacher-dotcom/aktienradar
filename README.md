# Aktienradar – Android-App (MVP)

**Radar:** durchsucht alle bei der SEC meldenden Unternehmen (mehrere tausend) und zeigt potenzielle Aktien,
bei denen sich Umsatzwachstum und Margen gerade verbessern. Wöchentlicher Hintergrund-Scan mit Benachrichtigung bei neuen Kandidaten.

**Watchlist:** Trendanalyse über bis zu 16 Quartale, Potential-Score mit Begründung,
Überwachung deiner Investment-These und Benachrichtigungen bei Veränderungen.

Download der fertigen APK: Releases → „aktuell“ → Assets → Aktienradar.apk

## APK bauen – Weg A: GitHub (ohne PC-Software, geht auch vom Handy)

1. Auf github.com ein neues **privates** Repository anlegen (z. B. `aktienradar`).
2. „uploading an existing file“ wählen und den **Inhalt** dieses Ordners hochladen
   (alle Dateien und Ordner, inklusive `.github`, `gradle`, `app`, `gradlew`).
   Hinweis: Der Ordner `.github` ist versteckt. Falls er beim Hochladen fehlt, die Datei
   `.github/workflows/build.yml` im Repository über „Add file → Create new file“ mit genau
   diesem Pfad anlegen und den Inhalt hineinkopieren.
3. Reiter **Actions** öffnen → Workflow „APK bauen“ läuft automatisch (ca. 5–8 Minuten).
4. Den fertigen Lauf öffnen → unten unter „Artifacts“ **Aktienradar-APK** herunterladen,
   entpacken, die `.apk` aufs Handy kopieren und installieren
   („Installation aus unbekannten Quellen“ erlauben).

## APK bauen – Weg B: Android Studio

Ordner in Android Studio öffnen → *Build → Build App Bundle(s) / APK(s) → Build APK(s)*.
Die APK liegt danach unter `app/build/outputs/apk/`.

## Erster Start

1. Kontakt-E-Mail eintragen (die SEC verlangt sie bei jeder Datenabfrage).
2. Benachrichtigungen erlauben.
3. Mit **+** ein US-Ticker-Symbol hinzufügen (z. B. MSFT, COST, CRWD).
4. In der Detailansicht eine These und Bedingungen hinterlegen
   (z. B. „Umsatzwachstum mindestens 20 %“, „Aktienanzahl höchstens 2 %“).

## Was die App bewertet (Score, Start 50 Punkte)

| Signal | Punkte |
|---|---|
| Umsatzwachstum ≥ 25 % / ≥ 10 % / negativ | +12 / +6 / −10 |
| Wachstum beschleunigt / verlangsamt (letzte 2 vs. vorherige 2 Quartale) | +10 / −10 |
| Operative Marge (12 Monate) steigt ≥ 1 Pp. / sinkt ≥ 2 Pp. | +8 / −8 |
| Bruttomarge steigt / sinkt | +4 / −4 |
| Free Cashflow positiv / negativ | +6 / −8 |
| Free Cashflow verbessert ≥ 15 % / fällt ≥ 25 % | +6 / −8 |
| Aktienrückkäufe / Verwässerung ≥ 3 % | +4 / −6 |

≥ 75 🟢 starke Konstellation · ≥ 60 🟡 beobachten · ≥ 40 🟠 These prüfen · < 40 🔴 mehrere negative Veränderungen.
Die Gewichte sind Heuristiken und nicht durch Backtests belegt.

## Grenzen dieser Version

- Nur bei der SEC meldepflichtige Unternehmen (v. a. US); Banken/Versicherer liefern oft keine passenden Umsatzfelder.
- Noch keine Kurse, also auch keine Bewertungskennzahlen (KGV, EV/EBITDA) und kein Momentum.
- Keine Analystenschätzungen, Insiderdaten oder News.
- Kein Backtesting.

Keine Anlageberatung – die App liefert Hinweise zur eigenen Prüfung.

# Latvia Events

A private Android app showing what's on within ~100 km of Riga: car and motorsport events, enduro and MX, ATV, festivals, exhibitions, food markets, outdoor activities and orienteering.

## Install on your phone

1. On the phone, open **https://github.com/artisonck-spec/latvia-events/releases/latest** and tap `latvia-events.apk`.
2. Open the download. Allow "Install unknown apps" for your browser if Android asks.
3. If Play Protect warns about an unknown app, choose **Install anyway**.

After that, the app updates itself: when a new build is out, it shows **Update available → Update now**.

## How it works

- `data/events.json`: the event list. A scheduled search updates it twice a week; the app downloads it every time you open it, so new events need no app update.
- `data/app.json`: newest build number and APK link. The app compares it with its own build and offers the update.
- `app/src/main/assets/index.html`: the whole app screen (home, calendar, map, saved, settings). The Kotlin part (`MainActivity.kt`) is a thin shell: WebView, external links, and the self-update download.
- `.github/workflows/build-apk.yml`: every change to the app builds the APK, publishes a release and bumps `data/app.json`.
- `.github/workflows/data.yml`: checks the event list and adds a picture to each event from its own page (og:image).
- `tools/validate_events.py`: rules for the event list (dates, coordinates in Latvia, https links, known categories).

## Event fields

`id`, `title`, `category` (cars, moto, atv, festival, market, exhibition, outdoor, orienteering, tips), `start`/`end` (YYYY-MM-DD) or `always: true`, optional `dates` list, `time`, `place`, `town`, `lat`, `lon`, `price`, `price_note`, `url` (official page), `source` (where it was found), `desc`, `verified` (false for unconfirmed tips), optional `image`.

## Suggest an event

Open an issue with the `tip` label (the app's **Suggest an event** button does this). The next scan checks it.

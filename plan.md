# Plan: Ryciny jako dodatek do Home Assistant (HA OS)

Cel: mikrofon wpięty do komputera z Home Assistant zbiera dźwięki 24/7, a telefon, tablet czy później
ekran e-ink tylko wyświetla ryciny. Wszystko, co dziś robi aplikacja na Androida (ptaki, psy, 34 dźwięki,
samoloty, pociągi, generowanie rycin), przenosi się do jednego dodatku HA. Aplikacja dostaje tryb „tylko ekran”.

Plan czeka na mikrofon. Nic z niego nie jest jeszcze zbudowane.

## Sprzęt

- Komputer z **HA OS**: Raspberry Pi 4/5, NUC albo maszyna wirtualna. BirdNET FP32 liczy 3 s nagrania
  w ok. 0,3–0,6 s na Pi 4, więc zapas jest duży.
- **Karta dźwiękowa USB z wejściem mikrofonowym** (np. UGREEN, ok. 40 zł). Daje zasilanie, którego potrzebuje
  mikrofon elektretowy (EM272, smartLav+ przez przejściówkę TRRS → TRS).
- Mikrofon za oknem, w osłonie od wiatru, na krótkim kablu. Jeśli HA stoi daleko, karta dźwiękowa leży przy oknie,
  a do komputera idzie przedłużacz USB (cyfrowo, bez szumu). Szczegóły w [docs/sprzet.md](docs/sprzet.md).

## Architektura

```
mikrofon ─ karta USB ─ HA OS ─ dodatek „Ryciny” (Docker, Python)
                                 ├─ nasłuch: PulseAudio z Supervisora, 48 kHz, okna 3 s
                                 ├─ BirdNET 6K v2.4 + filtr zasięgu   (te same .tflite co w APK)
                                 ├─ YAMNet: psy, 34 dźwięki, silniki  (shared/sounds.json)
                                 ├─ samoloty: adsb.lol + adsbdb        (co 10 s)
                                 ├─ pociągi: GTFS mkuran.pl            (raz dziennie)
                                 ├─ ryciny: fugleramme + OpenAI/Gemini/OpenRouter, wycinanie z tła
                                 ├─ /data: journal.json, art/, clips/, settings.json
                                 ├─ HTTP API (ingress + port w LAN) ──► aplikacja na Androida w trybie „tylko ekran”
                                 ├─ strona WWW z ryciną (ingress)  ──► panel w HA, przeglądarka, później e-ink
                                 └─ zdarzenia do HA (MQTT albo REST) ─► automatyzacje
```

Jeden dodatek zamiast BirdNET-Go i osobnej usługi. Mikrofon czyta jeden proces, wszystkie modele dostają
to samo okno 3 s, a Dziennik ma jeden format. BirdNET-Go zostaje alternatywą, gdyby był potrzebny jego panel WWW.

## Dodatek

Repozytorium dodatków w tym samym repo, żeby w HA wystarczyło dodać adres `https://github.com/qunabu/ryciny`:

```
repository.yaml                 nazwa repozytorium dodatków
homeassistant/ryciny/
  config.yaml                   audio: true, ingress: true, ports: 8099, map: share, options (klucze API, miejsce)
  Dockerfile                    obraz bazowy HA (Python 3.12) + ai-edge-litert, numpy, soundfile
  run.sh
  app/
    main.py                     start: nasłuch, pętle samolotów i pociągów, serwer HTTP
    audio.py                    PulseAudio → okna 3 s (port ListenService)
    analyzer.py                 BirdNET + YAMNet, minHits, pomijanie (port Analyzer.kt)
    dsp.py                      decymacja 48→16 kHz, wysokość szczekania, WAV (port Dsp.kt)
    store.py                    journal.json, ten sam schemat co Journal w Store.kt
    sky.py                      adsb.lol, adsbdb, onRoute (port Sky.kt)
    rails.py                    GTFS → przejazdy obok domu (port Timetable.kt + Rails.kt)
    art.py                      fugleramme, generatory, cache, wycinanie z tła (port Art.kt + Cutout.kt)
    api.py                      HTTP API + strona WWW
    ha.py                       zdarzenia do Home Assistant
  models/                       birdnet.tflite, birdnet_meta.tflite, yamnet.tflite, etykiety (z android/…/assets)
shared/                         style.json, sounds.json, dog_breeds.json, aircraft_types.json (bez zmian)
```

Opcje dodatku w UI Home Assistant: dostawca rycin i klucz (OpenAI, Gemini, OpenRouter), szerokość i długość
geograficzna (domyślnie z konfiguracji HA), promień samolotów, progi. Klucze trafiają do `options.json` dodatku,
nie do repo.

## API (dla aplikacji i strony)

| Metoda | Ścieżka | Co robi |
| --- | --- | --- |
| GET | `/api/journal?since=…` | Dziennik (ptaki, szczekania, psy, samoloty, dźwięki), ten sam JSON co w aplikacji |
| GET | `/api/live` | stan na żywo: czy słucha, co słychać, samolot nad głową (SSE albo odpytywanie co kilka s) |
| GET | `/api/art/{kind}/{key}.png` | rycina; jeśli jej nie ma, zamawia generowanie i zwraca 202 |
| GET | `/api/clips/{id}.wav` | nagranie szczekania do odsłuchu |
| POST | `/api/barks/{id}/tag` | „Kto to?”: przypisanie szczekania do psa, nowego albo znanego |
| PUT | `/api/dogs/{id}` | edycja i usuwanie psa |
| GET/PUT | `/api/settings` | przełączniki dźwięków, tylko słyszane samoloty, progi |
| POST | `/api/import` | wczytanie ZIP-a z eksportu aplikacji (ryciny, psy z próbkami, Dziennik) |

W sieci domowej bez logowania, przez ingress za logowaniem HA. Dostęp z zewnątrz: przez Nabu Casa albo VPN.

## Home Assistant

- Encje: `sensor.ryciny_ostatni_ptak`, `sensor.ryciny_samolot`, `sensor.ryciny_pociag` (z trasą w atrybutach),
  `binary_sensor.ryciny_szczekanie`, `event.ryciny_dzwiek` (typ: kosiarka, burza, karetka…).
- Przez MQTT (dodatek Mosquitto, autodiscovery) albo, bez MQTT, przez REST API Supervisora tokenem dodatku.
- Przykładowe automatyzacje do README: powiadomienie „przejeżdża PolRegio do Somonina”, zamknięcie okien przy
  burzy, światło w ogrodzie, gdy szczeka nieznany pies w nocy.

## Aplikacja na Androida: tryb „tylko ekran”

- Ustawienia → „Źródło”: **ten telefon** (jak dziś) albo **serwer Home Assistant** (adres, np. `http://homeassistant.local:8099`).
- W trybie serwera usługa nasłuchu się nie uruchamia, mikrofon nie jest potrzebny.
  - `Store` → `RemoteStore`: odpytuje `/api/journal`.
  - `Art` → pobiera ryciny z `/api/art`.
  - „Kto to?” i edycja psów idą do serwera.
- Rycina, Dziennik i Ustawienia wyglądają tak samo, bo zmienia się tylko źródło danych.
- Tablet przy ścianie w trybie ramki („Nie wygaszaj ekranu”) staje się samym ekranem.

## Ekran e-ink

Kolorowy e-ink z panelem E Ink Spectra 6, ten sam co w Inky Impression. W Polsce kupisz go jako Waveshare z HAT
(7,3" 800×480 za ok. 385 zł, 13,3" 1600×1200 za ok. 1320 zł); szczegóły w [docs/sprzet.md](docs/sprzet.md).

- **Gdzie działa:** HAT nakłada się na złącze GPIO Raspberry Pi. Jeśli HA działa na Raspberry Pi przy ramce, ekran
  może obsługiwać sam dodatek (dostęp do SPI i GPIO w `config.yaml`: `gpio: true`, `devices: /dev/spidev0.0`).
  Częściej HA stoi gdzie indziej, a przy ramce pracuje osobne Raspberry Pi Zero 2 W z małym programem `frame`.
- **Program `frame`:** co kilka minut pobiera z dodatku gotową grafikę `GET /api/plate.png?w=800&h=480`
  i wysyła ją na panel. Odświeża tylko przy zmianie, bo pełne odświeżenie Spectra 6 trwa 12–28 s i miga.
- **Rysowanie po stronie dodatku:** `/api/plate.png` składa jedną rycinę (jak ekran Rycina w aplikacji) na papierze,
  w rozmiarze panelu, z dithering do 6 kolorów panelu, czyli to samo, co robi `render/dither.py` w fugleramme.
  Ten sam obraz pokazuje strona WWW, więc wygląd sprawdzisz w przeglądarce bez ekranu.
- **Sterownik:** biblioteka Waveshare (`waveshare_epd`, model `epd7in3e` albo `epd13in3e`) zamiast Pimoroni `inky`.
  Mały interfejs `Panel.show(image)`, żeby obsłużyć oba (Inky też zadziała, gdyby kiedyś trafił się Pimoroni).

## Kolejność

1. **Rdzeń w Pythonie, bez HA:** BirdNET + YAMNet na pliku WAV, ten sam wynik co w aplikacji. Testy jak
   w Androidzie: `Timetable` na prawdziwym GTFS, `onRoute`, wysokość szczekania, `minHits`.
2. **Nasłuch na żywo** z karty USB (najpierw na Macu albo Linuksie, potem PulseAudio w HA) + `journal.json`.
3. **Samoloty, pociągi, ryciny** (porty `Sky`, `Rails`, `Art`, `Cutout`).
4. **API + prosta strona WWW** z ryciną i Dziennikiem (ingress w HA).
5. **Dodatek HA:** `config.yaml`, `Dockerfile`, obrazy na aarch64 i amd64 budowane w GitHub Actions,
   instalacja z adresu repozytorium.
6. **Encje i zdarzenia w HA.**
7. **Aplikacja w trybie „tylko ekran”** + import ZIP-a, żeby przenieść ryciny i nauczone psy z telefonu.
8. **Ekran e-ink:** `/api/plate.png` z ditheringiem do 6 kolorów, program `frame` na Raspberry Pi Zero 2 W, sterownik Waveshare.

Każdy krok da się sprawdzić osobno. Po kroku 4 ramka działa już w przeglądarce, bez aplikacji.

## Do ustalenia, gdy będzie mikrofon

- Gdzie stoi komputer z HA względem okna: długość kabla albo przedłużacza USB.
- Czy w HA jest Mosquitto (MQTT), czy zdarzenia mają iść przez REST.
- Czy panel w HA ma być osobną stroną (ingress), czy kartą na pulpicie.
- Licencja BirdNET (CC BY-NC-SA) dopuszcza użytek prywatny, więc dodatek nie może być sprzedawany.
